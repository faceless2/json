package com.bfo.json;

import javax.json.*;
import javax.json.stream.*;
import java.util.*;
import java.lang.reflect.Array;
import java.math.*;
import java.io.*;
import java.nio.charset.*;

class JSRFactory implements JsonParserFactory, JsonReaderFactory, JsonBuilderFactory, JsonWriterFactory, JsonGeneratorFactory {

    private final Map<String,?> config;
    private boolean pretty;

    JSRFactory(Map<String,?> config) {
        Map<String,Object> c = new LinkedHashMap<String,Object>();
        if (config != null) {
            // Only copy valid keys
            for (Map.Entry<String,?> e : config.entrySet()) {
                if (e.getKey().equals(JsonGenerator.PRETTY_PRINTING) && e.getValue() instanceof Boolean) {
                    pretty = Boolean.TRUE.equals(e.getValue());
                    c.put(e.getKey(), e.getValue());
                }
            }
        }
        this.config = Collections.<String,Object>unmodifiableMap(c);
    }

    @Override public JsonParser createParser(InputStream in) {
        return new JSRJsonParser(new JsonReader(), in);
    }

    @Override public JsonParser createParser(InputStream in, Charset charset) {
        return createParser(new InputStreamReader(in, charset));
    }

    @Override public JsonParser createParser(Reader in) {
        return new JSRJsonParser(new JsonReader(), in);
    }

    @Override public javax.json.JsonReader createReader(InputStream in) {
        return new JSRJsonReader(createParser(in));
    }

    @Override public javax.json.JsonReader createReader(InputStream in, Charset charset) {
        return new JSRJsonReader(createParser(in, charset));
    }

    @Override public javax.json.JsonReader createReader(Reader reader) {
        return new JSRJsonReader(createParser(reader));
    }

    @Override public JsonParser createParser(JsonArray array) {
        return null; // return new ReaderImpl(createParser(in, charset));
    }

    @Override public JsonParser createParser(JsonObject obj) {
        return null; // return createParser(new StringReader(obj.toString()));
    }

    @Override public JsonArrayBuilder createArrayBuilder() {
        return new JSRJsonArrayBuilder();
    }

    @Override public JsonArrayBuilder createArrayBuilder(Collection<?> collection) {
        JsonArrayBuilder builder = new JSRJsonArrayBuilder();
        Set<Object> seen = new HashSet<Object>();
        for (Object o : collection) {
            JsonValue v = toJsonValue(o, seen);
            if (v != null) {
                builder.add(v);
            }
        }
        return builder;
    }

    @Override public JsonArrayBuilder createArrayBuilder(JsonArray array) {
        JsonArrayBuilder builder = createArrayBuilder();
        for (JsonValue v : array) {
            builder.add(v);
        }
        return builder;
    }

    @Override public JsonObjectBuilder createObjectBuilder() {
        return new JSRJsonObjectBuilder();
    }

    @Override public JsonObjectBuilder createObjectBuilder(Map<String,Object> object) {
        JsonObjectBuilder builder = new JSRJsonObjectBuilder();
        Set<Object> seen = new HashSet<Object>();
        for (Map.Entry<String,Object> e : object.entrySet()) {
            JsonValue v = toJsonValue(e.getValue(), seen);
            if (v != null) {
                builder.add(e.getKey(), v);
            }
        }
        return builder;
    }

    @Override public JsonObjectBuilder createObjectBuilder(JsonObject object) {
        JsonObjectBuilder builder = createObjectBuilder();
        for (Map.Entry<String,JsonValue> e : object.entrySet()) {
            builder.add(e.getKey(), e.getValue());
        }
        return builder;
    }

    @Override public javax.json.JsonWriter createWriter(OutputStream out) {
        return createWriter(out, StandardCharsets.UTF_8);
    }

    @Override public javax.json.JsonWriter createWriter(OutputStream out, Charset charset) {
        return createWriter(new OutputStreamWriter(out, charset));
    }

