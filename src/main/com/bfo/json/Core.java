package com.bfo.json;

import java.util.*;
import java.io.*;

abstract class Core {

    abstract Object value();

    abstract String type();

    String stringValue() {
        Object o = value();
        return o == null ? null : o.toString();
    }

    Number numberValue() {
        throw new ClassCastException("Value is a "+type());
    }

    int intValue() {
        return numberValue().intValue();
    }

    long longValue() {
        return numberValue().longValue();
    }

    float floatValue() {
        return numberValue().floatValue();
    }

    double doubleValue() {
        return numberValue().doubleValue();
    }

    boolean booleanValue() {
        return intValue() != 0;
    }

    Map<String,Json> mapValue() {
        throw new ClassCastException("Value is a "+type());
    }

    List<Json> listValue() {
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

    abstract void write(Appendable sb, SerializerState state) throws IOException;

}
