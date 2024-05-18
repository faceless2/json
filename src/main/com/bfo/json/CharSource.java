package com.bfo.json;

import java.io.*;
import java.nio.*;

interface CharSource {

    /**
     * Initialize the positions. Called when a new CharSource is created
     * that is appending to an earlier one
     */
    void initializePositions(long byteNumber, long charNumber, long lineNumber, long columnNumber);

    /**
     * Return the nuber of characters that can be read without blocking, which
     * may be zero (and must be zero at EOF). Must not initiate a read operation.
     */
    int available() throws IOException;

    /**
     * Return the next character, if available, or -1 if not available.
     * This method updates the newline count.
     */
    int get() throws IOException;

    /**
     * Record the current position. Can read up to "size" chars before a reset,
     * after that we MAY discard the mark. If size > available, we can ignore
     */
    void mark(int size) throws IOException;

    /**
     * Reset to the current position, resetting the newline count etc.
     * Throw an IOException if not possible.
     */
    void reset() throws IOException;

    /**
     * Return a CharSequence containing the specified number of characters
     * starting at the current position. Move the cursor after this position.
     * If less than the specified number is available, throw an Exception.
     * If no characters can be read due to EOF, return null
     */
    CharSequence get(int len) throws IOException;

    /**
     * Return the number of characters read
     */
    long getCharNumber();

    /**
     * Return the number of bytes read if known, or 0 if not known
     */
    long getByteNumber();

    /**
     * Return the line number
     */
    long getLineNumber();

    /**
     * Return the column number
     */
    long getColumnNumber();

}
