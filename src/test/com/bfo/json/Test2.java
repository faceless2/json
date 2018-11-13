package com.bfo.json;

import java.util.*;
import java.text.*;

public class Test2 {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN STRUCTURE TESTS -----");
        Json json = Json.read("{\"a\":{\"b\":[0,null,2]}}");
        String origstring = json.toString();;
        Json dup = json.duplicate();
        json.get("a").addListener(new JsonListener() {
            public void jsonEvent(JsonEvent event) { 
                System.out.println(event);
            }
        });
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}");
        assert json.toString().equals(dup.toString());
        assert json.get("a").toString().equals("{\"b\":[0,null,2]}");
        assert json.get("a").type().equals("map");
        assert json.get("a.b").toString().equals("[0,null,2]");
        assert json.get("a.b").type().equals("list");
        assert json.get("a.b.0").type().equals("number");
        assert json.get("a.b.1").type().equals("null");
        assert json.get("a.b[0]").type().equals("number");
        assert json.get("a.b[1]").type().equals("null");
        assert json.read("true").type().equals("boolean");
        assert json.read("-12").type().equals("number");
        assert json.read("-12.345").type().equals("number");
        assert json.get("a[b][0]").type().equals("number");
        assert json.get("a[b][1]").type().equals("null");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert json.has("a.b[0]");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert !json.has("a.b[1]");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert !json.has("a.b[4]");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert json.get("a").parent() == json;
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert json.get("a.b").parent() == json.get("a");
        assert json.get("a.b[0]").parent() == json.get("a.b");
        assert json.get("a.b[0]").path(json).equals("a.b[0]");
        assert json.get("a.b").path(json).equals("a.b");
        assert json.get("a").path(json).equals("a");
        assert json.get("a.b[0]").path(json.get("a")).equals("b[0]") : json.get("a.b[0]").path(json.get("a"));
        assert json.get("a.b").path(json.get("a")).equals("b");
        assert json.read("{}").path(json) == null;
        assert "".equals(json.path(json)) : json.path(json);
        /*
        assert json.eval("a") == json.get("a");
        assert json.eval("a.b") == json.get("a.b");
        assert json.eval("a.b[0]") == json.get("a.b[0]");
        assert json.eval("$..*") == json.get("a");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}");
        assert json.evalAll("$..*").size() == 5 : json.evalAll("$..*");
        */
        Json q;
        Json t = json.get("a.b[0]");
        Json tp = t.parent();
        assert t.type().equals("number") : t.type();
        assert t.intValue() == 0 : t.intValue();
        assert t.parent() == json.get("a.b");
        assert (q=json.put("a.b", 1)).toString().equals("[0,null,2]") : q.toString();
        assert json.toString().equals("{\"a\":{\"b\":1}}") : json.toString();
        assert json.get("a.b").parent() == json.get("a");
        assert t != json.get("a.b[0]");
        assert t.parent() == tp;
        assert tp.parent() == null;
        assert (q=json.put("a[\"x.y\"]", 2)) == null : q.toString();
        assert json.toString().equals("{\"a\":{\"b\":1,\"x.y\":2}}") : json.toString();
        assert json.get("a[\"x.y\"]").path(json).equals("a[\"x.y\"]") : json.get("a[\"x.y\"]").path(json);
        assert json.get("a[\"x.y\"]").path(json).equals("a[\"x.y\"]") : json.get("a[\"x.y\"]").path(json);
        assert json.get("a.x.y") == null;
        json.get("a").put("b.c.d", Json.read("123456789123456789"));
        json.put("a.b", false);
        json.put("a.b", false);
        json.put("a", Json.read("123456789123456789"));
        assert json.toString().equals("{\"a\":123456789123456789}") : json.toString();
        assert json.get("a").type().equals("number");
        assert (q=json.put("a[3][3]", true)).toString().equals("123456789123456789") : q;
        assert json.toString().equals("{\"a\":[null,null,null,[null,null,null,true]]}") : json.toString();
        assert dup.toString().equals(origstring);
        JsonWriteOptions options = new JsonWriteOptions().setSorted(true);
        StringBuilder sb = new StringBuilder();
        assert (json=Json.read("{\"a\":1, \"z\":2, \"n\":3, \"g\":4, \"t\":5, \"e\":6}")).write(sb, options).toString().equals("{\"a\":1,\"e\":6,\"g\":4,\"n\":3,\"t\":5,\"z\":2}") : sb.toString();

        json = Json.read("{}");
        json.put("a.b.c", true);
        json.get("a.b").remove("c");
        assert(json.get("a.b").parent() == json.get("a"));

        json = Json.read("{\"a\":true}");
        assert (q=json.put("b.a", json.get("a"))) == null : q;
        assert json.toString().equals("{\"a\":true,\"b\":{\"a\":true}}") : json.toString();

        json = Json.read("{\"a\":\"100\"}");
        assert(json.get("a").stringValue().equals("100"));
        assert(json.get("a").intValue() == 100);
        assert(json.get("a").longValue() == 100);
        assert(json.get("a").doubleValue() == 100);
        assert(json.get("a").booleanValue());

        json = Json.read("{\"a\":\"100.123\"}");
        assert(json.get("a").stringValue().equals("100.123"));
        try { json.get("a").intValue(); assert(false) : "not an int"; } catch (ClassCastException e) {}
        try { json.get("a").longValue(); assert(false) : "not a long"; } catch (ClassCastException e) {}
        assert(json.get("a").doubleValue() == 100.123);
        assert(json.get("a").booleanValue());

        json = Json.read("{\"a\":2147483648}");
        assert(json.get("a").intValue() == Integer.MIN_VALUE) : "" + json.get("a").intValue();
        assert(json.get("a").longValue() == 2147483648l);

        json = Json.read("{\"a\":{}}");
        try { json.get("a").intValue(); assert(false) : "not an int"; } catch (ClassCastException e) {}
        try { json.get("a").longValue(); assert(false) : "not a long"; } catch (ClassCastException e) {}
        try { json.get("a").doubleValue(); assert(false) : "not a double"; } catch (ClassCastException e) {}
        try { json.get("a").booleanValue(); assert(false) : "not a bool"; } catch (ClassCastException e) {}
        json = Json.read("[]");
        json = Json.read("{\"a\":[]}");
        try { json.get("a").intValue(); assert(false) : "not an int"; } catch (ClassCastException e) {}
        try { json.get("a").longValue(); assert(false) : "not a long"; } catch (ClassCastException e) {}
        try { json.get("a").doubleValue(); assert(false) : "not a double"; } catch (ClassCastException e) {}
        try { json.get("a").booleanValue(); assert(false) : "not a bool"; } catch (ClassCastException e) {}
        json = Json.read("{\"a\":null}");
        try { json.get("a").intValue(); assert(false) : "not an int"; } catch (ClassCastException e) {}
        try { json.get("a").longValue(); assert(false) : "not a long"; } catch (ClassCastException e) {}
        try { json.get("a").doubleValue(); assert(false) : "not a double"; } catch (ClassCastException e) {}
        try { json.get("a").booleanValue(); assert(false) : "not a bool"; } catch (ClassCastException e) {}

        Date date = new Date(1542120121000l);
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String sdate = "D:" + df.format(date);
        JsonFactory factory = new JsonFactory() {
            public Json toJson(Object o) {
                if (o instanceof Date) {
                    return new Json("D:"+df.format((Date)o));
                }
                return null;
            }
            public Object fromJson(Json o) {
                if (o.type().equals("string") && o.stringValue().startsWith("D:")) {
                    try {
                        return df.parse(o.stringValue().substring(2));
                    } catch (Exception e) {}
                }
                return null;
            }
        };
        json = new Json(Collections.singletonMap("a", date), factory);
        assert json.get("a").stringValue().equals(sdate) : json.get("a");
        assert date.equals(json.get("a").objectValue(factory)) : json.get("a").objectValue(factory);
        System.out.println("----- END STRUCTURE TESTS -----");
    }
}
