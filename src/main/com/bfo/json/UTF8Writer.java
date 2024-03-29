package com.bfo.json;

import java.io.*;
import java.util.*;
import java.text.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * Because OutputStreamWriter is dog-slow.
 * Appendable is more convenient than Writer, so a slight misnomer
 * @hidden
 */
public class UTF8Writer extends Writer {

    private final OutputStream out;
    private final boolean nfc;

    public UTF8Writer(OutputStream out, boolean nfc) {
        this.out = out;
        this.nfc = nfc;
    }

    @Override public void close() throws IOException {
        out.close();
    }

    @Override public void flush() throws IOException {
        out.flush();
    }

    @Override public void write(int c) throws IOException {
        append((char)c);
    }

    @Override public void write(char[] buf, int off, int len) throws IOException {
        append(CharBuffer.wrap(buf, off, len));
    }

    /**
     * Write the length of the string about to be written.
     * Overriden by msgpack and definite-length cbor writers
     */
    void writeLength(int len) throws IOException {
    }

    @Override public Writer append(char c) throws IOException {
        return append(Character.toString(c), 0, 1);
    }

    @Override public Writer append(CharSequence s) throws IOException {
        return append(s, 0, s.length());
    }

    @Override public Writer append(CharSequence s, int off, int slen) throws IOException {
        if (nfc) {
            s = Normalizer.normalize(s.subSequence(off, slen).toString(), Normalizer.Form.NFC);
            off = 0;
            slen = s.length();
        }
        int len = 0;
        if (slen < 1024) {      // arbitrary limit will still catch most strings. Aiming for stack alloc so not too high
            byte[] buf = new byte[slen * 4];
            for (int i=0;i<slen;i++) {
                int c = s.charAt(off + i);
                if (c < 0x80) {
                    buf[len++] = (byte)c;
                } else if (c < 0x800) {
                    buf[len++] = (byte)((c >> 6) | 0300);
                    buf[len++] = (byte)((c & 077) | 0200);
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < slen) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(off + ++i)&0x3ff);
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
                if (c < 0x80) {
                    len++;
                } else if (c < 0x800) {
                    len += 2;
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < slen) {
                    i++;
                    len += 4;
                } else {
                    len += 3;
                }
            }
            writeLength(len);
            // Measurably faster than OutputStreamWriter, as we have to init it each time.
            for (int i=0;i<slen;i++) {
                int c = s.charAt(off + i);
                if (c < 0x80) {
                    out.write(c);
                } else if (c < 0x800) {
                    out.write((c >> 6) | 0300);
                    out.write((c & 077) | 0200);
                } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < slen) {
                    c = ((c-0xd7c0)<<10) | (s.charAt(off + ++i)&0x3ff);
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
