package com.bfo.json;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/**
 * JSON (and now CBOR) implementation that tries to be simple, complete, fast and follow the principle ofi
 * least surprise.
 * This class represents every type of JSON object; the type may vary, if for instance you call {@link #put}
 * on this object. The various {@link #isNumber isNNN} methods can be used to determine the current type,
 * or you can just call {@link #stringValue}, {@link #intValue} etc to attempt to retrieve a typed value.
 *
 * <h2>Paths</h2>
 * <p>
 * Paths passed into {@link #put put}, {@link #get get} and {@link #has has}
 * may be Strings or integers. Integers would normally be used to access an
 * item in a List, but if the parent object is a Map the path will be used
 * as a string.
 * </p><p>
 * Paths specified as strings may be compound paths, eg <code>a.b</code>, <code>a.b[2]</code>. If
 * a part of the path contains a dot or square bracket it can be quoted and
 * referenced inside square brackets, eg <code>a.b["dotted.key"]</code>. For speed, values supplied
 * between two quotes simply have their quotes removed; they are not unescaped. So <code>json.put("\"\"\", true)</code>
 * will create the structure <code>{"\\"":true}</code>
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * Object are read from the {@link #read} methods and written with the {@link #write} method. The process
 * can be controlled by specifying {@link JsonReadOptions} or {@link JsonWriteOptions} as appropriate, although
 * the default will read/write as defined in <a href="https://tools.ietf.org/html/rfc8259">RFC8259</a> or
 * <a href="https://tools.ietf.org/html/rfc7049">RFC7049</a> as appropriate.
 * In all cases the Stream is not closed at the end of the read or write.
 * </p><p>
 * Since version 2, objects can also be serialized as CBOR, as defined in
 * <a href="https://tools.ietf.org/html/rfc7049">RFC7049</a>. There are some differences between the JSON and CBOR
 * object models which are significant, and to combine the two into one interface, some minor limitations to the
 * datamodels are in place.
 * </p>
 * <ol>
 *  <li>
 *   Unlike JSON, CBOR suppors binary data, which we read as a {@link ByteBuffer} - these can be identified
 *   with the {@link #isBuffer isBuffer()} method. When serializing a ByteBuffer to JSON, it will be Base-64 encoded
 *   with no padding, as recommended in RFC7049
 *  </li>
 *  <li>
 *   JSON does not support NaN or infinite floating point values. When serializing these values to JSON,
 *   they will be serialized as null - this matches the behavior of all web-browsrs.
 *  </li>
 *  <li>
 *   CBOR supports tags on JSON value, which can be accessed with the {@link #setTag setTag()} and
 *   {@link #getTag getTag()} methods. Tags can be set on any object, but will be ignored when serializing to
 *   JSON. CBOR supports positive value tags of any value, but these are limited to 63 bits by this API.
 *   If values higher than that are encountered when reading, {@link #readCbor readCbor()} method will throw
 *   an IOException.
 *  </li>
 *  <li>
 *   CBOR supports keys in Maps that are not Strings. If these are encountered with this API when reading,
 *   they will be converted to Strings. CBOR also supports duplicate keys in Maps - this is not allowed in this
 *   API, and the {@link #readCbor readCbor()} method will throw an IOException if found.
 *  </li>
 *  <li>
 *   CBOR suppors the "undef" value, and also allows for a number of undefined special types to be used without
 *   error. These will be loaded as null values, with a Tag set that identifies the type. There is no such
 *   conversion when writing; these values will be written as a tagged null, no the original special type.
 *  </li>
 * </ol>
 *
 * <h2>Events</h2>
 * Listeners can be attached to a JSON object, and will received {@link JsonEvent JsonEvents} when changes are
 * made to this item <b>or its descendants</b>. See the {@link JsonEvent} API docs for an example of how to
 * use this to record changes to an object.
 *
 * <h2>Thread Safety</h2>
 * This object is not synchronized, and if it is being modified in one thread while being read in another, external
 * locking should be put in place.
 *
 * <h2>JSON Path</h2>
 * The JsonPath implementation from <a href="https://github.com/json-path/JsonPath">https://github.com/json-path/JsonPath</a>
 * is optional, but if this is in the classpath the {@link #eval} and {@link #evalAll} methods will work; if not in the
 * classpath a ClassNotFoundException will be thrown
 *
 * <h2>Examples</h2>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * Json json = Json.read("{}");
 * json.put("a.b[0]", 0);
 * assert json.get("a.b[0]").type().equals("number");
 * assert json.get("a.b[0]").isNumber();
 * assert json.get("a.b[0]").intValue() == 0;
 * assert json.get("a").type().equals("map");
 * json.put("a.b[2]", 1);
 * assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}");
 * json.put("a.b", true);
 * assert json.toString().equals("{\"a\":{\"b\":true}}");
 * json.write(System.out, null);
 * Json json2 = Json.read("[]");
 * json2.put("0", 0);
 * json2.put("2", 2);
 * assert json2.toString().equals("[0,null,2]");
 * json2.put("a", "a");
 * assert json2.toString().equals("{\"0\":0,\"2\":2,\"a\":\"a"}");
 * </pre>
 */
public class Json {

    private static final JsonWriteOptions DEFAULTWRITEOPTIONS = new JsonWriteOptions();
    private static final JsonReadOptions DEFAULTREADOPTIONS = new JsonReadOptions();

    private Core core;
    private Json parent;
    private Object parentkey;
    private List<JsonListener> listeners;
    private JsonFactory factory;
    private long tag = -1;

    /**
     * <p>
     * Create a new Json object that represents the specified object. The
     * object should be a {@link CharSequence}, {@link Boolean}, {@link Number},
     * {@link ByteBuffer}, <code>byte[]</code>,
     * {@link Map} or {@link Collection}; if a Map or Collection, the collection is
     * copied rather than referenced, and the values must also meet this criteria.
     * A ByteBuffer (or byte[]) is not a native Json type, but is used for CBOR.
     * The buffer is <i>not</i> copied.
     * </p><p>
     * An alternative method for creating a Json object representing an empty
     * map or list is to call {@link #read Json.read("{}")} or {@link #read Json.read("[]")}
     * </p>
     * @param object the object
     * @throws ClassCastException if these conditions are not met
     */
    public Json(Object object) {
        this(object, null);
    }

