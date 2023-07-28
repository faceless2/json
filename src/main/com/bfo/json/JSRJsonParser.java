package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import javax.json.*;
import javax.json.stream.*;
import javax.json.stream.JsonParser.Event;

class JSRJsonParser implements JsonParser, javax.json.JsonReader, JsonLocation {
    
    private static final Object COLON = ":", COMMA = ",";

    private final JsonReader jr;
    private State s;
    private Event lastevent, event;
    private Object lastvalue, value;
    private long line, col, tell;

    JSRJsonParser(Exception e) {
        this.event = Event.START_ARRAY; // arbitrary non-null;
        this.value = e;
        this.jr = null;
    }

    JSRJsonParser(JsonReader jr) {
        this.jr = jr;
        this.s = new State(null, ST_IV);
        donext();
    }

    @Override public boolean hasNext() {
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
            jr.reader.close();
        } catch (IOException e) {
            throw new JsonException(e.getMessage(), e);
        }
        event = lastevent = null;
        value = lastvalue = null;
    }

    @Override public String getString() {
        if (lastevent == Event.KEY_NAME || lastevent == Event.VALUE_STRING || lastevent == Event.VALUE_NUMBER) {
            return lastvalue.toString();
        }
        throw new IllegalStateException();
    }

    @Override public boolean isIntegralNumber() {
        if (lastevent == Event.VALUE_NUMBER) {
            return lastvalue instanceof Integer || lastvalue instanceof Long || lastvalue instanceof BigInteger;
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

    @Override public BigDecimal getBigDecimal() {
        if (lastevent == Event.VALUE_NUMBER) {
            return lastvalue instanceof BigDecimal ? (BigDecimal)lastvalue : lastvalue instanceof Double ? new BigDecimal((Double)lastvalue) : lastvalue instanceof Integer ? new BigDecimal((Integer)lastvalue) : lastvalue instanceof Long ? new BigDecimal((Long)lastvalue) : new BigDecimal((BigInteger)lastvalue);
        }
        throw new IllegalStateException();
    }

    Number getNumber() {
        if (lastevent == Event.VALUE_NUMBER) {
            return (Number)lastvalue;
        }
        throw new IllegalStateException();
    }

    private void donext() {
        if (jr.reader instanceof ContextReader) {
            line = ((ContextReader)jr.reader).line();
            col = ((ContextReader)jr.reader).column();
            tell = ((ContextReader)jr.reader).tell();
        }
//        System.out.println("## in donext");
        try {
            int c;
            Event event = null;
            Object value = null;
            Object t;
            while ((t = readToken(c = jr.next())) != null) {
//                System.out.println("TOKEN="+t+" STATE="+s);
                if (t == Event.END_ARRAY) {
                    if ((s.flags & ST_EA) != 0 || (jr.options.isAllowTrailingComma() && (s.flags & ST_AV) != 0)) {
                        s = s.parent;
                        s.flags = s.parent == null ? ST_IV : (s.flags & ST_EO) != 0 ? ST_MV : (s.flags & ST_EA) != 0 ? ST_AV : s.flags;
                        value = null;
                        event = (Event)t;
                    } else {
                        jr.unexpected("", c);
                    }
                    if ((s.flags & ST_AV) != 0) {
                        s.flags = ST_CM|ST_EA;
                    } else if ((s.flags & ST_MV) != 0) {
                        s.flags = ST_CM|ST_EO;
                    } else {
                        s.flags = 0;
                    }
                    break;
                } else if (t == Event.END_OBJECT) {
                    if ((s.flags & ST_EO) != 0 || (jr.options.isAllowTrailingComma() && (s.flags & ST_MV) != 0)) {
                        s = s.parent;
                        s.flags = s.parent == null ? ST_IV : (s.flags & ST_EO) != 0 ? ST_MV : (s.flags & ST_EA) != 0 ? ST_AV : s.flags;
                        value = null;
                        event = (Event)t;
                    } else {
                        jr.unexpected("", c);
                    }
                    if ((s.flags & ST_AV) != 0) {
                        s.flags = ST_CM|ST_EA;
                    } else if ((s.flags & ST_MV) != 0) {
                        s.flags = ST_CM|ST_EO;
                    } else {
                        s.flags = 0;
                    }
                    break;
                } else if (t == Event.START_ARRAY) {
                    if ((s.flags & (ST_AV|ST_MV|ST_IV)) != 0) {
                        s.flags = (s.flags & ST_AV) != 0 ? ST_CM|ST_EA : ST_CM|ST_EO;
                        s = new State(s, ST_AV|ST_EA);
                        value = null;
                        event = (Event)t;
                    } else {
                        jr.unexpected("", c);
                    }
                    break;
                } else if (t == Event.START_OBJECT) {
                    if ((s.flags & (ST_IV|ST_AV|ST_MV)) != 0) {
                        s.flags = (s.flags & ST_AV) != 0 ? ST_CM|ST_EA : ST_CM|ST_EO;
                        s = new State(s, ST_MK|ST_EO);
                        value = null;
                        event = (Event)t;
                    } else {
                        jr.unexpected("", c);
                    }
                    break;
                } else if (t == COMMA) {
                    if ((s.flags & (ST_CM|ST_EA)) == (ST_CM|ST_EA)) {            // comma or end-of-array
                        s.flags = ST_AV;
                    } else if ((s.flags & (ST_CM|ST_EO)) == (ST_CM|ST_EO)) {     // comma or end-of-object
                        s.flags = ST_MK;
                    } else {
                        jr.unexpected("", c);
                    }
                } else if (t == COLON) {
                    if ((s.flags & ST_CL) != 0) {
                        s.flags = ST_MV;
                    } else {
                        jr.unexpected("", c);
                    }
                } else if ((s.flags & (ST_AV|ST_MV|ST_IV)) != 0) {
                    if (t instanceof String) {
                        value = t;
                        event = Event.VALUE_STRING;
                    } else if (t instanceof Number) {
                        value = t;
                        event = Event.VALUE_NUMBER;
                    } else if (t instanceof Boolean) {
                        value = null;
                        event = t.equals(Boolean.TRUE) ? Event.VALUE_TRUE : Event.VALUE_FALSE;
                    } else if (t == Json.NULL) {
                        value = null;
                        event = Event.VALUE_NULL;
                    } else {
                        jr.unexpected("", c);
                    }
                    if ((s.flags & ST_AV) != 0) {
                        s.flags = ST_CM|ST_EA;
                    } else if ((s.flags & ST_MV) != 0) {
                        s.flags = ST_CM|ST_EO;
                    } else {
                        s.flags = 0;
                    }
                    break;
                } else if ((s.flags & ST_MK) != 0) {
                    if (t instanceof String) {
                        value = t;
                        event = Event.KEY_NAME;
                        s.flags = ST_CL;
                    } else {
                        jr.unexpected("", c);
                    }
                    break;
                } else {
                    jr.unexpected("", c);
                }
            }
            this.event = event;
            this.value = value;
        } catch (Exception e) {
            this.value = e;
        }
//        System.out.println("## out donext: e="+event+" v="+value+"/"+(value==null?null:value.getClass().getName())+" s="+s);
    }

    private Object readToken(int c) throws IOException {
        if (c < 0) {
            return null;
        }
        Object out = null;
        if (c == '[') {
            out = Event.START_ARRAY;
        } else if (c == '{') {
            out = Event.START_OBJECT;
        } else if (c == ']') {
            out = Event.END_ARRAY;
        } else if (c == '}') {
            out = Event.END_OBJECT;
        } else if (c == ',') {
            out = COMMA;
        } else if (c == ':') {
            out = COLON;
        } else if ((c <= '9' && c >= '0') || c == '-') {
            out = jr.readNumber(c);
        } else if (c == '"') {
            StringBuilder sb = new StringBuilder();
            jr.readString(jr.reader, c, Integer.MAX_VALUE, sb);
            out = sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.appendCodePoint(c);
            int maxlen = 5;
            while ((c=jr.reader.read())>=0 && (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != '{' && c != '}' && c != '[' && c != ']' && c != '(' && c != ')' && c != ',' && c != ':') && sb.length() < maxlen) {
                sb.appendCodePoint(c);
                jr.reader.mark(1);
            }
            jr.reader.reset();
            String s = sb.toString();
            if (s.equals("true")) {
                out = Boolean.TRUE;
            } else if (s.equals("false")) {
                out = Boolean.FALSE;
            } else if (s.equals("null")) {
                out = Json.NULL;
            } else {
                jr.unexpected("", s.codePointAt(0));
            }
        }
        return out;
    }

    private static final int ST_AV =   1; // arrayvalue
    private static final int ST_EA =   2; // endarray
    private static final int ST_EO =   4; // endobject
    private static final int ST_CM =   8; // array comma
    private static final int ST_CL =  16; // colon
    private static final int ST_IV =  32; // isolated value
    private static final int ST_MV =  64; // map value
    private static final int ST_MK = 128; // map key
    private static final int ST_LP = 256; // left-paren
    private static final int ST_RP = 512; // right-paren
    private static final int ST_TV =1024; // tag-value

    private static final class State {
        final State parent;
        int flags;
        State(State parent, int flags) {
            this.parent = parent;
            this.flags = flags;
        }
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if ((flags & ST_AV) != 0) sb.append("array-value ");
            if ((flags & ST_EA) != 0) sb.append("end-array ");
            if ((flags & ST_EO) != 0) sb.append("end-object ");
            if ((flags & ST_CM) != 0) sb.append("comma ");
            if ((flags & ST_CL) != 0) sb.append("colon ");
            if ((flags & ST_IV) != 0) sb.append("value ");
            if ((flags & ST_MV) != 0) sb.append("map-value ");
            if ((flags & ST_MK) != 0) sb.append("map-key ");
            if ((flags & ST_LP) != 0) sb.append("left-paren ");
            if ((flags & ST_RP) != 0) sb.append("right-paren ");
            if ((flags & ST_TV) != 0) sb.append("tag-value ");
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }
            return "{"+sb.toString()+" p="+parent+"}";
        }
    }

    @Override public long getLineNumber() {
        return line;
    }
    @Override public long getColumnNumber() {
        return col;
    }
    @Override public long getStreamOffset() {
        return tell;
    }
    @Override public JsonLocation getLocation() {
        return this;
    }

    @Override public JsonArray readArray() {
        return (JsonArray)readValue();
    }

    @Override public JsonObject readObject() {
        return (JsonObject)readValue();
    }

    @Override public JsonValue readValue() {
        return read();
    }
    @Override public JsonStructure read() {
        List<JsonStructure> stack = new ArrayList<JsonStructure>();
        String key = null;
        JsonStructure cursor = null;

        while (hasNext()) {
            JsonParser.Event event = next();
//            System.out.println("E="+event+" C="+cursor+" K="+key+" S="+stack);
            switch (event) {
                case START_ARRAY: 
                    if (cursor instanceof JsonArray) {
                        stack.add(cursor);
                        ((JSRJsonArray)cursor).list.add(cursor = new JSRJsonArray(new ArrayList<JsonValue>()));
                    } else if (cursor instanceof JsonObject) {
                        stack.add(cursor);
                        ((JSRJsonObject)cursor).map.put(key, cursor = new JSRJsonArray(new ArrayList<JsonValue>()));
                    } else {
                        cursor = new JSRJsonArray(new ArrayList<JsonValue>());
                    }
                    break;
                case START_OBJECT:
                    if (cursor instanceof JsonArray) {
                        stack.add(cursor);
                        ((JSRJsonArray)cursor).list.add(cursor = new JSRJsonObject(new LinkedHashMap<String,JsonValue>()));
                    } else if (cursor instanceof JsonObject) {
                        stack.add(cursor);
                        ((JSRJsonObject)cursor).map.put(key, cursor = new JSRJsonObject(new LinkedHashMap<String,JsonValue>()));
                    } else {
                        cursor = new JSRJsonObject(new LinkedHashMap<String,JsonValue>());
                    }
                    break;
                case END_ARRAY:
                case END_OBJECT:
                    if (stack.isEmpty()) {
                        return cursor;
                    } else {
                        cursor = stack.remove(stack.size() - 1);
                    }
                    break;
                case VALUE_TRUE:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(JsonValue.TRUE);
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, JsonValue.TRUE);
                    } else {
                        throw new IllegalStateException("Top-level " + event);
                    }
                    break;
                case VALUE_FALSE:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(JsonValue.FALSE);
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, JsonValue.FALSE);
                    } else {
                        throw new IllegalStateException("Top-level " + event);
                    }
                    break;
                case VALUE_NULL:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(JsonValue.NULL);
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, JsonValue.NULL);
                    } else {
                        throw new IllegalStateException("Top-level " + event);
                    }
                    break;
                case VALUE_STRING:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(new JSRJsonString(getString()));
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, new JSRJsonString(getString()));
                    } else {
                        throw new IllegalStateException("Top-level " + event);
                    }
                    break;
                case VALUE_NUMBER:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(new JSRJsonNumber(getNumber()));
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, new JSRJsonNumber(getNumber()));
                    } else {
                        throw new IllegalStateException("Top-level " + event);
                    }
                    break;
                case KEY_NAME:
                    key = getString();
                    break;
            }
        }
        throw new JsonParsingException("EOF", null, this);
    }

}
