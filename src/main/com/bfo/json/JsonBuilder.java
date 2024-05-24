package com.bfo.json;

import java.nio.*;
import java.io.*;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

/**
 * A {@link JsonStream} that can be used to build a {@link Json} object by calling {@link #build} when complete.
 * Extending this class and overriding the various <code>create...</code> method can be used for special
 * processing, such as storing large strings or buffers on disk, for example
 * <pre class="brush:java">
 * import java.lang.ref.Cleaner;
 * import java.io.*;
 * class StoringJsonBuilder extends JsonBuilder {
 *     final Cleaner cleaner = Cleaner.create();
 *     @Override protected Json createBuffer(long size) {
 *         return new StoringJson(cleaner);
 *     }
 *     @Override protected Json appendBuffer(Json json, ByteBuffer buf) {
 *         ((StoringJson)json).builder.write(buf);
 *     }
 *     @Override protected Json closeBuffer(Json json) {
 *         ((StoringJson)json).builder.close();
 *         ((StoringJson)json).builder = null;
 *     }
 * }
 * class StoringJson extends Json {
 *     final String file = "/tmp/json." + (new Random().nextLong());
 *     OutputStream builder;
 *     StoringJson(Cleaner cleaner) {
 *         super(ByteBuffer.allocate(0));
 *         builder = new FileOutputStream(file);
 *         cleaner.register(this, new Runnable() {
 *             public void run() {
 *                 new File(file).delete();
 *             }
 *         });
 *     }
 *     @Override protected InputStream getBufferStream() {
 *         return new FileInputStream(id);
 *     }
 *     @Override protected long getBufferStreamSize() {
 *         return new File(id).length();
 *     }
 * }
 * </pre>
 */
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

    /**
     * If the stream is complete, return the {@link Json} object created by this builder,
     * or <code>null</code> if the builder is incomplete
     */
    public Json build() {
        return peof && building == 0 ? ctx : null;
    }

    /**
     * Create a new Json map
     * @param size the number of entries in the map, or -1 or unknown
     */
    protected Json createMap(int size) {
        return Json.read("{}");
    }

    /**
     * Create a new Json list
     * @param size the number of items in the list, or -1 or unknown
     */
    protected Json createList(int size) {
        return Json.read("[]");
    }

    /**
     * Create a new Json String
     * @param value the value
     */
    protected Json createString(CharSequence value) {
        return new Json(value);
    }

    /**
     * Create a new Json String, which will be appended to by calls to {@link #appendString} and {@link #closeString}
     * @param size the length of the UTF-8 value of the string in bytes, or -1 if unknown
     */
    protected Json createString(long size) {
        Json json = new Json(Json.NULL);
        json.setStringByteLength(size < Integer.MAX_VALUE ? (int)size : -1);
        return json;
    }

    /**
     * Create a new Json Buffer, which will be appended to by calls to {@link #appendBuffer} and {@link #closeBuffer}
     * @param size the length of the buffer in bytes, or -1 if unknown
     */
    protected Json createBuffer(long size) {
        if (size < 0) {
            return new Json(new ExtendingByteBuffer());
        } else {
            return new Json(ByteBuffer.allocate((int)size));
        }
    }

    /**
     * Create a new Json number
     * @param n the number
     */
    protected Json createNumber(Number n) {
        return new Json(n);
    }

    /**
     * Create a new Json boolean
     * @param b the value
     */
    protected Json createBoolean(boolean b) {
        return new Json(b);
    }

    /**
     * Create a new Json null
     */
    protected Json createNull() {
        return new Json(Json.NULL);
    }

    /**
     * Create a new Json undefined value
     */
    protected Json createUndefined() {
        return new Json(Json.UNDEFINED);
    }

    /**
     * Create a new Json representing the specified CBOR "simple" type.
     * @param simple the simple type
     */
    protected Json createSimple(int simple) {
        Json j = new Json(Json.UNDEFINED);
        j.setTag(simple);
        return j;
    }

    /**
     * Return true if any objects being created will be added to a list
     */
    public boolean isList() {
        return list != null;
    }

    /**
     * Return true if any objects being created are being added to a map
     */
    public boolean isMap() {
        return map != null;
    }

    /**
     * Return the key which the next object being created will be stored in its parent,
     * or null if the next object to be created is a map key. If {@link #isList}, the
     * key is an integer which is the index into the list
     */
    public Object key() {
        return list != null ? Integer.valueOf(list.size()) : key;
    }

    /**
     * Append the supplied CharSequence to a string Json created earlier with {@link #createString(long)}
     * @param json the string Json
     * @param seq the sequence
     */
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

    /**
     * Append the data from the supplied Readable to a string Json created earlier with {@link #createString(long)}
     * @param json the string Json
     * @param readable the Readable
     */
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

    /**
     * Close a string Json created earlier with {@link #createString(long)}
     */
    protected void closeString(Json json) {
        Object v = json.value();
        if (v == Json.NULL) {
            json._setValue("");
        } else if (v instanceof StringBuilder) {
            json._setValue(((StringBuilder)v).toString());
        }
    }

    /**
     * Append the supplied ByteBuffer to a buffer Json created earlier with {@link #createBuffer(long)}
     * @param json the buffer Json
     * @param seq the data
     */
    protected void appendBuffer(Json json, ByteBuffer seq) {
        Object v = json.value();
        if (v instanceof ByteBuffer) {
            ((ByteBuffer)v).put(seq);
        } else {
            ((ExtendingByteBuffer)v).write(seq);
        }
    }

    /**
     * Append the data from the supplied ReadableByteChannel to a string Json created earlier with {@link #createString(long)}
     * @param json the string Json
     * @param readable the Readable
     */
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

    /**
     * Close a buffer Json created earlier with {@link #createString(long)}
     */
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
