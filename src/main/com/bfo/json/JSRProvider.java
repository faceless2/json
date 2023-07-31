package com.bfo.json;

import java.io.*;
import java.math.*;
import java.util.*;
import javax.json.*;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.spi.*;
import javax.json.stream.*;

/**
 * <p>
 * The JsonProvider implementation that provider the JSON-P (JSR374) implementation.
 * See {@link JsonProvider} for documentation relating to this class.
 * </p>
 */
public class JSRProvider extends JsonProvider {
    @Override public JsonArrayBuilder createArrayBuilder() {
        return createBuilderFactory(null).createArrayBuilder();
    }
    @Override public JsonBuilderFactory createBuilderFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonGenerator createGenerator(OutputStream out) {
        return createGeneratorFactory(null).createGenerator(out);
    }
    @Override public JsonGenerator createGenerator(Writer writer) {
        return createGeneratorFactory(null).createGenerator(writer);
    }
    @Override public JsonGeneratorFactory createGeneratorFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonObjectBuilder createObjectBuilder() {
        return createBuilderFactory(null).createObjectBuilder();
    }
    @Override public JsonParser createParser(InputStream in) {
        return createParserFactory(null).createParser(in);
    }
    @Override public JsonParser createParser(Reader reader) {
        return createParserFactory(null).createParser(reader);
    }
    @Override public JsonParserFactory createParserFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public javax.json.JsonReader createReader(InputStream in) {
        return createReaderFactory(null).createReader(in);
    }
    @Override public javax.json.JsonReader createReader(Reader reader) {
        return createReaderFactory(null).createReader(reader);
    }
    @Override public JsonReaderFactory createReaderFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonNumber createValue(double value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonNumber createValue(int value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonNumber createValue(long value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonString createValue(String value) {
        return new JSRJsonString(value);
    }
    @Override public JsonNumber createValue(BigDecimal value) {
        return new JSRJsonNumber(value);
    }
    @Override public JsonNumber createValue(BigInteger value) {
        return new JSRJsonNumber(value);
    }
    @Override public javax.json.JsonWriter createWriter(OutputStream out) {
        return createWriterFactory(null).createWriter(out);
    }
    @Override public javax.json.JsonWriter createWriter(Writer writer) {
        return createWriterFactory(null).createWriter(writer);
    }
    @Override public JsonWriterFactory createWriterFactory(Map<String,?> config) {
        return new JSRFactory(config);
    }
    @Override public JsonPointer createPointer(String jsonPointer) {
        return new JSRJsonPointer(jsonPointer);
    }
    @SuppressWarnings("unchecked")
    @Override public JsonArrayBuilder createArrayBuilder(Collection<?> collection) {
        JsonArrayBuilder b = createArrayBuilder();
        Set<Object> seen = new HashSet<Object>();
        for (Object o : collection) {
            JsonValue val = JSRFactory.toJsonValue(o, seen);
            if (val != null) {
                b.add( val);
            }
        }
        return b;
    }
    @Override public JsonArrayBuilder createArrayBuilder(JsonArray array) {
        return createArrayBuilder((Collection)array);
    }

    @SuppressWarnings("unchecked")
    @Override public JsonObjectBuilder createObjectBuilder(Map<String,Object> map) {
        JsonObjectBuilder b = createObjectBuilder();
        Set<Object> seen = new HashSet<Object>();
        for (Map.Entry<String,Object> e : map.entrySet()) {
            String key = e.getKey();
            JsonValue val = JSRFactory.toJsonValue(e.getValue(), seen);
            if (val != null) {
                b.add(key, val);
            }
        }
        return b;
    }

    @SuppressWarnings("unchecked")
    @Override public JsonObjectBuilder createObjectBuilder(JsonObject object) {
        JsonObjectBuilder b = createObjectBuilder();
        for (Map.Entry<String,JsonValue> e : object.entrySet()) {
            b.add(e.getKey(), e.getValue());
        }
        return b;
    }

    @Override public JsonPatch createDiff(JsonStructure source, JsonStructure target) {
        JsonPatchBuilder builder = createPatchBuilder();
        generateDiff("", source, target, builder);
//        System.out.println("DONE");
        return builder.build();
    }

    @Override public JsonMergePatch createMergeDiff(JsonValue source, JsonValue target) {
        return new JSRJsonMergePatch(JSRJsonMergePatch.build(source, target));
    }

    @Override public JsonMergePatch createMergePatch(JsonValue patch) {
        return new JSRJsonMergePatch(patch);
    }

    @Override public JsonPatchBuilder createPatchBuilder() {
        return new JSRJsonPatchBuilder();
    }

    @Override public JsonPatchBuilder createPatchBuilder(JsonArray array) {
        JSRJsonPatchBuilder b = new JSRJsonPatchBuilder();
        for (JsonValue v : array) {
            b.add((JsonObject)v);
        }
        return b;
    }

    @Override public JsonPatch createPatch(JsonArray array) {
        return createPatchBuilder(array).build();
    }

    private static class JSRJsonPatchBuilder implements JsonPatchBuilder {
        private List<JsonValue> a = new ArrayList<JsonValue>();
        private JSRJsonPatchBuilder op(Object... o) {
            LinkedHashMap<String,JsonValue> m = new LinkedHashMap<String,JsonValue>();
            m.put("op", new JSRJsonString((String)o[0]));
            for (int i=1;i<o.length;) {
                m.put((String)o[i++], (JsonValue)o[i++]);
            }
            add(new JSRJsonObject(m));
            return this;
        }
        void add(JsonObject m) {
            a.add(m);
        }
        @Override public JsonPatchBuilder add(String path, boolean value) {
            return op("add", "path", new JSRJsonString(path), "value", value == true ? JsonValue.TRUE : JsonValue.FALSE);
        }
        @Override public JsonPatchBuilder add(String path, int value) {
            return op("add", "path", new JSRJsonString(path), "value", new JSRJsonNumber(value));
        }
        @Override public JsonPatchBuilder add(String path, String value) {
            return op("add", "path", new JSRJsonString(path), "value", new JSRJsonString(value));
        }
        @Override public JsonPatchBuilder add(String path, JsonValue value) {
            return op("add", "path", new JSRJsonString(path), "value", value);
        }
        @Override public JsonPatchBuilder copy(String path, String from) {
            return op("copy", "path", new JSRJsonString(path), "from", new JSRJsonString(from));
        }
        @Override public JsonPatchBuilder move(String path, String from) {
            return op("move", "path", new JSRJsonString(path), "from", new JSRJsonString(from));
        }
        @Override public JsonPatchBuilder remove(String path) {
            return op("remove", "path", new JSRJsonString(path));
        }
        @Override public JsonPatchBuilder replace(String path, boolean value) {
            return op("replace", "path", new JSRJsonString(path), "value", value == true ? JsonValue.TRUE : JsonValue.FALSE);
        }
        @Override public JsonPatchBuilder replace(String path, int value) {
            return op("replace", "path", new JSRJsonString(path), "value", new JSRJsonNumber(value));
        }
        @Override public JsonPatchBuilder replace(String path, String value) {
            return op("replace", "path", new JSRJsonString(path), "value", new JSRJsonString(value));
        }
        @Override public JsonPatchBuilder replace(String path, JsonValue value) {
            return op("replace", "path", new JSRJsonString(path), "value", value);
        }
        @Override public JsonPatchBuilder test(String path, boolean value) {
            return op("test", "path", new JSRJsonString(path), "value", value == true ? JsonValue.TRUE : JsonValue.FALSE);
        }
        @Override public JsonPatchBuilder test(String path, int value) {
            return op("test", "path", new JSRJsonString(path), "value", new JSRJsonNumber(value));
        }
        @Override public JsonPatchBuilder test(String path, String value) {
            return op("test", "path", new JSRJsonString(path), "value", new JSRJsonString(value));
        }
        @Override public JsonPatchBuilder test(String path, JsonValue value) {
            return op("test", "path", new JSRJsonString(path), "value", value);
        }
        @Override public JsonPatch build() {
            return new JSRJsonArray(a);
        }
    }

    Object get(Object value) {
        if (value == JsonValue.TRUE) {
            return Boolean.TRUE;
        } else if (value == JsonValue.FALSE) {
            return Boolean.FALSE;
        } else if (value == JsonValue.NULL) {
            return null;
        } else if (value instanceof JSRJsonNumber) {
            return ((JSRJsonNumber)value).numberValue();
        } else if (value instanceof JsonNumber) {
            return ((JSRJsonNumber)value).bigDecimalValue();
        } else if (value instanceof JsonString) {
            return ((JSRJsonString)value).getString();
        } else {
            return value;
        }
    }

    private static void generateDiff(String path, JsonValue source, JsonValue target, JsonPatchBuilder builder) {
//        System.out.println("GD: p="+path+" s="+source+" t="+target);
        if (source != null && source.equals(target)) {
            return;
        } else if (source instanceof JsonArray && target instanceof JsonArray) {
            JsonArray sa = (JsonArray)source;
            JsonArray ta = (JsonArray)target;
            int ss = sa.size();
            int ts = ta.size();
            int[][] lut = new int[ss + 1][ts + 1];
            for (int i=0;i<ss;i++) {
                for (int j=0;j<ts;j++) {
                    if (sa.get(i).equals(ta.get(j))) {
                        lut[i+1][j+1] = ((lut[i][j]) & ~1) + 3;
                    } else {
                        lut[i+1][j+1] = Math.max(lut[i+1][j], lut[i][j+1]) & ~1;
                    }
                }
            }
            generateDiffLCS(path, lut, builder, sa, ta, ss, ts);
        } else if (source instanceof JsonObject && target instanceof JsonObject) {
            JsonObject so = (JsonObject)source;
            JsonObject to = (JsonObject)target;
            for (Map.Entry<String,JsonValue> e : so.entrySet()) {
                generateDiff(path + "/" + e.getKey(), e.getValue(), to.get(e.getKey()), builder);
            }
            for (Map.Entry<String,JsonValue> e : to.entrySet()) {
                if (!so.containsKey(e.getKey())) {
                    generateDiff(path + "/" + e.getKey(), null, e.getValue(), builder);
                }
            }
        } else if (source == null && target != null) {
            builder.add(path, target);
        } else if (source != null && target == null) {
            builder.remove(path);
        } else {
            builder.replace(path, target);
        }
    }

    private static final String dump(int[][] lut) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int[] t : lut) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(Arrays.toString(t));
        }
        sb.append("]");
        return sb.toString();
    }

