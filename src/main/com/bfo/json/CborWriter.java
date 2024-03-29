package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.math.*;
import java.util.*;
import java.text.*;

class CborWriter {

    private final OutputStream out;
    private final JsonWriteOptions options;
    private final boolean filtered;
    private final JsonWriteOptions.Filter filter;
    private final Appendable stringWriter;

    CborWriter(final OutputStream out, JsonWriteOptions options, Json root) {
        this.out = out;
        this.options = options;
        this.filtered = options.getFilter() != null;
        this.filter = options.initializeFilter(root);
        stringWriter = new UTF8Writer(out, options.isNFC()) {
            @Override void writeLength(int len) throws IOException {
                writeNum(3, len, out);
            }
        };
    }

    void write(Json j) throws IOException {
        if (j.getTag() >= 0) {
            writeNum(6, j.getTag(), out);
        }
        if (j.isNumber()) {
            writeNumber(j.numberValue(), out);
        } else if (j.isBuffer()) {
            if (j.isIndefiniteBuffer()) {
                // May have overriden writeBuffer - write as an indefinite length buffer
                writeNum(2, -1, out);
                OutputStream fo = new IndefiniteLengthOutputStream(out, 2);
                j.writeBuffer(fo);
                fo.close();
                out.write(0xFF);
            } else {
                ByteBuffer b = j.bufferValue();
                writeNum(2, b.limit(), out);
                ((Buffer)b).position(0);
                Channels.newChannel(out).write(b);
            }
        } else if (j.isString()) { 
            if (j.isIndefiniteString()) {
                // May have overriden writeString - write as an indefinite length string
                writeNum(3, -1, out);
                OutputStream fo = new IndefiniteLengthOutputStream(out, 3);
                j.writeString(new UTF8Writer(fo, options.isNFC()));
                fo.close();
                out.write(0xFF);
            } else {
                j.writeString(stringWriter);
            }
        } else if (j.isList()) {
            List<Json> list = j._listValue();
            writeNum(4, list.size(), out);
            String key = null;
            for (int i=0;i<list.size();i++) {
                Json ovalue = list.get(i);
                Json value = ovalue;
                if (filtered) {
                    key = Integer.toString(i);
                    value = filter.enter(key, ovalue);
                }
                write(value);
                if (filtered) {
                    filter.exit(key, ovalue);
                }
            }
        } else if (j.isMap()) {
            Map<Object,Json> map = j._mapValue();
            if (options.isSorted()) {
                Map<Object,Json> m = new TreeMap<Object,Json>(new Comparator<Object>() {
                    public int compare(Object o1, Object o2) {
                        return o1 instanceof Number && o2 instanceof Number ? Double.valueOf(((Number)o1).doubleValue()).compareTo(((Number)o2).doubleValue()) : o1.toString().compareTo(o2.toString());
                    }
                });
                m.putAll(map);
                map = m;
            }
            if (filtered) {
                writeNum(5, -1, out);
                for (Map.Entry<Object,Json> e : map.entrySet()) {
                    Json value = filter.enter(e.getKey(), e.getValue());
                    if (value != null) {
                        writeMapKey(e.getKey());
                        write(value);
                    }
                    filter.exit(e.getKey(), e.getValue());
                }
                out.write(0xFF);
            } else {
                writeNum(5, map.size(), out);
                for (Map.Entry<Object,Json> e : map.entrySet()) {
                    writeMapKey(e.getKey());
                    write(e.getValue());
                }
            }
        } else if (j.isBoolean()) {
            if (j.booleanValue()) {
                out.write(0xf5);
            } else {
                out.write(0xf4);
            }
        } else if (j.isNull()) {
            out.write(0xf6);
        } else if (j.isUndefined()) {
            out.write(0xf7);
        } else {
            throw new IOException("Unknown object " + j);
        }
    }

    private void writeMapKey(Object o) throws IOException {
        if (o instanceof String) {
            stringWriter.append((String)o);
        } else if (o instanceof Number) {
            writeNumber((Number)o, out);
        } else if (o instanceof Boolean && ((Boolean)o).booleanValue()) {
            out.write(0xf5);
        } else if (o instanceof Boolean) {
            out.write(0xf4);
        } else if (o == Json.NULL) {
            out.write(0xf6);
        } else if (o == Json.UNDEFINED) {
            out.write(0xf7);
        } else {
            throw new IOException("Unknown key " + o);
        }
    }

    private static void writeNumber(Number n, OutputStream out) throws IOException {
        if (n instanceof BigDecimal) {
            // No native BigDecimal in CBOR, write as tag 5 [exponent-10, mantissa]
            BigDecimal d = (BigDecimal)n;
            writeNum(6, CborReader.TAG_BIGDECIMAL10, out);      // tag
            writeNum(4, 2, out);                                // list of 2
            writeNumber(Integer.valueOf(-d.scale()), out);      // scale
            writeNumber(d.unscaledValue(), out);                // mantissa
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
        } else {
            int i = n.intValue();
            if (i < 0) {
                writeNum(1, -i - 1, out);
            } else {
                writeNum(0, i, out);
            }
        }
    }

    private static void writeNum(int prefix, long i, OutputStream out) throws IOException {
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

    private static class IndefiniteLengthOutputStream extends FilterOutputStream {
        private final ByteArrayOutputStream hold = new ByteArrayOutputStream();
        private final int type;

        IndefiniteLengthOutputStream(OutputStream out, int type) {
            super(out);
            this.type = type;
        }

        @Override public void write(int v) throws IOException {
            v &= 0xFF;
            hold.write(v);
            if (v == 0xFF) {        // stop bit
                close();
            }
        }

        @Override public void close() throws IOException {
            CborWriter.writeNum(type, hold.size(), out);
            hold.writeTo(out);
            hold.reset();
        }

        @Override public void write(byte[] buf, int off, int len) throws IOException {
            len += off;
            while (off < len) {
                write(buf[off++]);
            }
        }
    }

}
