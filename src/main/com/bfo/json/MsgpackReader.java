package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.channels.*;

/**
 * A Msgpack reader
 */
public class MsgpackReader extends AbstractReader {

    private static final int MODE_INIT    = 0;
    private static final int MODE_ROOT    = 1;
    private static final int MODE_LIST    = 2;
    private static final int MODE_MAP     = 3;
    private static final int MODE_STRING  = 4;
    private static final int MODE_BUFFER  = 5;
    private static final int MODE_DONE    = 8;

    private static final int TAG_REQUIRED = -1;
    private static final int TAG_NOT_REQUIRED = -2;

    private final ArrayDeque<JsonStream.Event> eq;
    private long mode, len;
    private ByteSource in;
    private long[] stack;
    private int stackLength;
    private long worknum;    // The number we are assembling
    private int worknuml;    // The number of bytes we have assembled in the number
    private int worknumt;    // The target number of bytes to assemble in the number
    private int worknumv;    // When the number is assembled, the value of "v" to reuse
    private boolean worknums;  // When the number is assembled, is it signed
    private Number n = null;
    private int tag;
    private CharBuffer cbuf;
    private CharsetDecoder decoder;

    public MsgpackReader() {
        eq = new ArrayDeque<JsonStream.Event>();
        stack = new long[32];
        stack[0] = 0;
        stack[1] = MODE_DONE;
        stackLength = 2;
        len = 1;
        mode = MODE_INIT;
        tag = TAG_NOT_REQUIRED;
        decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(getCodingErrorAction());
        decoder.onUnmappableCharacter(getCodingErrorAction());
    }

    @Override public MsgpackReader setInput(Readable in) {
        return (MsgpackReader)super.setInput(in);
    }
    @Override public MsgpackReader setInput(CharSequence in) {
        return (MsgpackReader)super.setInput(in);
    }
    @Override public MsgpackReader setInput(ReadableByteChannel in) {
        return (MsgpackReader)super.setInput(in);
    }
    @Override public MsgpackReader setInput(InputStream in) {
        return (MsgpackReader)super.setInput(in);
    }
    @Override public MsgpackReader setInput(ByteBuffer in) {
        return (MsgpackReader)super.setInput(in);
    }
    @Override void setSource(ByteSource source) {
        in = source;
    }

    @Override public JsonStream.Event next() throws IOException {
        if (eq.isEmpty()) {
            hasNext();
        }
        return eq.removeFirst();
    }

