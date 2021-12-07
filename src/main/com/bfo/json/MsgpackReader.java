package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;

class MsgpackReader {

    private final CountingInputStream in;
    private final JsonReadOptions options;
    private final JsonReadOptions.Filter filter;
    private final boolean strict;

    MsgpackReader(CountingInputStream in, JsonReadOptions options) {
        this.in = in;
        this.options = options;
        this.strict = options.isStrictTypes();
        this.filter = options.getFilter() != null ? options.getFilter() : new JsonReadOptions.Filter() {};
        filter.initialize();
    }

    /**
     * Read a Msgpack serialized object
     */
    Json read() throws IOException {
        int v = in.read();
        if (v < 0) {
            return null;
        }
        Json j = null;
        long l;
        if (v < 0x80) {
            j = filter.createNumber(v & 0x7f);
        } else if (v <= 0x8f) {
            j = readMap(v & 0xf);
        } else if (v <= 0x9f) {
            j = readList(v & 0xf);
        } else if (v <= 0xbf) {
            j = readString(v & 0x1f);
        } else if (v >= 0xe0) {
            j = filter.createNumber((byte)v);
        } else {
            switch (v) {
                case 0xc0:
                    j = filter.createNull();
                    break;
                case 0xc1:
                    throw new IOException("Unexpected type 0xC1");
                case 0xc2:
                    j = filter.createBoolean(false);
                    break;
                case 0xc3:
                    j = filter.createBoolean(true);
                    break;
                case 0xc4: // bin8;
                    v = in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readBuffer(v);
                    break;
                case 0xc5: // bin16;
                    v = (in.read() << 8) | in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readBuffer(v);
                    break;
                case 0xc6: // bin32;
                    v = readInt();
                    if (v < 0) {
                        throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " byte array in Java");
                    }
                    j = readBuffer(v);
                    break;
                case 0xc7: // ext8
                    v = in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readExt(v);
                    break;
                case 0xc8: // ext16
                    v = (in.read() << 8) | in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readExt(v);
                    break;
                case 0xc9: // ext32
                    v = readInt();
                    if (v < 0) {
                        throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " byte array in Java");
                    }
                    j = readExt(v);
                    break;
                case 0xca: // float32
                    v = readInt();
                    j = filter.createNumber(Float.intBitsToFloat(v));
                    break;
                case 0xcb:
                    l = readLong();
                    j = filter.createNumber(Double.longBitsToDouble(l));
                    break;
                case 0xcc: // uint8
                    v = in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = filter.createNumber(v & 0xff);
                    break;
                case 0xcd: // uint16
                    v = (in.read() << 8) | in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = filter.createNumber(v & 0xffff);
                    break;
                case 0xce: // uint32
                    v = readInt();
                    if (v < 0) {
                        j = filter.createNumber(v & 0xffffffffl);
                    } else {
                        j = filter.createNumber(v);
                    }
                    break;
                case 0xcf: // uint64
                    l = readLong();
                    if (l >= 0) {
                        j = filter.createNumber(l);
                    } else {
                        int upper = (int)(l >>> 32);
                        int lower = (int)l;
                        j = filter.createNumber((BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong(lower))));
                    }
                    break;
                case 0xd0: // int8
                    v = in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = filter.createNumber(Integer.valueOf((byte)v));
                    break;
                case 0xd1: // int16
                    v = (in.read() << 8) | in.read();
                    j = filter.createNumber(Integer.valueOf((short)v));
                    break;
                case 0xd2: // int32
                    v = readInt();
                    j = filter.createNumber(v);
                    break;
                case 0xd3: // int64
                    l = readLong();
                    j = filter.createNumber(l);
                    break;
                case 0xd4: // fixext1
                    j = readExt(1);
                    break;
                case 0xd5: // fixext2
                    j = readExt(2);
                    break;
                case 0xd6: // fixext4
                    j = readExt(4);
                    break;
                case 0xd7: // fixext8
                    j = readExt(8);
                    break;
                case 0xd8: // fixext16
                    j = readExt(16);
                    break;
                case 0xd9: // str8
                    v = in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readString(v);
                    break;
                case 0xda: // str16
                    v = (in.read() << 8) | in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readString(v);
                    break;
                case 0xdb: // str32
                    v = readInt();
                    if (v < 0) {
                        throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " byte String in Java");
                    }
                    j = readString(v);
                    break;
                case 0xdc: // array16
                    v = (in.read() << 8) | in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readList(v);
                    break;
                case 0xdd: // array32
                    v = readInt();
                    if (v < 0) {
                        throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " entry List in Java");
                    }
                    j = readList(v);
                    break;
                case 0xde: // map16
                    v = (in.read() << 8) | in.read();
                    if (v < 0) {
                        throw new EOFException();
                    }
                    j = readMap(v);
                    break;
                case 0xdf: // map32
                    v = readInt();
                    if (v < 0) {
                        throw new IOException("Can't create " + (v&0xFFFFFFFFl) + " entry Map in Java");
                    }
                    j = readMap(v);
                    break;
            }
        }
        if (j != null) {
            j.setStrict(strict);
        }
        return j;
    }

    private Json readBuffer(int len) throws IOException {
        InputStream in = readStream(len);
        return filter.createBuffer(in, in.available());
    }

    private Json readExt(int len) throws IOException {
        int tag = in.read();
        if (tag < 0) {
            throw new EOFException();
        }
        InputStream in = readStream(len);
        Json j = filter.createBuffer(in, in.available());
        j.setTag(tag);
        return j;
    }

    private Json readString(int len) throws IOException {
        InputStream in = readStream(len);
        if (in instanceof ByteBufferInputStream) {
            CharSequenceReader r = ((ByteBufferInputStream)in).getUTF8(options.getCborStringCodingErrorAction());
            return filter.createString(r, r.stringValue().length());
        } else {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(options.getCborStringCodingErrorAction());
            return filter.createString(new InputStreamReader(in, decoder), -1);
        }
    }

    private InputStream readStream(final int len) throws IOException {
        if (len <= 65535) {
            byte[] buf = new byte[len];
            int i = 0;
            int v;
            while (i < buf.length && (v=in.read(buf, i, buf.length - i)) >= 0) {
                i += v;
            }
            if (i == buf.length) {
                return new ByteBufferInputStream(ByteBuffer.wrap(buf));
            }
            throw new EOFException();
        } else {
            return new FilterInputStream(in) {
                int remaining = len;
                @Override public int available() throws IOException {
                    return remaining;
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
                        len = remaining;
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
                    return in.skip(v);
                }
                @Override public void close() throws IOException {
                }
            };
        }
    }

    private Json readMap(int len) throws IOException {
        // Because passing a populated collection into Json() clones items
        Json j = filter.createMap();
        Map<String,Json> map = j._mapValue();
        while (len-- > 0) {
            long tell = in.tell();
            Json key = read();
            if (key == null) {
                throw new EOFException();
            } else if (!key.isString() && options.isFailOnNonStringKeys()) {
                throw new IOException("Map key \"" + key + "\" is " + key.type() + " rather than string at " + tell);
            }
            String k = key.isNull() ? "null" : key.stringValue();
            filter.enter(j, k);
            Json val = read();
            filter.exit(j, k);
            if (val == null) {
                throw new EOFException();
            }
            Object o = map.put(k, val);
            if (o != null) {
                throw new IOException("Duplicate key \"" + key + "\" at " + tell);
            }
            Json.notify(j, k, null, val);
        }
        return j;
    }

    private Json readList(int len) throws IOException {
        // Because passing a populated collection into Json() clones items
        Json j = filter.createList();
        List<Json> list = j._listValue();
        while (len-- > 0) {
            int size = list.size();
            filter.exit(j, size);
            Json val = read();
            filter.exit(j, size);
            if (val == null) {
                throw new EOFException();
            }
            list.add(val);
            Json.notify(j, size, null, val);
        }
        return j;
    }

    private int readInt() throws IOException {
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

    private long readLong() throws IOException {
        return ((long)readInt()<<32) | (readInt()&0xFFFFFFFFl);
    }

}