    private static void generateDiffLCS(String path, int[][] lut, JsonPatchBuilder builder, JsonArray source, JsonArray target, int i, int j) {
//        System.out.println("GD2: p="+path+" s="+source+" t="+target+" i="+i+" j="+j+" lut="+dump(lut)+" = "+lut[i][j]);
        if (i == 0) {
            if (j > 0) {
                builder.add(path + "/" + (j - 1), target.get(j - 1));
                generateDiffLCS(path, lut, builder, source, target, i, j - 1);
            }
        } else if (j == 0) {
            if (i > 0) {
                builder.remove(path + "/" + (i - 1));
                generateDiffLCS(path, lut, builder, source, target, i - 1, j);
            }
        } else if ((lut[i][j] & 1) == 1) {
            generateDiffLCS(path, lut, builder, source, target, i - 1, j - 1);
        } else {
            final int k = lut[i][j-1] >> 1;
            final int l = lut[i-1][j] >> 1;
            if (k < l) {
                builder.remove(path + "/" + (i - 1));
                generateDiffLCS(path, lut, builder, source, target, i - 1, j);
            } else if (k > l) {
                generateDiffLCS(path, lut, builder, source, target, i, j - 1);
                builder.add(path + "/" + (j - 1), target.get(j - 1));
            } else {
                generateDiffLCS(path, lut, builder, source, target, i - 1, j - 1);
                generateDiff(path + "/" + (i - 1), source.get(i - 1), target.get(j - 1), builder);
            }
        }
    }

