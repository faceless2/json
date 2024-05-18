package com.bfo.json;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;
import java.math.*;
import javax.json.*;
import javax.json.stream.*;
import javax.json.stream.JsonParser.Event;

class JSRJsonReader implements javax.json.JsonReader {

    private final JsonParser parser;
    private boolean done;

    JSRJsonReader(JsonParser parser) {
        this.parser = parser;
    }
    
    @Override public void close() {
        parser.close();
    }

    @Override public JsonArray readArray() {
        return (JsonArray)readValue();
    }

    @Override public JsonObject readObject() {
        return (JsonObject)readValue();
    }

    @Override public JsonStructure read() {
        return (JsonStructure)readValue();
    }

    @Override public JsonValue readValue() {
        if (done) {
            throw new IllegalStateException("Already read");
        }
        done = true;
        return read(null);
    }

    JsonValue read(JsonParser.Event event) {
        List<JsonStructure> stack = new ArrayList<JsonStructure>();
        String key = null;
        JsonStructure cursor = null;

        while (parser.hasNext()) {
            if (event == null) {
                event = parser.next();
            }
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
                        return JsonValue.TRUE;
                    }
                    break;
                case VALUE_FALSE:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(JsonValue.FALSE);
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, JsonValue.FALSE);
                    } else {
                        return JsonValue.FALSE;
                    }
                    break;
                case VALUE_NULL:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(JsonValue.NULL);
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, JsonValue.NULL);
                    } else {
                        return JsonValue.NULL;
                    }
                    break;
                case VALUE_STRING:
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(new JSRJsonString(parser.getString()));
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, new JSRJsonString(parser.getString()));
                    } else {
                        return new JSRJsonString(parser.getString());
                    }
                    break;
                case VALUE_NUMBER:
                    Number n;
                    if (parser instanceof JSRJsonParser) {
                        n = ((JSRJsonParser)parser).getNumber();
                    } else if (parser.isIntegralNumber()) {
                        long l = parser.getLong();
                        n = l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? Integer.valueOf((int)l) : Long.valueOf(l);
                    } else {
                        n = parser.getBigDecimal();
                    }
                    if (cursor instanceof JsonArray) {
                        ((JSRJsonArray)cursor).list.add(new JSRJsonNumber(n));
                    } else if (cursor instanceof JsonObject) {
                        ((JSRJsonObject)cursor).map.put(key, new JSRJsonNumber(n));
                    } else {
                        return new JSRJsonNumber(n);
                    }
                    break;
                case KEY_NAME:
                    key = parser.getString();
                    break;
            }
            event = null;
        }
        throw new JsonParsingException("EOF", null, parser.getLocation());
    }

}
