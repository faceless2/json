package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.math.*;
import java.util.*;
import java.text.*;

class MsgpackWriter {

    static void write(Json j, OutputStream out, JsonWriteOptions options) throws IOException {
        Core o = j.getCore();
        if (o instanceof INumber) {
            Number n = o.numberValue();
            if (n instanceof BigDecimal) {      // No BigDecimal in MsgPack
                n = Double.valueOf(n.doubleValue());
            }
            if (n instanceof BigInteger) {
                BigInteger bi = (BigInteger)n;
                int bl = bi.bitLength();
                if (bl == 64 && bi.signum() > 0) {
                    long l = bi.longValue();
                    out.write(0xcf);
                    out.write((int)(l>>56));
                    out.write((int)(l>>48));
                    out.write((int)(l>>40));
                    out.write((int)(l>>32));
                    out.write((int)(l>>24));
                    out.write((int)(l>>16));
                    out.write((int)(l>>8));
                    out.write((int)l);
                    return;
                } else if (bl <= 64) {
                    n = Long.valueOf(bi.longValue());
                } else {
                    throw new IllegalArgumentException("Cannot write BigInteger "+bi+" to Msgpack");
                }
            }
            if (n instanceof Long) {
                long l = n.longValue();
                if (l < Integer.MIN_VALUE) {
                    out.write(0xd3);
                    out.write((int)(l>>56));
                    out.write((int)(l>>48));
                    out.write((int)(l>>40));
                    out.write((int)(l>>32));
                    out.write((int)(l>>24));
                    out.write((int)(l>>16));
                    out.write((int)(l>>8));
                    out.write((int)l);
                    return;
                } else if (l > Integer.MAX_VALUE) {
                    out.write(0xcf);
                    out.write((int)(l>>56));
                    out.write((int)(l>>48));
                    out.write((int)(l>>40));
                    out.write((int)(l>>32));
                    out.write((int)(l>>24));
                    out.write((int)(l>>16));
                    out.write((int)(l>>8));
                    out.write((int)l);
                    return;
                } else {
                    n = Integer.valueOf((int)l);
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
        } else if (o instanceof IBuffer) {
            int tag = (int)j.getTag();
            ByteBuffer b = o.bufferValue();
            b.position(0);
            int s = b.limit();
            if (s <= 255) {
                if (tag >= 0) {
                    out.write(0xd4);
                    out.write(tag);
                } else {
                    out.write(0xc4);
                }
                out.write(s);
                Channels.newChannel(out).write(b);
            } else if (s <= 65535) {
                if (tag >= 0) {
                    out.write(0xd5);
                    out.write(tag);
                } else {
                    out.write(0xc5);
                }
                out.write(s >> 8);
                out.write(s);
                Channels.newChannel(out).write(b);
            } else {
                if (tag >= 0) {
                    out.write(0xd6);
                    out.write(tag);
                } else {
                    out.write(0xc6);
                }
                out.write(s >> 24);
                out.write(s >> 16);
                out.write(s >> 8);
                out.write(s);
                Channels.newChannel(out).write(b);
            }
        } else if (o instanceof IString) {
            writeString(o.stringValue(), out, options);
        } else if (o instanceof IList) {
            List<Json> l = o.listValue();
            int s = l.size();
            if (s <= 15) {
                out.write(0x90 | s);
            } else if (s <= 65535) {
                out.write(0xdc);
                out.write(s>>8);
                out.write(s);
            } else {
                out.write(0xdd);
                out.write(s>>24);
                out.write(s>>16);
                out.write(s>>8);
                out.write(s);
            }
            for (Json j2 : o.listValue()) {
                write(j2, out, options);
            }
        } else if (o instanceof IMap) {
            Map<String,Json> map = o.mapValue();
            int s = map.size();
            if (s <= 15) {
                out.write(0x80 | s);
            } else if (s <= 65535) {
                out.write(0xde);
                out.write(s>>8);
                out.write(s);
            } else {
                out.write(0xdf);
                out.write(s>>24);
                out.write(s>>16);
                out.write(s>>8);
                out.write(s);
            }
            if (options != null && options.isSorted()) {
                map = new TreeMap<String,Json>(map);
            }
            for (Map.Entry<String,Json> e : map.entrySet()) {
                writeString(e.getKey(), out, options);
                write(e.getValue(), out, options);
            }
        } else if (o instanceof IBoolean) {
            if (o.booleanValue()) {
                out.write(0xc3);
            } else {
                out.write(0xc2);
            }
        } else if (o == INull.INSTANCE) {
            out.write(0xc0);
        } else {
            throw new IOException("Unknown object "+o);
        }
    }

    private static void writeString(String s, OutputStream out, JsonWriteOptions options) throws IOException {
        int len = 0;
        if (options != null && options.isNFC()) {
            s = Normalizer.normalize(s, Normalizer.Form.NFC);
        }
        int slen = s.length();
        if (slen < 1024) {      // arbitrary limit will still catch most strings. Aiming for stack alloc
            byte[] buf = new byte[slen * 4];
            for (int i=0;i<slen;i++) {
                int c = s.charAt(i);
                if (c < 0x80) {
                    buf[len++] = (byte)c;
                } else if (c < 0x800) {
                    buf[len++] = (byte)((c >> 6) | 0300);
                    buf[len++] = (byte)((c & 077) | 0200);
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length()) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(++i)&0x3ff);
                    buf[len++] = (byte)((c >> 18) | 0360);
                    buf[len++] = (byte)(((c >> 12) & 077) | 0200);
                    buf[len++] = (byte)(((c >> 6) & 077) | 0200);
                    buf[len++] = (byte)((c & 077) | 0200);
                } else {
                    buf[len++] = (byte)((c >> 12) | 0340);
                    buf[len++] = (byte)(((c >> 6) & 077) | 0200);
                    buf[len++] = (byte)((c & 077) | 0200);
                }
            }
            if (len <= 31) {
                out.write(0xa0 + len);
            } else if (len <= 255) {
                out.write(0xd9);
                out.write(len);
            } else {
                out.write(0xda);
                out.write(len>>8);
                out.write(len);
            }
            out.write(buf, 0, len);
        } else {
            for (int i=0;i<s.length();i++) {
                char c = s.charAt(i);
                if (c < 0x7f) {
                    len++;
                } else if (c < 0x800) {
                    len += 2;
                } else if (c >= 0xd800 && c <= 0xdbff) {
                    i++;
                    len += 4;
                } else {
                    len += 3;
                }
            }
            if (len <= 65535) {
                out.write(0xda);
                out.write(len>>8);
                out.write(len);
            } else {
                out.write(0xdb);
                out.write(len>>24);
                out.write(len>>16);
                out.write(len>>8);
                out.write(len);
            }
            // Measurably faster than OutputStreamWriter, as we have to init it each time.
            for (int i=0;i<s.length();i++) {
                int c = s.charAt(i);
                if (c < 0x80) {
                    out.write(c);
                } else if (c < 0x800) {
                    out.write((c >> 6) | 0300);
                    out.write((c & 077) | 0200);
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length()) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(++i)&0x3ff);
                    out.write((c >> 18) | 0360);
                    out.write(((c >> 12) & 077) | 0200);
                    out.write(((c >> 6) & 077) | 0200);
                    out.write((c & 077) | 0200);
                } else {
                    out.write((c >> 12) | 0340);
                    out.write(((c >> 6) & 077) | 0200);
                    out.write((c & 077) | 0200);
                }
            }
        }
    }
}
