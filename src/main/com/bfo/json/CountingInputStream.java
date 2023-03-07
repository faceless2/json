package com.bfo.json;

import java.io.*;

/**
 * A stream that counts. Note this MUST NOT add buffering, it breaks
 * reading sibling boxes in BoxFactory
 * @hidden
 */
public class CountingInputStream extends FilterInputStream {

    protected long pos;
    private long mark;
    private long limit;
    private byte[] rewind;
    private int rewindpos;

    public CountingInputStream(InputStream out) {
	super(out);
        limit = -1;
    }

    /**
     * Rewind the stream by inserting the specified buffer at the start of it.
     * If the stream is already partially rewound, throw an exception
     */
    public void rewind(byte[] buf) {
        if (rewind != null) {
            throw new IllegalStateException("Already rewound");
        }
        rewind = buf;
        rewindpos = 0;
        pos -= buf.length;
    }

    @Override public int read() throws IOException {
        if (limit >= 0 && pos >= limit) {
            return -1;
        }
        int v;
        if (rewind != null) {
            v = rewind[rewindpos] & 0xFF;
            rewindpos++;
            pos++;
            if (rewindpos == rewind.length) {
                rewind = null;
            }
        } else {
            v = super.read();
            if (v >= 0) {
                pos++;
            }
        }
        return v;
    }

    @Override public long skip(long n) throws IOException {
        if (n < 0) {
            n = 0;
        } else if (limit >= 0) {
            n = Math.min(n, Math.max(0, limit - pos));
        }
        if (rewind != null) {
            n = Math.min(rewind.length - rewindpos, n);
            rewindpos += n;
            pos += n;
            if (rewindpos == rewind.length) {
                rewind = null;
            }
            return n;
        } else {
            long v = super.skip(n);
            if (v >= 0) {
                pos += v;
            }
            return v;
        }
    }

    @Override public int available() throws IOException {
        int v = super.available();
        if (rewind != null) {
            v += rewind.length - rewindpos;
        }
        if (limit >= 0) {
            v = Math.min(v, (int)Math.max(0, limit - pos));
        }
        return v;
    }

    @Override public int read(byte[] buf, int off, int len) throws IOException {
        if (limit >= 0) {
            if (pos >= limit) {
                return -1;
            }
            len = Math.min(len, (int)(limit - pos));
        }
        if (rewind != null) {
            len = Math.min(rewind.length - rewindpos, len);
            System.arraycopy(rewind, rewindpos, buf, off, len);
            pos += len;
            rewindpos += len;
            if (rewindpos == rewind.length) {
                rewind = null;
            }
            return len;
        } else {
            int num = super.read(buf, off, len);
            if (num > 0) {
                pos += num;
            }
            return num;
        }
    }

    public long tell() {
	return pos;
    }

    public void limit(long limit) {
	this.limit = limit;
    }

    public long limit() {
        return this.limit;
    }

    @Override public void mark(int readlimit) {
        super.mark(readlimit);
        this.mark = pos;
    }

    @Override public void reset() throws IOException {
        super.reset();
        this.pos = mark;
    }

    @Override public String toString() {
        if (rewind != null) {
            return "{counting@" + pos + (limit >= 0 ? " to " + limit : "") + " on [" + rewindpos + "/" + rewind.length + "] and " + in + "}";
        } else {
            return "{counting@" + pos + (limit >= 0 ? " to " + limit : "") + " on " + in + "}";
        }
    }

}
