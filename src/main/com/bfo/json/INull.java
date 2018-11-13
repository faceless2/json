package com.bfo.json;

import java.io.*;

class INull extends Core {

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

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append("null");
    }

}
