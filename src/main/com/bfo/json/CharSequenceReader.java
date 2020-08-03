package com.bfo.json;

import java.io.*;
import java.nio.CharBuffer;

/**
 * Like StringReader but works on any CharSequence and has no locking
 */
class CharSequenceReader extends Reader {

    private static final int CONTEXT = 32;
    private static final int CONTEXTAFTER = 0;
    private final CharSequence buf;
    private final int length;
    private int off, mark;
    private int line, col, markline, markcol;

    CharSequenceReader(CharSequence s) {
        this.buf = s;
        this.length = buf.length();
        this.line = 1;
    }

    public int read() {
        if (off == length) {
            return -1;
        } else {
            char c = buf.charAt(off++);
            if (c == 10) {
                line++;
                col = 0;
            } else {
                col++;
            }
            return c;
        }
    }

    public void mark(int limit) {
        this.mark = off;
        this.markline = line;
        this.markcol = col;
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
            int j = ooff;
            while (len-- > 0) {
                obuf[j++] = buf.charAt(off++);
            }
        }
        for (int i=0;i<len;i++) {
            char c = obuf[ooff + i];
            if (c == 10) {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return len;
    }

    public boolean ready() {
        return true;
    }

    public void reset() {
        off = mark;
        line = markline;
        col = markcol;
    }

    public long skip(long len) {
        if (len > length - off) {
            len = length - off;
        }
        for (int i=0;i<len;i++) {
            char c = buf.charAt(off++);
            if (c == 10) {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return len;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        if (off > CONTEXT) {
            sb.append("...");
        }
        for (int i=Math.max(0, off - CONTEXT + CONTEXTAFTER);i<Math.min(length, off + CONTEXTAFTER);i++) {
            char c = buf.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == 10) {
                sb.append("\\n");
            } else if (c == 13) {
                sb.append("\\r");
            } else if (c == 9) {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        if (off + CONTEXT < length) {
            sb.append("...");
        }
        sb.append("\" (line ");
        sb.append(line);
        sb.append(" col ");
        sb.append(col);
        sb.append(')');
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
