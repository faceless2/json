package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.math.*;
import java.util.*;
import java.text.*;

class MsgpackWriter {

    private final OutputStream out;
    private final JsonWriteOptions options;
    private final JsonWriteOptions.Filter filter;
    private final boolean filtered;
    private final Appendable stringWriter;

    MsgpackWriter(final OutputStream out, final JsonWriteOptions options, final Json root) {
        this.out = out;
        this.options = options;
        this.filtered = options.getFilter() != null;
        this.filter = options.initializeFilter(root);
        this.stringWriter = new UTF8Writer(out, options.isNFC()) {
            @Override void writeLength(int len) throws IOException {
                if (len <= 31) {
                    out.write(0xa0 + len);
                } else if (len <= 255) {
                    out.write(0xd9);
                    out.write(len);
                } else if (len <= 65535) {
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
            }
        };
    }

    void write(Json j) throws IOException {
        if (j.isNumber()) {
            writeNumber(j.numberValue());
        } else if (j.isBuffer()) {
            int tag = (int)j.getTag();
            final ByteBuffer[] holder = new ByteBuffer[] { j.bufferValue() };
            if (j.getClass() != Json.class) {
                // Msgpack has no "indefinite length" option, so just make a bytebuffer
                // from the output written by the proxy
                ByteArrayOutputStream bout = new ByteArrayOutputStream() {
                    @Override public void close() {
                        holder[0] = ByteBuffer.wrap(buf, 0, count);
                    }
                };
                j.writeBuffer(bout);
                bout.close();
            }
            ByteBuffer b = holder[0];
            ((Buffer)b).position(0);
            int s = b.limit();
            if (tag >= 0) {
                if (s == 1) {
                    out.write(0xd4);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else if (s == 2) {
                    out.write(0xd5);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else if (s == 4) {
                    out.write(0xd6);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else if (s == 8) {
                    out.write(0xd7);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else if (s == 16) {
                    out.write(0xd8);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else if (s <= 255) {
                    out.write(0xc7);
                    out.write(s);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else if (s <= 65535) {
                    out.write(0xc8);
                    out.write(s>>8);
                    out.write(s);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                } else {
                    out.write(0xc9);
                    out.write(s>>24);
                    out.write(s>>16);
                    out.write(s>>8);
                    out.write(s);
                    out.write(tag);
                    Channels.newChannel(out).write(b);
                }
            } else if (s <= 255) {
                out.write(0xc4);
                out.write(s);
                Channels.newChannel(out).write(b);
            } else if (s <= 65535) {
                out.write(0xc5);
                out.write(s >> 8);
                out.write(s);
                Channels.newChannel(out).write(b);
            } else {
                out.write(0xc6);
                out.write(s >> 24);
                out.write(s >> 16);
                out.write(s >> 8);
                out.write(s);
                Channels.newChannel(out).write(b);
            }
        } else if (j.isString()) {
            j.writeString(stringWriter);
        } else if (j.isList()) {
            List<Json> list = j._listValue();
            // todo how do we do filtering here? no indefinite length so not possible without temp buffer
            int s = list.size();
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
            for (Json j2 : list) {
                write(j2);
            }
        } else if (j.isMap()) {
            Map<Object,Json> map = j._mapValue();
            // todo how do we do filtering here? no indefinite length so not possible without temp buffer
            if (options.isSorted()) {
                Map<Object,Json> m2 = new TreeMap<Object,Json>(new Comparator<Object>() {
                    public int compare(Object o1, Object o2) {
                        return o1 instanceof Number && o2 instanceof Number ? Double.valueOf(((Number)o1).doubleValue()).compareTo(((Number)o2).doubleValue()) : o1.toString().compareTo(o2.toString());
                    }
                });
                m2.putAll(map);
                map = m2;
            }
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
            for (Map.Entry<Object,Json> e : map.entrySet()) {
                writeMapKey(e.getKey());
                write(e.getValue());
            }
        } else if (j.isBoolean()) {
            if (j.booleanValue()) {
                out.write(0xc3);
            } else {
                out.write(0xc2);
            }
        } else if (j.isNull() || j.isUndefined()) {
            out.write(0xc0);
        } else {
            throw new IOException("Unknown object " + j);
        }
    }

    void writeNumber(Number n) throws IOException {
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
    }

    private void writeMapKey(Object o) throws IOException {
        if (o instanceof String) {
            stringWriter.append((String)o);
        } else if (o instanceof Number) {
            writeNumber((Number)o);
        } else if (o instanceof Boolean && ((Boolean)o).booleanValue()) {
            out.write(0xc3);
        } else if (o instanceof Boolean) {
            out.write(0xc2);
        } else if (o == Json.NULL || o == Json.UNDEFINED) {
            out.write(0xc0);
        } else {
            throw new IOException("Unknown map key " + o);
        }
    }
}
