package com.bfo.json;

import java.util.*;

/**
 * <p>
 * A Factory interface that can be implemented to convert more complex
 * types to/from Json if required. For example, here's how to convert
 * {@link Date} objects to specially formatted Json strings.
 * </p>
 *
 * <pre class="brush:java">
 * JsonFactory factory = new JsonFactory() {
 *     public Json toJson(Object o) {
 *         if (o instanceof Date) {
 *             return new Json("D:" + date.toInstant().toString());
 *         }
 *         return null;
 *     }
 *
 *     public Object fromJson(Json o) {
 *         if (o.type().equals("string")) {
 *             String s = o.stringValue();
 *             if (s.startsWith("D:")) {
 *                 try {
 *                     return Date.from(Instant.parse(s.substring(2)));
 *                 } catch (Exception e) {}
 *             }
 *         }
 *         return null;
 *     }
 * }
 * </pre>
 */
public interface JsonFactory {

    /**
     * Given a Json object, return a more specific
     * type if this factory knows about it, otherwise return null
     * @param json the Json object to convert
     * @return the plain Java object it represents, or null if no conversion was made
     */
    public Object fromJson(Json json);

    /**
     * Given a regular Object, return the equivalent Json object
     * if this factory knows about it, otherwise return null.
     * @param object the plain Java object to convert
     * @return the Json object to represent the object, or null if no conversion was made
     */
    public Json toJson(Object object);

}
