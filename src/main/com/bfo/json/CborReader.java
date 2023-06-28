package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;

class CborReader {

    static final int TAG_UNSIGNEDBIGNUM = 2;
    static final int TAG_SIGNEDBIGNUM   = 3;
    static final int TAG_BIGDECIMAL10   = 4;
    static final int TAG_BIGDECIMAL2    = 5;

    // private static final boolean DEBUG = false;
    private static final Number INDEFINITE = Float.intBitsToFloat(0x12345678);       // As good as any
    private static final Json BREAK = new Json(INDEFINITE, null) { @Override Json setStrict(boolean strict) { return this; } };

    private final CountingInputStream in;
    private final JsonReadOptions options;
    private final JsonReadOptions.Filter filter;
    private final boolean strict;

    CborReader(CountingInputStream in, JsonReadOptions options) {
        this.in = in;
        this.options = options;
        this.strict = options.isStrictTypes();
        this.filter = options.getFilter() != null ? options.getFilter() : new JsonReadOptions.Filter() {};
    }

    Json read() throws IOException {
        filter.initialize();
        Json j = readPrivate();
        filter.complete(j);
        return j;
    }

    /**
     * Read a CBOR serialized object
     */
    private Json readPrivate() throws IOException {
        int v = in.read();
        if (v < 0) {
            return null;
        }
        // final int origv = v;
        // final long origtell = in.tell();
        Number n;
        Json j;
        List<Json> list;
        Map<Object,Json> map;
        long tell;
        switch (v>>5) {
            case 0:
                j = filter.createNumber(readNumber(v, false));
                break;
            case 1:
                n = readNumber(v, false);
                if (n instanceof Integer) {
                    n = Integer.valueOf(-1 - n.intValue());
                } else if (n instanceof Long) {
                    n = Long.valueOf(-1l - n.longValue());
                } else if (n instanceof BigInteger) {
                    n = BigInteger.valueOf(-1).subtract((BigInteger)n);
                }
                j = filter.createNumber(n);
                break;
            case 2:
                n = readNumber(v, true);
                j = filter.createBuffer(createBufferInputStream(n, null), n == INDEFINITE ? -1 : n.longValue());
                break;
            case 3:
                n = readNumber(v, true);
                InputStream tin = createBufferInputStream(n, options.getCborStringCodingErrorAction());
                final String tostring = tin.toString();        // will be null if tostring not possible
                Reader r;
                long llen = -1;
                if (tostring != null) {
                    r = new StringReader(tostring) {
                        public String toString() {
                            return tostring;
                        }
                    };
                    llen = tostring.length();
                } else {
                    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                    decoder.onMalformedInput(options.getCborStringCodingErrorAction());
                    r = new InputStreamReader(tin, decoder);
                }
                j = filter.createString(r, llen);
                break;
            case 4:
                n = readNumber(v, true);
                // Because passing a populated collection into Json() clones items
                j = filter.createList();
                list = j._listValue();
                if (n == INDEFINITE) {
                    Json j2;
                    int i = 0;
                    filter.enter(j, i);
                    while ((j2 = readPrivate()) != BREAK) {
                        filter.exit(j, i);
                        if (j2 == null) {
                            throw new EOFException();
                        }
                        list.add(j2);
                        Json.notifyDuringLoad(j, i++, j2);
                        filter.enter(j, i);
                    }
                    filter.exit(j, i);
                } else {
                    int len = n.intValue();
                    for (int i=0;i<len;i++) {
                        tell = in.tell();
                        filter.enter(j, i);
                        Json j2 = readPrivate();
                        filter.exit(j, i);
                        if (j2 == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        } else if (j2 == null) {
                            throw new EOFException();
                        }
                        list.add(j2);
                        Json.notifyDuringLoad(j, i, j2);
                    }
                }
                break;
            case 5:
                n = readNumber(v, true);
                // Because passing a populated collection into Json() clones items
                j = filter.createMap();
                map = j._mapValue();
                if (n == INDEFINITE) {
                    Json key;
                    tell = in.tell();
                    while ((key = readPrivate()) != BREAK) {
                        if (key == null) {
                            throw new EOFException();
                        }
                        tell = in.tell();
                        Object k = Json.fixKey(key, options.isFailOnComplexKeys(), tell);
                        if (!(k instanceof String)) {
                            j.setNonStringKeys();
                        }
                        filter.enter(j, k);
                        Json val = readPrivate();
                        filter.exit(j, k);
                        if (val == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        } else if (val == null) {
                            throw new EOFException();
                        }
                        Object o = map.put(k, val);
                        if (o != null) {
                            throw new IOException("Duplicate key \"" + k + "\" at " + tell);
                        }
                        Json.notifyDuringLoad(j, k, val);
                        tell = in.tell();
                    }
                } else {
                    int len = n.intValue();
                    for (int i=0;i<len;i++) {
                        tell = in.tell();
                        Json key = readPrivate();
                        if (key == null) {
                            throw new EOFException();
                        } else if (key == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        }
                        Object k = Json.fixKey(key, options.isFailOnComplexKeys(), tell);
                        if (!(k instanceof String)) {
                            j.setNonStringKeys();
                        }
                        filter.enter(j, k);
                        Json val = readPrivate();
                        filter.exit(j, k);
                        if (val == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        } else if (val == null) {
                            throw new EOFException();
                        }
                        Object o = map.put(k, val);
                        if (o != null) {
                            throw new IOException("Duplicate key \"" + key + "\" at " + tell);
                        }
                        Json.notifyDuringLoad(j, k, val);
                    }
                }
                break;
            case 6:
                tell = in.tell();
                n = readNumber(v, false);
                if (n instanceof BigInteger && ((BigInteger)n).bitLength() > 63) {
                    throw new IOException("Tag "+n+" with "+((BigInteger)n).bitLength()+" bits is unsupported at "+tell);
                }
                j = readPrivate();
                if (j == null) {
                    throw new EOFException();
                } else if (j == BREAK) {
                    throw new IOException("Unexpected break at " + tell);
                }
                switch (n.intValue()) {
                    case TAG_UNSIGNEDBIGNUM:
                    case TAG_SIGNEDBIGNUM:
                        if (j.isBuffer()) {
                            int tag = n.intValue();
                            ByteBuffer b = j.bufferValue();
                            n = new BigInteger(1, b.array());
                            if (tag == TAG_SIGNEDBIGNUM) {
                                n = BigInteger.valueOf(-1).subtract((BigInteger)n);
                            }
                            j = filter.createNumber(n);
                            n = null;
                        }
                        break;
                    case TAG_BIGDECIMAL2:
                    case TAG_BIGDECIMAL10:
                        if (j.isList() && j.size() == 2 && j.get(0).isNumber() && j.get(1).isNumber()) {
                            Number v0 = j.get(0).numberValue();
                            Number v1 = j.get(1).numberValue();
                            if (v1 instanceof Integer || v1 instanceof Long) {
                                v1 = BigInteger.valueOf(v1.longValue());
                            }
                            if (v1 instanceof BigInteger && v0 instanceof Integer) {
                                int tag = n.intValue();
                                if (tag == TAG_BIGDECIMAL10) {
                                    j = filter.createNumber(new BigDecimal((BigInteger)v1, -v0.intValue()));
                                    n = null;
                                } else {
                                    BigDecimal d = new BigDecimal((BigInteger)v1);
                                    if (v0.intValue() > 0) {
                                        d = d.multiply(BigDecimal.valueOf(2).pow(v0.intValue()));
                                    } else {
                                        d = d.divide(BigDecimal.valueOf(2).pow(-v0.intValue()));
                                    }
                                    j = filter.createNumber(d);
                                    n = null;
                                }
                            }
                        }
                        break;
                }
                if (n != null) {
                    j.setTag(n.longValue());
                }
                break;
            default:
                v &= 0x1f;
                tell = in.tell();
                switch (v) {
                    case 20:
                        j = filter.createBoolean(false);
                        break;
                    case 21:
                        j = filter.createBoolean(true);
                        break;
                    case 22:
                        j = filter.createNull();
                        break;
                    case 23:
                        j = filter.createUndefined();
                        break;
                    case 25:
                        v = readNumber(v, false).intValue();
                        int s = (v & 0x8000) >> 15;
                        int e = (v & 0x7c00) >> 10;
                        int f = v & 0x3ff;
                        if (e == 0) {
                            n = Float.valueOf((float)((s != 0 ? -1f : 1f) * Math.pow(2, -14) * (f / Math.pow(2, 10))));
                        } else if (e == 0x1f) {
                            n = f != 0 ? Float.NaN : Float.valueOf((s != 0 ? -1f : 1f) * Float.POSITIVE_INFINITY);
                        } else {
                            n = Float.valueOf((float)((s != 0 ? -1f : 1f) * Math.pow(2, e - 15) * (1 + f / Math.pow(2, 10))));
                        }
                        j = filter.createNumber(n);
                        break;
                    case 26:
                        n = readNumber(v, false);
                        n = Float.intBitsToFloat(n.intValue());
                        j = filter.createNumber(n);
                        break;
                    case 27:
                        n = readNumber(v, false);
                        n = Double.longBitsToDouble(n.longValue());
                        j = filter.createNumber(n);
                        break;
                    case 31:
                        j = BREAK;
                        break;
                    case 24:
                        v = in.read();
                        if (v < 0) {
                            throw new EOFException();
                        }
                        // fallthrough
                    default:
                        if (options.isCborFailOnUnknownTypes()) {
                            throw new IOException("Undefined special type " + v + " at "+tell);
                        } else {
                            j = filter.createUndefined();
                            j.setTag(v);
                            break;
                        }
                }
        }
        j.setStrict(strict);
        // if (DEBUG) System.out.println("# [CBOR] off=" + origtell + " v=" + origv + " v>>5=" + (origv>>5) + " type=" + j.type() + " out=" + j.toString(new JsonWriteOptions().setCborDiag("hex")));
        return j;
    }

