package com.bfo.json;

import java.io.*;
import java.nio.*;

class CharSourceString implements CharSource {

    private final String s;
    private int pos;
    private int mark_pos;
    private final boolean trackpos;
    private long nlpos, linenum, mark_nlpos, mark_linenum, initialpos;

    CharSourceString(AbstractReader owner, String s) {
        this.s = s;
        this.trackpos = owner.isLineCounting();
        mark_pos = -1;
    }

    @Override public void initializePositions(long byteNumber, long charNumber, long lineNumber, long columnNumber) {
        this.initialpos += charNumber;
        this.linenum += lineNumber;
        this.nlpos += charNumber - columnNumber;
    }

    @Override public int available() {
        return s.length() - pos;
    }

    @Override public int get() {
        if (pos == s.length()) {
            return -1;
        } else {
            char c = s.charAt(pos);
            if (trackpos && c == '\n') {
                linenum++;
                nlpos = pos;
            }
            pos++;
            return c;
        }
    }

    @Override public void mark(int num) {
        mark_pos = pos;
        if (trackpos) {
            mark_nlpos = nlpos;
            mark_linenum = linenum;
        }
    }

    @Override public void reset() throws IOException {
        if (mark_pos < 0) {
            throw new IOException("Expired mark");
        }
        pos = mark_pos;
        if (trackpos) {
            nlpos = mark_nlpos;
            linenum = mark_linenum;
        }
    }

    @Override public CharSequence get(int len) throws IOException {
        String ss = s.substring(pos, pos + len);
        if (trackpos) {
            for (int i=0;i<ss.length();i++) {
                if (ss.charAt(i) == '\n') {
                    linenum++;
                    nlpos = pos;
                }
            }
        }
        pos += len;
        return ss;
    }

    @Override public long getLineNumber() {
        return linenum;
    }

    @Override public long getColumnNumber() {
        return initialpos + pos - nlpos;
    }

    @Override public long getCharNumber() {
        return initialpos + pos;
    }

    @Override public long getByteNumber() {
        return -1;
    }
}
