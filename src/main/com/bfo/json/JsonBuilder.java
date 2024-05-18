package com.bfo.json;

import java.nio.*;
import java.io.*;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

public class JsonBuilder implements JsonStream {
    
    private static final int BUILDING_KEYSTRING = 1, BUILDING_KEYBUFFER = 2, BUILDING_VALSTRING = 3, BUILDING_VALBUFFER = 4;
    private static final Object EMPTY = new Object();

    private boolean peof;
    private Json ctx;
    private Long tag;
    private List<Json> list;
    private Map<Object,Json> map;
    private Object key;
    private int building;

    public JsonBuilder() {
    }

    public Json build() {
        return peof && building == 0 ? ctx : null;
    }

    protected Json createMap(int size) {
        return Json.read("{}");
    }

    protected Json createList(int size) {
        return Json.read("[]");
    }

    protected Json createString(CharSequence value) {
        return new Json(value);
    }

    protected Json createString(long size) {
        Json json = new Json(Json.NULL);
        json.setStringByteLength(size < Integer.MAX_VALUE ? (int)size : -1);
        return json;
    }

    protected Json createBuffer(long size) {
        if (size < 0) {
            return new Json(new ExtendingByteBuffer());
        } else {
            return new Json(ByteBuffer.allocate((int)size));
        }
    }

    protected Json createNumber(Number n) {
        return new Json(n);
    }

    protected Json createBoolean(boolean b) {
        return new Json(b);
    }

    protected Json createNull() {
        return new Json(Json.NULL);
    }

    protected Json createUndefined() {
        return new Json(Json.UNDEFINED);
    }

    protected Json createSimple(int simple) {
        Json j = new Json(Json.UNDEFINED);
        j.setTag(simple);
        return j;
    }

    protected boolean isList() {
        return list != null;
    }

    protected boolean isMap() {
        return map != null;
    }

    protected Object key() {
        return list != null ? Integer.valueOf(list.size()) : key;
    }

    protected void appendString(Json json, CharSequence seq) {
        Object v = json.value();
        if (v == Json.NULL) {
            json._setValue(seq.toString());
        } else {
            if (!(v instanceof StringBuilder)) {
                v = new StringBuilder((CharSequence)v);
                json._setValue(v);
            }
            ((StringBuilder)v).append(seq);
        }
    }

    protected void appendString(Json json, Readable readable) throws IOException {
        Object v = json.value();
        if (!(v instanceof StringBuilder)) {
            v = new StringBuilder();
            json._setValue(v);
        }
        CharBuffer buf = CharBuffer.allocate(8192);
        while (readable.read(buf) >= 0) {
            buf.flip();
            ((StringBuilder)v).append(buf);
            buf.clear();
        }
    }

    protected void closeString(Json json) {
        Object v = json.value();
        if (v == Json.NULL) {
            json._setValue("");
        } else if (v instanceof StringBuilder) {
            json._setValue(((StringBuilder)v).toString());
        }
    }

    protected void appendBuffer(Json json, ByteBuffer seq) {
        Object v = json.value();
        if (v instanceof ByteBuffer) {
            ((ByteBuffer)v).put(seq);
        } else {
            ((ExtendingByteBuffer)v).write(seq);
        }
    }

    protected void appendBuffer(Json json, ReadableByteChannel readable) throws IOException {
        Object v = json.value();
        if (v instanceof ByteBuffer) {
            ByteBuffer b = (ByteBuffer)v;
            b.flip();
            v = new ExtendingByteBuffer();
            json._setValue(v);
            ((ExtendingByteBuffer)v).write(b);
        }
        ((ExtendingByteBuffer)v).write(readable);
    }

    protected void closeBuffer(Json json) {
        Object v = json.value();
        if (v instanceof ExtendingByteBuffer) {
            ExtendingByteBuffer eb = (ExtendingByteBuffer)v;
            ByteBuffer buf = eb.toByteBuffer();
            json._setValue(buf);
        }
    }

    final String eoferror() {
        if (ctx == null) {
            return "Input is empty";
        } else if (building == BUILDING_KEYSTRING || building == BUILDING_VALSTRING) {
            return "Unterminated string";
        } else if (building != 0) {
            return "Unterminated buffer";
        } else if (ctx.isList()) {
            return "Unterminated array";
        } else if (ctx.isMap()) {
            return "Unterminated object";
        } else {
            return "Completed";
        }
    }

