package com.bfo.json;

import javax.json.*;
import javax.json.stream.*;
import java.util.*;
import java.math.*;
import java.io.*;
import java.nio.charset.*;

class JSRFactory implements JsonParserFactory, JsonReaderFactory, JsonBuilderFactory, JsonWriterFactory, JsonGeneratorFactory {

    private final Map<String,?> config;

    JSRFactory(Map<String,?> config) {
        if (config == null || config.isEmpty()) {
            config = Collections.<String,Object>emptyMap();
        } else {
            config = Collections.<String,Object>unmodifiableMap(config);
        }
        this.config = config;
    }

    @Override public JsonParser createParser(InputStream in) {
        try {
            return createParser(JsonReader.createReader(in));
        } catch (IOException e) {
            return new JSRJsonParser(e);

        }
    }

    @Override public JsonParser createParser(InputStream in, Charset charset) {
        return createParser(new InputStreamReader(in, charset));
    }

    @Override public javax.json.JsonReader createReader(InputStream in) {
        try {
            return createReader(JsonReader.createReader(in));
        } catch (IOException e) {
            return new JSRJsonParser(e);

        }
    }

    @Override public javax.json.JsonReader createReader(InputStream in, Charset charset) {
        return createReader(new InputStreamReader(in, charset));
    }

    @Override public JsonParser createParser(JsonArray array) {
        return null;
    }

    @Override public JsonParser createParser(JsonObject obj) {
        return null;
    }

    @Override public JsonParser createParser(Reader in) {
        if (!in.markSupported()) {
            in = new BufferedReader(in);
        }
        if (!(in instanceof CharSequenceReader)) {
            in = new ContextReader(in);
        }
        return new JSRJsonParser(new JsonReader(in, new JsonReadOptions()));
    }

    @Override public javax.json.JsonReader createReader(Reader in) {
        if (!in.markSupported()) {
            in = new BufferedReader(in);
        }
        if (!(in instanceof CharSequenceReader)) {
            in = new ContextReader(in);
        }
        return new JSRJsonParser(new JsonReader(in, new JsonReadOptions()));
    }

    @Override public JsonArrayBuilder createArrayBuilder() {
        return new JSRJsonArrayBuilder();
    }

    @Override public JsonObjectBuilder createObjectBuilder() {
        return new JSRJsonObjectBuilder();
    }

    @Override public javax.json.JsonWriter createWriter(OutputStream out) {
        return createWriter(out, StandardCharsets.UTF_8);
    }
    @Override public javax.json.JsonWriter createWriter(OutputStream out, Charset charset) {
        return createWriter(new OutputStreamWriter(out, charset));
    }
    @Override public javax.json.JsonWriter createWriter(Writer writer) {
        return new JSRJsonWriter(new JsonWriter(WriterToAppendable.getInstance(writer), new JsonWriteOptions(), null)).asWriter();
    }
    @Override public JsonGenerator createGenerator(OutputStream out) {
        return createGenerator(out, StandardCharsets.UTF_8);
    }
    @Override public JsonGenerator createGenerator(OutputStream out, Charset charset) {
        return createGenerator(new OutputStreamWriter(out, charset));
    }
    @Override public JsonGenerator createGenerator(Writer writer) {
        return new JSRJsonWriter(new JsonWriter(WriterToAppendable.getInstance(writer), new JsonWriteOptions(), null));
    }

    @Override public Map<String,?> getConfigInUse() {
        return config;
    }

    private static class JSRJsonArrayBuilder implements JsonArrayBuilder {
        final List<JsonValue> a = new ArrayList<JsonValue>();
        @Override public JsonArrayBuilder add(boolean value) {
            a.add(value ? JsonValue.TRUE : JsonValue.FALSE);
            return this;
        }
        @Override public JsonArrayBuilder add(double value) {
            a.add(new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonArrayBuilder add(int value) {
            a.add(new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonArrayBuilder add(long value) {
            a.add(new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonArrayBuilder add(String value) {
            a.add(new JSRJsonString(value));
            return this;
        }
        @Override public JsonArrayBuilder add(BigDecimal value) {
            a.add(new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonArrayBuilder add(BigInteger value) {
            a.add(new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonArrayBuilder add(JsonArrayBuilder builder) {
            a.add(((JSRJsonArrayBuilder)builder).build());
            return this;
        }
        @Override public JsonArrayBuilder add(JsonObjectBuilder builder) {
            a.add(((JSRJsonObjectBuilder)builder).build());
            return this;
        }
        @Override public JsonArrayBuilder add(JsonValue value) {
            a.add(value);
            return this;
        }
        @Override public JsonArrayBuilder addNull() {
            a.add(JsonValue.NULL);
            return this;
        }
        @Override public JsonArrayBuilder remove(int index) {
            a.remove(index);
            return this;
        }
        @Override public JsonArray build() {
            return new JSRJsonArray(a);
        }
    }

    private static class JSRJsonObjectBuilder implements JsonObjectBuilder {
        LinkedHashMap<String,JsonValue> m = new LinkedHashMap<String,JsonValue>();
        @Override public JsonObjectBuilder add(String name, boolean value) {
            m.put(name, value ? JsonValue.TRUE : JsonValue.FALSE);
            return this;
        }
        @Override public JsonObjectBuilder add(String name, double value) {
            m.put(name, new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonObjectBuilder add(String name, int value) {
            m.put(name, new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonObjectBuilder add(String name, long value) {
            m.put(name, new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonObjectBuilder add(String name, String value) {
            m.put(name, new JSRJsonString(value));
            return this;
        }
        @Override public JsonObjectBuilder add(String name, BigDecimal value) {
            m.put(name, new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonObjectBuilder add(String name, BigInteger value) {
            m.put(name, new JSRJsonNumber(value));
            return this;
        }
        @Override public JsonObjectBuilder add(String name, JsonArrayBuilder builder) {
            m.put(name, ((JSRJsonArrayBuilder)builder).build());
            return this;
        }
        @Override public JsonObjectBuilder add(String name, JsonObjectBuilder builder) {
            m.put(name, ((JSRJsonObjectBuilder)builder).build());
            return this;
        }
        @Override public JsonObjectBuilder add(String name, JsonValue value) {
            m.put(name, value);
            return this;
        }
        @Override public JsonObjectBuilder addNull(String name) {
            m.put(name, JsonValue.NULL);
            return this;
        }
        @Override public JsonObjectBuilder remove(String name) {
            m.remove(name);
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

}
