package com.bfo.json;

import java.util.*;
import jakarta.json.*;

class JakartaJsonObject extends AbstractMap<String,JsonValue> implements JsonObject {

    final Map<String,JsonValue> map;
    private final Map<String,JsonValue> umap;

    JakartaJsonObject(LinkedHashMap<String,JsonValue> map) {
        this.map = map;
        this.umap = Collections.<String,JsonValue>unmodifiableMap(map);
    }

    @Override public Set<Map.Entry<String,JsonValue>> entrySet() {
        return umap.entrySet();
    }

    @Override public boolean getBoolean(String name) {
        JsonValue v = get(name);
        if (v == JsonValue.TRUE) {
            return true;
        } else if (v == JsonValue.FALSE) {
            return false;
        } else if (v == null) {
            throw new NullPointerException("No key " + name);
        } else {
            throw new ClassCastException(v.getClass().getName());
        }
    }
    @Override public boolean getBoolean(String name, boolean defaultValue) {
        JsonValue v = get(name);
        if (v == JsonValue.TRUE) {
            return true;
        } else if (v == JsonValue.FALSE) {
            return false;
        } else {
            return defaultValue;
        }

    }
    @Override public int getInt(String name) {
        return ((JsonNumber)get(name)).intValue();
    }
    @Override public int getInt(String name, int defaultValue) {
        JsonValue v = get(name);
        return v instanceof JsonNumber ? ((JsonNumber)v).intValue() : defaultValue;
    }
    @Override public JsonObject getJsonObject(String name) {
        return (JsonObject)get(name);
    }
    @Override public JsonNumber getJsonNumber(String name) {
        return (JsonNumber)get(name);
    }
    @Override public JsonArray getJsonArray(String name) {
        return (JsonArray)get(name);
    }
    @Override public JsonString getJsonString(String name) {
        return (JsonString)get(name);
    }
    @Override public String getString(String name) {
        return ((JsonString)get(name)).getString();
    }
    @Override public String getString(String name, String defaultValue) {
        JsonValue v = get(name);
        return v instanceof JsonString ? ((JsonString)v).getString() : defaultValue;
    }
    @Override public boolean isNull(String name) {
        return get(name) == JsonValue.NULL;
    }
    @Override public JsonValue.ValueType getValueType() {
        return JsonValue.ValueType.OBJECT;
    }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String,JsonValue> e : entrySet()) {
            if (!first) {
                sb.append(',');
            }
            Json.esc(e.getKey(), sb);
            sb.append(':');
            sb.append(e.getValue().toString());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }
}
