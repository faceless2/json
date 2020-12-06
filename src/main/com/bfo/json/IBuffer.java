package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.text.*;
import java.util.*;

class IBuffer extends Core {

    private final ByteBuffer value;

    IBuffer(ByteBuffer value) {
        this.value = value;
    }

    @Override Object value() {
        return value;
    }

    @Override String type() {
        return "buffer";
    }

    @Override String stringValue() {
        StringBuilder sb = new StringBuilder();
        try {
            write(sb);
        } catch (IOException e) {}      // Can't happen
        return sb.toString();
    }

    @Override ByteBuffer bufferValue() {
        return value;
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append('"');
        write(sb);
        sb.append('"');
    }

    // Do this all locally for speed
    static final char[] BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private void write(Appendable out) throws IOException {
        value.position(0);
        int len = value.remaining();
        for (int i=0;i<len - 2;i+=3) {
            int v = ((value.get()&0xFF)<<16) | ((value.get()&0xFF)<<8) | ((value.get()&0xFF)<<0);
            out.append(BASE64[(v>>18) & 0x3F]);
            out.append(BASE64[(v>>12) & 0x3F]);
            out.append(BASE64[(v>>6)  & 0x3F]);
            out.append(BASE64[v       & 0x3F]);
        }
        if (value.remaining() == 2) {
            int v = ((value.get()&0xFF)<<16) | ((value.get()&0xFF)<<8);
            out.append(BASE64[(v>>18) & 0x3F]);
            out.append(BASE64[(v>>12) & 0x3F]);
            out.append(BASE64[(v>>6)  & 0x3F]);
        } else if (value.remaining() == 1) {
            int v = ((value.get()&0xFF)<<16);
            out.append(BASE64[(v>>18) & 0x3F]);
            out.append(BASE64[(v>>12) & 0x3F]);
        }
    }

}