    static class DiffGenerator {
        private JsonPatchBuilder builder;

        JsonArray diff(JsonStructure source, JsonStructure target) {
            builder = javax.json.Json.createPatchBuilder();
            diff("", source, target);
            return builder.build().toJsonArray();
        }

        private void diff(String path, JsonValue source, JsonValue target) {
            if (source.equals(target)) {
                return;
            }
            JsonValue.ValueType s = source.getValueType();
            JsonValue.ValueType t = target.getValueType();
            if (s == JsonValue.ValueType.OBJECT && t == JsonValue.ValueType.OBJECT) {
                diffObject(path, (JsonObject) source, (JsonObject) target);
            } else if (s == JsonValue.ValueType.ARRAY && t == JsonValue.ValueType.ARRAY) {
                diffArray(path, (JsonArray) source, (JsonArray) target);
            } else {
                builder.replace(path, target);
            }
        }

        private void diffObject(String path, JsonObject source, JsonObject target) {
            source.forEach((key, value) -> {
                if (target.containsKey(key)) {
                    diff(path + '/' + key, value, target.get(key));
                } else {
                    builder.remove(path + '/' + key);
                }
            });
            target.forEach((key, value) -> {
                if (! source.containsKey(key)) {
                    builder.add(path + '/' + key, value);
                }
            });
        }

