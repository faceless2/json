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

    @Override String stringValue(Json json) {
        return null;
    }

    @Override Number numberValue(Json json) {
        return null;
    }

    @Override boolean booleanValue(Json json) {
        throw new ClassCastException("Value is null");
    }

    @Override int intValue(Json json) {
        throw new ClassCastException("Value is null");
    }

    @Override long longValue(Json json) {
        throw new ClassCastException("Value is null");
    }

    @Override float floatValue(Json json) {
        throw new ClassCastException("Value is null");
    }

    @Override double doubleValue(Json json) {
        throw new ClassCastException("Value is null");
    }

    @Override ByteBuffer bufferValue(Json json) {
        throw null;
    }

    @Override void write(Json json, Appendable sb, SerializerState state) throws IOException {
        sb.append("null");
    }

}
