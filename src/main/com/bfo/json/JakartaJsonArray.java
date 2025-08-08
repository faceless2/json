package com.bfo.json;

import java.util.*;
import jakarta.json.*;

class JakartaJsonArray extends AbstractList<JsonValue> implements JsonArray, JsonPatch {

    final List<JsonValue> list;

    JakartaJsonArray(List<JsonValue> list) {
        this.list = list;
    }

    @Override public int size() {
        return list.size();
    }

    @Override public JsonValue get(int i) {
        return list.get(i);
    }

    @Override public boolean getBoolean(int index) {
        JsonValue v = get(index);
        if (v == JsonValue.TRUE) {
            return true;
        } else if (v == JsonValue.FALSE) {
            return false;
        } else {
            throw new ClassCastException(v.getClass().getName());
        }
    }
    @Override public boolean getBoolean(int index, boolean defaultValue) {
        if (index < 0 || index >= size()) {
            return defaultValue;
        }
        JsonValue v = get(index);
        if (v == JsonValue.TRUE) {
            return true;
        } else if (v == JsonValue.FALSE) {
            return false;
        } else {
            return defaultValue;
        }

    }
    @Override public int getInt(int index) {
        return ((JsonNumber)get(index)).intValue();
    }
    @Override public int getInt(int index, int defaultValue) {
        if (index < 0 || index >= size()) {
            return defaultValue;
        }
        JsonValue v = get(index);
        return v instanceof JsonNumber ? ((JsonNumber)v).intValue() : defaultValue;
    }
    @Override public JsonArray getJsonArray(int index) {
        return (JsonArray)get(index);
    }
    @Override public JsonNumber getJsonNumber(int index) {
        return (JsonNumber)get(index);
    }
    @Override public JsonObject getJsonObject(int index) {
        return (JsonObject)get(index);
    }
    @Override public JsonString getJsonString(int index) {
        return (JsonString)get(index);
    }
    @Override public String getString(int index) {
        return ((JsonString)get(index)).getString();
    }
    @Override public String getString(int index, String defaultValue) {
        if (index < 0 || index >= size()) {
            return defaultValue;
        }
        JsonValue v = get(index);
        return v instanceof JsonString ? ((JsonString)v).getString() : defaultValue;
    }
    @SuppressWarnings("unchecked") @Override public <T extends JsonValue> List<T> getValuesAs(Class<T> clazz) {
        return (List<T>)this;
    }
    @Override public boolean isNull(int index) {
        return get(index) == JsonValue.NULL;
    }
    @Override public JsonValue.ValueType getValueType() {
        return JsonValue.ValueType.ARRAY;
    }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i=0;i<size();i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(get(i).toString());
        }
        sb.append(']');
        return sb.toString();
    }

    @Override public JsonArray toJsonArray() {
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override public <T extends JsonStructure> T apply(T target) {
        for (int i=0;i<size();i++) {
            JsonObject m = (JsonObject)get(i);
            String op = m.getString("op");
            JsonPointer path = new JakartaJsonPointer(m.getString("path"));

            if (op.equals("add")) {
                target = path.add(target, m.get("value"));
            } else if (op.equals("replace")) {
                target = path.replace(target, m.get("value"));
            } else if (op.equals("remove")) {
                target = path.remove(target);
            } else if (op.equals("copy")) {
                JakartaJsonPointer from = new JakartaJsonPointer(m.getString("from"));
                target = path.add(target, from.getValue(target));
            } else if (op.equals("move")) {
                if (m.getString("path").startsWith(m.getString("from") + "/")) {
                    throw new JsonException("Path \"" + m.getString("path") + "\" may not be prefix of from \"" + m.getString("from") + "\"");
                }
                JakartaJsonPointer from = new JakartaJsonPointer(m.getString("from"));
                JsonValue value = from.getValue(target);
                target = from.remove(target);
                target = path.add(target, value);
            } else if (op.equals("test")) {
                JsonValue value = path.getValue(target);
                if (!value.equals(m.get("value"))) {
                    throw new JsonException(m.toString()+": mismatch");
                }
            } else {
                throw new JsonException(m.toString()+": unsupported operation");
            }
        }
        return target;
    }

}
