package com.bfo.json;

import java.nio.*;
import java.io.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.math.*;
import java.text.*;

/**
 * A CBOR writer
 */
public class CborWriter implements JsonStream {
    
    private static final int STATE_DONE = 0;
    private static final int STATE_MAP = 1;
    private static final int STATE_LIST = 2;
    private static final int STATE_STRING = 3;
    private static final int STATE_BUFFER = 4;

    private OutputStream out;
    private long[] stack;
    private int stackLength;
    private long state;
    private long length;
    private boolean sorted;

    public CborWriter() {
        stack = new long[32];
        state = STATE_DONE;
    }

    /**
     * Set the OutputStream to write to
     * @param out the OutputStream
     * @return this
     */
    public CborWriter setOutput(OutputStream out) {
        this.out = out;
        return this;
    }

    /**
     * Set the ByteBuffer to write to
     * @param out the ByteBuffer
     * @return this
     */
    public CborWriter setOutput(final ByteBuffer buf) {
        this.out = new OutputStream() {
            public void write(int v) {
                buf.put((byte)v);
            }
            public void write(byte[] v, int off, int len) {
                buf.put(v, off, len);
            }
        };
        return this;
    }

    /**
     * Request that map keys are sorted before writing.
     * @param sorted true if keys should be sorted
     * @return this
     */
    public CborWriter setSorted(boolean sorted) {
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
            stack[stackLength++] = 0;
            stack[stackLength++] = STATE_DONE;
        }
        final int type = event.type();
        long decrement = 0;
//        System.out.println("WRITER: e="+event+" " + dump());
        switch(type) {
            case JsonStream.Event.TYPE_MAP_START:
                if (stackLength == stack.length) {
                    stack = Arrays.copyOf(stack, stackLength+(stackLength>>1));
                }
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_MAP;
                length = event.size();
                writeNum(5, (int)length);
                break;
            case JsonStream.Event.TYPE_LIST_START:
                if (stackLength == stack.length) {
                    stack = Arrays.copyOf(stack, stackLength+(stackLength>>1));
                }
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_LIST;
                length = event.size();
                writeNum(4, (int)length);
                break;
            case JsonStream.Event.TYPE_LIST_END:
                if (length > 0 || (state = stack[--stackLength]) != STATE_LIST) {
                    throw new IllegalStateException("Unexpected end-list");
                } else if (length < 0) {
                    out.write(0xFF);    // break
                }
                length = stack[--stackLength];
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_MAP_END:
                if (length > 0 || (state = stack[--stackLength]) != STATE_MAP) {
                    throw new IllegalStateException("Unexpected end-map");
                } else if (length < 0) {
                    out.write(0xFF);    // break
                }
                length = stack[--stackLength];
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_BUFFER_START:
                if (stackLength == stack.length) {
                    stack = Arrays.copyOf(stack, stackLength+(stackLength>>1));
                }
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_BUFFER;
                length = event.size();
                writeNum(2, length);
                break;
            case JsonStream.Event.TYPE_STRING_START:
                if (stackLength == stack.length) {
                    stack = Arrays.copyOf(stack, stackLength+(stackLength>>1));
                }
                stack[stackLength++] = length;
                state = stack[stackLength++] = STATE_STRING;
                length = event.size();
                writeNum(3, length);
                break;
            case JsonStream.Event.TYPE_STRING_DATA:
                if (state != STATE_STRING) {
                    throw new IllegalStateException("Unexpected string-data");
                }
                if (event.stringValue() != null) {
                    CharSequence v = event.stringValue();
                    if (length < 0) {
                        int len = lengthUTF8(v);
                        writeNum(3, len);
                        writeUTF8(v, len, out);
                    } else {
                        decrement = writeUTF8(v, 0, out);
                    }
                } else {
                    Readable r = event.readableValue();
                    CharBuffer buf = CharBuffer.allocate(8192);
                    while (r.read(buf) > 0) {
                        ((Buffer)buf).flip();
                        if (length < 0) {
                            int len = lengthUTF8(buf);
                            writeNum(3, len);
                            writeUTF8(buf, len, out);
                        } else {
                            decrement += writeUTF8(buf, 0, out);
                        }
                        ((Buffer)buf).clear();
                    }
                    if (r instanceof Closeable) {
                        ((Closeable)r).close();
                    }
                }
                break;
            case JsonStream.Event.TYPE_BUFFER_DATA:
                if (state != STATE_BUFFER) {
                    throw new IllegalStateException("Unexpected buffer-data");
                }
                if (event.bufferValue() != null) {
                    ByteBuffer v = event.bufferValue();
                    if (length < 0) {
                        writeNum(2, ((Buffer)v).remaining());
                        writeBuffer(v);
                    } else {
                        decrement = writeBuffer(v);
                    }
                } else {
                    ReadableByteChannel r = event.readableByteChannelValue();
                    // TODO use more efficient transfer to/from FileChannel if possible
                    ByteBuffer buf = ByteBuffer.allocate(8192);
                    while (r.read(buf) > 0) {
                        ((Buffer)buf).flip();
                        if (length < 0) {
                            writeNum(2, ((Buffer)buf).remaining());
                            writeBuffer(buf);
                        } else {
                            decrement += writeBuffer(buf);
                        }
                        ((Buffer)buf).clear();
                    }
                    r.close();
                }
                break;
            case JsonStream.Event.TYPE_STRING_END:
                if (length > 0 || (state = stack[--stackLength]) != STATE_STRING) {
                    throw new IllegalStateException("Unexpected end-string");
                } else if (length < 0) {
                    out.write(0xFF);    // break
                }
                length = stack[--stackLength];
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_BUFFER_END:
                if (length > 0 || (state = stack[--stackLength]) != STATE_BUFFER) {
                    throw new IllegalStateException("Unexpected end-buffer");
                } else if (length < 0) {
                    out.write(0xFF);    // break
                }
                length = stack[--stackLength];
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_TAG:
                writeNum(6, event.tagValue());
                break;
            case JsonStream.Event.TYPE_PRIMITIVE:
                final Object value = event.value();
                if (value instanceof Number) {
                    writeNumber((Number)value);
                } else if (value instanceof CharSequence) {
                    CharSequence s = (CharSequence)value;
                    int len = lengthUTF8(s);
                    writeNum(3, len);
                    writeUTF8(s, len, out);
                } else if (Boolean.FALSE.equals(value)) {
                    out.write(0xF4);
                } else if (Boolean.TRUE.equals(value)) {
                    out.write(0xF5);
                } else if (event.isNull()) {
                    out.write(0xF6);
                } else if (event.isUndefined()) {
                    out.write(0xF7);
                } else {
                    throw new IllegalStateException("Unsupported primitive " + (value == null ? null : value.getClass().getName()));
                }
                decrement = 1;
                break;
            case JsonStream.Event.TYPE_SIMPLE:
                int simple = event.numberValue().intValue();
                if (simple < 24) {
                    out.write(0xE0 | simple);
                } else {
                    out.write(0xE0 | 24);
                    out.write(simple);
                }
                decrement = 1;
                break;
            default:
                throw new IllegalStateException("Unknown event 0x" + type);
        }
        if (length > 0 && decrement > 0) {
            length -= decrement;
            if (length < 0) {
                throw new IllegalArgumentException("Overflow");
            }
        }
        return stackLength == 2 && decrement > 0;
    }

    private int writeBuffer(ByteBuffer buffer) throws IOException {
        int l = ((Buffer)buffer).remaining();
        if (buffer.isDirect()) {
            if (out instanceof WritableByteChannel) {
                ((WritableByteChannel)out).write(buffer);
            } else {
                ByteBuffer copy = ByteBuffer.allocate(((Buffer)buffer).remaining());
                copy.put(buffer);
                out.write(copy.array(), 0, ((Buffer)copy).limit());
            }
        } else {
            out.write(buffer.array(), ((Buffer)buffer).arrayOffset() + ((Buffer)buffer).position(), ((Buffer)buffer).remaining());
        }
        return l;
    }

    private void writeNumber(Number n) throws IOException {
//        System.out.println("NUM: type="+n+"/"+n.getClass().getName());
        if (n instanceof BigDecimal) {
            // No native BigDecimal in CBOR, write as tag 5 [exponent-10, mantissa]
            BigDecimal d = (BigDecimal)n;
            writeNum(6, CborReader.TAG_BIGDECIMAL10);      // tag
            writeNum(4, 2);                                // list of 2
            writeNumber(Integer.valueOf(-d.scale()));      // scale
            writeNumber(d.unscaledValue());                // mantissa
        } else if (n instanceof Long) {
            long l = n.longValue();
            if (l < 0) {
                writeNum(1, -l - 1);
            } else {
                writeNum(0, l);
            }
        } else if (n instanceof Float) {
            out.write(250);
            int v = Float.floatToIntBits(n.floatValue());
            out.write(v>>24);
            out.write(v>>16);
            out.write(v>>8);
            out.write(v);
        } else if (n instanceof Double) {
            out.write(251);
            long v = Double.doubleToLongBits(n.doubleValue());
            out.write((int)(v>>56));
            out.write((int)(v>>48));
            out.write((int)(v>>40));
            out.write((int)(v>>32));
            out.write((int)(v>>24));
            out.write((int)(v>>16));
            out.write((int)(v>>8));
            out.write((int)v);
        } else if (n instanceof BigInteger) {
            BigInteger bi = (BigInteger)n;
            int bl = bi.bitLength();
            if (bl < 64) {
                long l = bi.longValue();
                if (l < 0) {
                    writeNum(1, -l - 1);
                } else {
                    writeNum(0, l);
                }
            } else if (bi.signum() < 0) {
                bi = bi.negate().subtract(BigInteger.valueOf(1));
                bl = bi.bitLength();
                byte[] b = bi.toByteArray();
                if (bl == 64) {
                    out.write((1 << 5) | 27);
                    out.write(b, 1, 8);
                } else {
                    out.write(0xc3);
                    writeNum(2, b.length);
                    out.write(b);
                }
            } else {
                bl = bi.bitLength();
                byte[] b = bi.toByteArray();
                if (bl == 64) {
                    out.write(27);
                    out.write(b, 1, 8);
                } else {
                    out.write(0xc2);
                    writeNum(2, b.length);
                    out.write(b);
                }
            }
        } else {
            int i = n.intValue();
            if (i < 0) {
                writeNum(1, -i - 1);
            } else {
                writeNum(0, i);
            }
        }
    }

    private void writeNum(int prefix, long i) throws IOException {
        if (i < 0) {    // indefinite length
            out.write((prefix << 5) | 0x1F);
        } else if (i < 24) {
            out.write((prefix << 5) | ((int)i));
        } else if (i <= 255) {
            out.write((prefix << 5) | 24);
            out.write((int)i);
        } else if (i <= 65535) {
            out.write((prefix << 5) | 25);
            out.write((int)(i>>8));
            out.write((int)i);
        } else if (i <= 0x7fffffff) {
            out.write((prefix << 5) | 26);
            out.write((int)i>>24);
            out.write((int)i>>16);
            out.write((int)i>>8);
            out.write((int)i);
        } else {
            out.write((prefix << 5) | 27);
            out.write((int)(i>>56));
            out.write((int)(i>>48));
            out.write((int)(i>>40));
            out.write((int)(i>>32));
            out.write((int)(i>>24));
            out.write((int)(i>>16));
            out.write((int)(i>>8));
            out.write((int)i);
        }
    }

    /**
     * Return the length of the String as UTF8. Presumes no lonely surrogates
     */
    static int lengthUTF8(CharSequence in) {
        int len = 0;
        for (int i=0;i<in.length();i++) {
            char c = in.charAt(i);
            if (c < 0x80) {
                len++;
            } else if (c < 0x800) {
                len += 2;
            } else if (c >= 0xd800 && c <= 0xdbff) {
                len += 4;
                i++;
            } else {
                len += 3;
            }
        }
        return len;
    }

    /**
     * Write a String as UTF8 to am OutputStream as efficiently as possible. Presumes no lonely surrogates
     * @param in the String
     * @param utf8length the length of UTF8 encoding of the String in bytes if known, otherwise 0
     * @param out the OutputStream to write to
     * @return the number of bytes written
     */
    static int writeUTF8(CharSequence in, int utf8length, OutputStream out) throws IOException {
        boolean direct = out instanceof ByteArrayOutputStream; // utf8length == in.length();
        if (in instanceof String && in.length() < 4096) {
            byte[] buf = ((String)in).getBytes(StandardCharsets.UTF_8); // Definitely fastest
            out.write(buf);
            return buf.length;
        } else if (direct) {
            final int slen = in.length();
            if (slen == utf8length) {
                for (int i=0;i<slen;i++) {
                    out.write(in.charAt(i));
                }
                return slen;
            } else {
                int written = 0;
                for (int i=0;i<slen;i++) {
                    int c = in.charAt(i);
                    if (c < 0x80) {
                        out.write((byte)c);
                        written++;
                    } else if (c < 0x800) {
                        out.write((byte)((c >> 6) | 0300));
                        out.write((byte)((c & 077) | 0200));
                        written += 2;
                    } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < slen) {
                        c = ((c - 0xd800) << 10) + (in.charAt(++i) - 0xdc00) + 0x10000;
                        out.write((byte)((c >> 18) | 0360));
                        out.write((byte)(((c >> 12) & 077) | 0200));
                        out.write((byte)(((c >> 6) & 077) | 0200));
                        out.write((byte)((c & 077) | 0200));
                        written += 4;
                    } else {
                        out.write((byte)((c >> 12) | 0340));
                        out.write((byte)(((c >> 6) & 077) | 0200));
                        out.write((byte)((c & 077) | 0200));
                        written += 3;
                    }
                }
                return written;
            }
        } else {
            final int blen = 8192;
            final byte[] buf = new byte[blen + 4];
            final int slen = in.length();
            int j = 0;
            if (slen == utf8length) {
                for (int i=0;i<slen;i++) {
                    int c = in.charAt(i);
                    buf[j++] = (byte)c;
                    if (j == blen) {
                        out.write(buf, 0, j);
                        j = 0;
                    }
                }
                if (j > 0) {
                    out.write(buf, 0, j);
                }
                return slen;
            } else {
                int written = 0;
                for (int i=0;i<slen;i++) {
                    int c = in.charAt(i);
                    if (c < 0x80) {
                        buf[j++] = (byte)c;
                    } else if (c < 0x800) {
                        buf[j++] = (byte)((c >> 6) | 0300);
                        buf[j++] = (byte)((c & 077) | 0200);
                    } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < slen) {
                        c = ((c - 0xd800) << 10) + (in.charAt(++i) - 0xdc00) + 0x10000;
                        buf[j++] = (byte)((c >> 18) | 0360);
                        buf[j++] = (byte)(((c >> 12) & 077) | 0200);
                        buf[j++] = (byte)(((c >> 6) & 077) | 0200);
                        buf[j++] = (byte)((c & 077) | 0200);
                    } else {
                        buf[j++] = (byte)((c >> 12) | 0340);
                        buf[j++] = (byte)(((c >> 6) & 077) | 0200);
                        buf[j++] = (byte)((c & 077) | 0200);
                    }
                    if (j >= blen) {
                        out.write(buf, 0, j);
                        written += j;
                        j = 0;
                    }
                }
                if (j > 0) {
                    out.write(buf, 0, j);
                    written += j;
                }
                return written;
            }
        }
    }

}
