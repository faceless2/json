package com.bfo.json;

import java.math.*;
import java.io.*;
import java.util.*;
import jakarta.json.*;
import jakarta.json.stream.*;

class JakartaJsonGenerator implements JsonGenerator {

    private static final int ROOT = 0, OBJECT = 1, ARRAY = 2;

    private class State {
        final State parent;
        final int type;
        private int count;
        State(State parent, int type) {
            this.parent = parent;
            this.type = type;
        }
        void preKey() throws IOException {
            if (type == OBJECT && (count & 1) == 0) {
                count++;
            } else {
                throw new JsonGenerationException("Wrong state");
            }
        }
        void preValue() throws IOException {
            if (type == ROOT && count == 0) {
                count++;
            } else if (type == ARRAY) {
                count++;
            } else if (type == OBJECT && (count & 1) == 1) {
                count++;
            } else {
                throw new JsonGenerationException("Wrong state");
            }
        }
        public String toString() {
            return "{type="+(type==ROOT?"root":type==OBJECT?"object":"array")+" count="+count+" parent="+parent+"}";
        }
    }

    private State state;
    private final JsonWriter out;
    private final Closeable closeable;
    boolean wrote;

    JakartaJsonGenerator(JsonWriter out, Closeable closeable) {
        this.out = out;
        this.closeable = closeable;
        state = new State(null, ROOT);
    }

    public jakarta.json.JsonWriter asWriter() {
        return new jakarta.json.JsonWriter() {
            boolean wrote;
            @Override public void writeArray(JsonArray array) {
                write(array);
            }
            @Override public void writeObject(JsonObject object) {
                write(object);
            }
            @Override public void write(JsonStructure value) {
                write((JsonValue)value);
            }
            @Override public void write(JsonValue value) {
                if (wrote) {
                    throw new IllegalStateException("Already written");
                }
                JakartaJsonGenerator.this.write(value);
                wrote = true;
            }
            @Override public void close() {
                JakartaJsonGenerator.this.close();
            }
        };
    }

    @Override public void close() {
        try {
            if (!wrote) {
                throw new JsonGenerationException("Nothing generated");
            }
            if (state.parent != null) {
                throw new JsonGenerationException("Needs a writeEnd");
            }
            closeable.close();
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public void flush() {
        try {
            if (closeable instanceof Flushable) {
                ((Flushable)out).flush();
            }
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
        } else if (value instanceof JakartaJsonNumber) {
            write(((JakartaJsonNumber)value).numberValue());
        } else if (value instanceof JakartaJsonString) {
            write(((JakartaJsonString)value).getString());
        } else {
            throw new IllegalArgumentException();
        }
        return this;
    }
    @Override public JsonGenerator write(boolean value) {
        try {
            state.preValue();
            out.event(JsonStream.Event.booleanValue(value));
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(double value) {
        return write(Double.valueOf(value));
    }
    @Override public JsonGenerator write(int value) {
        return write(Integer.valueOf(value));
    }
    @Override public JsonGenerator write(long value) {
        return write(Long.valueOf(value));
    }
    JsonGenerator write(Number value) {
        try {
            state.preValue();
            out.event(JsonStream.Event.numberValue(value));
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String value) {
        try {
            state.preValue();
            out.event(JsonStream.Event.startString(value.length()));
            out.event(JsonStream.Event.stringData(value));
            out.event(JsonStream.Event.endString());
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String name, boolean value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(String name, double value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(String name, int value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(String name, long value) {
        return writeKey(name).write(value);
    }
    JsonGenerator write(String name, Number value) {
        writeKey(name);
        return write(value);
    }
    @Override public JsonGenerator write(String name, String value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(String name, BigDecimal value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(String name, BigInteger value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(String name, JsonValue value) {
        return writeKey(name).write(value);
    }
    @Override public JsonGenerator write(BigDecimal value) {
        try {
            state.preValue();
            out.event(JsonStream.Event.numberValue(value));
            state.preValue();
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(BigInteger value) {
        try {
            state.preValue();
            out.event(JsonStream.Event.numberValue(value));
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeEnd() {
        try {
            if (state.type == ARRAY) {
                state = state.parent;
                out.event(JsonStream.Event.endList());
            } else if (state.type == OBJECT) {
                state = state.parent;
                out.event(JsonStream.Event.endMap());
            } else {
                throw new JsonGenerationException("Wrong state");
            }
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeKey(String value) {
        try {
            state.preKey();
            out.event(JsonStream.Event.startString(value.length()));
            out.event(JsonStream.Event.stringData(value));
            out.event(JsonStream.Event.endString());
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeNull() {
        try {
            state.preValue();
            out.event(JsonStream.Event.nullValue());
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeNull(String name) {
        return writeKey(name).writeNull();
    }
    @Override public JsonGenerator writeStartArray() {
        try {
            state.preValue();
            out.event(JsonStream.Event.startList(-1));
            state = new State(state, ARRAY);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartArray(String name) {
        return writeKey(name).writeStartArray();
    }
    @Override public JsonGenerator writeStartObject() {
        try {
            state.preValue();
            out.event(JsonStream.Event.startMap(-1));
            state = new State(state, OBJECT);
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartObject(String name) {
        return writeKey(name).writeStartObject();
    }
}