    /**
     * Create a new Json object that represents the specified object.
     * As for {@link #Json(Object)}, but first attempts to convert the
     * object to a Json object by calling {@link JsonFactory#toJson}.
     * @param object the object
     * @param factory the factory for conversion, which may be null
     * @throws ClassCastException if the object cannot be converted to Json
     */
    @SuppressWarnings("unchecked")
    public Json(Object object, JsonFactory factory) {
        if (object == null) {
            core = INull.INSTANCE;
        } else if (object instanceof Core) {
            core = (Core)object;
        } else if (object instanceof Json) {
            core = ((Json)object).core;
        } else {
            if (factory != null) {
                Json json = factory.toJson(object);
                if (json != null) {
                    core = json.core;
                }
            }
            if (core == null) {
                if (object instanceof CharSequence) {
                    core = new IString(object.toString(), null);
                } else if (object instanceof byte[]) {
                    core = new IBuffer(ByteBuffer.wrap((byte[])object));
                } else if (object instanceof ByteBuffer) {
                    core = new IBuffer((ByteBuffer)object);
                } else if (object instanceof Boolean) {
                    core = new IBoolean(((Boolean)object).booleanValue(), null);
                } else if (object instanceof Number) {
                    core = new INumber((Number)object, null);
                } else if (object instanceof Map) {
                    core = new IMap();
                    Map map = (Map)object;
                    for (Iterator<Map.Entry> i = map.entrySet().iterator();i.hasNext();) {
                        Map.Entry e = i.next();
                        String key = e.getKey().toString();
                        Json value = new Json(e.getValue(), factory);
                        ((IMap)core).put(key, value);
                    }
                } else if (object instanceof Collection) {
                    core = new IList();
                    Collection list = (Collection)object;
                    for (Object o : list) {
                        Json value = new Json(o, factory);
                        ((IList)core).add(value);
                    }
                }
            }
        }
        if (core == null) {
            throw new IllegalArgumentException(object.getClass().getName());
        }
    }

    Core getCore() {
        return core;
    }

    private void setCore(Core core) {
        this.core = core;
    }

    /**
     * Set the default JsonFactory for this object and its descendants.
     * Any objects passed into {@link put put()} will be converted using
     * this factory. The default is null
     *
     * @param factory the factory
     * @since 2
     */
    public void setFactory(JsonFactory factory) {
        this.factory = factory;
        for (Iterator<Map.Entry<String,Json>> i = leafIterator();i.hasNext();) {
            Map.Entry<String,Json> e = i.next();
            e.getValue().factory = factory;
        }
    }

    /**
     * Return the default JsonFactory, as set by {@link #setFactory}
     * @since 2
     * @return the Factory set by {@link #setFactory}
     */
    public JsonFactory getFactory() {
        return factory;
    }

    /**
     * Read a Json object from the specified String. The object may be a structured
     * type or a primitive value (boolean, number or string). The values "{}" and "[]"
     * may be supplied to create a new Json map or array.
     * @param in the String, which must not be null or empty.
     * @return the Json object
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static Json read(CharSequence in) {
        if (in == null) {
            throw new IllegalArgumentException("Can't read from a null string");
        } else if (in.length() == 0) {
            throw new IllegalArgumentException("Can't read from an empty string");
        } else if (in.length() == 2 && in.toString().equals("{}")) {
            return new Json(new IMap());
        } else if (in.length() == 2 && in.toString().equals("[]")) {
            return new Json(new IList());
        } else {
            try {
                return read(new CharSequenceReader(in), DEFAULTREADOPTIONS);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * <p>
     * Read a Json object from the specified InputStream. If the
     * stream begins with a valid byte-order mark, that will be used
     * determine the encoding (which must be UTF-8 or UTF-16),
     * otherwise the stream will be sniffed for UTF-16, and otherwise
     * parsed as UTF-8.
     * </p><p>
     * If you are sure the InputStream is in UTF-8 and has no
     * byte-order mark, as recommended in RFC8259, then you're better
     * off calling {@link #read(Reader,JsonReadOptions) read(new InputStreamReader(in, "UTF-8"), options)}
     * as this will remove the possibility of guessing an incorrect encoding
     * </p>
     *
     * @param in the InputStream
     * @param options the options to use for reading, or null to use the default
     * @return the Json object
     * @throws IOException if an IO exception was encountered during reading
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static Json read(InputStream in, JsonReadOptions options) throws IOException {
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        in.mark(3);
        int v = in.read();
        if (v < 0) {
            throw new EOFException("Empty file");
        } else if (v == 0xEF) {
            if ((v = in.read()) == 0xBB) {
                if ((v = in.read()) == 0xBF) {
                    return read(new InputStreamReader(in, "UTF-8"), options);
                } else {
                    throw new IOException("Invalid Json (begins with 0xEF 0xBB 0x"+Integer.toHexString(v));
                }
            } else {
                throw new IOException("Invalid Json (begins with 0xEF 0x"+Integer.toHexString(v));
            }
        } else if (v == 0xFE) {
            if ((v = in.read()) == 0xFF) {
                return read(new InputStreamReader(in, "UTF-16BE"), options);
            } else {
                throw new IOException("Invalid Json (begins with 0xFE 0x"+Integer.toHexString(v));
            }
        } else if (v == 0xFF) {
            if ((v = in.read()) == 0xFE) {
                return read(new InputStreamReader(in, "UTF-16LE"), options);
            } else {
                throw new IOException("Invalid Json (begins with 0xFF 0x"+Integer.toHexString(v));
            }
        } else if (v == 0) {
            if ((v = in.read()) >= 0x20) { // Sniff: probably UTF-16BE
                in.reset();
                return read(new InputStreamReader(in, "UTF-16BE"), options);
            } else {
                throw new IOException("Invalid Json (begins with 0x0 0x"+Integer.toHexString(v));
            }
        } else {
            if (in.read() == 0x0) { // Sniff: probably UTF-16LE
                in.reset();
                return read(new InputStreamReader(in, "UTF-16LE"), options);
            }
            in.reset();
            return read(new InputStreamReader(in, "UTF-8"), options);
        }
    }

    /**
     * Read a Json object from the specified Reader.
     * @param in the Reader
     * @param options the options to use for reading, or null to use the default
     * @return the Json object
     * @throws IOException if an IO exception was encountered during reading
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static Json read(Reader in, JsonReadOptions options) throws IOException {
        if (options == null) {
            options = DEFAULTREADOPTIONS;
        }
        if (!in.markSupported()) {
            in = new BufferedReader(in);
        }
        if (!(in instanceof CharSequenceReader) && !options.isContextFree()) {
            in = new ContextReader(in);
        }
        return (Json)JsonReader.read(in, options);
    }

    /**
     * Read a CBOR formatted object from the specified InputStream.
     * @param in the InputStream
     * @param options the options to use for reading, or null to use the default
     * @return the Json object
     * @throws IOException if an I/O exception was encountered during reading or the stream does not meet the CBOR format
     * @since 2
     */
    public static Json readCbor(InputStream in, JsonReadOptions options) throws IOException {
        if (options == null) {
            options = DEFAULTREADOPTIONS;
        }
        if (!in.markSupported()) {
            // We don't need mark, but buffering is a good idea
            in = new BufferedInputStream(in);
        }
        return (Json)CborReader.read(new CountingInputStream(in), options);
    }

