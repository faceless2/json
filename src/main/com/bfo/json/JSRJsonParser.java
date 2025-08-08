package com.bfo.json;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;
import java.math.*;
import javax.json.*;
import javax.json.stream.*;
import javax.json.stream.JsonParser.Event;

class JSRJsonParser implements JsonParser, JsonLocation {

    private static final int MODE_ROOT = 0, MODE_LIST = 1, MODE_MAPKEY = 2, MODE_MAPVAL = 3;
    private final JsonReader in;
    private final InputStream stream;
    private final Reader reader;
    private JsonParser.Event event, lastevent;
    private Object value, lastvalue;
    private State state;

    JSRJsonParser(JsonReader in, InputStream stream) {
        this.in = (JsonReader)in.setInput(stream);
        this.stream = stream;
        this.reader = null;
        donext();
    }

    JSRJsonParser(JsonReader in, Reader reader) {
        this.in = (JsonReader)in.setInput(reader);
        this.stream = null;
        this.reader = reader;
        donext();
    }

    private static class State {
        final State parent;
        int mode;
        State(State parent, int mode) {
            this.parent = parent;
            this.mode = mode;
        }
    }

    @Override public boolean hasNext() {
        if (value instanceof JsonParsingException) {
            throw new JsonParsingException("next failed", (JsonParsingException)value, ((JsonParsingException)value).getLocation());
        }
        return event != null;
    }

    @Override public Event next() throws JsonException, JsonParsingException {
        if (value instanceof JsonException) {
            throw (JsonException)value;
        } else if (value instanceof RuntimeException) {
            throw new JsonParsingException(((Exception)value).getMessage(), (Exception)value, this);
        } else if (value instanceof Exception) {
            throw new JsonException(((Exception)value).getMessage(), (Exception)value);
        } else if (event == null) {
            throw new NoSuchElementException();
        }
        this.lastevent = this.event;
        this.lastvalue = this.value;
        donext();
        return lastevent;
    }

    @Override public void close() {
        try {
            event = lastevent = null;
            value = lastvalue = null;
            in.close();
        } catch (IOException e) {
            throw new JsonException("IOException", e);
        }
    }

    @Override public BigDecimal getBigDecimal() {
        if (lastvalue != null) {
            return lastvalue instanceof BigDecimal ? (BigDecimal)lastvalue : lastvalue instanceof Double ? new BigDecimal((Double)lastvalue) : lastvalue instanceof Integer ? new BigDecimal((Integer)lastvalue) : lastvalue instanceof Long ? new BigDecimal((Long)lastvalue) : new BigDecimal((BigInteger)lastvalue);
        }
        throw new IllegalStateException();
    }

    @Override public boolean isIntegralNumber() {
        if (lastevent == Event.VALUE_NUMBER) {
            return lastvalue instanceof Integer || lastvalue instanceof Long || lastvalue instanceof BigInteger;
        }
        return false;
    }

    @Override public String getString() {
        if (lastevent == Event.KEY_NAME || lastevent == Event.VALUE_STRING || lastevent == Event.VALUE_NUMBER) {
            return lastvalue.toString();
        }
        throw new IllegalStateException();
    }

    @Override public int getInt() {
        if (lastevent == Event.VALUE_NUMBER) {
            return ((Number)lastvalue).intValue();
        }
        throw new IllegalStateException();
    }

    @Override public long getLong() {
        if (lastevent == Event.VALUE_NUMBER) {
            return ((Number)lastvalue).longValue();
        }
        throw new IllegalStateException();
    }

    @Override public JsonLocation getLocation() {
        return this;
    }

    @Override public long getColumnNumber() {
        return in.getColumnNumber();
    }

    @Override public long getLineNumber() {
        return in.getLineNumber();
    }

    @Override public long getStreamOffset() {
        return in.getByteNumber();
    }

    //----------------------------------------------------------------

    private void donext() {
        try {
            if (!in.hasNext()) {
                event = null;
                return;
            }
            JsonStream.Event e = in.next();
            if (e == null) {
                value = new EOFException();
            } else {
                final int type = e.type();
                if (type == JsonStream.Event.TYPE_PRIMITIVE) {
                    if (e.numberValue() != null) {
                        value = e.numberValue();
                        event = JsonParser.Event.VALUE_NUMBER;
                    } else if (Boolean.TRUE.equals(e.booleanValue())) {
                        value = null;
                        event = JsonParser.Event.VALUE_TRUE;
                    } else if (Boolean.FALSE.equals(e.booleanValue())) {
                        value = null;
                        event = JsonParser.Event.VALUE_FALSE;
                    } else if (e.isNull()) {
                        value = null;
                        event = JsonParser.Event.VALUE_NULL;
                    } else if (e.stringValue() != null) {
                        value = e.stringValue().toString();
                        event = JsonParser.Event.VALUE_STRING;
                    } else {
                        value = new IllegalStateException("Unknown primitive data " + e.value());
                    }
                    if (state.mode == MODE_MAPKEY) {
                        state.mode = MODE_MAPVAL;
                    } else if (state.mode == MODE_MAPVAL) {
                        state.mode = MODE_MAPKEY;
                    }
                } else if (type == JsonStream.Event.TYPE_STRING_START) {
                    CharSequence seq = null;
                    while (in.hasNext() && (e=in.next()).type() == JsonStream.Event.TYPE_STRING_DATA) {
                        if (e.stringValue() != null) {
                            if (seq == null) {
                                seq = e.stringValue().toString();
                            } else {
                                if (!(seq instanceof StringBuilder)) {
                                    seq = new StringBuilder(seq);
                                }
                                ((StringBuilder)seq).append(e.stringValue());
                            }
                        } else {
                            // We won't see Reader from the JsonParser
                            value = new IllegalStateException("Unknown string data " + e.value());
                        }
                        e = null;
                    }
                    if (e == null) {
                        value = new EOFException();
                    } else if (e.type() == JsonStream.Event.TYPE_STRING_END) {
                        value = seq.toString();
                        if (state.mode == MODE_MAPKEY) {
                            event = JsonParser.Event.KEY_NAME;
                            state.mode = MODE_MAPVAL;
                        } else if (state.mode == MODE_MAPVAL) {
                            event = JsonParser.Event.VALUE_STRING;
                            state.mode = MODE_MAPKEY;
                        } else if (state.mode == MODE_LIST) {
                            event = JsonParser.Event.VALUE_STRING;
                        }
                    } else {
                        value = new IllegalStateException("Unexpected event " + e);
                    }
                } else if (type == JsonStream.Event.TYPE_MAP_START) {
                    value = null;
                    state = new State(state, MODE_MAPKEY);
                    event = JsonParser.Event.START_OBJECT;
                } else if (type == JsonStream.Event.TYPE_MAP_END) {
                    value = null;
                    state = state.parent;
                    event = JsonParser.Event.END_OBJECT;
                } else if (type == JsonStream.Event.TYPE_LIST_START) {
                    value = null;
                    state = new State(state, MODE_LIST);
                    event = JsonParser.Event.START_ARRAY;
                } else if (type == JsonStream.Event.TYPE_LIST_END) {
                    value = null;
                    state = state.parent;
                    event = JsonParser.Event.END_ARRAY;
                } else {
                    value = new IllegalStateException("Unexpected event " + e);
                }
            }
        } catch (IOException e) {
            value = e;
        }
    }

