package com.bfo.json;

import javax.json.*;

class JSRJsonString implements JsonString {
    private final String value;

    JSRJsonString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("String is null");
        }
        this.value = value;
    }
    public boolean equals(Object o) {
        return o instanceof JsonString && ((JsonString)o).getString().equals(value);
    }
    public int hashCode() {
        return value.hashCode();
    }
    public CharSequence getChars() {
        return value;
    }
    public String getString() {
        return value;
    }
    public JsonValue.ValueType getValueType() {
        return JsonValue.ValueType.STRING;
    }
    public String toString() {
        return Json.esc(value, null).toString();
    }
}
