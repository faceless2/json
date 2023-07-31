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
        final String prefix;
        private boolean needkey;
        private int count;
        State(State parent, int type, String prefix) {
            this.parent = parent;
            this.type = type;
            if (parent == null) {
                this.prefix = prefix;
            } else if (parent.prefix == null) {
                this.prefix = null;
            } else {
                this.prefix = parent.prefix.equals("") ? "\n  " : parent.prefix + "  ";
            }
            needkey = type == OBJECT;
        }
        void prefix() throws IOException {
            if (prefix != null) {
                writer.write(prefix);
            }
        }
        void preKey() throws IOException {
            if (type == OBJECT && needkey) {
                if (count > 0) {
                    writer.writeComma();
                }
                needkey = false;
                count++;
                prefix();
            } else {
                throw new JsonGenerationException("Wrong state");
            }
        }
        void preValue() throws IOException {
            if (type == ROOT) {
                if (count > 0) {
                    throw new JsonGenerationException("Wrong state" + this);
                }
                count++;
            } else if (type == ARRAY) {
                if (count > 0) {
                    writer.writeComma();
                }
                count++;
                prefix();
            } else if (type == OBJECT) {
                if (needkey) {
                    throw new JsonGenerationException("Wrong state" + this);
                }
                needkey = true;
            }
        }
        public String toString() {
            return "{type="+(type==ROOT?"root":type==OBJECT?"object":"array")+" count="+count+" parent="+parent+"}";
        }
    }

    private State state;
    private final JsonWriter writer;
    private boolean wrote = false;
    private StringBuilder prefix;

    JSRJsonWriter(JsonWriter writer) {
        this.writer = writer;
        state = new State(null, ROOT, writer.options.isPretty() ? "\n" : null);
    }

    public javax.json.JsonWriter asWriter() {
        return new javax.json.JsonWriter() {
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
                JSRJsonWriter.this.write(value);
                wrote = true;
            }
            @Override public void close() {
                JSRJsonWriter.this.close();
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
            state.preValue();
            writer.writeBoolean(value);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(double value) {
        try {
            state.preValue();
            writer.writeNumber(value);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(int value) {
        try {
            state.preValue();
            writer.writeNumber(value);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(long value) {
        try {
            state.preValue();
            writer.writeNumber(value);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    JsonGenerator write(Number value) {
        try {
            state.preValue();
            writer.writeNumber(value);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(String value) {
        try {
            state.preValue();
            writer.writeString(value);
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
            writer.writeNumber(value);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator write(BigInteger value) {
        try {
            state.preValue();
            writer.writeNumber(value);
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
                state.prefix();
                writer.writeEndArray();
            } else if (state.type == OBJECT) {
                state = state.parent;
                state.prefix();
                writer.writeEndObject();
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
            state.preKey();
            writer.writeKey(name);
            if (state.prefix != null) {
                writer.write(" ");
            }
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeNull() {
        try {
            state.preValue();
            writer.writeNull();
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
            writer.writeStartArray();
            state = new State(state, ARRAY, null);
            wrote = true;
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
            writer.writeStartObject();
            state = new State(state, OBJECT, null);
            wrote = true;
            return this;
        } catch (IOException e) {
            throw new JsonGenerationException(e.getMessage(), e);
        }
    }
    @Override public JsonGenerator writeStartObject(String name) {
        return writeKey(name).writeStartObject();
    }
}
