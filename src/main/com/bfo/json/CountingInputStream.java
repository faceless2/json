package com.bfo.json;

import java.io.*;

class CountingInputStream extends BufferedInputStream {

    protected long pos;
    private long mark;

    public CountingInputStream(InputStream out) {
	super(out);
    }

    public int read() throws IOException {
        int v = super.read();
        if (v >= 0) {
            pos++;
        }
        return v;
    }

    public long skip(long n) throws IOException {
        long v = super.skip(n);
        if (v >= 0) {
            pos += v;
        }
        return v;
    }

    public int read(byte[] buf, int off, int len) throws IOException {
	int num = super.read(buf, off, len);
        if (num > 0) {
            pos += num;
        }
        return num;
    }

    public long tell() {
	return pos;
    }

    public void mark(int readlimit) {
        super.mark(readlimit);
        this.mark = pos;
    }

    public void reset() throws IOException {
        super.reset();
        this.pos = mark;
    }

    public String toString() {
        return "{counting@"+pos+" on "+in+"}";
    }

}
