package com.bfo.json;

import java.io.*;
import java.nio.*;

class CharSourceReadable extends CharSourceCharBuffer {

    private static final int BUFSIZE = 8192;
    private final Readable in;

    CharSourceReadable(AbstractReader owner, Readable in) {
        super(owner, (CharBuffer)CharBuffer.allocate(BUFSIZE).position(BUFSIZE));
        this.in = in;
    }

    @Override boolean nextBuffer() throws IOException {
        super.nextBuffer();
        CharBuffer cbuf = getBuffer();
        int rem = cbuf.remaining();
        if (rem > 0) {
            char[] a = cbuf.array();
            System.arraycopy(a, cbuf.position(), a, 0, rem);
        }
        cbuf.limit(cbuf.capacity()).position(rem);
        int l = in.read(cbuf);
        cbuf.flip();
        setBuffer(cbuf, l);
        return cbuf.hasRemaining();
    }

}
