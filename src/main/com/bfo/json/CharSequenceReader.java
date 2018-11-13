package com.bfo.json;

import java.io.*;
import java.nio.CharBuffer;

/**
 * Like StringReader but works on any CharSequence and has no locking
 */
class CharSequenceReader extends Reader {

    private static final int CONTEXT = 8;
    private final CharSequence buf;
    private final int length;
    private int off, mark;

    CharSequenceReader(CharSequence s) {
        this.buf = s;
        this.length = buf.length();
    }

    public int read() {
        return off == length ? -1 : buf.charAt(off++);
    }

    public void mark(int limit) {
        this.mark = off;
    }

    public int tell() {
        return off;
    }

    public int length() {
        return length;
    }

    public boolean markSupported() {
        return true;
    }

    public int read(char[] obuf, int ooff, int len) {
        if (off == length) {
            return -1;
        }
        if (off + len > length) {
            len = length - off;
        }
        if (buf instanceof String) {
            ((String)buf).getChars(off, off + len, obuf, ooff);
            off += len;
        } else if (buf instanceof StringBuffer) {
            ((StringBuffer)buf).getChars(off, off + len, obuf, ooff);
            off += len;
        } else if (buf instanceof StringBuilder) {
            ((StringBuilder)buf).getChars(off, off + len, obuf, ooff);
            off += len;
        } else if (buf instanceof CharBuffer) {
            ((CharBuffer)buf).position(off);
            ((CharBuffer)buf).get(obuf, ooff, len);
            off += len;
        } else {
            while (len-- > 0) {
                obuf[ooff++] = buf.charAt(off++);
            }
        }
        return len;
    }

    public int read(char[] obuf) {
        return read(obuf, 0, obuf.length);
    }

    public int read(CharBuffer cb) {
        if (off == length) {
            return -1;
        }
        int len = cb.remaining();
        if (off + len > length) {
            len = length - off;
        }
        cb.append(buf, off, len);
        return len;
    }

    public boolean ready() {
        return true;
    }

    public void reset() {
        off = mark;
    }

    public long skip(long len) {
        if (len > length - off) {
            len = length - off;
        }
        off += len;
        return len;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        if (off > CONTEXT) {
            sb.append("...");
        }
        for (int i=Math.max(0, off - CONTEXT);i<Math.min(length, off + CONTEXT);i++) {
            sb.append(buf.charAt(i));
        }
        if (off + CONTEXT < length) {
            sb.append("...");
        }
        sb.append("\"");
        return sb.toString();
    }

    public void close() {
    }

    /*
    public CharSequence substring(int start, int end) {
        return buf.subSequence(start, end);
    }
    */

}
