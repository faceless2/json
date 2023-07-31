package com.bfo.json;

import java.util.*;
import javax.json.*;
import javax.json.Json;

@SuppressWarnings("unchecked")
class JSRJsonPointer implements JsonPointer {

    private List<String> path;

    JSRJsonPointer(String s) {
        if (s.length() == 0) {
            path = Collections.<String>emptyList();
        } else if (!s.startsWith("/")) {
            throw new IllegalArgumentException("Invalid JsonPointer \"" + s + "\"");
        } else {
            s = s.substring(1);
            path = Arrays.asList(s.split("/"));
            for (int i=0;i<path.size();i++) {
                path.set(i, path.get(i).replaceAll("~1", "/").replaceAll("~0", "~"));
            }
        }
    }

    JSRJsonPointer(List<String> path) {
        this.path = path;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String s : path) {
            sb.append('/');
            if (s.equals("/")) {
                sb.append("~1");
            } else if (s.equals("~0")) {
                sb.append("~");
            } else {
                sb.append(s);
            }
        }
        return sb.toString();
    }

    // {"foo":[1,{"bar":20},2],"baz":false}
    // replace path=["foo", 1, "bar"] value=30

    private JsonValue apply(JsonValue cursor, List<String> path, String op, JsonValue value) {
//        System.out.println("A: c="+cursor+" p="+path+" op="+op);
        if (path.isEmpty()) {
            throw new JsonException(this + ": empty path");
        } else if (cursor == null) {
            throw new JsonException(this + ": target is null");
        } else if (path.size() > 1) {
            String p = path.get(0);
            if (cursor instanceof JsonObject) {
                JsonObject o = (JsonObject)cursor;
                JsonObjectBuilder builder = Json.createObjectBuilder(o);
                builder.add(p, apply(o.get(p), path.subList(1, path.size()), op, value));
                cursor = builder.build();
            } else if (cursor instanceof JsonArray) {
                JsonArray a = (JsonArray)cursor;
                JsonArrayBuilder builder = Json.createArrayBuilder(a);
                int index = Integer.parseInt(p);
                if (index < 0 || index >= a.size()) {
                    throw new JsonException(this + ": not found");
                } else {
                    builder.set(index, apply(a.get(index), path.subList(1, path.size()), op, value));
                }
                cursor = builder.build();
            } else {
                throw new JsonException(this + ": not found");
            }
        } else if (cursor instanceof JsonObject) {
            String p = path.get(0);
            JsonObject o = (JsonObject)cursor;
            JsonObjectBuilder builder = Json.createObjectBuilder(o);
            if (op.equals("add") || (op.equals("replace") && o.containsKey(p))) {
                builder.add(p, value);
            } else if (op.equals("remove") && o.containsKey(p)) {
                builder.remove(p);
            } else {
                throw new JsonException(this + ": not found");
            }
            cursor = builder.build();
        } else if (cursor instanceof JsonArray) {
            String p = path.get(0);
            JsonArray a = (JsonArray)cursor;
            JsonArrayBuilder builder = Json.createArrayBuilder(a);
            if (p.equals("-")) {
                if (op.equals("add")) {
                    builder.add(value);
                } else {
                    throw new JsonException(this + ": not found");
                }
            } else {
                try {
                    int index = Integer.parseInt(p);
                    if (index < 0 || index >= a.size()) {
                        throw new JsonException(this + ": not found");
                    } else if (op.equals("add")) {
                        builder.add(index, value);
                    } else if (op.equals("replace")) {
                        builder.set(index, value);
                    } else if (op.equals("remove")) {
                        builder.remove(index);
                    }
                } catch (Exception e) {
                    throw new JsonException(this + ": not found");
                }
            }
            cursor = builder.build();
        } else {
            throw new JsonException(this + ": not found");
        }
        return cursor;
    }

    @Override public <T extends JsonStructure> T add(T target, JsonValue value) {
        return (T)apply(target, path, "add", value);
    }

    @Override public <T extends JsonStructure> T remove(T target) {
        return (T)apply(target, path, "remove", null);
    }

    @Override public <T extends JsonStructure> T replace(T target, JsonValue value) {
        return (T)apply(target, path, "replace", value);
    }

    @Override public boolean containsValue(JsonStructure target) {
        try {
            JsonValue value = target;
            for (String p : path) {
                if (value instanceof JsonObject && ((JsonObject)value).containsKey(p)) {
                    value = ((JsonObject)value).get(p);
                } else if (value instanceof JsonArray) {
                    value = ((JsonArray)value).get(Integer.parseInt(p));
                } else {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public JsonValue getValue(JsonStructure target) {
        if (target == null) {
            throw new NullPointerException("target is null");
        }
        try {
            JsonValue value = target;
            for (String p : path) {
                if (value instanceof JsonObject && ((JsonObject)value).containsKey(p)) {
                    value = ((JsonObject)value).get(p);
                } else if (value instanceof JsonArray) {
                    value = ((JsonArray)value).get(Integer.parseInt(p));
                } else {
                    throw new JsonException(this + ": not found");
                }
            }
            return value;
        } catch (Exception e) {
            throw new JsonException(this + ": not found");
        }
    }

}
