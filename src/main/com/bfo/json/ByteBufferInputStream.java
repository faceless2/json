package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/**
 * Extends CountingInputStream but overrides everything.
 * Just so we can get a single interface for an InputStream with a tell() method
 * @hidden
 */
public class ByteBufferInputStream extends CountingInputStream {

    private final ByteBuffer in;

    public ByteBufferInputStream(ByteBuffer in) {
        super(null);
        this.in = in;
    }

    @Override public void rewind(byte[] buf) {
        int pos = ((Buffer)in).position();
        if (pos - buf.length < 0) {
            throw new IllegalStateException("Too far");
        }
        for (int i=0;i<buf.length;i++) {
            if (buf[i] != in.get(pos - buf.length + i)) {
                throw new IllegalStateException("Already rewound: " + this+" " +i);
            }
        }
        ((Buffer)in).position(pos - buf.length);
    }

    @Override public int read() {
        return available() > 0 ? in.get() & 0xFF : -1;
    }

    @Override public int read(byte[] buf, int off, int len) {
        int r = available();
        if (r == 0) {
            return -1;
        } else if (r < len) {
            len = r;
        }
        in.get(buf, off, len);
        return len;
    }

    @Override public int available() {
        long limit = limit();
        return limit < 0 ? in.remaining() : Math.min(in.remaining(), (int)(limit - tell()));
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
        int r = Math.min(available(), (int)skip);
        ((Buffer)in).position(((Buffer)in).position() + r);
        return r;
    }

    @Override public long tell() {
        return ((Buffer)in).position();
    }

}