    private InputStream createBufferInputStream(final Number n, CodingErrorAction action) throws IOException {
        if (n == INDEFINITE) {
            return createIndefiniteBufferInputStream(action);
        } else {
            return createFixedInputStream(in, n.longValue(), options.getFastStringLength(), action);
        }
    }

    private InputStream createIndefiniteBufferInputStream(final CodingErrorAction action) throws IOException {
        return new FilterInputStream(in) {
            boolean eof;
            private InputStream current = new ByteArrayInputStream(new byte[0]);
            private void next() throws IOException {
                int c = in.read();
                if (c < 0) {
                    throw new EOFException();
                }
                if (c == 0xFF) {
                    eof = true;
                } else {
                    Number n = readNumber(c, true);
                    while (n.longValue() == 0) {
                        // Because a zero-length here is mistaken for eof
                        c = in.read();
                        if (c == 0xFF) {
                            eof = true;
                            return;
                        } else {
                            n = readNumber(c, true);
                        }
                    }
                    current = createFixedInputStream(in, n.longValue(), options.getFastStringLength(), action);
                }
            }
            @Override public int available() throws IOException {
                int v = current.available();
                if (v == 0 && !eof) {
                    next();
                    v = current.available();
                }
                return v;
            }
            @Override public int read() throws IOException {
                int v = current.read();
                if (v < 0 && !eof) {
                    next();
                    v = current.read();
                }
                return v;
            }
            @Override public int read(byte[] buf, int off, int len) throws IOException {
                int v = current.read(buf, off, len);
                if (v < 0 && !eof) {
                    next();
                    v = current.read(buf, off, len);
                }
                return v;
            }
            @Override public long skip(long v) throws IOException {
                long n = current.skip(v);
                if (n <= 0 && !eof) {
                    next();
                    n = current.skip(v);
                }
                return n;
            }
            @Override public boolean markSupported() {
                return false;
            }
            @Override public void close() {
            }
            @Override public String toString() {
                return action == null ? super.toString() : null;
            }
        };
    }

