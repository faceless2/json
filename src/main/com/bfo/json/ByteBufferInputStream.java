package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/**
 * Extends CountingInputStream but overrides everything.
 * Just so we can get a single interface for an InputStream with a tell() method
 */
class ByteBufferInputStream extends CountingInputStream {

    ByteBuffer in;

    ByteBufferInputStream(ByteBuffer in) {
        super(null);
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

    @Override public long tell() {
        return in.position();
    }

}
