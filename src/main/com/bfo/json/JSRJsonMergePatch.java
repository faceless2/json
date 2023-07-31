package com.bfo.json;

import java.util.*;
import javax.json.*;
import javax.json.Json;

class JSRJsonMergePatch implements JsonMergePatch {

    final JsonValue patch;

    JSRJsonMergePatch(JsonValue patch) {
        this.patch = patch;
    }

    @Override public String toString() {
        return patch.toString();
    }

    @Override public JsonValue toJsonValue() {
        return patch;
    }

    @Override public JsonValue apply(JsonValue target) {
        return mergePatch(target, patch);
    }

    private static JsonValue mergePatch(JsonValue target, JsonValue patch) {
        if (patch instanceof JsonObject) {
            JsonObjectBuilder builder;
            if (!(target instanceof JsonObject)) {
                builder = Json.createObjectBuilder();
            } else {
                builder = Json.createObjectBuilder((JsonObject)target);
            }
            for (Map.Entry<String,JsonValue> e : ((JsonObject)patch).entrySet()) {
                if (e.getValue() == JsonValue.NULL) {
                    builder.remove(e.getKey());
                } else if (target instanceof JsonObject && ((JsonObject)target).containsKey(e.getKey())) {
                    builder.add(e.getKey(), mergePatch(((JsonObject)target).get(e.getKey()), e.getValue()));
                } else {
                    builder.add(e.getKey(), mergePatch(Json.createObjectBuilder().build(), e.getValue()));
                }
            }
            return builder.build();
        } else {
            return patch;
        }
    }

    static JsonValue build(JsonValue source, JsonValue target) {
        if (!(source instanceof JsonObject && target instanceof JsonObject)) {
            return target;
        }
        JsonObject s = (JsonObject)source;
        JsonObject t = (JsonObject)target;
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String,JsonValue> e : s.entrySet()) {
            String key = e.getKey();
            JsonValue sv = e.getValue();
            JsonValue tv = t.get(key);
            if (tv == null) {
                builder.addNull(key);
            } else if (!sv.equals(tv)) {
                builder.add(key, build(sv, tv));
            }
        }
        for (Map.Entry<String,JsonValue> e : t.entrySet()) {
            String key = e.getKey();
            JsonValue tv = e.getValue();
            JsonValue sv = s.get(key);
            if (sv == null) {
                builder.add(key, tv);
            }
        }
        return builder.build();
    }

}
