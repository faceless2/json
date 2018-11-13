package com.bfo.json;

import java.io.*;

class IBoolean extends Core {

    private final boolean value;
    private final byte flags;

    IBoolean(boolean value, byte flags) {
        this.value = value;
        this.flags = flags;
    }

    @Override Object value() {
        return value ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override String type() {
        return "boolean";
    }

    @Override boolean booleanValue() {
        return value;
    }

    @Override Number numberValue() {
        return Integer.valueOf(intValue());
    }

    @Override int intValue() {
        if ((flags & JsonReadOptions.FLAG_STRICT) != 0) {
            throw new ClassCastException("Cannot convert boolean "+value+" to number in strict mode");
        }
        return value ? 1 : 0;
    }

    @Override float floatValue() {
        return intValue();
    }

    @Override double doubleValue() {
        return intValue();
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append(value ? "true" : "false");
    }

}
