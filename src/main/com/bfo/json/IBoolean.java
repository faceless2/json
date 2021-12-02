package com.bfo.json;

import java.nio.ByteBuffer;
import java.io.*;

class IBoolean extends Core {

    private final boolean value;
    private final byte flags;

    IBoolean(boolean value, JsonReadOptions options) {
        this.value = value;
        this.flags = options == null ? 0 : options.storeOptions();
    }

    @Override Object value() {
        return value ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override String type() {
        return "boolean";
    }

    @Override boolean booleanValue(Json json) {
        return value;
    }

    @Override Number numberValue(Json json) {
        return Integer.valueOf(intValue(json));
    }

    @Override int intValue(Json json) {
        if ((flags & JsonReadOptions.FLAG_STRICT) != 0) {
            throw new ClassCastException("Cannot convert boolean "+value+" to number in strict mode");
        }
        return value ? 1 : 0;
    }

    @Override float floatValue(Json json) {
        return intValue(json);
    }

    @Override double doubleValue(Json json) {
        return intValue(json);
    }

    @Override void write(Json json, Appendable sb, SerializerState state) throws IOException {
        sb.append(value ? "true" : "false");
    }

}