    /**
     * Return an InputStream of a fixed length from the specified InputStream.
     * If "action" is not null, the stream will be used as a UTF8 String using the
     * specified CodingErrorAction. To make that as fast as possible and use as
     * little memory as possible, this is done by ensuring that toString() on the
     * returned stream is the string value of the content, or that it returns null
     * if it has to be read normally. Horrible, needs must.
     * @param in the InputStream
     * @param len the length from the stream
     * @param fastlen if len is less than this value, read it in as a buffer
     * @param action if not null the stream will be turned into a String
     */
    static InputStream createFixedInputStream(InputStream in, final long len, int fastlen, final CodingErrorAction action) throws IOException {
        if (len < fastlen) {
            byte[] buf = new byte[(int)len];
            int i = 0;
            int v;
            while (i < buf.length && (v=in.read(buf, i, buf.length - i)) >= 0) {
                i += v;
            }
            if (i != buf.length) {
                throw new EOFException();
            } else if (action == null) {
                return new ByteArrayInputStream(buf, 0, buf.length);
            } else {
                String s;
                if (action == CodingErrorAction.REPLACE) {
                    s = new String(buf, 0, buf.length, StandardCharsets.UTF_8);
                } else {
                    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                    decoder.onMalformedInput(action);
                    s = decoder.decode(ByteBuffer.wrap(buf, 0, buf.length)).toString();
                }
                final String fs = s;
                return new ByteArrayInputStream(buf, 0, buf.length) {
                    public String toString() {
                        return fs;
                    }
                };
            }
        } else {
            return new FilterInputStream(in) {
                long remaining = len;
                @Override public int available() throws IOException {
                    int v = in.available();
                    if (remaining < v) {
                        v = (int)remaining;
                    }
                    return v;
                }
                @Override public int read() throws IOException {
                    if (remaining == 0) {
                        return -1;
                    }
                    int c = in.read();
                    if (c >= 0) {
                        remaining--;
                    }
                    return c;
                }
                @Override public int read(byte[] buf, int off, int len) throws IOException {
                    if (remaining == 0) {
                        return -1;
                    }
                    if (remaining < len) {
                        len = (int)remaining;
                    }
                    int v = in.read(buf, off, len);
                    if (v >= 0) {
                        remaining -= v;
                    }
                    return v;
                }
                @Override public long skip(long v) throws IOException {
                    if (remaining < v) {
                        v = remaining;
                    }
                    return super.skip(v);
                }
                @Override public void close() throws IOException {
                }
                @Override public boolean markSupported() {
                    return false;
                }
                @Override public String toString() {
                    return action == null ? super.toString() : null;
                }
            };
        }
    }