    /**
     * Read a CBOR formatted object from the specified ByteBuffer.
     * @param in the ByteBuffer
     * @param options the options to use for reading, or null to use the default
     * @return the Json object
     * @throws IOException if an I/O exception was encountered during reading or the stream does not meet the CBOR format
     * @since 2
     */
    public static Json readCbor(final ByteBuffer in, JsonReadOptions options) throws IOException {
        return readCbor(new InputStream() {
            public int read() {
                return in.hasRemaining() ? in.get() & 0xFF : -1;
            }
            public int read(byte[] buf, int off, int len) {
                int r = in.remaining();
                if (r == 0) {
                    return -1;
                } else if (r < len) {
                    len = r;
                }
                in.get(buf, off, len);
                return len;
            }
            public int available() {
                return in.remaining();
            }
            public boolean markSupported() {
                return true;
            }
            public void mark() {
                in.mark();
            }
            public void reset() {
                in.reset();
            }
            public long skip(long skip) {
                int r = Math.min(in.remaining(), (int)skip);
                in.position(in.position() + r);
                return r;
            }
        }, options);
    }

    /**
     * Write the Json object in the CBOR format to the specified output
     * @param out the output
     * @param options the JsonWriteOptions to use when writing, or null to use the default
     * @return the "out" parameter
     * @throws IOException if an IOException is thrown while writing
     * @since 2
     */
    public OutputStream writeCbor(OutputStream out, JsonWriteOptions options) throws IOException {
        CborWriter.write(this, out, options);
        return out;
    }

