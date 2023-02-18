package com.bfo.json;

import java.io.*;

/**
 * To write to an appendable without going via an intermediate stream or buffer,
 * as java.util.Base64.getEncoder() requires
 */
class Base64OutputStream extends OutputStream {

    static final char[] BASE64_NORMAL  = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();      // Traditional
    static final char[] BASE64_RFC4648 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();      // Used by JWT

    private final Appendable out;
    private final char[] base64;
    private int v, count;

    Base64OutputStream(Appendable a, boolean base64standard) {
        this.out = a;
        this.base64 = base64standard ? BASE64_NORMAL : BASE64_RFC4648;
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
                out.append(base64[(v>>18) & 0x3F]);
                out.append(base64[(v>>12) & 0x3F]);
                out.append(base64[(v>>6)  & 0x3F]);
                out.append(base64[v       & 0x3F]);
                count = 0;
        }
    }

    @Override public void write(byte[] buf, int off, int len) throws IOException {
        len += off;
        while (off + 2 < len) {
            int v = ((buf[off++]&0xFF)<<16) | ((buf[off++]&0xFF)<<8) | (buf[off++]&0xFF);
            out.append(base64[(v>>18) & 0x3F]);
            out.append(base64[(v>>12) & 0x3F]);
            out.append(base64[(v>>6)  & 0x3F]);
            out.append(base64[v       & 0x3F]);
        }
        while (off < len) {
            write(buf[off++]);
        }
    }

    @Override public void close() throws IOException {
        if (count == 1) {
            out.append(base64[(v>>2) & 0x3F]);
            out.append(base64[(v<<4) & 0x3F]);
        } else if (count == 2) {
            out.append(base64[(v>>10) & 0x3F]);
            out.append(base64[(v>>4)  & 0x3F]);
            out.append(base64[(v<<2)  & 0x3F]);
        }
        count = 0;
    }

}
