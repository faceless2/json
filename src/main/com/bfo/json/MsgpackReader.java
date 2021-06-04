package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;

class MsgpackReader {

    /**
     * Read a Msgpack serialized object
     */
    static Json read(CountingInputStream in, JsonReadOptions options) throws IOException {
        int v = in.read();
        if (v < 0) {
            return null;
        }
        Json j;
        long l;
        if (v < 0x80) {
            return new Json(new INumber(v & 0x7f, options));
        }
        if (v <= 0x8f) {
            return readMap(in, options, v & 0xf);
        }
        if (v <= 0x9f) {
            return readList(in, options, v & 0xf);
        }
        if (v <= 0xbf) {
            return readString(in, options, v & 0x1f);
        }
        if (v >= 0xe0) {
            return new Json(new INumber((byte)v, options));
        }
        switch (v) {
            case 0xc0:
                return new Json(INull.INSTANCE);
            case 0xc1:
                throw new IOException("Unexpected type 0xC1");
            case 0xc2:
                return new Json(new IBoolean(false, options));
            case 0xc3:
                return new Json(new IBoolean(true, options));
            case 0xc4: // bin8;
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return new Json(new IBuffer(ByteBuffer.wrap(readBuffer(in, options, v))));
            case 0xc5: // bin16;
                v = (in.read() << 8) | in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return new Json(new IBuffer(ByteBuffer.wrap(readBuffer(in, options, v))));
            case 0xc6: // bin32;
                v = readInt(in);
                if (v < 0) {
                    throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " byte array in Java");
                }
                return new Json(new IBuffer(ByteBuffer.wrap(readBuffer(in, options, v))));
            case 0xc7: // ext8
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return readExt(in, options, v);
            case 0xc8: // ext16
                v = (in.read() << 8) | in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return readExt(in, options, v);
            case 0xc9: // ext32
                v = readInt(in);
                if (v < 0) {
                    throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " byte array in Java");
                }
                return readExt(in, options, v);
            case 0xca: // float32
                v = readInt(in);
                return new Json(new INumber(Float.intBitsToFloat(v), options));
            case 0xcb:
                l = readLong(in);
                return new Json(new INumber(Double.longBitsToDouble(l), options));
            case 0xcc: // uint8
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return new Json(new INumber(v & 0xff, options));
            case 0xcd: { // uint16
                v = (in.read() << 8) | in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return new Json(new INumber(v & 0xffff, options));
            }
            case 0xce: // uint32
                v = readInt(in);
                return new Json(v < 0 ? new INumber(v & 0xFFFFFFFFl, options) : new INumber(v, options));
            case 0xcf: // uint64
                l = readLong(in);
                if (l >= 0) {
                    return new Json(new INumber(l, options));
                } else {
                    int upper = (int)(l >>> 32);
                    int lower = (int)l;
                    return new Json(new INumber((BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong(lower))), options));
                }
            case 0xd0: // int8
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return new Json(new INumber(Integer.valueOf((byte)v), options));
            case 0xd1: // int16
                v = (in.read() << 8) | in.read();
                return new Json(new INumber(Integer.valueOf((short)v), options));
            case 0xd2: // int32
                v = readInt(in);
                return new Json(new INumber(v, options));
            case 0xd3: // int64
                l = readLong(in);
                return new Json(new INumber(l, options));
            case 0xd4: // fixext1
                return readExt(in, options, 1);
            case 0xd5: // fixext2
                return readExt(in, options, 2);
            case 0xd6: // fixext4
                return readExt(in, options, 4);
            case 0xd7: // fixext8
                return readExt(in, options, 8);
            case 0xd8: // fixext16
                return readExt(in, options, 16);
            case 0xd9: // str8
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return readString(in, options, v);
            case 0xda: // str16
                v = (in.read() << 8) | in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return readString(in, options, v);
            case 0xdb: // str32
                v = readInt(in);
                if (v < 0) {
                    throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " byte String in Java");
                }
                return readString(in, options, v);
            case 0xdc: // array16
                v = (in.read() << 8) | in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return readList(in, options, v);
            case 0xdd: // array32
                v = readInt(in);
                if (v < 0) {
                    throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " entry List in Java");
                }
                return readList(in, options, v);
            case 0xde: // map16
                v = (in.read() << 8) | in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                return readMap(in, options, v);
            case 0xdf: // map32
                v = readInt(in);
                if (v < 0) {
                    throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " entry Map in Java");
                }
                return readMap(in, options, v);
        }
        return null;    // Can't happen
    }

    private static byte[] readBuffer(CountingInputStream in, JsonReadOptions options, int len) throws IOException {
        byte[] buf = new byte[len];
        int i = 0, v;
        while (i < len && (v=in.read(buf, i, len - i)) >= 0) {
            i += v;
        }
        if (i == len) {
            return buf;
        }
        throw new EOFException();
    }

    private static Json readString(CountingInputStream in, JsonReadOptions options, int len) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(options == null ? CodingErrorAction.REPLACE : options.getCborStringCodingErrorAction());
        return new Json(new IString(decoder.decode(ByteBuffer.wrap(readBuffer(in, options, len))).toString(), options));
    }

    private static Json readMap(CountingInputStream in, JsonReadOptions options, int len) throws IOException {
        Json j = new Json(new IMap());
        while (len-- > 0) {
            long tell = in.tell();
            Json key = read(in, options);
            if (key == null) {
                throw new EOFException();
            } else if (!key.isString()) {
                // TODO warning?
            }
            Json val = read(in, options);
            if (val == null) {
                throw new EOFException();
            }
            Object o = j._mapValue().put(key.stringValue(), val);
            if (o != null) {
                throw new IOException("Duplicate key \"" + key + "\" at " + tell);
            }
        }
        return j;
    }

    private static Json readList(CountingInputStream in, JsonReadOptions options, int len) throws IOException {
        Json j = new Json(new IList());
        while (len-- > 0) {
            Json j2 = read(in, options);
            if (j2 == null) {
                throw new EOFException();
            }
            j._listValue().add(j2);
        }
        return j;
    }

    private static Json readExt(CountingInputStream in, JsonReadOptions options, int len) throws IOException {
        int tag = in.read();
        if (tag < 0) {
            throw new EOFException();
        }
        Json j = new Json(new IBuffer(ByteBuffer.wrap(readBuffer(in, options, len))));
        j.setTag(tag);
        return j;
    }

    private static int readInt(InputStream in) throws IOException {
        int v0 = in.read();
        if (v0 < 0) {
            throw new EOFException();
        }
        int v1 = (in.read()<<16) | (in.read()<<8) | in.read();
        if (v1 < 0) {
            throw new EOFException();
        }
        return (v0 << 24) | v1;
    }

    private static long readLong(InputStream in) throws IOException {
        return ((long)readInt(in)<<32) | (readInt(in)&0xFFFFFFFFl);
    }

}
