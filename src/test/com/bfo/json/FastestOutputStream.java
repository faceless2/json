package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

class FastByteArrayOutputStream extends OutputStream {

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private byte[] buf;
    private int count;

    public FastByteArrayOutputStream() {
        this(32);
    }

    public FastByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        buf = new byte[size];
    }

    public void close() {
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - buf.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        int oldCapacity = buf.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            newCapacity = hugeCapacity(minCapacity);
        }
        buf = Arrays.copyOf(buf, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) {
            throw new OutOfMemoryError();
        }
        return (minCapacity > MAX_ARRAY_SIZE) ?  Integer.MAX_VALUE : MAX_ARRAY_SIZE;
    }

    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count++] = (byte) b;
    }

    public void write(byte b[], int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void write(byte b[]) {
        ensureCapacity(count + b.length);
        System.arraycopy(b, 0, buf, count, b.length);
        count += b.length;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    public void reset() {
        count = 0;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    public int size() {
        return count;
    }

    public String toString() {
        return new String(buf, 0, count);
    }

    public String toString(String charsetName) throws UnsupportedEncodingException {
        return new String(buf, 0, count, charsetName);
    }

    public String toString(Charset charset) {
        return new String(buf, 0, count, charset);
    }

    //--------------------
    // Above methods are all 100% standard
    //--------------------

    public InputStream toInputStream() {
        close();
        return new ByteArrayInputStream(buf, 0, count);
    }

    public ByteBuffer getAsByteBuffer() {
        return ByteBuffer.wrap(buf, 0, count);
    }

    public int get(int off) {
        if (off < 0 || off >= count) {
            throw new IllegalArgumentException();
        }
        return buf[off] & 0xFF;
    }

}
