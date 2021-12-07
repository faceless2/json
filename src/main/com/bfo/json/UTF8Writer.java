package com.bfo.json;

import java.io.*;
import java.util.*;
import java.text.*;
import java.nio.ByteBuffer;

/**
 * Because OutputStreamWriter is dog-slow.
 * Appendable is more convenient than Writer, so a slight misnomer
 */
class UTF8Writer implements Appendable {

    private final OutputStream out;
    private final boolean nfc;

    UTF8Writer(OutputStream out, boolean nfc) {
        this.out = out;
        this.nfc = nfc;
    }

    /**
     * Write the length of the string about to be written.
     * Overriden by msgpack and definite-length cbor writers
     */
    void writeLength(int len) throws IOException {
    }

    public Appendable append(char c) throws IOException {
        return append(Character.toString(c), 0, 1);
    }

    public Appendable append(CharSequence s) throws IOException {
        return append(s, 0, s.length());
    }

    public Appendable append(CharSequence s, int off, int slen) throws IOException {
        if (nfc) {
            s = Normalizer.normalize(s.subSequence(off, slen).toString(), Normalizer.Form.NFC);
            off = 0;
            slen = s.length();
        }
        int len = 0;
        if (slen < 1024) {      // arbitrary limit will still catch most strings. Aiming for stack alloc so not too high
            byte[] buf = new byte[slen * 4];
            for (int i=0;i<slen;i++) {
                int c = s.charAt(i);
                if (c < 0x80) {
                    buf[len++] = (byte)c;
                } else if (c < 0x800) {
                    buf[len++] = (byte)((c >> 6) | 0300);
                    buf[len++] = (byte)((c & 077) | 0200);
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length()) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(++i)&0x3ff);
                    buf[len++] = (byte)((c >> 18) | 0360);
                    buf[len++] = (byte)(((c >> 12) & 077) | 0200);
                    buf[len++] = (byte)(((c >> 6) & 077) | 0200);
                    buf[len++] = (byte)((c & 077) | 0200);
                } else {
                    buf[len++] = (byte)((c >> 12) | 0340);
                    buf[len++] = (byte)(((c >> 6) & 077) | 0200);
                    buf[len++] = (byte)((c & 077) | 0200);
                }
            }
            writeLength(len);
            out.write(buf, 0, len);
        } else {
            for (int i=0;i<slen;i++) {
                char c = s.charAt(off + i);
                if (c < 0x7f) {
                    len++;
                } else if (c < 0x800) {
                    len += 2;
                } else if (c >= 0xd800 && c <= 0xdbff) {
                    i++;
                    len += 4;
                } else {
                    len += 3;
                }
            }
            writeLength(len);
            // Measurably faster than OutputStreamWriter, as we have to init it each time.
            for (int i=0;i<s.length();i++) {
                int c = s.charAt(i);
                if (c < 0x80) {
                    out.write(c);
                } else if (c < 0x800) {
                    out.write((c >> 6) | 0300);
                    out.write((c & 077) | 0200);
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length()) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(++i)&0x3ff);
                    out.write((c >> 18) | 0360);
                    out.write(((c >> 12) & 077) | 0200);
                    out.write(((c >> 6) & 077) | 0200);
                    out.write((c & 077) | 0200);
                } else {
                    out.write((c >> 12) | 0340);
                    out.write(((c >> 6) & 077) | 0200);
                    out.write((c & 077) | 0200);
                }
            }
        }
        return this;
    }

}
