package com.bfo.json;

import java.io.*;
import java.nio.*;

interface ByteSource {

    void initializePosition(long byteNumber);

    /**
     * Return the number of bytes that can be read without blocking, which
     * may be zero (and must be zero at EOF). Must not initiate a read operation
     */
    int available() throws IOException;

    /**
     * Return the next bytes, if available, or -1 if not available.
     */
    int get() throws IOException;

    /**
     * Mark a position
     */
    void mark(int num) throws IOException;

    /**
     * Return to the marked posiition, or throw an IOException if not possible
     */
    void reset() throws IOException;

    /**
     * Return a ByteBuffer containing the characters starting at the offset,
     * from the current location, which may be negative.
     * If no bytes can be read due to EOF, return null
     */
    ByteBuffer get(int len) throws IOException;

    /**
     * Return the number of bytes read if known, or -1 if not
     */
    long getByteNumber();

}