    @Override public boolean hasNext() throws IOException {
        if (mode == MODE_INIT) {
            requestByteSource();
            if (in == null) {
                return false;
            }
            mode = MODE_ROOT;
        } else if (mode == MODE_DONE) {
            return !eq.isEmpty();
        }
        while (eq.isEmpty()) {
            boolean decrement = false;
            if (mode == MODE_BUFFER) {
                if (len > 0) {
                    if (in.available() == 0) {
                        in.mark(1);
                        if (in.get() < 0) {
                            break;  // outer loop
                        }
                        in.reset();
                    }
                    int l = (int)Math.min(len, in.available());
                    ByteBuffer bbuf = in.get(l);
                    event(JsonStream.Event.bufferData(bbuf));
                    len -= l;
                }
                if (len == 0) {
                    mode = stack[--stackLength];
                    len = stack[--stackLength];
                    event(JsonStream.Event.endBuffer());
                    decrement = true;
                }
            } else if (mode == MODE_STRING) {
                if (len > 0) {
                    if (in.available() == 0) {
                        in.mark(1);
                        if (in.get() < 0) {
                            break;  // outer loop
                        }
                        in.reset();
                    }
                    int l = (int)Math.min(len, in.available());
                    in.mark(l);
                    ByteBuffer bbuf = in.get(l);
                    if (cbuf == null || ((Buffer)cbuf).capacity() < l) {
                        cbuf = CharBuffer.allocate(l);
                    }
                    cbuf.clear();
                    int pos = ((Buffer)bbuf).position();
                    CoderResult result = decoder.decode(bbuf, cbuf, l == len);
                    l = ((Buffer)bbuf).position() - pos;
                    if (result.isError()) {
                        in.reset();
                        in.get(result.length());
                        throw new IOException("Invalid UTF-8 sequence");
                    } else if (((Buffer)bbuf).hasRemaining()) {
                        in.reset();
                        if (l == 0) {
                            break; // outer
                        } else {
                            in.get(l);
                        }
                    }
                    ((Buffer)cbuf).flip();
                    event(JsonStream.Event.stringData((CharSequence)cbuf));
                    len -= l;
                }
                if (len == 0) {
                    mode = stack[--stackLength];
                    len = stack[--stackLength];
                    event(JsonStream.Event.endString());
                    decrement = true;
                }
            } else {
                int v = in.get();
                if (v < 0) {
                    break; // outer loop
                }
                if (tag == TAG_REQUIRED) {
                    tag = v;
                } else if (worknumt > 0) {
                    worknum = (worknum<<8) | v;
                    if (++worknuml == worknumt) {
                        v = worknumv;
                        n = fixNumber(worknum, worknuml, worknums);
                        worknumv = worknumt = 0;
                        worknum = 0;
                    } else {
                        continue;
                    }
                }
//                System.out.println("V=0x" + Integer.toHexString(v)+" b="+n+" tell="+in.getByteNumber());
                if (v < 0x80) {
                    event(JsonStream.Event.numberValue(v & 0x7f));
                    decrement = true;
                } else if (v <= 0x8f) {
                    if (stack.length == stackLength) {
                        stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                    }
                    stack[stackLength++] = len;
                    stack[stackLength++] = mode;
                    len = v & 0x0F;
                    event(JsonStream.Event.startMap((int)len));
                    len *= 2;
                    mode = MODE_MAP;
                    if (len == 0) {
                        mode = stack[--stackLength];
                        len = stack[--stackLength];
                        event(JsonStream.Event.endMap());
                        decrement = true;
                    }
                } else if (v <= 0x9f) {
                    if (stack.length == stackLength) {
                        stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                    }
                    stack[stackLength++] = len;
                    stack[stackLength++] = mode;
                    len = v & 0x0F;
                    event(JsonStream.Event.startList((int)len));
                    mode = MODE_LIST;
                    if (len == 0) {
                        mode = stack[--stackLength];
                        len = stack[--stackLength];
                        event(JsonStream.Event.endList());
                        decrement = true;
                    }
                } else if (v <= 0xbf) {
                    if (stack.length == stackLength) {
                        stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                    }
                    stack[stackLength++] = len;
                    stack[stackLength++] = mode;
                    len = v & 0x1F;
                    event(JsonStream.Event.startString(len));
                    mode = MODE_STRING;
                    decoder.reset();
                    if (len == 0) {
                        mode = stack[--stackLength];
                        len = stack[--stackLength];
                        event(JsonStream.Event.endString());
                        decrement = true;
                    }
                } else if (v >= 0xe0) {
                    event(JsonStream.Event.numberValue((byte)v));
                    decrement = true;
                } else {
                    boolean ok = true;
                    switch (v) {
                        case 0xc0: // nil
                            event(JsonStream.Event.nullValue());
                            decrement = true;
                            break;
                        case 0xc1: // unused
                            throw new IOException("Unexpected type 0xC1");
                        case 0xc2: // false
                            event(JsonStream.Event.booleanValue(false));
                            decrement = true;
                            break;
                        case 0xc3: // true
                            event(JsonStream.Event.booleanValue(true));
                            decrement = true;
                            break;
                        case 0xc4: // bin8;
                        case 0xc5: // bin16;
                        case 0xc6: // bin32;
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xc4), false);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = n.longValue();
                            event(JsonStream.Event.startBuffer(len));
                            mode = MODE_BUFFER;
                            if (len == 0) {
                                mode = stack[--stackLength];
                                len = stack[--stackLength];
                                event(JsonStream.Event.endBuffer());
                                decrement = true;
                            }
                            break;
                        case 0xc7: // ext8
                        case 0xc8: // ext16
                        case 0xc9: // ext32
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xc7), false);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (tag < 0) {
                                tag = in.get();
                                if (tag < 0) {
                                    tag = TAG_REQUIRED;
                                    ok = false;
                                    break;
                                }
                            }
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            event(JsonStream.Event.tagNext(tag));
                            len = n.longValue();
                            event(JsonStream.Event.startBuffer(len));
                            tag = TAG_NOT_REQUIRED;
                            mode = MODE_BUFFER;
                            if (len == 0) {
                                mode = stack[--stackLength];
                                len = stack[--stackLength];
                                event(JsonStream.Event.endBuffer());
                                decrement = true;
                            }
                            break;
                        case 0xca: // float32
                            if (n == null) {
                                n = readNumber(v, 4, true);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            event(JsonStream.Event.numberValue(Float.intBitsToFloat(n.intValue())));
                            decrement = true;
                            break;
                        case 0xcb:
                            if (n == null) {
                                n = readNumber(v, 8, true);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            event(JsonStream.Event.numberValue(Double.longBitsToDouble(n.longValue())));
                            decrement = true;
                            break;
                        case 0xcc: // uint8
                        case 0xcd: // uint16
                        case 0xce: // uint32
                        case 0xcf: // uint64
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xcc), false);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            event(JsonStream.Event.numberValue(n));
                            decrement = true;
                            break;
                        case 0xd0: // int8
                        case 0xd1: // int16
                        case 0xd2: // int32
                        case 0xd3: // int64
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xd0), true);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            event(JsonStream.Event.numberValue(n));
                            decrement = true;
                            break;
                        case 0xd4: // fixext1
                        case 0xd5: // fixext2
                        case 0xd6: // fixext4
                        case 0xd7: // fixext8
                        case 0xd8: // fixext16
                            if (tag < 0) {
                                tag = in.get();
                                if (tag < 0) {
                                    tag = TAG_REQUIRED;
                                    ok = false;
                                    break;
                                }
                            }
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = 1 << (v - 0xd4);
                            event(JsonStream.Event.tagNext(tag));
                            event(JsonStream.Event.startBuffer(len));
                            mode = MODE_BUFFER;
                            tag = TAG_NOT_REQUIRED;
                            break;
                        case 0xd9: // str8
                        case 0xda: // str16
                        case 0xdb: // str32
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xd9), false);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = n.longValue();
                            if (len < 0) {
                                throw new IOException("Can't create " + (len&0xFFFFFFFFl) + " byte String");
                            }
                            event(JsonStream.Event.startString(len));
                            mode = MODE_STRING;
                            decoder.reset();
                            if (len == 0) {
                                mode = stack[--stackLength];
                                len = stack[--stackLength];
                                event(JsonStream.Event.endString());
                                decrement = true;
                            }
                            break;
                        case 0xdc: // array16
                        case 0xdd: // array32
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xdb), false);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = n.longValue();
                            if (len > Integer.MAX_VALUE || len < 0) {
                                throw new IOException("Can't create " + (len&0xFFFFFFFFl) + " entry List");
                            }
                            event(JsonStream.Event.startList((int)len));
                            mode = MODE_LIST;
                            if (len == 0) {
                                mode = stack[--stackLength];
                                len = stack[--stackLength];
                                event(JsonStream.Event.endList());
                                decrement = true;
                            }
                            break;
                        case 0xde: // map16
                        case 0xdf: // map32
                            if (n == null) {
                                n = readNumber(v, 1<<(v - 0xdd), false);
                                if (n == null) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = n.longValue();
                            if (len > Integer.MAX_VALUE || len < 0) {
                                throw new IOException("Can't create " + (len&0xFFFFFFFFl) + " entry Map");
                            }
                            event(JsonStream.Event.startMap((int)len));
                            len *= 2;
                            mode = MODE_MAP;
                            if (len == 0) {
                                mode = stack[--stackLength];
                                len = stack[--stackLength];
                                event(JsonStream.Event.endMap());
                                decrement = true;
                            }
                            break;
                    }
                    n = null;
                    if (!ok) {
                        break; // outer loop
                    }
                }
            }
            if (decrement) {
                while (len > 0 && --len == 0) {
                    if (mode == MODE_LIST) {
                        event(JsonStream.Event.endList());
                    } else if (mode == MODE_MAP) {
                        event(JsonStream.Event.endMap());
                    }
                    mode = stack[--stackLength];
                    len = stack[--stackLength];
                }
            }
        }
        if (eq.isEmpty()) {
            if (requestByteSource()) {
                return hasNext();
            }
        }
        return !eq.isEmpty();
    }

    private Number readNumber(int fv, final int len, boolean signed) throws IOException {
        // Major type 0:  an unsigned integer.  The 5-bit additional information
        // the integer itself (for additional information values 0
        // through 23) or the length of additional data.  Additional
        // information 24 means the value is represented in an additional
        // uint8_t, 25 means a uint16_t, 26 means a uint32_t, and 27 means a
        // uint64_t.  For example, the integer 10 is denoted as the one byte
        // 0b000_01010 (major type 0, additional information 10).  The
        // integer 500 would be 0b000_11001 (major type 0, additional
        // information 25) followed by the two bytes 0x01f4, which is 500 in
        // decimal.
        long lv = 0;
        for (int i=0;i<len;i++) {
            int v = in.get();
            if (v < 0) {
                worknum = lv; worknuml = i; worknumt = len; worknumv = fv; worknums = signed;
                return null;
            } else {
                lv = (lv<<8) | v;
            }
        }
        return fixNumber(lv, len, signed);
    }

    private static Number fixNumber(long lv, int len, boolean signed) {
        if (signed) {
            if (len == 1) {
                return Integer.valueOf((byte)lv);
            } else if (len == 2) {
                return Integer.valueOf((short)lv);
            } else if (len == 4) {
                return Integer.valueOf((int)lv);
            } else {
                return Long.valueOf(lv);
            }
        } else if (lv < 0) {
            return BigInteger.valueOf(Integer.toUnsignedLong((int)(lv>>32))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong((int)lv)));
        } else if (len == 8 || lv > Integer.MAX_VALUE) {
            return Long.valueOf(lv);
        } else {
            return Integer.valueOf((int)lv);
        }
    }

    private void event(JsonStream.Event event) throws IOException {
//        System.out.println("ADD " + event);
        eq.add(event);
    }

    private static String hex(ByteBuffer buf) {
        return hex(buf.array(), ((Buffer)buf).arrayOffset() + ((Buffer)buf).position(), ((Buffer)buf).remaining());
    }

    private static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i=0;i<len;i++) {
            int v = b[off + i] & 0xff;
            if (v < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

}
