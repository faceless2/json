package com.bfo.json;

import java.nio.*;
import java.util.*;
import java.io.*;

abstract class Core {

    abstract Object value();

    abstract String type();

    ByteBuffer bufferValue(Json json) {
        throw new ClassCastException("Value is a "+type());
    }

    String stringValue(Json json) {
        Object o = value();
        return o == null ? null : o.toString();
    }

    Number numberValue(Json json) {
        throw new ClassCastException("Value is a "+type());
    }

    int intValue(Json json) {
        return numberValue(json).intValue();
    }

    long longValue(Json json) {
        return numberValue(json).longValue();
    }

    float floatValue(Json json) {
        return numberValue(json).floatValue();
    }

    double doubleValue(Json json) {
        return numberValue(json).doubleValue();
    }

    boolean booleanValue(Json json) {
        return intValue(json) != 0;
    }

    Map<String,Json> mapValue(Json json) {
        throw new ClassCastException("Value is a "+type());
    }

    List<Json> listValue(Json json) {
        throw new ClassCastException("Value is a "+type());
    }

    public int hashCode() {
        Object o = value();
        return o == null ? 0 : o.hashCode();
    }

    public boolean equals(Object o) {
        if (o instanceof Core) {
            Core other = (Core)o;
            o = value();
            if (o == null) {
                return other.value() == null;
            } else {
                return o.equals(other.value());
            }
        }
        return false;
    }

    abstract void write(Json json, Appendable sb, SerializerState state) throws IOException;

}
