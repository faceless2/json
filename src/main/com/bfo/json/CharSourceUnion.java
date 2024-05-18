package com.bfo.json;

import java.io.*;
import java.nio.*;

class CharSourceUnion implements CharSource {

    private final AbstractReader owner;
    private final CharSource a, b;
    private long byteNumber, charNumber, lineNumber, columnNumber;

    CharSourceUnion(AbstractReader owner, CharSource a, CharSource b) {
        this.owner = owner;
        this.a = a;
        this.b = b;
    }

    @Override public void initializePositions(long byteNumber, long charNumber, long lineNumber, long columnNumber) {
        this.byteNumber = byteNumber;
        this.charNumber = charNumber;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    @Override public int available() throws IOException {
        return a.available();
    }

    @Override public int get() throws IOException {
        int v = a.get();
        if (v >= 0) {
            return v;
        }
        b.initializePositions(byteNumber + a.getByteNumber(), charNumber + a.getCharNumber(), lineNumber + a.getLineNumber(), columnNumber + a.getColumnNumber());
        owner.setSource(b);
        return b.get();
    }

    @Override public void mark(int size) throws IOException {
        a.mark(size);
    }

    @Override public void reset() throws IOException {
        a.reset();
    }

    @Override public CharSequence get(int len) throws IOException {
        return a.get(len);
    }

    @Override public long getCharNumber() {
        return a.getCharNumber();
    }

    @Override public long getByteNumber() {
        return a.getByteNumber();
    }

    @Override public long getLineNumber() {
        return a.getLineNumber();
    }

    @Override public long getColumnNumber() {
        return a.getColumnNumber();
    }

}