    private Number readNumber(int v, boolean allowIndefinite) throws IOException {
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
        v &= 0x1F;
        if (v == 31) {
            if (allowIndefinite) {
                return INDEFINITE;
            } else {
                throw new IOException("Unexpected break at " + in.tell());
            }
        } else if (v < 24) {
            return Integer.valueOf(v);
        } else if (v == 24) {
            v = in.read();
            if (v >= 0) {
                return Integer.valueOf(v);
            }
        } else if (v == 25) {
            v = in.read();
            int v2 = in.read();
            if (v2 >= 0) {
                return Integer.valueOf((v<<8) + v2);
            }
        } else if (v == 26) {
            v = in.read();
            int v2 = in.read();
            int v3 = in.read();
            int v4 = in.read();
            if (v4 >= 0) {
                Number n;
                if (v < 128) {
                    return Integer.valueOf((v<<24) | (v2<<16) | (v3<<8) | v4);
                } else {
                    return Long.valueOf((((long)v)<<24) | (v2<<16) | (v3<<8) | v4);
                }
            }
        } else if (v == 27) {
            byte[] b = new byte[8];
            int l = 0;
            while (l < 8 && (v=in.read(b, l, 8 - l)) >= 0) {
                l += v;
            }
            if (l == 8) {
                if (b[0] >= 0) {
                    return Long.valueOf(((b[0]&0xFFl)<<56) | ((b[1]&0xFFl)<<48) | ((b[2]&0xFFl)<<40) | ((b[3]&0xFFl)<<32) | ((b[4]&0xFFl)<<24) | ((b[5]&0xFFl)<<16) | ((b[6]&0xFF)<<8) | (b[7]&0xFF));
                } else {
                    return new BigInteger(1, b);
                }
            }
        } else {
            throw new IOException("Unknown unsigned integer type 0x" + v +" at " + in.tell());
        }
        throw new EOFException();
    }

}
