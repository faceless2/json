package com.bfo.json;

import java.io.*;
import java.math.*;
import java.util.*;
import javax.json.*;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.spi.*;
import javax.json.stream.*;

/**
 * The JsonProvider implementation that provider the JSON-P (JSR374) implementation
 */
public class JSRProvider extends JsonProvider {
    @Override public JsonArrayBuilder createArrayBuilder() {
        return createBuilderFactory(null).createArrayBuilder();
    }
    @Override public JsonBuilderFactory createBuilderFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonGenerator createGenerator(OutputStream out) {
        return createGeneratorFactory(null).createGenerator(out);
    }
    @Override public JsonGenerator createGenerator(Writer writer) {
        return createGeneratorFactory(null).createGenerator(writer);
    }
    @Override public JsonGeneratorFactory createGeneratorFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonObjectBuilder createObjectBuilder() {
        return createBuilderFactory(null).createObjectBuilder();
    }
    @Override public JsonParser createParser(InputStream in) {
        return createParserFactory(null).createParser(in);
    }
    @Override public JsonParser createParser(Reader reader) {
        return createParserFactory(null).createParser(reader);
    }
    @Override public JsonParserFactory createParserFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonReader createReader(InputStream in) {
        return createReaderFactory(null).createReader(in);
    }
    @Override public JsonReader createReader(Reader reader) {
        return createReaderFactory(null).createReader(reader);
    }
    @Override public JsonReaderFactory createReaderFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonNumber createValue(double value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonNumber createValue(int value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonNumber createValue(long value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonString createValue(String value) {
        return new JSRJsonString(value);
    }
    @Override public JsonNumber createValue(BigDecimal value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonNumber createValue(BigInteger value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonWriter createWriter(OutputStream out) {
        return createWriterFactory(null).createWriter(out);
    }
    @Override public JsonWriter createWriter(Writer writer) {
        return createWriterFactory(null).createWriter(writer);
    }
    @Override public JsonWriterFactory createWriterFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonPointer createPointer(String jsonPointer) {
        return new JSRJsonPointer(jsonPointer);
    }
    @SuppressWarnings("unchecked")
    @Override public JsonArrayBuilder createArrayBuilder(Collection<?> collection) {
        JsonArrayBuilder b = createArrayBuilder();
        for (Object o : collection) {
            if (o == null) {
                b.addNull();
            } else if (o instanceof JsonValue) {
                b.add((JsonValue)o);
            } else if (o instanceof Boolean) {
                b.add(((Boolean)o).booleanValue());
            } else if (o instanceof Float || o instanceof Double) {
                b.add(((Number)o).doubleValue());
            } else if (o instanceof BigDecimal) {
                b.add((BigDecimal)o);
            } else if (o instanceof BigInteger) {
                b.add((BigDecimal)o);
            } else if (o instanceof Long) {
                b.add((Long)o);
            } else if (o instanceof Number) {
                b.add(((Number)o).intValue());
            } else if (o instanceof String) {
                b.add(o.toString());
            } else if (o instanceof Map) {
                b.add(createObjectBuilder((Map<String,Object>)o));
            } else if (o instanceof Collection) {
                b.add(createArrayBuilder((Collection)o));
            } else {
                throw new IllegalArgumentException(o.getClass().getName());
            }
        }
        return b;
    }
    @Override public JsonArrayBuilder createArrayBuilder(JsonArray array) {
        return createArrayBuilder((Collection)array);
    }

    @SuppressWarnings("unchecked")
    @Override public JsonObjectBuilder createObjectBuilder(Map<String,Object> map) {
        JsonObjectBuilder b = createObjectBuilder();
        for (Map.Entry<String,Object> e : map.entrySet()) {
            String key = e.getKey();
            Object o = e.getValue();
            if (o == null) {
                b.addNull(key);
            } else if (o instanceof JsonValue) {
                b.add(key, (JsonValue)o);
            } else if (o instanceof Boolean) {
                b.add(key, ((Boolean)o).booleanValue());
            } else if (o instanceof Float || o instanceof Double) {
                b.add(key, ((Number)o).doubleValue());
            } else if (o instanceof BigDecimal) {
                b.add(key, (BigDecimal)o);
            } else if (o instanceof BigInteger) {
                b.add(key, (BigDecimal)o);
            } else if (o instanceof Long) {
                b.add(key, (Long)o);
            } else if (o instanceof Number) {
                b.add(key, ((Number)o).intValue());
            } else if (o instanceof String) {
                b.add(key, o.toString());
            } else if (o instanceof Map) {
                b.add(key, createObjectBuilder((Map<String,Object>)o));
            } else if (o instanceof Collection) {
                b.add(key, createArrayBuilder((Collection)o));
            } else {
                throw new IllegalArgumentException(o.getClass().getName());
            }
        }
        return b;
    }

    @SuppressWarnings("unchecked")
    @Override public JsonObjectBuilder createObjectBuilder(JsonObject object) {
        JsonObjectBuilder b = createObjectBuilder();
        for (Map.Entry<String,JsonValue> e : object.entrySet()) {
            b.add(e.getKey(), e.getValue());
        }
        return b;
    }

//   @Override public JsonPatch createDiff(JsonStructure source, JsonStructure target)
//   @Override public JsonMergePatch createMergeDiff(JsonValue source, JsonValue target)
//   @Override public JsonMergePatch createMergePatch(JsonValue patch)

    @Override public JsonPatchBuilder createPatchBuilder() {
        return new JSRJsonPatchBuilder();
    }

    @Override public JsonPatchBuilder createPatchBuilder(JsonArray array) {
        JSRJsonPatchBuilder b = new JSRJsonPatchBuilder();
        ((JsonArray)b.build()).addAll(array);
        return b;
    }

    @Override public JsonPatch createPatch(JsonArray array) {
        return createPatchBuilder(array).build();
    }

    private static class JSRJsonPatchBuilder implements JsonPatchBuilder {
        private List<JsonValue> a = new ArrayList<JsonValue>();
        private JSRJsonPatchBuilder op(Object... o) {
            LinkedHashMap<String,JsonValue> m = new LinkedHashMap<String,JsonValue>();
            m.put("op", new JSRJsonString((String)o[0]));
            for (int i=1;i<o.length;) {
                m.put((String)o[i++], (JsonValue)o[i++]);
            }
            a.add(new JSRJsonObject(m));
            return this;
        }
        @Override public JsonPatchBuilder add(String path, boolean value) {
            return op("add", "path", new JSRJsonString(path), "value", value == true ? JsonValue.TRUE : JsonValue.FALSE);
        }
        @Override public JsonPatchBuilder add(String path, int value) {
            return op("add", "path", new JSRJsonString(path), "value", new JSRJsonNumber(value));
        }
        @Override public JsonPatchBuilder add(String path, String value) {
            return op("add", "path", new JSRJsonString(path), "value", new JSRJsonString(value));
        }
        @Override public JsonPatchBuilder add(String path, JsonValue value) {
            return op("add", "path", new JSRJsonString(path), "value", value);
        }
        @Override public JsonPatchBuilder copy(String path, String from) {
            return op("copy", "path", new JSRJsonString(path), "from", from);
        }
        @Override public JsonPatchBuilder move(String path, String from) {
            return op("move", "path", new JSRJsonString(path), "from", new JSRJsonString(from));
        }
        @Override public JsonPatchBuilder remove(String path) {
            return op("remove", "path", new JSRJsonString(path));
        }
        @Override public JsonPatchBuilder replace(String path, boolean value) {
            return op("replace", "path", new JSRJsonString(path), "value", value == true ? JsonValue.TRUE : JsonValue.FALSE);
        }
        @Override public JsonPatchBuilder replace(String path, int value) {
            return op("replace", "path", new JSRJsonString(path), "value", new JSRJsonNumber(value));
        }
        @Override public JsonPatchBuilder replace(String path, String value) {
            return op("replace", "path", new JSRJsonString(path), "value", new JSRJsonString(value));
        }
        @Override public JsonPatchBuilder replace(String path, JsonValue value) {
            return op("replace", "path", new JSRJsonString(path), "value", value);
        }
        @Override public JsonPatchBuilder test(String path, boolean value) {
            return op("test", "path", new JSRJsonString(path), "value", value == true ? JsonValue.TRUE : JsonValue.FALSE);
        }
        @Override public JsonPatchBuilder test(String path, int value) {
            return op("test", "path", new JSRJsonString(path), "value", new JSRJsonNumber(value));
        }
        @Override public JsonPatchBuilder test(String path, String value) {
            return op("test", "path", new JSRJsonString(path), "value", new JSRJsonString(value));
        }
        @Override public JsonPatchBuilder test(String path, JsonValue value) {
            return op("test", "path", new JSRJsonString(path), "value", value);
        }
        @Override public JsonPatch build() {
            return new JSRJsonArray(a);
        }
    }

    Object get(Object value) {
        if (value == JsonValue.TRUE) {
            return Boolean.TRUE;
        } else if (value == JsonValue.FALSE) {
            return Boolean.FALSE;
        } else if (value == JsonValue.NULL) {
            return null;
        } else if (value instanceof JSRJsonNumber) {
            return ((JSRJsonNumber)value).numberValue();
        } else if (value instanceof JsonNumber) {
            return ((JSRJsonNumber)value).bigDecimalValue();
        } else if (value instanceof JsonString) {
            return ((JSRJsonString)value).getString();
        } else {
            return value;
        }
    }
}
