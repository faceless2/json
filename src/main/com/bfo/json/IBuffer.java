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
        StringBuilder sb = new StringBuilder(value.remaining() * 4 / 3 + 2);
        try {
            write(sb);
        } catch (IOException e) {}      // Can't happen
        return sb.toString();
    }

    @Override ByteBuffer bufferValue() {
        return value;
    }

    @Override public boolean equals(Object o) {
        return o instanceof IBuffer && ((IBuffer)o).value.position(0).equals(value.position(0));
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append('"');
        if (state.filter != null && state.filter.isProxy(state.json)) {
            Base64OutputStream out = new Base64OutputStream(sb);
            state.filter.proxyWrite(state.json, out);
            out.complete();
        } else {
            int len = state.options == null ? 0 : state.options.getMaxStringLength();
            if (len == 0) {
                write(sb);
            } else {
                sb.append("(" + value.limit() + " bytes)");
            }
        }
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

    // Because Base64.Encoder makes me write to an OutputStream.
    private static final class Base64OutputStream extends OutputStream {
        final Appendable out;
        int v, count;
        Base64OutputStream(Appendable a) {
            this.out = a;
        }

        @Override public void write(int c) throws IOException {
            c &= 0xFF;
            switch (count) {
                case 0:
                    v = c;
                    count++;
                    break;
                case 1:
                    v = (v<<8) | c;
                    count++;
                    break;
                case 2:
                    v = (v<<8) | c;
                    out.append(BASE64[(v>>18) & 0x3F]);
                    out.append(BASE64[(v>>12) & 0x3F]);
                    out.append(BASE64[(v>>6)  & 0x3F]);
                    out.append(BASE64[v       & 0x3F]);
                    count = 0;
            }
        }

        @Override public void write(byte[] buf, int off, int len) throws IOException {
            len += off;
            while (off < len) {
                write(buf[off++]);
            }
        }

        void complete() throws IOException {
            if (count == 1) {
                // 12345678 -> 00123456 00780000
                // 12345678 9ABCDEFG -> 00123456 00789ABC 00DEFG00
                out.append(BASE64[(v>>2) & 0x3F]);
                out.append(BASE64[(v<<4) & 0x3F]);
            } else if (count == 2) {
                out.append(BASE64[(v>>10) & 0x3F]);
                out.append(BASE64[(v>>4)  & 0x3F]);
                out.append(BASE64[(v<<2)  & 0x3F]);
            }
        }
    }

}
