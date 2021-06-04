package com.bfo.json;

import java.io.*;
import java.nio.*;

class ByteBufferInputStream extends InputStream {

    private final ByteBuffer in;

    ByteBufferInputStream(ByteBuffer in) {
        this.in = in;
    }

    @Override public int read() {
        return in.hasRemaining() ? in.get() & 0xFF : -1;
    }

    @Override public int read(byte[] buf, int off, int len) {
        int r = in.remaining();
        if (r == 0) {
            return -1;
        } else if (r < len) {
            len = r;
        }
        in.get(buf, off, len);
        return len;
    }

    @Override public int available() {
        return in.remaining();
    }

    @Override public boolean markSupported() {
        return true;
    }

    @Override public void mark(int limit) {
        in.mark();
    }

    @Override public void reset() {
        in.reset();
    }

    @Override public long skip(long skip) {
        int r = Math.min(in.remaining(), (int)skip);
        in.position(in.position() + r);
        return r;
    }

}
