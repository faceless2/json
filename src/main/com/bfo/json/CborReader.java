package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.channels.*;

/**
 * A CBOR reader
 */
public class CborReader extends AbstractReader {

    static final int TAG_UNSIGNEDBIGNUM = 2;
    static final int TAG_SIGNEDBIGNUM   = 3;
    static final int TAG_BIGDECIMAL10   = 4;
    static final int TAG_BIGDECIMAL2    = 5;

    private static final int MODE_INIT    = 0;
    private static final int MODE_ROOT    = 1;
    private static final int MODE_LIST    = 2;
    private static final int MODE_MAP     = 3;
    private static final int MODE_DSTRING = 4;
    private static final int MODE_DBUFFER = 5;
    private static final int MODE_ISTRING = 6;
    private static final int MODE_IBUFFER = 7;
    private static final int MODE_DONE    = 8;

    private static final Number INDEFINITE = Float.intBitsToFloat(0x12345678);       // As good as any

    private final ArrayDeque<JsonStream.Event> eq;
    private long mode, len;
    private ByteSource in;
    private SpecialTagHandler divert;
    private long[] stack;
    private int stackLength;
    private long worknum;    // The number we are assembling
    private int worknuml;    // The number of bytes we have assembled in the number
    private int worknumt;    // The target number of bytes to assemble in the number
    private int worknumv;    // When the number is assembled, the value of "v" to reuse
    private CharBuffer cbuf;
    private CharsetDecoder decoder;

    public CborReader() {
        eq = new ArrayDeque<JsonStream.Event>();
        stack = new long[32];
        stack[0] = 0;
        stack[1] = MODE_DONE;
        stackLength = 2;
        len = 1;
        mode = MODE_INIT;
    }

