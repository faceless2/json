package com.bfo.json;

import java.nio.*;
import java.io.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.math.*;
import java.text.*;

public class MsgpackWriter implements JsonStream {
    
    private static final int STATE_DONE = 0;
    private static final int STATE_MAP = 1;
    private static final int STATE_LIST = 2;
    private static final int STATE_STRING = 3;
    private static final int STATE_BUFFER = 4;
    private static final int NO_TAG = -1;

    private OutputStream out;
    private long[] stack;
    private int stackLength;
    private long state;
    private long length;
    private boolean sorted;
    private int tag;
    private StringBuilder indeterminateStringBuilder;
    private ExtendingByteBuffer indeterminateBuffer;

    public MsgpackWriter() {
        stack = new long[32];
        state = STATE_DONE;
        tag = NO_TAG;
    }

    public MsgpackWriter setOutput(OutputStream out) {
        this.out = out;
        return this;
    }

    public MsgpackWriter setSorted(boolean sorted) {
        this.sorted = sorted;
        return this;
    }

    public boolean isSorted() {
        return sorted;
    }

    private static String stateString(long state) {
        switch ((int)state) {
            case STATE_DONE: return "done";
            case STATE_MAP: return "map";
            case STATE_LIST: return "list";
            case STATE_STRING: return "string";
            case STATE_BUFFER: return "buffer";
            default: return "unknown-"+state;
        }
    }
    private String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i=0;i<stackLength;) {
            if (i > 0) {
                sb.append(", ");
            }
            long len = stack[i++];
            long state = stack[i++];
            sb.append("{" + stateString(state) + " len=" + len + "}");
        }
        sb.append("]");
        sb.append(" state="+stateString(state)+" len="+length);
        return sb.toString();
    }

    @Override public boolean event(JsonStream.Event event) throws IOException {
        if (stackLength == 0) {
            stack[stackLength++] = length = 1;
            stack[stackLength++] = STATE_DONE;
        }
        final int type = event.type();
        long decrement = 0;
        int l;
//        System.out.println("WRITER: e="+event+" " + dump());
        switch(type) {
            case JsonStream.Event.TYPE_STARTMAP:
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_MAP;
                length = event.size();
                if (length < 0 || length > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Invalid Msgpack map size " + (length < 0 ? "'indeterminate'" : Long.valueOf(length)));
                }
                l = (int)length;
                if (length <= 15) {
                    out.write(0x80 | l);
                } else if (l <= 65535) {
                    out.write(0xde);
                    out.write(l>>8);
                    out.write(l);
                } else {
                    out.write(0xdf);
                    out.write(l>>24);
                    out.write(l>>16);
                    out.write(l>>8);
                    out.write(l);
                }
                length *= 2;
                tag = NO_TAG;
                break;
            case JsonStream.Event.TYPE_STARTLIST:
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_LIST;
                length = event.size();
                if (length < 0 || length > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Invalid Msgpack map size " + (length < 0 ? "'indeterminate'" : Long.valueOf(length)));
                }
                l = (int)length;
                if (l <= 15) {
                    out.write(0x90 | l);
                } else if (l <= 65535) {
                    out.write(0xdc);
                    out.write(l>>8);
                    out.write(l);
                } else {
                    out.write(0xdd);
                    out.write(l>>24);
                    out.write(l>>16);
                    out.write(l>>8);
                    out.write(l);
                }
                tag = NO_TAG;
                break;
            case JsonStream.Event.TYPE_ENDLIST:
                if (length > 0 || (state = stack[--stackLength]) != STATE_LIST) {
                    throw new IllegalStateException("Unexpected end-list");
                }
                length = stack[--stackLength];
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_ENDMAP:
                if (length > 0 || (state = stack[--stackLength]) != STATE_MAP) {
                    throw new IllegalStateException("Unexpected end-map");
                }
                length = stack[--stackLength];
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_STARTBUFFER:
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_BUFFER;
                length = event.size();
                l = (int)length;
                if (length > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Invalid Msgpack buffer size " + (length < 0 ? "'indeterminate'" : Long.valueOf(length)));
                } else if (length < 0) {
                    indeterminateBuffer = new ExtendingByteBuffer();
                    break;
                }
                writeStartBuffer(l, tag);
                tag = NO_TAG;
                break;
            case JsonStream.Event.TYPE_STARTSTRING:
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_STRING;
                length = event.size();
                if (length > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Invalid Msgpack string size " + (length < 0 ? "'indeterminate'" : Long.valueOf(length)));
                } else if (length < 0) {
                    indeterminateStringBuilder = new StringBuilder();
                    break;
                }
                l = (int)length;
                writeStartString(l);
                tag = NO_TAG;
                break;
            case JsonStream.Event.TYPE_STRINGDATA:
                if (state != STATE_STRING) {
                    throw new IllegalStateException("Unexpected string-data");
                }
                if (event.stringValue() != null) {
                    final CharSequence v = event.stringValue();
                    if (indeterminateStringBuilder != null) {
                        indeterminateStringBuilder.append(v);
                    } else {
                        decrement = CborWriter.writeUTF8(v, 0, out);
                    }
                } else {
                    final Readable r = event.readableValue();
                    CharBuffer buf = CharBuffer.allocate(8192);
                    while (r.read(buf) > 0) {
                        buf.flip();
                        if (indeterminateStringBuilder != null) {
                            indeterminateStringBuilder.append(buf);
                        } else {
                            decrement += CborWriter.writeUTF8(buf, 0, out);
                        }
                        buf.clear();
                    }
                    if (r instanceof Closeable) {
                        ((Closeable)r).close();
                    }
                }
                break;
            case JsonStream.Event.TYPE_BUFFERDATA:
                if (state != STATE_BUFFER) {
                    throw new IllegalStateException("Unexpected buffer-data");
                }
                if (event.bufferValue() != null) {
                    final ByteBuffer buffer = event.bufferValue();
                    if (indeterminateBuffer != null) {
                        indeterminateBuffer.write(buffer);
                    } else {
                        l = buffer.remaining();
                        if (buffer.isDirect()) {
                            if (out instanceof WritableByteChannel) {
                                ((WritableByteChannel)out).write(buffer);
                            } else {
                                ByteBuffer copy = ByteBuffer.allocate(l);
                                copy.put(buffer);
                                out.write(copy.array(), 0, copy.limit());
                            }
                        } else {
                            out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), l);
                        }
                        decrement = l;
                    }
                } else {
                    final ReadableByteChannel r = event.readableByteChannelValue();
                    ByteBuffer buf = ByteBuffer.allocate(8192);
                    while (r.read(buf) > 0) {
                        buf.flip();
                        if (indeterminateBuffer != null) {
                            indeterminateBuffer.write(buf);
                        } else {
                            out.write(buf.array(), 0, buf.remaining());
                            decrement += buf.remaining();
                        }
                        buf.clear();
                    }
                    r.close();
                }
                break;
            case JsonStream.Event.TYPE_ENDSTRING:
                if ((state = stack[--stackLength]) != STATE_STRING) {
                    throw new IllegalStateException("Unexpected end-string");
                } else if (indeterminateStringBuilder != null) {
                    int utf8l = CborWriter.lengthUTF8(indeterminateStringBuilder);
                    writeStartString(utf8l);
                    CborWriter.writeUTF8(indeterminateStringBuilder, utf8l, out);
                    indeterminateStringBuilder = null;
                } else if (length > 0) {
                    throw new IllegalStateException("Unexpected end-string (expecting data)");
                }
                length = stack[--stackLength];
                decrement = 1;
                tag = NO_TAG;
                break;
            case JsonStream.Event.TYPE_ENDBUFFER:
                if ((state = stack[--stackLength]) != STATE_BUFFER) {
                    throw new IllegalStateException("Unexpected end-buffer");
                } else if (indeterminateBuffer != null) {
                    l = indeterminateBuffer.size();
                    writeStartBuffer(l, tag);
                    indeterminateBuffer.writeTo(out);
                    indeterminateBuffer = null;
                } else if (length > 0) {
                    throw new IllegalStateException("Unexpected end-buffer");
                }
                length = stack[--stackLength];
                decrement = 1;
                tag = NO_TAG;
                break;
            case JsonStream.Event.TYPE_TAG:
                tag = (int)event.tagValue();
                break;
            case JsonStream.Event.TYPE_PRIMITIVE:
                final Object value = event.value();
                if (value instanceof Number) {
                    Number n = (Number)value;
                    if (n instanceof BigDecimal) {      // No BigDecimal in MsgPack
                        n = Double.valueOf(n.doubleValue());
                    }
                    if (n instanceof BigInteger) {
                        BigInteger bi = (BigInteger)n;
                        int bl = bi.bitLength();
                        if (bl == 64 && bi.signum() > 0) {
                            long lv = bi.longValue();
                            out.write(0xcf);
                            out.write((int)(lv>>56));
                            out.write((int)(lv>>48));
                            out.write((int)(lv>>40));
                            out.write((int)(lv>>32));
                            out.write((int)(lv>>24));
                            out.write((int)(lv>>16));
                            out.write((int)(lv>>8));
                            out.write((int)lv);
                        } else if (bl <= 64) {
                            n = Long.valueOf(bi.longValue());
                        } else {
                            throw new IllegalArgumentException("Cannot write BigInteger "+bi+" to Msgpack");
                        }
                    }
                    if (n instanceof Long) {
                        long lv = n.longValue();
                        if (lv < Integer.MIN_VALUE) {
                            out.write(0xd3);
                            out.write((int)(lv>>56));
                            out.write((int)(lv>>48));
                            out.write((int)(lv>>40));
                            out.write((int)(lv>>32));
                            out.write((int)(lv>>24));
                            out.write((int)(lv>>16));
                            out.write((int)(lv>>8));
                            out.write((int)lv);
                        } else if (lv > Integer.MAX_VALUE) {
                            out.write(0xcf);
                            out.write((int)(lv>>56));
                            out.write((int)(lv>>48));
                            out.write((int)(lv>>40));
                            out.write((int)(lv>>32));
                            out.write((int)(lv>>24));
                            out.write((int)(lv>>16));
                            out.write((int)(lv>>8));
                            out.write((int)lv);
                        } else {
                            n = Integer.valueOf((int)lv);
                        }
                    }
                    if (n instanceof Integer || n instanceof Short || n instanceof Byte) {
                        int i = n.intValue();
                        if (i >= -32 && i < 127) {
                            out.write(i);
                        } else if (i > 0) {
                            if (i <= 0xFF) {
                                out.write(0xcc);
                                out.write(i);
                            } else if (i <= 0xFFFF) {
                                out.write(0xcd);
                                out.write(i>>8);
                                out.write(i);
                            } else {
                                out.write(0xce);
                                out.write(i>>24);
                                out.write(i>>16);
                                out.write(i>>8);
                                out.write(i);
                            }
                        } else if (i >= -128) {
                            out.write(0xd0);
                            out.write(i);
                        } else if (i >= -32768) {
                            out.write(0xd1);
                            out.write(i>>8);
                            out.write(i);
                        } else {
                            out.write(0xd2);
                            out.write(i>>24);
                            out.write(i>>16);
                            out.write(i>>8);
                            out.write(i);
                        }
                    } else if (n instanceof Float) {
                        out.write(0xca);
                        int v = Float.floatToIntBits(n.floatValue());
                        out.write(v>>24);
                        out.write(v>>16);
                        out.write(v>>8);
                        out.write(v);
                    } else if (n instanceof Double) {
                        out.write(0xcb);
                        long v = Double.doubleToLongBits(n.doubleValue());
                        out.write((int)(v>>56));
                        out.write((int)(v>>48));
                        out.write((int)(v>>40));
                        out.write((int)(v>>32));
                        out.write((int)(v>>24));
                        out.write((int)(v>>16));
                        out.write((int)(v>>8));
                        out.write((int)v);
                    }
                } else if (value instanceof CharSequence) {
                    CharSequence s = (CharSequence)value;
                    int len = CborWriter.lengthUTF8(s);
                    writeStartString(len);
                    CborWriter.writeUTF8(s, len, out);
                } else if (Boolean.FALSE.equals(value)) {
                    out.write(0xC2);
                } else if (Boolean.TRUE.equals(value)) {
                    out.write(0xC3);
                } else if (event.isNull() || event.isUndefined()) {
                    out.write(0xC0);
                } else {
                    throw new IllegalStateException("Unsupported primitive " + (value == null ? null : value.getClass().getName()));
                }
                tag = NO_TAG;
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_SIMPLE:
                throw new IllegalStateException("No simple types in Msgpack");
            default:
                throw new IllegalStateException("Unknown event 0x" + type);
        }
        if (decrement > 0) {
            length -= decrement;
            if (length < 0) {
                throw new IllegalArgumentException("Overflow");
            }
        }
        return stackLength == 2 && decrement > 0;
    }

    private void writeStartBuffer(int l, int tag) throws IOException {
        if (tag >= 0) {
            if (l == 1) {
                out.write(0xd4);
                out.write(tag);
            } else if (l == 2) {
                out.write(0xd5);
                out.write(tag);
            } else if (l == 4) {
                out.write(0xd6);
                out.write(tag);
            } else if (l == 8) {
                out.write(0xd7);
                out.write(tag);
            } else if (l == 16) {
                out.write(0xd8);
                out.write(tag);
            } else if (l <= 255) {
                out.write(0xc7);
                out.write(l);
                out.write(tag);
            } else if (l <= 65535) {
                out.write(0xc8);
                out.write(l>>8);
                out.write(l);
                out.write(tag);
            } else {
                out.write(0xc9);
                out.write(l>>24);
                out.write(l>>16);
                out.write(l>>8);
                out.write(l);
                out.write(tag);
            }
        } else if (l <= 255) {
            out.write(0xc4);
            out.write(l);
        } else if (l <= 65535) {
            out.write(0xc5);
            out.write(l>>8);
            out.write(l);
        } else {
            out.write(0xc6);
            out.write(l>>24);
            out.write(l>>16);
            out.write(l>>8);
            out.write(l);
        }
    }

    private void writeStartString(int l) throws IOException {
        if (l <= 31) {
            out.write(0xa0 + l);
        } else if (l <= 255) {
            out.write(0xd9);
            out.write(l);
        } else if (l <= 65535) {
            out.write(0xda);
            out.write(l>>8);
            out.write(l);
        } else {
            out.write(0xdb);
            out.write(l>>24);
            out.write(l>>16);
            out.write(l>>8);
            out.write(l);
        }
    }

}
