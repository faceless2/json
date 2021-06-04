package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;

class CborReader {

    private static final Number INDEFINITE = Float.intBitsToFloat(0x12345678);       // As good as any
    private static final Json BREAK = new Json(new INumber(INDEFINITE, null));

    /**
     * Read a CBOR serialized object
     */
    static Json read(CountingInputStream in, JsonReadOptions options) throws IOException {
        int v = in.read();
        if (v < 0) {
            return null;
        }
        Number n;
        Json j;
        long tell;
        switch (v>>5) {
            case 0:
                return new Json(new INumber(readNumber(v, in, false), options));
            case 1:
                n = readNumber(v, in, false);
                if (n instanceof Integer) {
                    n = Integer.valueOf(-1 - n.intValue());
                } else if (n instanceof Long) {
                    n = Long.valueOf(-1l - n.longValue());
                } else if (n instanceof BigInteger) {
                    n = BigInteger.valueOf(-1).subtract((BigInteger)n);
                }
                return new Json(new INumber(n, options));
            case 2:
                return new Json(new IBuffer(ByteBuffer.wrap(readBuffer(v, in))));
            case 3:
                 CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                 decoder.onMalformedInput(options == null ? CodingErrorAction.REPLACE : options.getCborStringCodingErrorAction());
                 return new Json(new IString(decoder.decode(ByteBuffer.wrap(readBuffer(v, in))).toString(), options));
            case 4:
                n = readNumber(v, in, true);
                j = new Json(new IList());
                if (n == INDEFINITE) {
                    Json j2;
                    while ((j2 = read(in, options)) != BREAK) {
                        if (j2 == null) {
                            throw new EOFException();
                        }
                        j._listValue().add(j2);
                    }
                } else {
                    int l = n.intValue();
                    for (int i=0;i<l;i++) {
                        tell = in.tell();
                        Json j2 = read(in, options);
                        if (j2 == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        } else if (j2 == null) {
                            throw new EOFException();
                        }
                        j._listValue().add(j2);
                    }
                }
                return j;
            case 5:
                n = readNumber(v, in, true);
                j = new Json(new IMap());
                if (n == INDEFINITE) {
                    Json key;
                    while ((key = read(in, options)) != BREAK) {
                        if (key == null) {
                            throw new EOFException();
                        }
                        if (!key.isString()) {
                            // TODO warning?
                        }
                        tell = in.tell();
                        Json val = read(in, options);
                        if (val == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        } else if (val == null) {
                            throw new EOFException();
                        }
                        Object o = j._mapValue().put(key.stringValue(), val);
                        if (o != null) {
                            throw new IOException("Duplicate key \"" + key + "\" at " + tell);
                        }
                    }
                } else {
                    int l = n.intValue();
                    for (int i=0;i<l;i++) {
                        tell = in.tell();
                        Json key = read(in, options);
                        if (key == null) {
                            throw new EOFException();
                        } else if (key == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        }
                        if (!key.isString()) {
                            // TODO warning?
                        }
                        Json val = read(in, options);
                        if (val == BREAK) {
                            throw new IOException("Unexpected break at " + tell);
                        } else if (val == null) {
                            throw new EOFException();
                        }
                        Object o = j._mapValue().put(key.stringValue(), val);
                        if (o != null) {
                            throw new IOException("Duplicate key \"" + key + "\" at " + tell);
                        }
                    }
                }
                return j;
            case 6:
                tell = in.tell();
                n = readNumber(v, in, false);
                if (n instanceof BigInteger && ((BigInteger)n).bitLength() > 63) {
                    throw new IOException("Tag "+n+" with "+((BigInteger)n).bitLength()+" bits is unsupported at "+tell);
                }
                j = read(in, options);
                if (j == null) {
                    throw new EOFException();
                } else if (j == BREAK) {
                    throw new IOException("Unexpected break at " + tell);
                }
                if ((n.intValue() == 2 || n.intValue() == 3) && j.isBuffer()) {
                    int tag = n.intValue();
                    ByteBuffer b = j.getCore().bufferValue();
                    n = new BigInteger(1, b.array());
                    if (tag == 3) {
                        n = BigInteger.valueOf(-1).subtract((BigInteger)n);
                    }
                    j = new Json(new INumber(n, options));
                } else {
                    j.setTag(n.longValue());
                }
                return j;
            default:
                v &= 0x1f;
                tell = in.tell();
                switch (v) {
                    case 20:
                        return new Json(new IBoolean(false, options));
                    case 21:
                        return new Json(new IBoolean(true, options));
                    case 22:
                        return new Json(INull.INSTANCE);
                    case 23:
                        j = new Json(INull.INSTANCE);
                        j.setTag(v);
                        return j;
                    case 25:
                        v = readNumber(v, in, false).intValue();
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
                        return new Json(new INumber(n, options));
                    case 26:
                        n = readNumber(v, in, false);
                        n = Float.intBitsToFloat(n.intValue());
                        return new Json(new INumber(n, options));
                    case 27:
                        n = readNumber(v, in, false);
                        n = Double.longBitsToDouble(n.longValue());
                        return new Json(new INumber(n, options));
                    case 31:
                        return BREAK;
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
                            j = new Json(INull.INSTANCE);
                            j.setTag(v);
                            return j;
                        }
                }
        }
    }

    private static byte[] readBuffer(int v, CountingInputStream in) throws IOException {
        Number n = readNumber(v, in, true);
        if (n == INDEFINITE) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while ((v = in.read()) >= 0) {
                if (v == 0xFF) {
                    return out.toByteArray();
                }
                out.write(readBuffer(v, in));
            }
            throw new EOFException();
        } else {
            if (n.intValue() == 0) {
                return new byte[0];
            }
            byte[] buf = new byte[n.intValue()];
            int i = 0;
            while (i < buf.length && (v=in.read(buf, i, buf.length - i)) >= 0) {
                i += v;
            }
            if (i == v) {
                return buf;
            }
            throw new EOFException();
        }
    }

    private static Number readNumber(int v, CountingInputStream in, boolean allowIndefinite) throws IOException {
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