    @Override public javax.json.JsonWriter createWriter(Writer writer) {
        JsonWriter w = new JsonWriter();
        w.setOutput(WriterToAppendable.getInstance(writer));
        w.setIndent(pretty ? 2 : 0);
        return new JSRJsonGenerator(w, writer).asWriter();
    }

    @Override public JsonGenerator createGenerator(OutputStream out) {
        return createGenerator(out, StandardCharsets.UTF_8);
    }

    @Override public JsonGenerator createGenerator(OutputStream out, Charset charset) {
        return createGenerator(new OutputStreamWriter(out, charset));
    }

    @Override public JsonGenerator createGenerator(Writer writer) {
        JsonWriter w = new JsonWriter();
        w.setOutput(WriterToAppendable.getInstance(writer));
        w.setIndent(pretty ? 2 : 0);
        return new JSRJsonGenerator(w, writer);
    }

    @Override public Map<String,?> getConfigInUse() {
        return config;
    }

    private static class JSRJsonArrayBuilder implements JsonArrayBuilder {
        final List<JsonValue> a = new ArrayList<JsonValue>();
        @Override public JsonArrayBuilder add(int index, boolean value) {
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, double value) {
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, int value) {
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, long value) {
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, String value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, BigDecimal value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, BigInteger value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder add(int index, JsonArrayBuilder value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(index, ((JSRJsonArrayBuilder)value).build());
        }
        @Override public JsonArrayBuilder add(int index, JsonObjectBuilder value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(index, ((JSRJsonObjectBuilder)value).build());
        }
        @Override public JsonArrayBuilder add(int index, JsonValue value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            a.add(index, value);
            return this;
        }
        @Override public JsonArrayBuilder addNull(int index) {
            return add(index, JsonValue.NULL);
        }
        @Override public JsonArrayBuilder remove(int index) {
            a.remove(index);
            return this;
        }
        @Override public JsonArrayBuilder add(boolean value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(double value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(int value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(long value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(String value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(BigDecimal value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(BigInteger value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(JsonArrayBuilder value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(JsonObjectBuilder value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder add(JsonValue value) {
            return add(a.size(), value);
        }
        @Override public JsonArrayBuilder addAll(JsonArrayBuilder builder) {
            for (JsonValue v : builder.build()) {
                add(v);
            }
            return this;
        }
        @Override public JsonArrayBuilder addNull() {
            return addNull(a.size());
        }
        @Override public JsonArrayBuilder set(int index, boolean value) {
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, double value) {
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, int value) {
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, long value) {
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, String value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, BigDecimal value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, BigInteger value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return set(index, toJsonValue(value, null));
        }
        @Override public JsonArrayBuilder set(int index, JsonArrayBuilder value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return set(index, value.build());
        }
        @Override public JsonArrayBuilder set(int index, JsonObjectBuilder value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return set(index, value.build());
        }
        @Override public JsonArrayBuilder set(int index, JsonValue value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            a.set(index, value);
            return this;
        }
        @Override public JsonArrayBuilder setNull(int index) {
            return set(index, JsonValue.NULL);
        }
        @Override public JsonArray build() {
            return new JSRJsonArray(a);
        }
    }

    private static class JSRJsonObjectBuilder implements JsonObjectBuilder {
        LinkedHashMap<String,JsonValue> m = new LinkedHashMap<String,JsonValue>();
        @Override public JsonObjectBuilder add(String name, boolean value) {
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, double value) {
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, int value) {
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, long value) {
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, String value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, BigDecimal value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, BigInteger value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(name, toJsonValue(value, null));
        }
        @Override public JsonObjectBuilder add(String name, JsonArrayBuilder value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(name, value.build());
        }
        @Override public JsonObjectBuilder add(String name, JsonObjectBuilder value) {
            if (value == null) {
                throw new NullPointerException("Value is null");
            }
            return add(name, value.build());
        }
        @Override public JsonObjectBuilder add(String name, JsonValue value) {
            if (name == null || value == null) {
                throw new NullPointerException("Key or value is null");
            }
            m.put(name, value);
            return this;
        }
        @Override public JsonObjectBuilder addNull(String name) {
            if (name == null) {
                throw new NullPointerException("Key is null");
            }
            m.put(name, JsonValue.NULL);
            return this;
        }
        @Override public JsonObjectBuilder remove(String name) {
            if (name == null) {
                throw new NullPointerException("Key is null");
            }
            m.remove(name);
            return this;
        }
        @Override public JsonObjectBuilder addAll(JsonObjectBuilder builder) {
            for (Map.Entry<String,JsonValue> e : builder.build().entrySet()) {
                add(e.getKey(), e.getValue());
            }
            return this;
        }
        @Override public JsonObject build() {
            return new JSRJsonObject(m);
        }
    }

    private static class WriterToAppendable implements Appendable, Flushable, Closeable {
        final Writer w;
        static Appendable getInstance(Writer w) {
            if (w instanceof Appendable) {
                return (Appendable)w;
            }
            return new WriterToAppendable(w);
        }
        private WriterToAppendable(Writer w) {
            this.w = w;
        }
        @Override public Appendable append(char c) throws IOException {
            w.write(c);
            return this;
        }
        @Override public Appendable append(CharSequence s) throws IOException {
            if (s instanceof String) {
                w.write((String)s);
            } else {
                for (int i=0;i<s.length();i++) {
                    w.write(s.charAt(i));
                }
            }
            return this;
        }
        @Override public Appendable append(CharSequence s, int off, int len) throws IOException {
            for (int i=0;i<len;i++) {
                w.write(s.charAt(off + i));
            }
            return this;
        }
        @Override public void flush() throws IOException {
            w.flush();
        }
        @Override public void close() throws IOException {
            w.close();
        }
    }

    @SuppressWarnings("rawtypes")
    static JsonValue toJsonValue(Object o, Set<Object> seen) {
        if (o instanceof Optional) {
            if (((Optional)o).isPresent()) {
                o = ((Optional)o).get();
            } else {
                return null;
            }
        }
        if (o instanceof Json) {
            o = ((Json)o).value();
        }
        if (o instanceof JsonValue) {
            return (JsonValue)o;
        } else if (o == null) {
            return JsonValue.NULL;
        } else if (Boolean.TRUE.equals(o)) {
            return JsonValue.TRUE;
        } else if (Boolean.FALSE.equals(o)) {
            return JsonValue.FALSE;
        } else if (o instanceof Number) {
            return new JSRJsonNumber((Number)o);
        } else if (o instanceof String) {
            return new JSRJsonString((String)o);
        } else if (o.getClass().isArray()) {
            if (!seen.add(o)) {
                throw new IllegalArgumentException("Objects form a loop");
            }
            List<JsonValue> list = new ArrayList<JsonValue>();
            for (int i=0;i<Array.getLength(o);i++){
                list.add(toJsonValue(Array.get(o, i), seen));
            }
            return new JSRJsonArray(list);
        } else if (o instanceof Collection) {
            if (!seen.add(o)) {
                throw new IllegalArgumentException("Objects form a loop");
            }
            List<JsonValue> list = new ArrayList<JsonValue>();
            @SuppressWarnings("unchecked") Collection<Object> in = (Collection<Object>)o;
            for (Object v : in) {
                list.add(toJsonValue(v, seen));
            }
            return new JSRJsonArray(list);
        } else if (o instanceof Map) {
            if (!seen.add(o)) {
                throw new IllegalArgumentException("Objects form a loop");
            }
            LinkedHashMap<String,JsonValue> map = new LinkedHashMap<String,JsonValue>();
            @SuppressWarnings("unchecked") Map<Object,Object> in = (Map<Object,Object>)o;
            for (Map.Entry<Object,Object> e : in.entrySet()) {
                if (e.getKey() instanceof String) {
                    map.put((String)e.getKey(), toJsonValue(e.getValue(), seen));
                } else {
                    throw new IllegalArgumentException("Map key " + e.getKey()+" is " + (e.getKey() == null ? null : e.getKey().getClass().getName()) + " not java.lang.String");
                }
            }
            return new JSRJsonObject(map);
        } else {
            throw new IllegalArgumentException("Can't convert " + o.getClass().getName() + " to JsonValue");
        }
    }

}
