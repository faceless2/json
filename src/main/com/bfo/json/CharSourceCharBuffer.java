package com.bfo.json;

import java.io.*;
import java.nio.*;

class CharSourceCharBuffer implements CharSource {

    private CharBuffer cbuf;
    private final boolean trackpos;
    private int mark_pos;
    private int bufstart;
    private long tell;
    private long nlpos, mark_nlpos, linenum, mark_linenum;

    CharSourceCharBuffer(AbstractReader owner, CharBuffer cbuf) {
        setBuffer(cbuf, 1);
        this.trackpos = owner.isLineCounting();
    }

    @Override public void initializePositions(long byteNumber, long charNumber, long lineNumber, long columnNumber) {
        this.tell += charNumber;
        this.linenum += lineNumber;
        this.nlpos += charNumber - columnNumber;
    }

    CharBuffer getBuffer() {
        return cbuf;
    }

    void setBuffer(CharBuffer b, int len) {
        if (b == null) {
            mark_pos = -1;
        } else {
            if (cbuf != null) {
                tell += cbuf.position() - bufstart;
            }
            bufstart = b.position();
            cbuf = b;
            mark_pos -= len;
        }
    }

    boolean nextBuffer() throws IOException {
        return false;
    }

    @Override public int available() throws IOException {
        return cbuf.remaining();
    }

    @Override public int get() throws IOException {
        if (!cbuf.hasRemaining() && !nextBuffer()) {
            return -1;
        }
        char c = cbuf.get();
        if (trackpos && c == '\n') {
            linenum++;
            nlpos = getCharNumber() - 1;
        }
        return c;
    }

    @Override public void mark(int num) throws IOException {
        mark_pos = cbuf.position();
        if (trackpos) {
            mark_nlpos = nlpos;
            mark_linenum = linenum;
        }
    }

    @Override public void reset() throws IOException {
        if (mark_pos < 0) {
            throw new IOException("Expired mark");
        }
        cbuf.position(mark_pos);
        if (trackpos) {
            nlpos = mark_nlpos;
            linenum = mark_linenum;
        }
    }

    @Override public CharSequence get(int len) throws IOException {
        if (len == 0) {
            return null;
        }
        CharBuffer dup = (CharBuffer)cbuf.duplicate().limit(cbuf.position() + len);
        cbuf.position(cbuf.position() + len);
        if (trackpos) {
            int p = cbuf.position();
            for (int i=0;i<len;i++) {
                if (dup.get(i) == '\n') {
                    linenum++;
                    nlpos = getCharNumber() - 1;
                }
            }
        }
        return dup;
    }

    @Override public long getLineNumber() {
        return linenum;
    }

    @Override public long getColumnNumber() {
        return cbuf.position() - nlpos;
    }

    @Override public long getCharNumber() {
        return tell + cbuf.position() - bufstart;
    }

    @Override public long getByteNumber() {
        return -1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(getClass().getName().substring(getClass().getName().lastIndexOf(".") + 1));
        if (cbuf == null) {
            sb.append(" cbuf=null}");
        } else {
            sb.append(" next=");
            if (cbuf.hasRemaining()) {
                Json.esc(Character.toString(cbuf.get(cbuf.position())), null);
            } else {
                sb.append("eof");
            }
            sb.append(" cbuf=");
            sb.append(bufstart);
            sb.append("/");
            sb.append(cbuf.position());
            sb.append("/");
            sb.append(cbuf.limit());
            if (mark_pos >= 0) {
                sb.append(" mark=");
                sb.append(mark_pos);
            }
            sb.append(" buf=");
            CharBuffer cb = cbuf;
            if (cb.remaining() > 20) {
                cb = (CharBuffer)cb.duplicate().limit(cb.position() + 20);
            }
            Json.esc(cb, sb);
            if (cb != cbuf) {
                sb.append("...");
            }
            sb.append('}');
        }
        return sb.toString();
    }

}