    /**
     * Add a {@link JsonListener} to this class, if it has not already been added.
     * The listener will received events for any changes to this object or its
     * descendants - the {@link JsonEvent} will reference which object the event
     * relates to, and the {@link #find} method can be used to construct the path
     * to that object if of interest.
     * @param listener listener to add, which may not be null
     * @return this object
     */
    public Json addListener(JsonListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        if (listeners == null) {
            listeners = new ArrayList<JsonListener>();
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        return this;
    }

    /**
     * Remove a {@link JsonListener} from this class
     * @param listener listener to remove
     * @return this object
     */
    public Json removeListener(JsonListener listener) {
        if (listeners != null && listeners.remove(listener) && listeners.isEmpty()) {
            listeners = null;
        }
        return this;
    }

    /**
     * Return the read-only set of listeners on this class.
     * @return a read-only collection of listeners, which will never be null
     */
    public Collection<JsonListener> getListeners() {
        return listeners == null ? Collections.<JsonListener>emptyList() : Collections.unmodifiableCollection(listeners);
    }

    /**
     * Fire a JsonEvent on this object and its ancestors
     * @param event the event
     */
    protected void fireEvent(JsonEvent event) {
        Json j = this;
        do {
            if (j.listeners != null) {
                for (JsonListener l : j.listeners) {
                    l.jsonEvent(j, event);
                }
            }
        } while ((j = j.parent) != null);
    }

    /**
     * Write the Json object to the specified output
     * @param out the output
     * @param options the JsonWriteOptions to use when writing, or null to use the default
     * @return the "out" parameter
     * @throws IOException if an IOException is thrown while writing
     */
    public Appendable write(Appendable out, JsonWriteOptions options) throws IOException {
        if (options == null) {
            options = DEFAULTWRITEOPTIONS;
        }
        SerializerState state = new SerializerState(this, options);
        core.write(out, state);
        if (out instanceof Flushable) {
            ((Flushable)out).flush();
        }
        return out;
    }

    /**
     * Create and return a deep copy of this Json tree
     * @return a deep copy of this item
     */
    public Json duplicate() {
        Json json;
        if (isMap()) {
            json = new Json(new IMap());
            json.setFactory(getFactory());
            Map<String,Json> om = json._mapValue();
            for (Map.Entry<String,Json> e : _mapValue().entrySet()) {
                om.put(e.getKey(), e.getValue().duplicate());
            }
        } else if (isList()) {
            json = new Json(new IList());
            json.setFactory(getFactory());
            List<Json> il = _listValue();
            List<Json> ol = json._listValue();
            for (int i=0;i<il.size();i++) {
                ol.add(il.get(i).duplicate());
            }
        } else {
            json = new Json(core);
        }
        return json;
    }

    /**
     * Get the parent of this node, if known
     * @return the items parent, or null if it has not been added to another Json object
     */
    public Json parent() {
        return parent;
    }

    /**
     * Get the parent key of this node, if known.
     */
    Object getParentKey() {
        return parentkey;
    }

    static void notify(Json parent, Object parentkey, Json oldvalue, Json newvalue) {
        if (newvalue == null) {
            oldvalue.fireEvent(new JsonEvent(oldvalue, null));
            oldvalue.parent = null;
            oldvalue.parentkey = null;
        } else {
            if (oldvalue != null) {
                oldvalue.parent = null;
                oldvalue.parentkey = null;
            }
            newvalue.parent = parent;
            newvalue.parentkey = parentkey;
            newvalue.fireEvent(new JsonEvent(oldvalue, newvalue));
        }
    }

    /**
     * <p>
     * Get the path from this node to the specified object,
     * which should be a descendant of this node.
     * If the parameter is null or not a descendant of
     * this object, this method returns null.
     * </p><p>
     * Specifically, if this method returns not null then it is the case that
     * <code>this.get(this.find(node)) == node</code>.
     * </p>
     * <p>
     * <i>Implementation note: this method is implemented by traversing
     * up from the descendant to this object; possible because a
     * Json object can only have one parent. So the operation is O(n).</i>
     * </p>
     * @param descendant the presumed descendant of this object to find in the tree
     * @return the path from this node to the descendant object
     */
    public String find(Json descendant) {
        if (descendant == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (descendant.getPath(sb, this)) {
            return sb.toString();
        }
        return null;
    }

    private boolean getPath(StringBuilder sb, Json to) {
        if (this == to) {
            return true;
        } else if (parent != null && parent.getPath(sb, to)) {
            boolean stringok = false;
            if (parentkey instanceof String) {
                String pk = (String)parentkey;
                stringok = true;
                for (int i=0;i<pk.length();i++) {
                    char c = pk.charAt(i);
                    if (!(Character.isAlphabetic(c) || c =='_' || (c >='0' && c <= '9' && i > 0))) {
                        stringok = false;
                        break;
                    }
                }
                if (stringok) {
                    if (sb.length() > 0) {
                        sb.append('.');
                    }
                    sb.append(parentkey);
                } else {
                    sb.append("[");
                    try {
                        IString.write(pk, 0, sb);
                    } catch (IOException e) {
                        throw new RuntimeException(e);      // Can't happen.
                    }
                    sb.append("]");
                }
            } else {
                sb.append('[');
                sb.append(parentkey);
                sb.append(']');
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Put the specified value into this object or one of its descendants.
     * If the path specifies a compound key than any intermediate descendants
     * are created as required. If the path specifies an existing object then
     * the old object (which may be a subtree) is removed and returned.
     * The object will be converted with the factory set by {@link #setFactory setFactory()}, if any.
     * @param path the key, which may be a compound key (e.g "a.b" or "a.b[2]") and must not be null
     * @param value the value to insert, which must not be null, this or an ancestor of this
     * @return the object that was previously found at that path, which may be null
     */
    public Json put(String path, Object value) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        Json object;
        if (value instanceof Json) {
            object = (Json)value;
            Json t = this;
            while (t != null) {
                if (t == value) {
                    throw new IllegalArgumentException("value is this or an ancestor");
                }
                t = t.parent;
            }
        } else {
            object = new Json(value, getFactory());
        }
        boolean convert = true;
        if (core instanceof IList) {
            try {
                convert = !Character.isDigit(path.charAt(0)) || Integer.parseInt(path) < 0;
            } catch (Exception e) { }
        }
        if (convert) {
            convertToMap();
        }

        if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"' && core instanceof IMap) {
            // Shortcut
            path = readQuotedPath(path);
            Map<String,Json> m = _mapValue();
            Json oldvalue = m.get(path);
            m.put(path, object);
            notify(this, path, oldvalue, object);
            return oldvalue;
        }
        return traverse(path, object);
    }

    /**
     * Given a quoted String, eg "test" in the path, return test (without quotes)
     */
    private static String readQuotedPath(String path) {
        // Several ways we could do this. First, we could do full string parsing,
        // which is not great as the keys we're being given have been supplied
        // in the Java code, they're not encoded. Actually forcing someone to
        // escape all their quotes etc. is a pain.
        //
        // That means we allow """ to mean a solitary quote:no escaping, whatever
        // is between begin/end quotes is verbatim.
        return path.substring(1, path.length() - 1);
    }

    /**
     * Put the specified value into this object with the specified path.
     * Intended for use on arrays, this will also work with Maps that contain a key with this value.
     * The object will be converted with the factory set by {@link #setFactory setFactory()}, if any.
     * @param path the key - if a non-negative integer and this item is a list, the item will be inserted into the list at that point
     * @param value the value to insert, which must not be null, this or an ancestor of this
     * @return the object that was previously found at that path, which may be null
     */
    public Json put(int path, Object value) {
        if (core instanceof IList && path >= 0) {
            if (value == null) {
                throw new IllegalArgumentException("value is null");
            }
            Json object;
            if (value instanceof Json) {
                object = (Json)value;
                Json t = this;
                while (t != null) {
                    if (t == value) {
                        throw new IllegalArgumentException("value is this or an ancestor");
                    }
                    t = t.parent;
                }
            } else {
                object = new Json(value);
            }
            return buildlist(path, (IList)core, this, object);
        } else {
            convertToMap();
            return put(Integer.toString(path), value);
        }
    }

    /**
     * Remove the item at the specified path from this object or one of its descendants.
     * @param path the key, which may be a compound key (e.g "a.b" or "a.b[2]") and must not be null
     * @return the object that was removed, or null if nothing was removed
     */
    public Json remove(String path) {
        Json json = traverse(path, null);
        if (json != null) {
            boolean removed = false;
            Json parent = json.parent();
            if (parent == null) {
                // Should not be possible - ???
            } else if (parent.isList()) {
                int ix = ((Integer)json.getParentKey()).intValue();
                IList list = (IList)parent.core;
                Json oldvalue = list.get(ix);
                notify(parent, Integer.valueOf(ix), oldvalue, null);
                list.remove(ix);
                removed = true;
            } else if (parent.isMap()) {
                IMap map = (IMap)parent.core;
                String key = (String)json.getParentKey();
                Json oldvalue = map.get(key);
                notify(parent, key, oldvalue, null);
                map.remove(key);
                removed = true;
            }
        }
        return json;
    }

    /**
     * Remove the specified child from this object.
     * Objects are compared with == not equals(). Calling this
     * method on a primitive type Json returns null.
     * @param json the object to remove
     * @return the parameter "json" if it was removed, or null otherwise
     */
    public Json remove(Json json) {
        if (isList()) {
            IList list = (IList)core;
            for (int i=0;i<list.size();i++) {
                if (list.get(i) == json) {
                    notify(this, Integer.valueOf(i), json, null);
                    list.remove(i);
                    return json;
                }
            }
        } else if (isMap()) {
            IMap map = (IMap)core;
            for (Iterator<Map.Entry<String,Json>> i = map.mapValue().entrySet().iterator();i.hasNext();) {
                Map.Entry<String,Json> e = i.next();
                if (e.getValue() == json) {
                    notify(this, e.getKey(), json, null);
                    i.remove();
                    return json;
                }
            }
        }
        return null;
    }

    /**
     * Remove the specified item from this object.
     * Intended for use on arrays, this will also work with Maps that contain a key with this value.
     * Calling this method on a primitive type Json will return null.
     * @param path the key
     * @return the removed object, or null of no object was removed
     */
    public Json remove(int path) {
        if (isList()) {
            return ((IList)core).remove(path);
        } else if (isMap()) {
            return ((IMap)core).remove(Integer.toString(path));
        }
        return null;
    }

    /**
     * Return true if this object has a non-null descendant at the specified path.
     * @param path the path
     * @return true if this object is a list or map, it has the specified descendant and the descendant is not null
     */
    public boolean has(String path) {
        if (isList() || isMap()) {
            boolean full = false;
            if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"') {
                path = readQuotedPath(path);
            } else {
                for (int i=0;i<path.length();i++) {
                    char c = path.charAt(i);
                    if (c == '.' || c == '[' || c == ']') {
                        full = true;
                        break;
                    }
                }
            }
            Json json = null;
            if (full) {
                json = traverse(path, null);
            } else if (isMap()) {
                json = _mapValue().get(path);
            } else if (isList()) {
                try {
                    int ix = Integer.parseInt(path);
                    json = ((IList)core).get(ix);
                } catch (NumberFormatException e) { }
            }
            return json != null && !json.isNull();
        }
        return false;
    }

    /**
     * Return true if this object has a non-null child at the specified path.
     * Intended to identify if the array has a specified index, it will also work with maps.
     * @param path the path
     * @return true if this object is a list or map, it has the specified child and the child is not null
     */
    public boolean has(int path) {
        if (isList()) {
            Json j = ((IList)core).get(path);
            return j != null && !j.isNull();
        } else if (isMap()) {
            Json j = ((IMap)core).get(Integer.toString(path));
            return j != null && !j.isNull();
        }
        return false;
    }

    /**
     * Return the specified descendant of this object, or null
     * if no value exists at the specified path.
     * @param path the path, which must not be null
     * @return the Json object at that path or null if none exists
     */
    public Json get(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        if (isList() || isMap()) {
            boolean full = false;
            if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"') {
                path = readQuotedPath(path);
            } else {
                for (int i=0;i<path.length();i++) {
                    char c = path.charAt(i);
                    if (c == '.' || c == '[' || c == ']') {
                        full = true;
                        break;
                    }
                }
            }
            if (full) {
                return traverse(path, null);
            } else if (isMap()) {
                return ((IMap)core).get(path);
            } else if (isList()) {
                try {
                    int ix = Integer.parseInt(path);
                    return ((IList)core).get(ix);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Return the specified child of this object, or null
     * if no value exists at the specified path.
     * @param path the path
     * @return the object at the specified path, or null if none exists.
     */
    public Json get(int path) {
        if (isList()) {
            return ((IList)core).get(path);
        } else if (isMap()) {
            return ((IMap)core).get(Integer.toString(path));
        } else {
            return null;
        }
    }

    /**
     * Return the size of this object, or zero if this object is a number, string, buffer, boolean or null.
     * @return the size of the object
     */
    public int size() {
        if (isList()) {
            return ((IList)core).size();
        } else if (isMap()) {
            return ((IMap)core).size();
        } else {
            return 0;
        }
    }

    /**
     * Return the tag for this item. Tags are only used with the CBOR serialization.
     * Although CBOR alows positive tag values of any size, this implementation limits
     * them to 63 bits.
     * If no tag is set (the default) this method returns -1.
     * @since 2
     * @return the tag, or -1 if none is set.
     */
    public long getTag() {
        return tag;
    }

    /**
     * Set the tag for this item. Tags are only used with the CBOR serialization, so they
     * will be ignored when writing to Json. Although CBOR allows positive value tags of
     * any size, this implementation limits them to 63 bits.
     * @param tag the tag, or a negative number to remove the tag.
     * @since 2
     */
    public void setTag(long tag) {
        this.tag = tag < 0 ? -1 : tag;
    }

    /**
     * Return true if this node is a number, string, buffer, boolean, null or an empty map or list.
     * @return true if the node is a leaf node.
     */
    public boolean isEmpty() {
        if (isMap()) {
            return ((IMap)core).size() == 0;
        } else if (isList()) {
            return ((IList)core).size() == 0;
        } else {
            return true;
        }
    }

    /**
     * Return true if the specified descendant of this object is of type "buffer".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isBuffer()</code>
     * @param path the path
     * @return true if the descendant exists and is a string
     */
    public boolean isBuffer(String path) {
        Json j = get(path);
        return j != null && j.isBuffer();
    }

    /**
     * Return true if the specified descendant of this object is of type "buffer".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isBuffer()</code>
     * @param path the path
     * @return true if the descendant exists and is a buffer
     */
    public boolean isBuffer(int path) {
        Json j = get(path);
        return j != null && j.isBuffer();
    }

    /**
     * Return true if the specified descendant of this object is of type "string".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isString()</code>
     * @param path the path
     * @return true if the descendant exists and is a string
     */
    public boolean isString(String path) {
        Json j = get(path);
        return j != null && j.isString();
    }

    /**
     * Return true if the specified descendant of this object is of type "string".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isString()</code>
     * @param path the path
     * @return true if the descendant exists and is a string
     */
    public boolean isString(int path) {
        Json j = get(path);
        return j != null && j.isString();
    }

    /**
     * Return true if the specified descendant of this object is of type "number".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isNumber()</code>
     * @param path the path
     * @return true if the descendant exists and is a number
     */
    public boolean isNumber(String path) {
        Json j = get(path);
        return j != null && j.isNumber();
    }

    /**
     * Return true if the specified descendant of this object is of type "number".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isNumber()</code>
     * @param path the path
     * @return true if the descendant exists and is a number
     */
    public boolean isNumber(int path) {
        Json j = get(path);
        return j != null && j.isNumber();
    }

    /**
     * Return true if the specified descendant of this object is of type "boolean".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isBoolean()</code>
     * @param path the path
     * @return true if the descendant exists and is a boolean
     */
    public boolean isBoolean(String path) {
        Json j = get(path);
        return j != null && j.isBoolean();
    }

    /**
     * Return true if the specified descendant of this object is of type "boolean".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isBoolean()</code>
     * @param path the path
     * @return true if the descendant exists and is a boolean
     */
    public boolean isBoolean(int path) {
        Json j = get(path);
        return j != null && j.isBoolean();
    }

    /**
     * Return true if the specified descendant of this object is of type "list".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isList()</code>
     * @param path the path
     * @return true if the descendant exists and is a list
     */
    public boolean isList(String path) {
        Json j = get(path);
        return j != null && j.isList();
    }

    /**
     * Return true if the specified descendant of this object is of type "list".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isList()</code>
     * @param path the path
     * @return true if the descendant exists and is a list
     */
    public boolean isList(int path) {
        Json j = get(path);
        return j != null && j.isList();
    }

    /**
     * Return true if the specified descendant of this object is of type "map".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isMap()</code>
     * @param path the path
     * @return true if the descendant exists and is a map
     */
    public boolean isMap(String path) {
        Json j = get(path);
        return j != null && j.isMap();
    }

    /**
     * Return true if the specified descendant of this object is of type "map".
     * Equivalent to <code>has(path) &amp;&amp; get(path).isMap()</code>
     * @param path the path
     * @return true if the descendant exists and is a map
     */
    public boolean isMap(int path) {
        Json j = get(path);
        return j != null && j.isMap();
    }

    /**
     * Return an Iterator that will descend through every leaf node under this
     * object in a depth-first traveral. The returned keys are relative to this node's path
     * and start with a '.'. If this is called on a leaf nodes, it returns an empty iterator
     * @return an Iterator as described
     */
    public Iterator<Map.Entry<String,Json>> leafIterator() {
        if (!isMap() && !isList()) {
            return Collections.<Map.Entry<String,Json>>emptyIterator();
        }
        return new Iterator<Map.Entry<String,Json>>() {
            List<Iterator> stack;
            List<Object> string;
            Json last;

            {
                stack = new ArrayList<Iterator>();
                string = new ArrayList<Object>();
                last = Json.this;
                if (isMap()) {
                    stack.add(_mapValue().entrySet().iterator());
                } else {
                    stack.add(_listValue().listIterator());
                }
                string.add(null);
            }

            private boolean hasDown() {
                boolean ret = last != null && (last.isMap() || last.isList());
//                System.out.print(ret ? "{d}":"{!d}");
                return ret;
            }

            private boolean hasUp() {
                boolean ret =  !stack.isEmpty();
//                System.out.print(ret ? "{y}":"{!u}");
                return ret;
            }

            private boolean hasAcross() {
                boolean ret = !stack.isEmpty() && stack.get(stack.size() - 1).hasNext();
//                System.out.print(ret ? "{a}":"{!a}");
                return ret;
            }

            private void down() {
//                System.out.print("{D}");
                if (last.isMap()) {
                    stack.add(last._mapValue().entrySet().iterator());
                    string.add(null);
                } else if (last.isList()) {
                    stack.add(last._listValue().listIterator());
                    string.add(null);
                } else {
                    throw new NoSuchElementException();
                }
                last = null;
            }

            private void up() {
//                System.out.print("{U}");
                if (stack.isEmpty()) {
                    throw new NoSuchElementException();
                }
                stack.remove(stack.size() - 1);
                string.remove(string.size() - 1);
                last = null;
            }

            @SuppressWarnings("unchecked")
            private void across() {
                Iterator i = stack.get(stack.size() - 1);
                if (i instanceof ListIterator) {
                    string.set(string.size() - 1, Integer.valueOf(((ListIterator)i).nextIndex()));
                    last = (Json)i.next();
                } else {
                    Map.Entry<String,Object> e = (Map.Entry<String,Object>)i.next();
                    string.set(string.size() - 1, e.getKey());
                    last = (Json)e.getValue();
                }
//                System.out.print("{A}");
            }

            public boolean hasNext() {
//                System.out.print("[");
                if (hasAcross()) {
                    across();
                    if (!hasDown()) {
//                        System.out.print("->true]");
                        return true;
                    }
                }
                while (hasDown()) {
                    down();
                }
                if (hasAcross()) {
                    return hasNext();
                } else {
                    while (hasUp()) {
                        up();
                        if (hasAcross()) {
                            return hasNext();
                        }
                    }
                }
//                System.out.print("->false]");
                return false;
            }

            public Map.Entry<String,Json> next() {
                StringBuilder sb = new StringBuilder();
                for (int i=0;i<string.size();i++) {
                    if (string.get(i) instanceof String) {
                        sb.append('.');
                        sb.append(string.get(i));
                    } else {
                        sb.append('[');
                        sb.append(string.get(i));
                        sb.append(']');
                    }
                }
                return new AbstractMap.SimpleImmutableEntry<String,Json>(sb.toString(), last);
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void convertToMap() {
        if (!isMap()) {
            IMap imap = new IMap();
            if (core instanceof IList) {
                IList list = (IList)core;
                List<JsonListener> tlisteners = listeners;
                listeners = null;
                for (int i=0;i<list.size();i++) {
                    Json item = list.get(i);
                    String key = Integer.toString(i);
                    imap.put(key, item);
                    item.parent = this;
                    item.parentkey = key;
                }
                listeners = tlisteners;
            } else if (!(core instanceof INull)) {
                notify(parent, null, this, null);
            }
            setCore(imap);
        }
    }

    private void convertToList() {
        if (!isList() && !isMap()) {
            IList ilist = new IList();
            notify(parent, null, this, null);
            setCore(ilist);
        }
    }

    private static final Object DOT = new Object() { public String toString() { return "."; } };
    private static final Object LB = new Object() { public String toString() { return "["; } };
    private static final Object RB = new Object() { public String toString() { return "]"; } };

    private static Json buildlist(int ix, IList ilist, Json ctx, Json child) {
        Json out = null;
        while (ilist.size() < ix) {
            Json t = new Json(null);
            int s = ilist.size();
            ilist.add(t);
            notify(ctx, Integer.valueOf(s), null, t);
        }
        if (ix == ilist.size()) {
            if (child == null) {
                child = new Json(null);
            }
            ilist.add(child);
            notify(ctx, Integer.valueOf(ix), null, child);
        } else if (child != null) {
            out = ilist.set(ix, child);
            notify(ctx, Integer.valueOf(ix), out, child);
        }
        return out;
    }

    private Json traverse(String path, Json finalchild) {
//        System.out.println();
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        Json output = null;
        Json ctx = this;
        Object[] stack = new Object[5];
        int lasti = 0, stacklen = 0;

        stack[stacklen++] = DOT;
//        System.out.println(Arrays.toString(stack));
        int len = path.length();
        for (int i=0;ctx != null && i<=len;i++) {
            char c = i == len ? '.' : path.charAt(i);
            boolean last = i == len || (i == len -1 && c == ']');;
            if (c == '\'' || c == '\"') {
                StringBuilder sb = new StringBuilder();
                StringReader r = new StringReader(path);
                try {
                    r.skip(i+1);
                    i += IString.parseString(c, r, sb);
                    continue;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (c == '.') {
                String p = path.substring(lasti, i);
                if (p.length() > 0) {
                    stack[stacklen++] = p;
                }
                stack[stacklen++] = DOT;
                lasti = i+1;
            } else if (c == '[') {
                String p = path.substring(lasti, i);
                if (p.length() > 0) {
                    stack[stacklen++] = p;
                }
                stack[stacklen++] = LB;
                lasti = i+1;
            } else if (c == ']') {
                String p = path.substring(lasti, i);
//                System.out.println("RB: string='"+p+"'");
                if (p.length() > 1 && (c=p.charAt(0)) == p.charAt(p.length()-1) && c == '\"') {
                    stack[stacklen++] = p.substring(1, p.length() - 1);
                } else if (p.length() > 0) {
                    if (Character.isDigit(p.charAt(0))) {
                        try {
                            stack[stacklen++] = Integer.valueOf(p);
                        } catch (NumberFormatException e) {
                            stack[stacklen++] = p;
                        }
                    } else {
                        stack[stacklen++] = p;  // Putting unquoted string in as legit arg
                    }
                }
                stack[stacklen++] = RB;
                lasti = i+1;
            } else {
                continue;
            }
//            System.out.println(Arrays.toString(stack));
            if (stack[0] == DOT && stack[1] instanceof String) {
                // . WORD -
                // if WORD is int and ctx is array, cvt to int and add to array,
                // otherwise cvt to map and add to map
                Json newctx = null;
                String key = (String)stack[1];
                if (ctx.isList()) {
                    try {
                        int ix = Integer.parseInt(key);
                        IList ilist = (IList)ctx.core;
                        if (finalchild != null) {
                            Json t = buildlist(ix, ilist, ctx, last ? finalchild : null);
                            if (output == null) {
                                output = t;
                            }
                        }
                        newctx = ilist.get(ix);
                    } catch (NumberFormatException e) { }
                }
                if (newctx == null) {
                    if (finalchild != null) {
                        ctx.convertToMap();
                    }
                    if (ctx.isMap()) {
                        IMap imap = (IMap)ctx.core;
                        newctx = imap.get(key);
                        if (finalchild != null && (newctx == null || last)) {
                            Json t = imap.put(key, newctx = last ? finalchild : new Json(null));
                            if (output == null) {
                                output = t;
                            }
                            notify(ctx, key, t, newctx);
                        }
                    }
                }
//                System.out.println("CTX now "+ctx);
                ctx = newctx;
                stack[0] = stack[2];
                stack[1] = stack[3];
                stack[2] = null;
                stack[3] = null;
                stacklen -= 2;
            } else if (stack[0] == LB && stack[2] == RB) {
                // [ ? ]
                Json newctx = null;
                if (stack[1] instanceof Integer) {
                    // [ INT ]
                    // if ctx is array, add to array
                    int ix = ((Integer)stack[1]).intValue();
                    if (finalchild != null) {
                        if (output == null) {
                            output = new Json(ctx.core);
                        }
                        ctx.convertToList();
                    }
                    if (ctx.isList()) {
                        IList ilist = (IList)ctx.core;
                        if (finalchild != null) {
                            Json t = buildlist(ix, ilist, ctx, last ? finalchild : null);
                            if (output == null) {
                                output = t;
                            }
                        }
                        newctx = ilist.get(ix);
                    }
                }
                if (newctx == null) {
                    // [ STR ] - ctx to map, and add STR to map
                    String key = stack[1].toString();
                    if (finalchild != null) {
                        ctx.convertToMap();
                    }
                    if (ctx.isMap()) {
                        IMap imap = (IMap)ctx.core;
                        newctx = imap.get(key);
                        if (finalchild != null && (newctx == null || last)) {
                            Json t = imap.put(key, newctx = last ? finalchild : new Json(null));
                            if (output == null) {
                                output = t;
                            }
                            notify(ctx, key, t, newctx);
                        }
                    }
                }
//                System.out.println("CTX now "+ctx);
                ctx = newctx;
                stack[0] = stack[3];
                stack[1] = stack[4];
                stack[2] = null;
                stack[3] = null;
                stack[4] = null;
                stacklen -= 3;
            } else if (stacklen > 3) {
                break;
            }
        }
        if (stacklen != 1 && ctx != null) {
            throw new IllegalArgumentException("Invalid path \""+path+"\"");
        }
        if (finalchild != null) {
            return output;
        }
        return ctx;
    }

    /**
     * Return the type of this node, which may be "number", "string", "boolean", "list", "map", "buffer" or "null"
     * @return the object type
     */
    public String type() {
        return core.type();
    }

    /**
     * Return true if this node is null
     * @return true if the object is null
     */
    public boolean isNull() {
        return core instanceof INull;
    }

    /**
     * Return true if this node is a "number"
     * @return true if the object is a number
     */
    public boolean isNumber() {
        return core instanceof INumber;
    }

    /**
     * Return true if this node is a "boolean"
     * @return true if the object is a boolean
     */
    public boolean isBoolean() {
        return core instanceof IBoolean;
    }

    /**
     * Return true if this node is a "string"
     * @return true if the object is a string
     */
    public boolean isString() {
        return core instanceof IString;
    }

    /**
     * Return true if this node is a "buffer"
     * @return true if the object is a buffer
     */
    public boolean isBuffer() {
        return core instanceof IBuffer;
    }

    /**
     * Return true if this node is a "map"
     * @return true if the object is a map
     */
    public boolean isMap() {
        return core instanceof IMap;
    }

    /**
     * Return true if this node is a "list"
     * @return true if the object is a list
     */
    public boolean isList() {
        return core instanceof IList;
    }

    /**
     * Return the value of this Json as a plain Java object,
     * which may be a {@link String}, {@link Boolean}, {@link Number},
     * {@link List}, {@link Map}, {@link ByteBuffer} or null.
     * @return the object value
     */
    public Object value() {
        return core.value();
    }

    /**
     * Copy the internal value from the specified Json object to this object.
     * This can be useful to update the value of a Json object in a structure, without
     * having to modify the structure. For example:
     * <pre>
     * Json a = new Json(999);
     * Json b = new Json("string");
     * a.setValue(b);
     * System.out.println(a); // "string"
     * </pre>
     * @param json the json object that is the source of the intended value of this object
     */
    public void setValue(Json json) {
        setCore(json.core);
    }

    /**
     * Return the value of this node as a String. This method will always
     * succeed. It will return null if this object represents null, but it
     * otherwise identical to {@link #toString}
     * @return the string value of this object
     */
    public String stringValue() {
        return core.stringValue();
    }

    /**
     * Return the value of this node as a ByteBuffer. ByteBuffers only exist
     * natively in the CBOR serialization.
     * <ul>
     * <li>A string will be decoded using Base64 (no padding) and, if valid
     * returned as a ByteBuffer. If invalid, a ClassCastException will be thrown</li>
     * <li>null will return null</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the buffer value of this object
     * @since 2
     */
    public ByteBuffer bufferValue() {
        return core.bufferValue();
    }

    /**
     * Return the value of this node as a Number.
     * <ul>
     * <li>A number will be returned as an Integer, Long, Double, BigInteger or BigDecimal as appropriate</li>
     * <li>A string in the format specified in RFC8259 will be parsed and returned as an Integer, Long or Double (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>An empty string will return 0 (if {@link JsonReadOptions#setLooseEmptyStrings "looseEmptyStrings"} was specified))</li>
     * <li>A boolean will return an Integer that is 1 or 0 (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>null will return null</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the number value of this object
     */
    public Number numberValue() {
        return core.numberValue();
    }

    /**
     * Return the value of this node as an integer.
     * <ul>
     * <li>A number will be converted to int</li>
     * <li>A string that can be parsed with Integer.parseInt will converted (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>An empty string will return 0 (if {@link JsonReadOptions#setLooseEmptyStrings "looseEmptyStrings"} was specified))</li>
     * <li>A boolean will return 1 or 0 (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the int value of this object
     */
    public int intValue() {
        return core.intValue();
    }

    /**
     * Return the value of this node as a long.
     * <ul>
     * <li>A number will be converted to long</li>
     * <li>A string that can be parsed with Long.parseLong will converted (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>An empty string will return 0 (if {@link JsonReadOptions#setLooseEmptyStrings "looseEmptyStrings"} was specified))</li>
     * <li>A boolean will return 1 or 0 (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the long value of this object
     */
    public long longValue() {
        return core.longValue();
    }

    /**
     * Return the value of this node as an float.
     * <ul>
     * <li>A number will be converted to float</li>
     * <li>A string in the format specified in RFC8259 will be parsed and returned (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>An empty string will return 0 (if {@link JsonReadOptions#setLooseEmptyStrings "looseEmptyStrings"} was specified)</li>
     * <li>A boolean will return 1 or 0 (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the float value of this object
     */
    public float floatValue() {
        return core.floatValue();
    }

    /**
     * Return the value of this node as a double.
     * <ul>
     * <li>A number will be converted to double</li>
     * <li>A string in the format specified in RFC8259 will be parsed and returned (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>An empty string will return 0 (if {@link JsonReadOptions#setLooseEmptyStrings "looseEmptyStrings"} was specified)</li>
     * <li>A boolean will return 1 or 0 (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the double value of this object
     */
    public double doubleValue() {
        return core.doubleValue();
    }

    /**
     * Return the value of this node as a boolean.
     * <ul>
     * <li>A boolean will return its value</li>
     * <li>A number evaluating to 0 will return false, otherwise it will return true (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>The string "false", or one that represents a number evaluating to 0 will return false, otherwise it will return true (if {@link JsonReadOptions#setStrictTypes "strictTypes"} was not specified)</li>
     * <li>An empty string will return false (if {@link JsonReadOptions#setLooseEmptyStrings "looseEmptyStrings"} was specified)</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the boolean value of this object
     */
    public boolean booleanValue() {
        return core.booleanValue();
    }

    /**
     * Return the value of this node as a map. The returned Map is read-only
     * @throws ClassCastException if the node is not a map.
     * @return the read-only map value of this object
     */
    public Map<String,Json> mapValue() {
        return Collections.unmodifiableMap(_mapValue());
    }

    Map<String,Json> _mapValue() {
        return core.mapValue();
    }

    /**
     * Return the value of this node as a list. The returned List is read-only
     * @throws ClassCastException if the node is not a list
     * @return the read-only list value of this object
     */
    public List<Json> listValue() {
        return Collections.unmodifiableList(_listValue());
    }

    List<Json> _listValue() {
        return core.listValue();
    }

    /**
     * Return this Json value as a plain object. Unlike {@link #value} this
     * method will traverse through its descendants, converting Json objects
     * to plain objects so that the returned object is guaranteed to have
     * no reference to this package. The returned value is as follows
     * <ul>
     * <li>If this object {@link #isNull is null}, return null</li>
     * <li>If factory is not null and {@link JsonFactory#fromJson fromJson()} returns a non-null value, return that value</li>
     * <li>If this value is a string, buffer, number or boolean, return the value from {@link #value()}</li>
     * <li>If this value is a list or map, populate the map values with the output of this method and return as a Map&lt;String,Object&gt; or List&lt;Object&gt;</li>
     * </ul>
     * @param factory the factory for conversion, which may be null
     * @return a String, Number, Boolean, Map&lt;String,Object&gt;, List&lt;Object&gt; or null as described
     */
    public Object objectValue(JsonFactory factory) {
        if (isNull()) {
            return null;
        }
        if (factory != null) {
            Object o = factory.fromJson(this);
            if (o != null) {
                return o;
            }
        }
        if (isMap()) {
            Map<String,Object> out = new LinkedHashMap<String,Object>(core.mapValue());
            for (Map.Entry<String,Object> e : out.entrySet()) {
                e.setValue(((Json)e.getValue()).objectValue(factory));
            }
            return out;
        } else if (isList()) {
            List<Json> list = core.listValue();
            List<Object> out = new ArrayList<Object>(list.size());
            for (Json o : list) {
                out.add(o.objectValue(factory));
            }
            return out;
        } else {
            return value();
        }
    }

    /**
     * Return a hashCode based on the {@link #value()}
     */
    public int hashCode() {
        return core.hashCode();
    }

    /**
     * Return true if the specified object is a Json object and has an equal {@link #value() value}
     */
    public boolean equals(Object o) {
        return o instanceof Json && core.equals(((Json)o).core);
    }

    /**
     * Return a String representation of this Json object. Equivalent
     * to calling <code>return {@link #write write}(new StringBuilder(), null).toString()</code>
     */
    public String toString() {
        JsonWriteOptions j = new JsonWriteOptions().setAllowNaN(true);
        try {
            StringBuilder sb = new StringBuilder();
            /*
            if (getTag() >= 0) {
                sb.append(getTag() + "(");
            }
            */
            write(sb, j);
            /*
            if (getTag() >= 0) {
                sb.append(")");
            }
            */
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //--------------------------------------------------------
    // jsonpath

    /**
     * Evaluate the "JSON path" expression at this node, and return the
     * single object it finds, or null if none were found.
     * If more than one object is found, only the first is returned
     * This method requires JsonPath to be in the classpath.
     * @param path the JSON path expression
     * @return the Json object matching the evaluation of the specified path from this node, or null if no node matches
     */
    public Json eval(String path) {
        Object o = JsonPathProviderBFO.read(path, this);
        if (o instanceof List) {
            return (Json)((List)o).get(0);
        } else {
            return (Json)o;
        }
    }

    /**
     * Evaluate the "JSON path" expression at this node, and return the
     * set of objects it finds, or null if none were found.
     * This method requires JsonPath to be in the classpath.
     * @param path the JSON path expression
     * @return a Collection of Json objects matching the evaluation of the specified path from this node, or null if no node matches
     */
    @SuppressWarnings("unchecked")
    public List<Json> evalAll(String path) {
        Object o = JsonPathProviderBFO.read(path, this);
        if (o instanceof Json) {
            o =  Collections.singletonList(o);
        }
        return (List<Json>)o;
    }

}
