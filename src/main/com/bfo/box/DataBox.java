package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A general Box that stores its content as a byte array
 * Subclasses of DataBox can always be written with {@link Box#write} or {@link Box#getEncoded}
 * There is no "setData()" method - a subclass of the approriate type should be created.
 * @since 5
 */
public class DataBox extends Box {

    private byte[] data;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public DataBox() {
    }

    /**
     * Create a new DataBox
     * @param label the label, which must not be null
     */
    public DataBox(String label) {
        super(label);
    }

    @Override protected void read(InputStream in) throws IOException {
        data = readFully(in);
    }

    @Override protected void write(OutputStream out) throws IOException {
        out.write(data());
    }

    /**
     * Return a copy of the byte data from this box.
     * Subclasses should provided more semantically-appropriate methods to retrieve data.
     * @return data the data
     */
    public byte[] data() {
        return (byte[])data.clone();
    }

}