    @Override public boolean event(JsonStream.Event event) throws IOException {
        final int type = event.type();
        boolean ret;
        switch(type) {
            case JsonStream.Event.TYPE_STARTMAP: {
                if (peof) {
                    throw new IllegalStateException(eoferror());
                }
                Json j = createMap((int)event.size());
                if (list != null) {
                    Integer size = Integer.valueOf(list.size());
                    list.add(j);
                    Json.notifyDuringLoad(ctx, size, j);
                    list = null;
                } else if (key != null) {
                    map.put(key, j);
                    Json.notifyDuringLoad(ctx, key, j);
                    key = null;
                } else if (map != null) {
                    throw new IllegalStateException("Object as key not supported");
                }
                ctx = j;
                if (tag != null) {
                    ctx.setTag(tag);
                    tag = null;
                }
                map = ctx._mapValue();
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_STARTLIST: {
                if (peof) {
                    throw new IllegalStateException(eoferror());
                }
                Json j = createList((int)event.size());
                if (list != null) {
                    Integer size = Integer.valueOf(list.size());
                    list.add(j);
                    Json.notifyDuringLoad(ctx, size, j);
                } else if (key != null) {
                    map.put(key, j);
                    Json.notifyDuringLoad(ctx, key, j);
                    key = null;
                    map = null;
                } else if (map != null) {
                    throw new IllegalStateException("List as key not supported");
                }
                ctx = j;
                if (tag != null) {
                    ctx.setTag(tag);
                    tag = null;
                }
                list = ctx._listValue();
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_ENDMAP: {
                if (map != null && key == null && tag == null) {
                    Json j = ctx.parent();
                    if (j == null) {
                        map = null;
                        peof = true;
                        ret = true;
                    } else {
                        ctx = j;
                        if (ctx.isList()) {
                            list = ctx._listValue();
                            map = null;
                        } else {
                            map = ctx._mapValue();
                        }
                        ret = false;
                    }
                } else {
                    throw new IllegalStateException("Unexpected object close");
                }
                break;
            }
            case JsonStream.Event.TYPE_ENDLIST: {
                if (list != null && tag == null) {
                    Json j = ctx.parent();
                    if (j == null) {
                        list = null;
                        peof = true;
                        ret = true;
                    } else {
                        ctx = j;
                        if (ctx.isList()) {
                            list = ctx._listValue();
                        } else {
                            list = null;
                            map = ctx._mapValue();
                        }
                        ret = false;
                    }
                } else {
                    throw new IllegalStateException("Unexpected list close");
                }
                break;
            }
            case JsonStream.Event.TYPE_PRIMITIVE: {
                if (peof) {
                    throw new IllegalStateException(eoferror());
                } else {
                    if (map != null && key == null) {
                        key = event.value();
                        if (tag != null) {
                            tag = null; // throw new IllegalStateException("Can't tag keys");
                        }
                        ret = false;
                    } else {
                        Json j;
                        final Object value = event.value();
                        if (value instanceof Number) {
                            j = createNumber((Number)value);
                        } else if (value instanceof Boolean) {
                            j = createBoolean((Boolean)value);
                        } else if (value instanceof CharSequence) {
                            j = createString((CharSequence)value);
                        } else if (event.isNull()) {
                            j = createNull();
                        } else if (event.isUndefined()) {
                            j = createUndefined();
                        } else {
                            throw new IllegalStateException("Unsupported primitive data value " + (value == null ? null : value.getClass().getName()));
                        }
                        if (list != null) {
                            Integer size = Integer.valueOf(list.size());
                            list.add(j);
                            Json.notifyDuringLoad(ctx, size, j);
                            ret = false;
                        } else if (key != null) {
                            map.put(key, j);
                            Json.notifyDuringLoad(ctx, key, j);
                            key = null;
                            ret = false;
                        } else {
                            ctx = j;
                            peof = true;
                            ret = true;
                        }
                        if (tag != null) {
                            j.setTag(tag);
                            tag = null;
                        }
                    }
                }
                break;
            }
            case JsonStream.Event.TYPE_STARTSTRING: {
                if (peof) {
                    throw new IllegalStateException(eoferror());
                } else {
                    if (map != null && key == null) {
                        key = EMPTY;
                        if (tag != null) {
                            tag = null; // throw new IllegalStateException("Can't tag keys");
                        }
                        building = BUILDING_KEYSTRING;
                        peof = true;
                    } else {
                        Json j = createString(event.size());
                        if (list != null) {
                            Integer size = Integer.valueOf(list.size());
                            list.add(j);
                            Json.notifyDuringLoad(ctx, size, j);
                        } else if (key != null) {
                            map.put(key, j);
                            Json.notifyDuringLoad(ctx, key, j);
                            key = null;
                        }
                        ctx = j;
                        if (tag != null) {
                            ctx.setTag(tag);
                            tag = null;
                        }
                        building = BUILDING_VALSTRING;
                        peof = true;
                    }
                }
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_STARTBUFFER: {
                if (peof) {
                    throw new IllegalStateException(eoferror());
                } else {
                    if (map != null && key == null) {
                        key = EMPTY;
                        if (tag != null) {
                            tag = null; // throw new IllegalStateException("Can't tag keys");
                        }
                        peof = true;
                        building = BUILDING_KEYBUFFER;
                    } else {
                        Json j = createBuffer(event.size());
                        if (list != null) {
                            Integer size = Integer.valueOf(list.size());
                            list.add(j);
                            Json.notifyDuringLoad(ctx, size, j);
                        } else if (key != null) {
                            map.put(key, j);
                            Json.notifyDuringLoad(ctx, key, j);
                            key = null;
                        }
                        ctx = j;
                        if (tag != null) {
                            ctx.setTag(tag);
                            tag = null;
                        }
                        building = BUILDING_VALBUFFER;
                        peof = true;
                    }
                }
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_STRINGDATA: {
                CharSequence seq = event.stringValue();
                if (building == BUILDING_KEYSTRING) {
                    if (seq != null) {
                        if (key == EMPTY) {
                            key = seq.toString();
                        } else {
                            if (!(key instanceof StringBuilder)) {
                                key = new StringBuilder((CharSequence)key);
                            }
                            ((StringBuilder)key).append(seq);
                        }
                    } else {
                        Readable r = event.readableValue();
                        key = new StringBuilder();
                        CharBuffer cb = CharBuffer.allocate(8192);
                        while (r.read(cb) > 0) {
                            cb.flip();
                            ((StringBuilder)key).append(cb);
                            cb.clear();
                        }
                    }
                } else if (building == BUILDING_VALSTRING) {
                    if (seq != null) {
                        appendString(ctx, seq);
                    } else {
                        appendString(ctx, event.readableValue());
                    }
                } else {
                    throw new IllegalStateException("String not started");
                }
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_BUFFERDATA: {
                ByteBuffer buf = event.bufferValue();
                if (building == BUILDING_KEYBUFFER) {
                    if (buf != null) {
                        if (key == EMPTY) {
                            key = new ExtendingByteBuffer();
                        }
                        ((ExtendingByteBuffer)key).write(buf);
                    } else {
                        ReadableByteChannel r = event.readableByteChannelValue();
                        ((ExtendingByteBuffer)key).write(r);
                    }
                } else if (building == BUILDING_VALBUFFER) {
                    if (buf != null) {
                        appendBuffer(ctx, buf);
                    } else {
                        appendBuffer(ctx, event.readableByteChannelValue());
                    }
                } else {
                    throw new IllegalStateException("Buffer not started");
                }
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_ENDSTRING: {
                if (building == BUILDING_KEYSTRING) {
                    key = key == EMPTY ? "" : key.toString();
                    ret = false;
                } else if (building == BUILDING_VALSTRING) {
                    closeString(ctx);
                    Json j = ctx.parent();
                    if (j == null) {
                        peof = true;
                        ret = true;
                    } else {
                        ctx = j;
                        ret = false;
                    }
                } else {
                    throw new IllegalStateException("String not started");
                }
                building = 0;
                if (list != null || map != null) {
                    peof = false;
                }
                break;
            }
            case JsonStream.Event.TYPE_ENDBUFFER: {
                if (building == BUILDING_KEYBUFFER) {
                    key = key == EMPTY ? ByteBuffer.wrap(new byte[0]) : ((ExtendingByteBuffer)key).toByteBuffer();
                    ret = false;
                } else if (building == BUILDING_VALBUFFER) {
                    closeBuffer(ctx);
                    Json j = ctx.parent();
                    if (j == null) {
                        peof = true;
                        ret = true;
                    } else {
                        ctx = j;
                        ret = false;
                    }
                } else {
                    throw new IllegalStateException("Buffer not started");
                }
                building = 0;
                if (list != null || map != null) {
                    peof = false;
                }
                break;
            }
            case JsonStream.Event.TYPE_TAG: {
                if (peof || tag != null) {
                    throw new IllegalStateException(eoferror());
                } else {
                    tag = event.tagValue();
                }
                ret = false;
                break;
            }
            case JsonStream.Event.TYPE_SIMPLE: {
                if (peof) {
                    throw new IllegalStateException(eoferror());
                } else {
                    Json j = createSimple(event.numberValue().intValue());
                    if (list != null) {
                        Integer size = Integer.valueOf(list.size());
                        list.add(j);
                        Json.notifyDuringLoad(ctx, size, j);
                        ret = false;
                    } else if (key != null) {
                        map.put(key, j);
                        Json.notifyDuringLoad(ctx, key, j);
                        key = null;
                        ret = false;
                    } else {
                        ctx = j;
                        peof = true;
                        ret = true;
                    }
                }
                break;
            }
            default:
                throw new IllegalStateException("Unknown event 0x" + type);
        }
//        System.out.println("BUILDER: " + event +" = " + ret);
        return ret;
    }

}