    //----------------------------------------------------------------

    @Override public JsonArray getArray() {
        if (lastevent != Event.START_ARRAY) {
            throw new IllegalStateException("Not an array");
        }
        return (JsonArray)new JSRJsonReader(this).read(lastevent);
    }

    @Override public Stream<JsonValue> getArrayStream() {
        if (lastevent != Event.START_ARRAY) {
            throw new IllegalStateException("Not an array");
        }
        Spliterator<JsonValue> spliterator = new Spliterators.AbstractSpliterator<JsonValue>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override public Spliterator<JsonValue> trySplit() {
                return null;
            }
            @Override public boolean tryAdvance(Consumer<? super JsonValue> action) {
                if (action == null) {
                    throw new NullPointerException();
                }
                if (!hasNext() || next() == JsonParser.Event.END_ARRAY) {
                    return false;
                }
                action.accept(getValue());
                return true;
            }
        };
        return StreamSupport.stream(spliterator, false);
    }

    @Override public JsonObject getObject() {
        if (lastevent != Event.START_OBJECT) {
            throw new IllegalStateException("Not an object");
        }
        return (JsonObject)new JSRJsonReader(this).read(lastevent);
    }

    @Override public Stream<Map.Entry<String,JsonValue>> getObjectStream() {
        if (lastevent != Event.START_OBJECT) {
            throw new IllegalStateException("Not an array");
        }
        Spliterator<Map.Entry<String,JsonValue>> spliterator = new Spliterators.AbstractSpliterator<Map.Entry<String,JsonValue>>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override public Spliterator<Map.Entry<String,JsonValue>> trySplit() {
                return null;
            }
            @Override public boolean tryAdvance(Consumer<? super Map.Entry<String,JsonValue>> action) {
                if (action == null) {
                    throw new NullPointerException();
                }
                if (!hasNext()) {
                    return false;
                }
                JsonParser.Event e = next();
                if (e == Event.END_OBJECT) {
                    return false;
                } else if (e != Event.KEY_NAME) {
                    throw new IllegalStateException("Not a key");
                }
                String key = getString();
                next();
                JsonValue value = getValue();
                action.accept(new AbstractMap.SimpleImmutableEntry<String,JsonValue>(key, value));
                return true;
            }
        };
        return StreamSupport.stream(spliterator, false);
    }

    @Override public JsonValue getValue() {
        return new JSRJsonReader(this).read(lastevent);
    }

    @Override public Stream<JsonValue> getValueStream() {
        if (lastevent != Event.VALUE_STRING && lastevent != Event.VALUE_NUMBER && lastevent != Event.VALUE_TRUE && lastevent != Event.VALUE_FALSE && lastevent != Event.VALUE_NULL) {
            throw new IllegalStateException("Not a value");
        }
        Spliterator<JsonValue> spliterator = new Spliterators.AbstractSpliterator<JsonValue>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override public Spliterator<JsonValue> trySplit() {
                return null;
            }
            @Override public boolean tryAdvance(Consumer<? super JsonValue> action) {
                if (action == null) {
                    throw new NullPointerException();
                }
                if (!hasNext()) {
                    return false;
                }
                next();
                action.accept(getValue());
                return true;
            }
        };
        return StreamSupport.stream(spliterator, false);
    }

    @Override public void skipArray() {
        if (lastevent != Event.START_ARRAY) {
            throw new IllegalStateException("Not an array");
        }
        int depth = 1;
        do {
            Event e = next();
            if (e == Event.START_ARRAY) {
                depth++;
            } else if (e == Event.END_ARRAY) {
                depth--;
            }
        } while (depth != 0);
    }

    @Override public void skipObject() {
        if (lastevent != Event.START_OBJECT) {
            throw new IllegalStateException("Not an object");
        }
        int depth = 1;
        do {
            Event e = next();
            if (e == Event.START_OBJECT) {
                depth++;
            } else if (e == Event.END_OBJECT) {
                depth--;
            }
        } while (depth != 0);
    }

    Number getNumber() {
        if (lastevent == Event.VALUE_NUMBER) {
            return (Number)lastvalue;
        }
        throw new IllegalStateException();
    }

}
