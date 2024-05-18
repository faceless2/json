package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;
import javax.json.*;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.stream.*;
import javax.json.stream.JsonParser.Event;

public class TestJSR {
    public static void main(String[] s) throws Exception {
        System.out.println("----- BEGIN JSR TESTS -----");
        test1();
        test2("{\"name\":\"Falco\",\"age\":3,\"biteable\":false}", "[START_OBJECT, KEY_NAME, name, VALUE_STRING, Falco, KEY_NAME, age, VALUE_NUMBER, 3, KEY_NAME, biteable, VALUE_FALSE, END_OBJECT]");
        test2("{\"firstName\": \"John\", \"lastName\": \"Smith\", \"age\": 25, \"phoneNumber\": [ { \"type\": \"home\", \"number\": \"212-555-1234\" }, { \"type\": \"fax\", \"number\": \"646-555-4567\" } ] }", "[START_OBJECT, KEY_NAME, firstName, VALUE_STRING, John, KEY_NAME, lastName, VALUE_STRING, Smith, KEY_NAME, age, VALUE_NUMBER, 25, KEY_NAME, phoneNumber, START_ARRAY, START_OBJECT, KEY_NAME, type, VALUE_STRING, home, KEY_NAME, number, VALUE_STRING, 212-555-1234, END_OBJECT, START_OBJECT, KEY_NAME, type, VALUE_STRING, fax, KEY_NAME, number, VALUE_STRING, 646-555-4567, END_OBJECT, END_ARRAY, END_OBJECT]");
        test3();
        try {
            JsonObject json = Json.createObjectBuilder().add("name", "Falco").build();
            json.put("key", Json.createValue(1));
            assert false : "JsonObject is modifiable";
        } catch (Exception e) {}
        try {
            JsonArray json = Json.createArrayBuilder().add("Falco").build();
            json.remove(0);
            assert false : "JsonArray is modifiable";
        } catch (Exception e) {}
        String q = "{\"name\":\"Falco\",\"age\":3,\"biteable\":false,\"null\":[null,2]}";
        com.bfo.json.Json j = new com.bfo.json.Json(Json.createReader(new StringReader(q)).read());
        assert q.toString().equals(q);


        System.out.println("----- END JSR TESTS -----");
    }

    static void test1() {
        System.out.println("Reading/writing JSON");
        // Create Json and print
        JsonObject json = Json.createObjectBuilder()
         .add("name", "Falco")
         .add("age", BigDecimal.valueOf(3))
         .add("biteable", Boolean.FALSE).build();
        String result = json.toString();
        JsonReader jsonReader = Json.createReader(new StringReader("{\"name\":\"Falco\",\"age\":3,\"biteable\":false}"));
        JsonObject jobj = jsonReader.readObject();
        assert result.equals(jobj.toString()) : "OLD="+result+" JSR="+jobj;
    }

    static void test2(String in, String test) {
        System.out.println("Parsing JSON");
        final JsonParser parser = Json.createParser(new StringReader(in));
        List<String> out = new ArrayList<String>();
        while (parser.hasNext()) {
            final Event event = parser.next();
            out.add(event.toString());
            switch (event) {
                case KEY_NAME:
                    out.add(parser.getString());
                    break;
                case VALUE_STRING:
                    out.add(parser.getString());
                    break;
                case VALUE_NUMBER:
                    out.add(parser.getBigDecimal().toString());
                    break;
            }
        }
        parser.close();
        assert out.toString().equals(test) : out + " != " + test;
    }

    static void test3() {
        JsonObject json = Json.createObjectBuilder()
         .add("firstName", "John")
         .add("lastName", "Smith")
         .add("age", 25)
         .add("address", Json.createObjectBuilder()
             .add("streetAddress", "21 2nd Street")
             .add("city", "New York")
             .add("state", "NY")
             .add("postalCode", "10021"))
         .add("phoneNumber", Json.createArrayBuilder()
             .add(Json.createObjectBuilder()
                 .add("type", "home")
                 .add("number", "212-555-1234"))
             .add(Json.createObjectBuilder()
                 .add("type", "fax")
                 .add("number", "646-555-4567")))
         .build();
         String s = json.toString();
         assert s.equals("{\"firstName\":\"John\",\"lastName\":\"Smith\",\"age\":25,\"address\":{\"streetAddress\":\"21 2nd Street\",\"city\":\"New York\",\"state\":\"NY\",\"postalCode\":\"10021\"},\"phoneNumber\":[{\"type\":\"home\",\"number\":\"212-555-1234\"},{\"type\":\"fax\",\"number\":\"646-555-4567\"}]}");
         StringWriter sw = new StringWriter();
         Json.createWriter(sw).write(json);
         assert s.equals(sw.toString());
    }


}
