package com.bfo.json;

import java.util.*;
import javax.json.*;

class JSRJsonPointer implements JsonPointer {

    private List<String> path;

    JSRJsonPointer(String s) {
        path = Arrays.asList(s.split("/"));
        for (int i=0;i<path.size();i++) {
            path.set(i, path.get(i).replaceAll("~1", "/").replaceAll("~0", "~"));
        }
    }

    JSRJsonPointer(List<String> path) {
        this.path = path;
    }

    @Override public <T extends JsonStructure> T add(T target, JsonValue value) {
        if (target == null) {
            throw new NullPointerException("target is null");
        }
        JsonValue p = getParent().getValue(target);
        try {
            String last = path.get(path.size() - 1);
            if (p instanceof JsonArray) {
                ((JsonArray)p).add(Integer.parseInt(last), value);
            } else {
                ((JsonObject)p).put(last, value);
            }
            return target;
        } catch (JsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    @Override public <T extends JsonStructure> T remove(T target) {
        if (target == null) {
            throw new NullPointerException("target is null");
        }
        JsonValue p = getParent().getValue(target);
        try {
            String last = path.get(path.size() - 1);
            if (p instanceof JsonArray) {
                ((JsonArray)p).remove(Integer.parseInt(last));
            } else if (((JsonObject)p).containsKey(last)) {
                ((JsonObject)p).remove(last);
            } else {
                throw new JsonException(toString()+": not found");
            }
        } catch (JsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonException(e.getMessage(), e);
        }
        return target;
    }

    @Override public <T extends JsonStructure> T replace(T target, JsonValue value) {
        if (target == null) {
            throw new NullPointerException("target is null");
        }
        JsonValue p = getParent().getValue(target);
        try {
            String last = path.get(path.size() - 1);
            if (p instanceof JsonArray) {
                ((JsonArray)p).add(Integer.parseInt(last), value);
            } else if (((JsonObject)p).containsKey(last)) {
                ((JsonObject)p).put(last, value);
            } else {
                throw new JsonException(toString()+": not found");
            }
        } catch (JsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonException(e.getMessage(), e);
        }
        return target;
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

    JsonPointer getParent() {
        return new JSRJsonPointer(path.subList(0, path.size() - 1));
    }

}