    @Override public CborReader setInput(ReadableByteChannel in) {
        return (CborReader)super.setInput(in);
    }
    @Override public CborReader setInput(InputStream in) {
        return (CborReader)super.setInput(in);
    }
    @Override public CborReader setInput(ByteBuffer in) {
        return (CborReader)super.setInput(in);
    }
    @Override public CborReader setPartial() {
        return (CborReader)super.setPartial();
    }
    @Override public CborReader setFinal() {
        return (CborReader)super.setFinal();
    }
    @Override public CborReader setDraining() {
        return (CborReader)super.setDraining();
    }
    @Override public CborReader setNonDraining() {
        return (CborReader)super.setNonDraining();
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
            if (mode == MODE_DBUFFER) {
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
                    if (mode != MODE_IBUFFER) {
                        event(JsonStream.Event.endBuffer());
                    }
                    decrement = true;
                }
            } else if (mode == MODE_DSTRING) {
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
                    if (cbuf == null || cbuf.capacity() < l) {
                        cbuf = CharBuffer.allocate(l);
                    }
                    ((Buffer)cbuf).clear();
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
                    if (mode != MODE_ISTRING) {
                        event(JsonStream.Event.endString());
                    }
                    decrement = true;
                }
            } else {
                int v;
                Number n = null;
                if (worknumt > 0) {
                    v = in.get();
                    if (v < 0) {
                        break;  // outer loop
                    }
                    worknum = (worknum<<8) | v;
                    if (++worknuml == worknumt) {
                        v = worknumv;
                        if (worknumt <= 4 && (int)worknum >= 0) {
                            n = Integer.valueOf((int)worknum);
                        } else if (worknum >= 0) {
                            n = Long.valueOf(worknum);
                        } else {
                            n = BigInteger.valueOf(Integer.toUnsignedLong((int)(worknum>>32))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong((int)worknum)));
                        }
                        worknumv = worknumt = 0;
                        worknum = 0;
                    } else {
                        continue;
                    }
                } else {
                    v = in.get();
                    if (v < 0) {
                        break;  // outer loop
                    }
                }
                boolean ok = true;
                switch (v>>5) {
                    case 0:
                        if (n == null) {
                            n = readNumber(v, false);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        event(JsonStream.Event.numberValue(n));
                        decrement = true;
                        break;
                    case 1:
                        if (n == null) {
                            n = readNumber(v, false);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        if (n instanceof Integer) {
                            n = Integer.valueOf(-1 - n.intValue());
                        } else if (n instanceof Long) {
                            n = Long.valueOf(-1l - n.longValue());
                        } else if (n instanceof BigInteger) {
                            n = BigInteger.valueOf(-1).subtract((BigInteger)n);
                        }
                        event(JsonStream.Event.numberValue(n));
                        decrement = true;
                        break;
                    case 2:
                        if (n == null) {
                            n = readNumber(v, true);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        if (stack.length == stackLength) {
                            stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                        }
                        stack[stackLength++] = len;
                        stack[stackLength++] = mode;
                        if (n == INDEFINITE) {
                            if (mode == MODE_ISTRING || mode == MODE_IBUFFER) {
                                throw new IOException("Can't nest indefinite buffer");
                            } else {
                                event(JsonStream.Event.startBuffer(-1));
                                len = -1;
                            }
                            mode = MODE_IBUFFER;
                        } else if (mode == MODE_ISTRING) {
                            throw new IOException("Buffer not valid inside indefinite string");
                        } else {
                            len = n.longValue();
                            if (mode != MODE_IBUFFER) {
                                event(JsonStream.Event.startBuffer(len));
                            }
                            mode = MODE_DBUFFER;
                        }
                        break;
                    case 3:
                        if (n == null) {
                            n = readNumber(v, true);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        if (stack.length == stackLength) {
                            stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                        }
                        stack[stackLength++] = len;
                        stack[stackLength++] = mode;
                        if (n == INDEFINITE) {
                            if (mode == MODE_ISTRING || mode == MODE_IBUFFER) {
                                throw new IOException("Can't nest indefinite string");
                            } else {
                                event(JsonStream.Event.startString(len = -1));
                            }
                            mode = MODE_ISTRING;
                        } else if (mode == MODE_IBUFFER) {
                            throw new IOException("String not valid inside indefinite buffer");
                        } else {
                            len = n.longValue();
                            if (mode != MODE_ISTRING) {
                                event(JsonStream.Event.startString(len));
                            }
                            mode = MODE_DSTRING;
                            if (decoder == null) {
                                decoder = StandardCharsets.UTF_8.newDecoder();
                                decoder.onMalformedInput(getCodingErrorAction());
                                decoder.onUnmappableCharacter(getCodingErrorAction());
                            } else {
                                decoder.reset();
                            }
                        }
                        break;
                    case 4:
                        if (n == null) {
                            n = readNumber(v, true);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        if (n != INDEFINITE && n.intValue() == 0) {
                            event(JsonStream.Event.startList(0));
                            event(JsonStream.Event.endList());
                            decrement = true;
                        } else {
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = n == INDEFINITE ? -1 : n.intValue();
                            event(JsonStream.Event.startList((int)len));
                            mode = MODE_LIST;
                        }
                        break;
                    case 5:
                        if (n == null) {
                            n = readNumber(v, true);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        if (n != INDEFINITE && n.intValue() == 0) {
                            event(JsonStream.Event.startMap(0));
                            event(JsonStream.Event.endMap());
                            decrement = true;
                        } else {
                            if (stack.length == stackLength) {
                                stack = Arrays.copyOf(stack, stack.length + (stack.length >> 1));
                            }
                            stack[stackLength++] = len;
                            stack[stackLength++] = mode;
                            len = n == INDEFINITE ? -1 : n.intValue();
                            event(JsonStream.Event.startMap((int)len));
                            if (len > 0) {
                                len *= 2;
                            }
                            mode = MODE_MAP;
                        }
                        break;
                    case 6:
                        if (n == null) {
                            n = readNumber(v, false);
                            if (n == null) {
                                ok = false;
                                break; // switch
                            }
                        }
                        if (n instanceof BigInteger && ((BigInteger)n).bitLength() > 63) {
                            throw new IOException("Tag " + n + " with " + ((BigInteger)n).bitLength() + " bits is unsupported");
                        }
                        divert = new SpecialTagHandler(divert);
                        event(JsonStream.Event.tagNext(n.longValue()));
                        break;
                    default:
                        decrement = true;
                        switch (v & 0x1F) {
                            case 20:
                                event(JsonStream.Event.booleanValue(false));
                                decrement = true;
                                break;
                            case 21:
                                event(JsonStream.Event.booleanValue(true));
                                decrement = true;
                                break;
                            case 22:
                                event(JsonStream.Event.nullValue());
                                decrement = true;
                                break;
                            case 23:
                                event(JsonStream.Event.undefinedValue());
                                decrement = true;
                                break;
                            case 25:
                                if (n == null) {
                                    n = readNumber(v, false);
                                    if (n == null) {
                                        ok = false;
                                        break; // inner switch
                                    }
                                }
                                v = n.intValue();
                                int s = (v & 0x8000) >> 15;
                                int e = (v & 0x7c00) >> 10;
                                int f = v & 0x3ff;
                                if (e == 0) {
                                    n = Float.valueOf((float)((s != 0 ? -1f : 1f) * Math.pow(2, -14) * (f / Math.pow(2, 10))));
                                } else if (e == 0x1f) {
                                    n = f != 0 ? Float.NaN : s == 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
                                } else {
                                    n = Float.valueOf((float)((s != 0 ? -1f : 1f) * Math.pow(2, e - 15) * (1 + f / Math.pow(2, 10))));
                                }
                                event(JsonStream.Event.numberValue(n));
                                decrement = true;
                                break;
                            case 26:
                                if (n == null) {
                                    n = readNumber(v, false);
                                    if (n == null) {
                                        ok = false;
                                        break; // inner switch
                                    }
                                }
                                event(JsonStream.Event.numberValue(Float.intBitsToFloat(n.intValue())));
                                decrement = true;
                                break;
                            case 27:
                                if (n == null) {
                                    n = readNumber(v, false);
                                    if (n == null) {
                                        ok = false;
                                        break; // inner switch
                                    }
                                }
                                event(JsonStream.Event.numberValue(Double.longBitsToDouble(n.longValue())));
                                decrement = true;
                                break;
                            case 31:
                                if (len == -1) {
                                    if (mode == MODE_IBUFFER) {
                                        event(JsonStream.Event.endBuffer());
                                    } else if (mode == MODE_ISTRING) {
                                        event(JsonStream.Event.endString());
                                    } else if (mode == MODE_LIST) {
                                        event(JsonStream.Event.endList());
                                    } else if (mode == MODE_MAP) {
                                        event(JsonStream.Event.endMap());
                                    }
                                    mode = stack[--stackLength];
                                    len = stack[--stackLength];
                                    decrement = true;
                                } else {
                                    throw new IOException("Unexpected break");
                                }
                                break;
                            case 24:
                                if (n == null) {
                                    n = readNumber(v, false);
                                    if (n == null) {
                                        ok = false;
                                        break; // inner switch
                                    }
                                }
                                // fallthrough
                            default:
                                if (n == null) {
                                    n = Integer.valueOf(v & 0x1F);
                                }
                                event(JsonStream.Event.simple(n.intValue()));
                                decrement = true;
                        }
                }
                if (!ok) {
                    break; // outer loop
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

    private Number readNumber(final int fv, boolean allowIndefinite) throws IOException {
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
        int v = fv & 0x1F;
        if (v == 31) {
            if (allowIndefinite) {
                return INDEFINITE;
            } else {
                throw new IOException("Unexpected break");
            }
        } else if (v < 24) {
            return Integer.valueOf(v);
        } else if (v == 24) {
            v = in.get();
            if (v < 0) {
                worknum = 0; worknuml = 0; worknumt = 1; worknumv = fv;
            } else {
                return Integer.valueOf(v);
            }
        } else if (v == 25) {
            v = in.get();
            if (v < 0) {
                worknum = 0; worknuml = 0; worknumt = 2; worknumv = fv;
            } else {
                int v2 = in.get();
                if (v2 < 0) {
                    worknum = v; worknuml = 1; worknumt = 2; worknumv = fv;
                } else {
                    return Integer.valueOf((v<<8) + v2);
                }
            }
        } else if (v == 26) {
            v = in.get();
            if (v < 0) {
                worknum = 0; worknuml = 0; worknumt = 4; worknumv = fv;
            } else {
                int v2 = in.get();
                if (v2 < 0) {
                    worknum = v; worknuml = 1; worknumt = 4; worknumv = fv;
                } else {
                    int v3 = in.get();
                    if (v3 < 0) {
                        worknum = (v<<8) | v2; worknuml = 2; worknumt = 4; worknumv = fv;
                    } else {
                        int v4 = in.get();
                        if (v4 < 0) {
                            worknum = (v<<16) | (v2<<8) | v; worknuml = 2; worknumt = 4; worknumv = fv;
                        } else if (v < 128) {
                            return Integer.valueOf((v<<24) | (v2<<16) | (v3<<8) | v4);
                        } else {
                            return Long.valueOf((((long)v)<<24) | (v2<<16) | (v3<<8) | v4);
                        }
                    }
                }
            }
        } else if (v == 27) {
            long lv = 0;
            for (int i=0;i<8;i++) {
                v = in.get();
                if (v < 0) {
                    worknum = lv; worknuml = i; worknumt = 8; worknumv = fv;
                    return null;
                } else {
                    lv = (lv<<8) | v;
                }
            }
            if (lv < 0) {
                return BigInteger.valueOf(Integer.toUnsignedLong((int)(lv>>32))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong((int)lv)));
            } else {
                return Long.valueOf(lv);
            }
        } else {
            throw new IOException("Unknown unsigned integer type 0x" + Integer.toHexString(v));
        }
        return null;
    }

    private void event(JsonStream.Event event) throws IOException {
        if (divert != null) {
            divert.event(event);
        } else {
            eq.add(event);
        }
    }

    private class SpecialTagHandler {
        static final int TAG_UNSIGNEDBIGNUM = 2;
        static final int TAG_SIGNEDBIGNUM   = 3;
        static final int TAG_BIGDECIMAL10   = 4;
        static final int TAG_BIGDECIMAL2    = 5;

        private SpecialTagHandler parent;
        private int tag, depth;
        private List<JsonStream.Event> q;
        private ExtendingByteBuffer buf;
        private Integer n0;
        private BigInteger n1;

        SpecialTagHandler(SpecialTagHandler parent) {
            this.parent = parent;
            this.q = new ArrayList<JsonStream.Event>();
            this.tag = -1;
        }

        public void event(JsonStream.Event event) throws IOException {
            q.add(event.copy());
            final int type = event.type();
            if (type == JsonStream.Event.TYPE_TAG) {
                long ltag = event.tagValue();
                if (ltag > Integer.MAX_VALUE) {
                    close();
                    return;
                }
                tag = (int)ltag;
                switch (tag) {
                    case TAG_UNSIGNEDBIGNUM:
                    case TAG_SIGNEDBIGNUM:
                        buf = new ExtendingByteBuffer();
                        break;
                    case TAG_BIGDECIMAL10:
                    case TAG_BIGDECIMAL2:
                        break;
                    default:
                        close();
                }
                return;
            } else if (tag == TAG_UNSIGNEDBIGNUM || tag == TAG_SIGNEDBIGNUM) {
                if (type == JsonStream.Event.TYPE_BUFFER_DATA) {
                    // Will never be a stream
                    buf.write(event.bufferValue());
                } else if (type != JsonStream.Event.TYPE_BUFFER_START && type != JsonStream.Event.TYPE_BUFFER_END) {
                    close();
                    return;
                }
            } else if (tag == TAG_BIGDECIMAL10 || tag == TAG_BIGDECIMAL2) {
                Number n = event.numberValue();
                if (depth == 1 && n0 == null && type == JsonStream.Event.TYPE_PRIMITIVE && n0 == null && n instanceof Integer) {
                    n0 = (Integer)n;
                } else if (depth == 1 && n0 != null && n1 == null && type == JsonStream.Event.TYPE_PRIMITIVE && (n instanceof Integer || n instanceof Long || n instanceof BigInteger)) {
                    n1 = n instanceof BigInteger ? (BigInteger)n : BigInteger.valueOf(n.longValue());
                } else if ((depth == 0 && type == JsonStream.Event.TYPE_STARTLIST) || (depth == 1 && type == JsonStream.Event.TYPE_ENDLIST)) {
                    // OK
                } else {
                    close();
                    return;
                }
            }

            switch (type) {
                case JsonStream.Event.TYPE_STARTMAP:
                case JsonStream.Event.TYPE_BUFFER_START:
                case JsonStream.Event.TYPE_STRING_START:
                case JsonStream.Event.TYPE_STARTLIST:
                    depth++;
                    break;
                case JsonStream.Event.TYPE_ENDMAP:
                case JsonStream.Event.TYPE_BUFFER_END:
                case JsonStream.Event.TYPE_STRING_END:
                case JsonStream.Event.TYPE_ENDLIST:
                    depth--;
            }

            if (depth == 0) {       // process
                switch (tag) {
                    case TAG_UNSIGNEDBIGNUM:
                    case TAG_SIGNEDBIGNUM:
                        ByteBuffer b = buf.toByteBuffer();
                        BigInteger n = new BigInteger(1, b.array());
                        if (tag == TAG_SIGNEDBIGNUM) {
                            n = BigInteger.valueOf(-1).subtract(n);
                        }
                        q.clear();
                        q.add(JsonStream.Event.numberValue(n));
                        break;
                    case TAG_BIGDECIMAL10:
                        q.clear();
                        q.add(JsonStream.Event.numberValue(new BigDecimal(n1, -n0.intValue())));
                        break;
                    case TAG_BIGDECIMAL2:
                        BigDecimal d = new BigDecimal(n1);
                        if (n0.intValue() > 0) {
                            d = d.multiply(BigDecimal.valueOf(2).pow(n0.intValue()));
                        } else {
                            d = d.divide(BigDecimal.valueOf(2).pow(-n0.intValue()));
                        }
                        q.clear();
                        q.add(JsonStream.Event.numberValue(d));
                        break;
                }
                close();
                return;
            }
        }

        void close() throws IOException {
            CborReader reader = CborReader.this;
            reader.divert = parent;
            if (parent != null) {
                for (JsonStream.Event e : q) {
                    parent.event(e);
                }
            } else {
                for (JsonStream.Event e : q) {
                    reader.event(e);
                }
            }
        }
    }

}
