package com.bfo.json;

import java.io.*;

/**
 * Much faster version of OutputStreamWriter(out, "UTF-8"),
 * and one which doesn't buffer. No buffering is critical
 * if we're writing Json to a mixed stream with other content.
 */
class UTF8OutputStreamWriter extends Writer {

    protected final OutputStream out;
    private int hold;

    public UTF8OutputStreamWriter(OutputStream out) {
        this.out = out;
    }

    public void write(int c) throws IOException {
        if (c < 0x80) {
            out.write(c);
        } else if (c < 0x800) {
            out.write((c >> 6) | 0xC0);
            out.write((c & 0x3F) | 0x80);
        } else if (c < 0xd800 || c > 0xdfff) {
            out.write((c >> 12) | 0xE0);
            out.write(((c >> 6) & 0x3F) | 0x80);
            out.write((c & 0x3F) | 0x80);
        } else if (c < 0xdc00) {
            hold = c;
        } else {
            c = ((hold - 0xd800)<<10) | (c - 0xdc00) + 0x10000;
            if (c < 0x200000) {
                out.write((c >> 18) | 0xF0);
                out.write(((c >> 12) & 0x3F) | 0x80);
                out.write(((c >> 6) & 0x3F) | 0x80);
                out.write((c & 0x3F) | 0x80);
            } else if (c < 0x4000000) {
                out.write((c >> 24) | 0xF8);
                out.write(((c >> 18) & 0x3F) | 0x80);
                out.write(((c >> 12) & 0x3F) | 0x80);
                out.write(((c >> 6) & 0x3F) | 0x80);
                out.write((c & 0x3F) | 0x80);
            } else {
                out.write((c >> 30) | 0xFC);
                out.write(((c >> 24) & 0x3F) | 0x80);
                out.write(((c >> 18) & 0x3F) | 0x80);
                out.write(((c >> 12) & 0x3F) | 0x80);
                out.write(((c >> 6) & 0x3F) | 0x80);
                out.write((c & 0x3F) | 0x80);
            }
        }
    }

    public void write(char[] buf, int off, int len) throws IOException {
        len += off;
        for (int i=off;i<len;i++) {
            write(buf[i]);
        }
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }

}