        /*
         * For array element diff, find the longest common subsequence, per
         * http://en.wikipedia.org/wiki/Longest_common_subsequence_problem .
         * We modify the algorithm to generate a replace if possible.
         */
        private void diffArray(String path, JsonArray source, JsonArray target) {
            /* The array c keeps track of length of the subsequence. To avoid
             * computing the equality of array elements again, we
             * left shift its value by 1, and use the low order bit to mark
             * that two items are equal.
             */
            int m = source.size();
            int n = target.size();
            int [][] c = new int[m+1][n+1];
            for (int i = 0; i < m+1; i++)
                c[i][0] = 0;
            for (int i = 0; i < n+1; i++)
                c[0][i] = 0;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (source.get(i).equals(target.get(j))) {
                        c[i+1][j+1] = ((c[i][j]) & ~1) + 3;
                        // 3 = (1 << 1) | 1;
                    } else {
                        c[i+1][j+1] = Math.max(c[i+1][j], c[i][j+1]) & ~1;
                    }
                }
            }

            emit(path, source, target, c, m, n);
        }

        private void emit(final String path, final JsonArray source, final JsonArray target, final int[][] c, final int i, final int j) {
           if (i == 0) {
               if (j > 0) {
                   emit(path, source, target, c, i, j - 1);
                   builder.add(path + '/' + (j - 1), target.get(j - 1));
               }
           } else if (j == 0) {
               if (i > 0) {
                   builder.remove(path + '/' + (i - 1));
                   emit(path, source, target, c, i - 1, j);
               }
           } else if ((c[i][j] & 1) == 1) {
               emit(path, source, target, c, i - 1, j - 1);
           } else {
               final int f = c[i][j-1] >> 1;
               final int g = c[i-1][j] >> 1;
               if (f > g) {
                   emit(path, source, target, c, i, j - 1);
                   builder.add(path + '/' + (j - 1), target.get(j - 1));
               } else if (f < g) {
                   builder.remove(path + '/' + (i - 1));
                   emit(path, source, target, c, i - 1, j);
               } else { 
                   diff(path + '/' + (i - 1), source.get(i - 1), target.get(j - 1));
                   emit(path, source, target, c, i - 1, j - 1);
               }
           }
        }
    }

}
