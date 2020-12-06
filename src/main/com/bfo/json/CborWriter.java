package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.math.*;
import java.util.*;
import java.text.*;

class CborWriter {

    static void write(Json j, OutputStream out, JsonWriteOptions options) throws IOException {
        if (j.getTag() >= 0) {
            writeNum(6, j.getTag(), out);
        }
        Core o = j.getCore();
        if (o instanceof INumber) {
            Number n = o.numberValue();
            if (n instanceof BigDecimal) {      // No BigDecimal in CBOR
                n = Double.valueOf(n.doubleValue());
            }
            if (n instanceof Integer) {
                int i = n.intValue();
                if (i < 0) {
                    writeNum(1, -i - 1, out);
                } else {
                    writeNum(0, i, out);
                }
            } else if (n instanceof Long) {
                long l = n.longValue();
                if (l < 0) {
                    writeNum(1, -l - 1, out);
                } else {
                    writeNum(0, l, out);
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
                        writeNum(1, -l - 1, out);
                    } else {
                        writeNum(0, l, out);
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
                        writeNum(2, b.length, out);
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
                        writeNum(2, b.length, out);
                        out.write(b);
                    }
                }
            }
        } else if (o instanceof IBuffer) {
            ByteBuffer b = o.bufferValue();
            writeNum(2, b.limit(), out);
            b.position(0);
            Channels.newChannel(out).write(b);
        } else if (o instanceof IString) {
            writeString(o.stringValue(), out, options);
        } else if (o instanceof IList) {
            List<Json> l = o.listValue();
            writeNum(4, l.size(), out);
            for (Json j2 : o.listValue()) {
                write(j2, out, options);
            }
        } else if (o instanceof IMap) {
            Map<String,Json> map = o.mapValue();
            writeNum(5, map.size(), out);
            if (options != null && options.isSorted()) {
                map = new TreeMap<String,Json>(map);
            }
            for (Map.Entry<String,Json> e : map.entrySet()) {
                writeString(e.getKey(), out, options);
                write(e.getValue(), out, options);
            }
        } else if (o instanceof IBoolean) {
            if (o.booleanValue()) {
                out.write(0xf5);
            } else {
                out.write(0xf4);
            }
        } else if (o == INull.INSTANCE) {
            out.write(0xf6);
        } else {
            throw new IOException("Unknown object "+o);
        }
    }

    private static void writeNum(int prefix, long i, OutputStream out) throws IOException {
        if (i < 23) {
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

    private static void writeString(String s, OutputStream out, JsonWriteOptions options) throws IOException {
        int len = 0;
        if (options != null && options.isNFC()) {
            s = Normalizer.normalize(s, Normalizer.Form.NFC);
        }
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            if (c < 0x7f) {
                len++;
            } else if (c < 0x800) {
                len += 2;
            } else if (c >= 0xd800 && c <= 0xdbff) {
                i++;
                len += 4;
            } else if (c < 0x10000) {
                len += 3;
            }
        }
        writeNum(3, len, out);
        OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write(s);
        w.flush();
    }
}
