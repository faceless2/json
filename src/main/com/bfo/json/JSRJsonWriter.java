package com.bfo.json;

import java.math.*;
import java.io.*;
import java.util.*;
import javax.json.*;
import javax.json.stream.*;

class JSRJsonWriter implements JsonGenerator {

    private static final int ROOT = 0, OBJECT = 1, ARRAY = 2;
    private class State {
        final State parent;
        final int type;
        private int count;
        State(State parent, int type) {
            this.parent = parent;
            this.type = type;
        }
        void map() throws IOException {
            if (type == OBJECT) {
                if (count > 0) {
                    writer.writeComma();
                }
            } else {
                throw new JsonGenerationException("Wrong state");
            }
            count++;
        }
        void rootOrArray() throws IOException {
            if (type == ROOT) {
                if (count > 0) {
                    throw new JsonGenerationException("Wrong state" + this);
                }
            } else if (type == ARRAY) {
                if (count > 0) {
                    writer.writeComma();
                }
            } else {
                throw new JsonGenerationException("Wrong state");
            }
            count++;
        }
        public String toString() {
            return "{type="+(type==ROOT?"root":type==OBJECT?"object":"array")+" count="+count+" parent="+parent+"}";
        }
    }

    private State state;
    private final JsonWriter writer;

    JSRJsonWriter(JsonWriter writer) {
        this.writer = writer;
        state = new State(null, ROOT);
    }

    public javax.json.JsonWriter asWriter() {
        return new javax.json.JsonWriter() {
            @Override public void writeArray(JsonArray array) {
                write(array);
            }
            @Override public void writeObject(JsonObject object) {
                write(object);
            }
            @Override public void write(JsonStructure value) {
                JSRJsonWriter.this.write((JsonValue)value);
            }
            @Override public void close() {
                JSRJsonWriter.this.close();
            }
        };
    }

    @Override public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public void flush() {
        try {
            writer.flush();
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }

    @Override public JsonGenerator write(JsonValue value) {
        if (value instanceof JsonArray) {
            writeStartArray();
            JsonArray a = (JsonArray)value;
            for (int i=0;i<a.size();i++) {
                write(a.get(i));
            }
            writeEnd();
        } else if (value instanceof JsonObject) {
            writeStartObject();
            JsonObject m = (JsonObject)value;
            for (Map.Entry<String,JsonValue> e : m.entrySet()) {
                write(e.getKey(), e.getValue());
            }
            writeEnd();
        } else if (value == JsonValue.TRUE) {
            write(true);
        } else if (value == JsonValue.FALSE) {
            write(false);
        } else if (value == JsonValue.NULL) {
            writeNull();
        } else if (value instanceof JSRJsonNumber) {
            write(((JSRJsonNumber)value).numberValue());
        } else if (value instanceof JSRJsonString) {
            write(((JSRJsonString)value).getString());
        } else {
            throw new IllegalArgumentException();
        }
        return this;
    }
    @Override public JsonGenerator write(boolean value) {
        try {
            state.rootOrArray();
            writer.writeBoolean(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(double value) {
        try {
            state.rootOrArray();
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(int value) {
        try {
            state.rootOrArray();
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(long value) {
        try {
            state.rootOrArray();
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    JsonGenerator write(Number value) {
        try {
            state.rootOrArray();
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String value) {
        try {
            state.rootOrArray();
            writer.writeString(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, boolean value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeBoolean(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, double value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, int value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, long value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    JsonGenerator write(String name, Number value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, String value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeString(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, BigDecimal value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, BigInteger value) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, JsonValue value) {
        if (value instanceof JsonArray) {
            writeStartArray(name);
            JsonArray a = (JsonArray)value;
            for (int i=0;i<a.size();i++) {
                write(a.get(i));
            }
            writeEnd();
        } else if (value instanceof JsonObject) {
            writeStartObject(name);
            JsonObject m = (JsonObject)value;
            for (Map.Entry<String,JsonValue> e : m.entrySet()) {
                write(e.getKey(), e.getValue());
            }
            writeEnd();
        } else if (value == JsonValue.TRUE) {
            write(name, true);
        } else if (value == JsonValue.FALSE) {
            write(name, false);
        } else if (value == JsonValue.NULL) {
            writeNull(name);
        } else if (value instanceof JSRJsonNumber) {
            write(name, ((JSRJsonNumber)value).numberValue());
        } else if (value instanceof JSRJsonString) {
            write(name, ((JSRJsonString)value).getString());
        } else {
            throw new IllegalArgumentException();
        }
        return this;
    }
    @Override public JsonGenerator write(BigDecimal value) {
        try {
            state.rootOrArray();
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(BigInteger value) {
        try {
            state.rootOrArray();
            writer.writeNumber(value);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeEnd() {
        try {
            if (state.type == ARRAY) {
                writer.writeEndArray();
                state = state.parent;
            } else if (state.type == OBJECT) {
                writer.writeEndObject();
                state = state.parent;
            } else {
                throw new JsonGenerationException("Wrong state");
            }
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeKey(String name) {
        try {
            state.map();
            writer.writeKey(name);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeNull() {
        try {
            state.rootOrArray();
            writer.writeNull();
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeNull(String name) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeNull();
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartArray() {
        try {
            state.rootOrArray();
            writer.writeStartArray();
            state = new State(state, ARRAY);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartArray(String name) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeStartArray();
            state = new State(state, ARRAY);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartObject() {
        try {
            state.rootOrArray();
            writer.writeStartObject();
            state = new State(state, OBJECT);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartObject(String name) {
        try {
            state.map();
            writer.writeKey(name);
            writer.writeStartObject();
            state = new State(state, OBJECT);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
}
