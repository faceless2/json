package com.bfo.json;

import java.util.*;
import java.text.*;

public class Test2 {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN STRUCTURE TESTS -----");
        Json json = Json.read("{\"a\":{\"b\":[0,null,2]}}");
        String origstring = json.toString();;
        Json dup = json.duplicate();
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}");
        assert json.toString().equals(dup.toString());
        assert json.get("a").toString().equals("{\"b\":[0,null,2]}");
        assert json.get("a").type().equals("map");
        assert json.getPath("a.b").toString().equals("[0,null,2]");
        assert json.getPath("a.b") == json.get("a").get("b");
        assert json.getPath("a.b").type().equals("list");
        assert json.getPath("a.b.0").type().equals("number");
        assert json.getPath("a.b.0") == json.get("a").get("b").get(0);
        assert json.getPath("a.b.1").type().equals("null");
        assert json.getPath("a.b[0]") == json.get("a").get("b").get(0);
        assert json.getPath("a.b[0]").type().equals("number");
        assert json.getPath("a.b[1]").type().equals("null");
        assert Json.read("true").type().equals("boolean");
        assert Json.read("-12").type().equals("number");
        assert Json.read("-12.345").type().equals("number");
        assert json.getPath("a[b][0]").type().equals("number");
        assert json.getPath("a[b][1]").type().equals("null");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert json.hasPath("a.b[0]");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert !json.hasPath("a.b[1]");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert !json.hasPath("a.b[4]");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert json.get("a").parent() == json;
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}") : json.toString();;
        assert json.getPath("a.b").parent() == json.get("a");
        assert json.getPath("a.b[0]").parent() == json.getPath("a.b");
        assert "a.b[0]".equals(json.find(json.getPath("a.b[0]"))) : json.find(json.getPath("a.b[0]"));
        assert "a.b".equals(json.find(json.getPath("a.b"))) : json.find(json.getPath("a.b"));
        assert "a".equals(json.find(json.get("a"))) : json.find(json.get("a"));
        assert "b[0]".equals(json.get("a").find(json.getPath("a.b[0]"))) : json.getPath("a").find(json.getPath("b[0]"));
        assert json.find(Json.read("{}")) == null;
        assert "".equals(json.find(json)) : json.find(json);

        for (int i=0;i<11;i++) {
            byte[] b = new byte[i];
            for (int k=0;k<i;k++) {
                b[k] = 'a';
            }
            Json j = Json.read("{}");
            j.put("a", new Json(b, null));
            j = Json.read(j.toString());
            b = j.get("a").bufferValue().array();
            assert b.length == i : "Base64 roundtrip failed at " + i;
            for (int k=0;k<i;k++) {
                assert b[k] == 'a' : "Base64 roundtrip failed at " + i + ": j="+j+" a="+Arrays.toString(b);
            }
            String s = j.get("a").stringValue();
            while ((s.length() & 3) != 0) {
                s += "=";
            }
            j.put("a", new Json(s, null));
            byte[] b2 = j.get("a").bufferValue().array();
            assert Arrays.equals(b, b2) : "Base64 roundtrip failed at " + i + ": " + s + " " + Arrays.toString(b)+" "+Arrays.toString(b2);
            System.out.println("* base64 round-trip at " + i);
        }

        /*
        assert json.eval("a") == json.get("a");
        assert json.eval("a.b") == json.get("a.b");
        assert json.eval("a.b[0]") == json.get("a.b[0]");
        assert json.eval("$..*") == json.get("a");
        assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}");
        assert json.evalAll("$..*").size() == 5 : json.evalAll("$..*");
        */
        Json q;
        Json t = json.getPath("a.b[0]");
        Json tp = t.parent();
        assert t.type().equals("number") : t.type();
        assert t.intValue() == 0 : t.intValue();
        assert t.parent() == json.getPath("a.b");
        assert (q=json.putPath("a.b", 1)).toString().equals("[0,null,2]") : q.toString();
        assert json.toString().equals("{\"a\":{\"b\":1}}") : json.toString();
        assert json.getPath("a.b").parent() == json.get("a");
        assert t != json.getPath("a.b[0]");
        assert t.parent() == tp;
        assert tp.parent() == null;
        assert (q=json.putPath("a[\"x.y\"]", 2)) == null : q.toString();
        assert json.toString().equals("{\"a\":{\"b\":1,\"x.y\":2}}") : json.toString();
        assert "a[\"x.y\"]".equals(json.find(json.getPath("a[\"x.y\"]"))) : json.find(json.getPath("a[\"x.y\"]"));
        assert json.getPath("a.x.y") == null;
        json.get("a").putPath("b.c.d", Json.read("123456789123456789"));
        json.putPath("a.b", false);
        json.putPath("a.b", false);
        json.put("a", Json.read("123456789123456789"));
        assert json.toString().equals("{\"a\":123456789123456789}") : json.toString();
        assert json.get("a").type().equals("number");
        assert (q=json.putPath("a[3][3]", true)).toString().equals("123456789123456789") : q;
        assert json.toString().equals("{\"a\":[null,null,null,[null,null,null,true]]}") : json.toString();
        assert dup.toString().equals(origstring);
        StringBuilder sb = new StringBuilder();
        (json=Json.read("{\"a\":1, \"z\":2, \"n\":3, \"g\":4, \"t\":5, \"e\":6}")).write(new JsonWriter().setOutput(sb).setSorted(true));
        assert sb.toString().equals("{\"a\":1,\"e\":6,\"g\":4,\"n\":3,\"t\":5,\"z\":2}") : sb.toString();

        json = Json.read("{}");
        json.putPath("a.b.c", true);
        json.getPath("a.b").remove("c");
        assert(json.getPath("a.b").parent() == json.get("a"));

        json = Json.read("{\"a\":true}");
        assert (q=json.putPath("b.a", json.get("a"))) == null : q;
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

        // Stupid keys
        json = Json.read("{\"a\":{\"x.y\":1}}");
        assert json.getPath("a.x.y") == null;
        assert json.getPath("a[\"x.y\"]") != null;
        try { assert json.getPath("a[x.y]") == null; } catch (IllegalArgumentException e) {}
        try { assert json.getPath("a.\"x.y\"") == null; } catch (IllegalArgumentException e) {}
        try { json.putPath("a[]", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}
        try { json.putPath("a.", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}
        try { json.putPath(".a", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}
//        try { json.put("a[[]", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}    // valid?
        try { json.putPath("a[", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}
        try { json.putPath("\"a", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}
        try { json.putPath("a\"", 1); assert false : json.toString(); } catch (IllegalArgumentException e) {}
        json.putPath("\"\"", false);
        assert json.toString().equals("{\"a\":{\"x.y\":1},\"\":false}") : json.toString();
        json.putPath("\"\"\"", false);
        assert json.toString().equals("{\"a\":{\"x.y\":1},\"\":false,\"\\\"\":false}") : json.toString();
        json.put("\u0000", false);
        assert json.toString().equals("{\"a\":{\"x.y\":1},\"\":false,\"\\\"\":false,\"\\u0000\":false}") : json.toString();
        json.putPath("\"\u0000\"", true);
        assert json.toString().equals("{\"a\":{\"x.y\":1},\"\":false,\"\\\"\":false,\"\\u0000\":true}") : json.toString();

        // Loops keys
        json = Json.read("{\"a\":{\"b\":{\"c\":1}}}");
        q = json.get("a").get("b").get("c");
        try { q.put("d", json.getPath("a.b.c")); assert false : json.toString(); } catch (IllegalArgumentException e) {}      // Add to itself
        try { q.put("d", json.getPath("a.b")); assert false : json.toString(); } catch (IllegalArgumentException e) {}        // Add parent to child
        try { q.put("d", json.get("a")); assert false : json.toString(); } catch (IllegalArgumentException e) {}        // Add grandparent to child
        try { q.put("d", json); assert false : json.toString(); } catch (IllegalArgumentException e) {}        // Add great-grandparent to child


        System.out.println("----- END STRUCTURE TESTS -----");

        System.out.println("----- BEGIN FACTORY TESTS -----");
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
        assert date.equals(json.get("a").objectValue()) : json.get("a").objectValue();
        System.out.println("----- END FACTORY TESTS -----");

        System.out.println("----- BEGIN EVENT TESTS -----");
        sb.setLength(0);
        json = Json.read("{\"a\":{\"b\":[0,null,2]}}");
        json.addListener(new JsonListener() {
            public void jsonEvent(Json root, JsonEvent event) {
                if (event.after == null) {
                    sb.append("REMOVE "+root.find(event.before)+"="+event.before+"\n");
                } else if (event.before == null) {
                    sb.append("ADD "+root.find(event.after)+"="+event.after+"\n");
                } else {
                    sb.append("CHANGE "+root.find(event.after)+"="+event.before+" -> "+event.after+"\n");
                }
            }
        });

        json.putPath("a.b[1]", 1);
        json.putPath("a.b[3]", 3);
        json.get("a").put("c", Json.read("{\"d\":true,\"e\":false}"));
        json.get("a").put("b", false);
        json.get("a").remove("c");
        assert sb.toString().equals("CHANGE a.b[1]=null -> 1\nADD a.b[3]=3\nADD a.c={\"d\":true,\"e\":false}\nCHANGE a.b=[0,1,2,3] -> false\nREMOVE a.c={\"d\":true,\"e\":false}\n");
        System.out.println("----- END EVENT TESTS -----");
    }
}
