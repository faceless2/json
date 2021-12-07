package com.bfo.json;

import java.io.*;
import java.nio.*;

/**
 * A Reader with a nice toString()
 */
class ContextReader extends Reader {

    private final Reader parent;
    private int line, col, markline, markcol;
    private int ctxoff, markctxoff;
    private boolean ctxwrap, markctxwrap;
    private char[] ctx = new char[32];
    private char[] markctx = new char[ctx.length];
    private String name;

    public ContextReader(Reader parent) {
        this.parent = parent;
        this.line = 1;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContextLength(int contextLength) {
        ctx = new char[contextLength];
        markctx = new char[contextLength];
        ctxoff = markctxoff = 0;
        ctxwrap = markctxwrap = false;
    }

    @Override public void close() throws IOException {
        parent.close();
    }

    @Override public void mark(int limit) throws IOException {
        parent.mark(limit);
        markline = line;
        markcol = col;
        markctxwrap = ctxwrap;
        markctxoff = ctxoff;
        System.arraycopy(ctx, 0, markctx, 0, ctx.length);
    }

    @Override public boolean markSupported() {
        return parent.markSupported();
    }

    @Override public int read() throws IOException {
        int c = parent.read();
        if (c < 0) {
            return c;
        }
        ctx[ctxoff++] = (char)c;
        if (ctxoff == ctx.length) {
            ctxoff = 0;
            ctxwrap = true;
        }
        if (c == 10) {
            line++;
            col = 0;
        } else {
            col++;
        }
        return c;
    }

    @Override public int read(char[] buf, int off, int len) throws IOException {
        for (int i=0;i<len;i++) {
            int c = read();
            if (c < 0) {
                return i == 0 ? -1 : i;
            }
            buf[off++] = (char)c;
        }
        return len;
    }

    @Override public boolean ready() throws IOException {
        return parent.ready();
    }

    @Override public void reset() throws IOException {
        parent.reset();
        line = markline;
        col = markcol;
        char[] t = ctx;
        ctx = markctx;
        markctx = t;
        ctxwrap = markctxwrap;
        ctxoff = markctxoff;
    }

    @Override public long skip(long n) throws IOException {
        for (int i=0;i<n;i++) {
            if (read() < 0) {
                return i;
            }
        }
        return n;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        int s, l;
        if (ctxwrap) {
            sb.append("...");
            s = ctxoff;
            l = ctx.length;
        } else {
            s = 0;
            l = ctxoff;
        }
        for (int i=0;i<l;i++) {
            char c = ctx[(s + i) % ctx.length];
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
        sb.append("...");
        sb.append("\" (line ");
        sb.append(line);
        sb.append(" col ");
        sb.append(col);
        sb.append(')');
        return sb.toString();
    }


}
