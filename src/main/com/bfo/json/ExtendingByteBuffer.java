package com.bfo.json;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;

class ExtendingByteBuffer extends OutputStream implements WritableByteChannel {

    // Because ByteArrayOutputStream is synchronized, and toByteArray() always copies the buffer

    byte[] data;
    int len;

    ExtendingByteBuffer() {
        data = new byte[8192];
    }

    @Override public void write(int v) {
        if (data.length == len) {
            data = Arrays.copyOf(data, data.length + (data.length >> 1));
        }
        data[len++] = (byte)v;
    }

    @Override public void write(byte[] buf) {
        write(ByteBuffer.wrap(buf));
    }

    @Override public void write(byte[] buf, int off, int len) {
        write(ByteBuffer.wrap(buf, off, len));
    }

    @Override public boolean isOpen() {
        return true;
    }

    @Override public void close() {
    }

    int size() {
        return len;
    }

    @Override public int write(ByteBuffer buf) {
        final int newlen = buf.remaining();
        if (data.length - len - newlen < 0) {
            data = Arrays.copyOf(data, Math.min(data.length + (data.length >> 1), len + newlen));
        }
        buf.get(data, len, newlen);
        len += newlen;
        return newlen;
    }

    void write(ReadableByteChannel r) throws IOException {
        int l;
        do {
            if (data.length - len < 8192) {
                data = Arrays.copyOf(data, Math.min(data.length + (data.length >> 1), len + 8192));
            }
            l = r.read(ByteBuffer.wrap(data, len, data.length - len));
            if (l > 0) {
                len += l;
            }
        } while (l >= 0);
    }

    ByteBuffer toByteBuffer() {
        if (len != data.length) {
            data = Arrays.copyOf(data, len);
        }
        return ByteBuffer.wrap(data);
    }

    void writeTo(OutputStream out) throws IOException {
        out.write(data, 0, len);
    }


}
