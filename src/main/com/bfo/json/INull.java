package com.bfo.json;

import java.io.*;
import java.nio.ByteBuffer;

class INull extends Core {

    static final INull INSTANCE = new INull();

    private INull() {
    }

    @Override Object value() {
        return null;
    }

    @Override String type() {
        return "null";
    }

    @Override String stringValue() {
        return null;
    }

    @Override Number numberValue() {
        return null;
    }

    @Override boolean booleanValue() {
        throw new ClassCastException("Value is null");
    }

    @Override int intValue() {
        throw new ClassCastException("Value is null");
    }

    @Override long longValue() {
        throw new ClassCastException("Value is null");
    }

    @Override float floatValue() {
        throw new ClassCastException("Value is null");
    }

    @Override double doubleValue() {
        throw new ClassCastException("Value is null");
    }

    @Override ByteBuffer bufferValue() {
        throw null;
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append("null");
    }

}
