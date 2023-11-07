package com.bfo.json;

import java.util.*;
import java.lang.reflect.Array;
import java.io.*;
import java.nio.*;
import java.math.*;
import java.nio.channels.*;
import java.nio.charset.*;

/**
 * JSON (and now CBOR/Msgpack) implementation that tries to be simple, complete, fast and follow the principle ofi
 * least surprise.
 * This class represents every type of JSON object; the type may vary, if for instance you call {@link #put}
 * on this object. The various {@link #isNumber isNNN} methods can be used to determine the current type,
 * or you can just call {@link #stringValue}, {@link #intValue} etc to attempt to retrieve a typed value.
 *
 * <h2>Keys and Paths</h2>
 * <p>
 * {@link #put put}, {@link #get get}, {@link #has has} and {@link #remove remove} all accept a <i>key</i>,
 * which is an index into this object (a normal "map" or "list" key).
 * For lists it must be an Integer, and for maps it can theoretically be any type of object, but is currently
 * limited in to <code>String</code>, <code>Number</code> or <code>Boolean</code>.
 * Non-String keys are only preserved when serialising to CBOR or Msgpack, and will be converted
 * to Strings when the object is serialised to JSON;
 * </p><p>
 * {@link #putPath putPath}, {@link #getPath getPath}, {@link #hasPath hasPath} and {@link #removePath removePath}
 * all accept a <i>path</i>, which is a String identifying a <b>descendent</b>. 
 * These may be compound paths, eg <code>a.b</code>, <code>a.b[2]</code>. If a part of the path contains a dot or
 * square bracket it can be quoted and referenced inside square brackets, eg <code>a.b["dotted.key"]</code>.
 * For speed, values supplied between two quotes simply have their quotes removed; they are not unescaped.
 * So <code>json.put("\"\"\", true)</code> will create the structure <code>{"\\"":true}</code>.
 * Paths that traverse lists are always converted to integers, and paths that traverse maps are converted to strings.
 * </p><p>
 *  <b>Note</b>: prior to version 5, 
 * {@link #put put}, {@link #get get}, {@link #has has} and {@link #remove remove} accepted a <i>path</i>, as
 * non-String keys could not be stored. 
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
 * Since version 2, objects can also be serialized as CBOR (and since version 3, Msgpack), as defined in
 * <a href="https://tools.ietf.org/html/rfc7049">RFC7049</a> and
 * <a href="https://github.com/msgpack/msgpack/blob/master/spec.md">the Msgpack spec</a>.
 * There are some differences between the JSON and CBOR/Msgpack
 * object models which are significant, and to combine the two into one interface, some minor limitations to the
 * datamodels are in place.
 * </p>
 * <ol>
 *  <li>
 *   Unlike JSON, CBOR/Msgpack supports binary data, which we read as a {@link ByteBuffer} - these can be identified
 *   with the {@link #isBuffer isBuffer()} method. When serializing a ByteBuffer to JSON, it will be Base-64 encoded
 *   with no padding, as recommended in RFC7049. By default the "URL- and filename-safe" variation of BAse64 will
 *   be used when writing (see {@link JsonWriteOptions#setBase64Standard}). When reading, all Base64 variations
 *   can be parsed. <b>The ByteBuffer position is ignored</b>; it will be reset to zero before every read, write,
 *   or when it is returned from {@link #bufferValue}.
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
 *   Msgpack supports "ext" types, which are treated as ByteBuffer objects with the extension type set
 *   retrievable by calling {@link #getTag getTag()} - the tag is a number from 0..255. Msgpack does not
 *   support integer values greater than 64-bit; attempting to write these will throw an IOException.
 *  </li>
 *  <li>
 *   CBOR/Msgpack support complex key types in maps, such as lists or other maps. If these are encountered
 *   with this API when reading, they will be converted to strings by default, or
 *   (if {@link JsonReadOptions#setFailOnComplexKeys} is set) throw an {@link IOException}.
 *   CBOR/Msgpack also supports duplicate keys in maps - this abomination is not allowed in this
 *   API, and the {@link #readCbor readCbor()} and {@link #readMsgpack readMsgpack()} methods will throw an
 *   {@link IOException} if found.
 *  </li>
 *  <li>
 *   CBOR supports the "undefined" value which is distinct from null. This can be created using the
 *   {@link UNDEFINED} constant, and tested for with {@link #isUndefined} (since version 5)
 *  </li>
 *  <li>
 *   CBOR allows for a number of undefined special types to be used without error. These will be loaded as
 *   undefined values, with a Tag set that identifies the type. There is no such conversion when writing; these
 *   values will be written as a tagged undefined, not the original special type.
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
 * <h2>Examples</h2>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * Json json = Json.read("{}");
 * json.putPath("a.b[0]", 0);
 * assert json.get("a").get("b") == json.getPath("a.b");
 * assert json.getPath("a.b[0]").type().equals("number");
 * assert json.getPath("a.b[0]").isNumber();
 * assert json.getPath("a.b[0]").intValue() == 0;
 * assert json.get("a").type().equals("map");
 * json.putPath("a.b[2]", 1);
 * assert json.toString().equals("{\"a\":{\"b\":[0,null,2]}}");
 * json.putPath("a.b", true);
 * assert json.toString().equals("{\"a\":{\"b\":true}}");
 * json.write(System.out, null);
 * Json json2 = Json.read("[]");
 * json2.put(0, 0);
 * json2.put(2, 2);
 * assert json2.toString().equals("[0,null,2]");
 * json2.put("a", "a");
 * assert json2.toString().equals("{\"0\":0,\"2\":2,\"a\":\"a"}");
 * </pre>
 */
public class Json {

    private static final JSRProvider jsr2json;
    static {
        JSRProvider j = null;
        try {
            j = new JSRProvider();
        } catch (Throwable e) { }
        jsr2json = j;
    }

    /**
     * A constant object that can be passed into the Json constructor to create a Cbor "undefined" value
     * @since 5
     */
    public static final Object UNDEFINED = new Object() { public String toString() { return "<undefined>"; } };
    static final Object NULL = new Object() { public String toString() { return "<null>"; } };

    private static final int FLAG_STRICT = 1;
    private static final int FLAG_SIMPLESTRING = 2;
    private static final int FLAG_NONSTRINGKEY = 4;
    private static final JsonWriteOptions DEFAULTWRITEOPTIONS = new JsonWriteOptions();
    private static final JsonReadOptions DEFAULTREADOPTIONS = new JsonReadOptions();

    private Object core;
    private byte flags;
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
     * The buffer is <i>not</i> copied, it is stored by reference.
     * </p><p>
     * An fast alternative method for creating a Json object representing an empty
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
     * @param factory the factory for conversion, which may be null. Will be passed to {@link #setFactory}
     * @throws ClassCastException if the object cannot be converted to Json
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public Json(Object object, JsonFactory factory) {
        this(object, factory, null);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private Json(Object object, JsonFactory factory, Set<Object> seen) {
        if (jsr2json != null) {
            object = jsr2json.get(object);
        }
        if (object == null) {
            core = NULL;
        } else if (object == NULL || object == UNDEFINED) {
            core = object;
        } else if (object instanceof Json) {
            core = ((Json)object).core;
            tag = ((Json)object).tag;
        } else {
            if (factory != null) {
                Json json = factory.toJson(object);
                if (json != null) {
                    core = json.core;
                }
            }
            if (core == null) {
                if (object instanceof CharSequence) {
                    core = object;
                } else if (object instanceof byte[]) {
                    core = ByteBuffer.wrap((byte[])object);
                } else if (object instanceof ByteBuffer) {
                    core = ((Buffer)object).position(0);
                } else if (object instanceof Boolean) {
                    core = object;
                } else if (object instanceof Number) {
                    core = object;
                } else if (object instanceof Map) {
                    if (seen == null) {
                        seen = new HashSet<Object>();
                    }
                    if (!seen.add(object)) {
                        throw new IllegalArgumentException("Objects form a loop");
                    }
                    Map<Object,Json> map = new LinkedHashMap<Object,Json>();
                    for (Iterator<Map.Entry> i = ((Map)object).entrySet().iterator();i.hasNext();) {
                        Map.Entry e = i.next();
                        Object key = e.getKey();
                        if (e.getValue() instanceof Optional && !((Optional)e.getValue()).isPresent()) {
                            continue;
                        }
                        Json child = new Json(e.getValue(), factory, seen);
                        child.parent = this;
                        child.parentkey = key;
                        try {
                            key = fixKey(key, false, 0);
                        } catch (IOException e2) {
                            // can't happen
                        }
                        if (!(key instanceof String)) {
                            setNonStringKeys();
                        }
                        map.put(key, child);
                    }
                    core = map;
                } else if (object instanceof Collection) {
                    if (seen == null) {
                        seen = new HashSet<Object>();
                    }
                    if (!seen.add(object)) {
                        throw new IllegalArgumentException("Objects form a loop");
                    }
                    List<Json> list = new ArrayList<Json>();
                    for (Object o : (Collection)object) {
                        Json child = new Json(o, factory, seen);
                        child.parent = this;
                        child.parentkey = list.size();
                        list.add(child);
                    }
                    core = list;
                } else if (object.getClass().isArray()) {
                    if (seen == null) {
                        seen = new HashSet<Object>();
                    }
                    if (!seen.add(object)) {
                        throw new IllegalArgumentException("Objects form a loop");
                    }
                    List<Json> list = new ArrayList<Json>();
                    for (int i=0;i<Array.getLength(object);i++){
                        Json child = new Json(Array.get(object, i), factory, seen);
                        child.parent = this;
                        child.parentkey = list.size();
                        list.add(child);
                    }
                    core = list;
                }
            }
        }
        this.factory = factory;
        if (core == null) {
            throw new IllegalArgumentException(object.getClass().getName());
        }
    }

    private boolean isStrict() {
        return (flags & FLAG_STRICT) != 0;
    }

    boolean isNonStringKeys() {
        return (flags & FLAG_NONSTRINGKEY) != 0;
    }

    static Object fixKey(Object key, boolean failOnNonStrings, long tell) throws IOException {
        Object o = key instanceof Json ? ((Json)key).core : key;
        if (o == null) {
            return NULL;
        } else if (o instanceof Boolean || o instanceof Number || o == NULL || o == UNDEFINED) {
            return o;
        } else if (o instanceof String) {
            return o;
        } else if (o instanceof CharSequence) {
            return o.toString();
        } else if (failOnNonStrings) {
            throw new IOException("Map key \"" + key + "\" is " + ((Json)key).type() + " rather than string at " + tell);
        } else {
            return o.toString();
        }
    }

    Json setNonStringKeys() {
        flags |= FLAG_NONSTRINGKEY;
        return this;
    }

    Json setStrict(boolean strict) {
        if (strict) {
            flags |= FLAG_STRICT;
        } else {
            flags &= ~FLAG_STRICT;
        }
        return this;
    }

    boolean isIndefiniteBuffer() {
        // For now, a simple test - if writeBuffer is overridden in this
        // subclass of Json, write CBOR output as indefinite.
        if (getClass() != Json.class) {
            try {
                getClass().getDeclaredMethod("writeBuffer", OutputStream.class);
                return true;
            } catch (Exception e) {}
        }
        return false;
    }

    boolean isIndefiniteString() {
        if (getClass() != Json.class) {
            try {
                getClass().getDeclaredMethod("writeString", Appendable.class);
                return true;
            } catch (Exception e) {}
        }
        return false;
    }

    boolean isSimpleString() {
        return (flags & FLAG_SIMPLESTRING) != 0;
    }

    Json setSimpleString(boolean simple) {
        if (simple) {
            flags |= FLAG_SIMPLESTRING;
        } else {
            flags &= ~FLAG_SIMPLESTRING;
        }
        return this;
    }

    /**
     * Set the default JsonFactory for this object and its descendants.
     * Any objects passed into {@link put put()} will be converted using
     * this factory. The default is null
     *
     * @param factory the factory
     * @since 2
     * @return this
     */
    public Json setFactory(JsonFactory factory) {
        List<Json> q = new ArrayList<Json>();
        q.add(this);
        for (int i=0;i<q.size();i++) {
            Json j = q.get(i);
            if (q.indexOf(j) == i) {
                j.factory = factory;
                if (j.isList()) {
                    q.addAll(j.listValue());
                } else if (j.isMap()) {
                    q.addAll(j.mapValue().values());
                }
            }
        }
        return this;
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
            throw new IllegalArgumentException("Input is null");
        } else if (in.length() == 0) {
            throw new IllegalArgumentException("Can't read from an empty string");
        } else if (in.length() == 2 && in.toString().equals("{}")) {
            return new Json(Collections.EMPTY_MAP);
        } else if (in.length() == 2 && in.toString().equals("[]")) {
            return new Json(Collections.EMPTY_LIST);
        } else {
            try {
                return read(new CharSequenceReader(in), null);
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
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        Reader reader = JsonReader.createReader(in);
        return read(reader, options);
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
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        if (options == null) {
            options = DEFAULTREADOPTIONS;
        }
        if (!in.markSupported()) {
            in = new BufferedReader(in);
        }
        if (!(in instanceof CharSequenceReader) && !options.isContextFree()) {
            in = new ContextReader(in);
        }
        return new JsonReader(in, options).read();
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
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        if (options == null) {
            options = DEFAULTREADOPTIONS;
        }
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        if (!(in instanceof CountingInputStream)) {
            in = new CountingInputStream(in);
        }
        return new CborReader((CountingInputStream)in, options).read();
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
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        InputStream stream;
        if (!in.isDirect()) {
            stream = new ByteArrayInputStream(in.array(), in.arrayOffset(), in.remaining());        // seems faster
        } else {
            stream = new ByteBufferInputStream(in);
        }
        return readCbor(stream, options);
    }

    /**
     * Read a Msgpack formatted object from the specified InputStream.
     * @param in the InputStream
     * @param options the options to use for reading, or null to use the default
     * @return the Json object
     * @throws IOException if an I/O exception was encountered during reading or the stream does not meet the MsgPack format
     * @since 3
     */
    public static Json readMsgpack(InputStream in, JsonReadOptions options) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        if (options == null) {
            options = DEFAULTREADOPTIONS;
        }
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        if (!(in instanceof CountingInputStream)) {
            in = new CountingInputStream(in);
        }
        return new MsgpackReader((CountingInputStream)in, options).read();
    }

    /**
     * Read a Msgpack formatted object from the specified ByteBuffer.
     * @param in the ByteBuffer
     * @param options the options to use for reading, or null to use the default
     * @return the Json object
     * @throws IOException if an I/O exception was encountered during reading or the stream does not meet the CBOR format
     * @since 3
     */
    public static Json readMsgpack(final ByteBuffer in, JsonReadOptions options) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        InputStream stream;
        if (!in.isDirect()) {
            stream = new ByteArrayInputStream(in.array(), in.arrayOffset(), in.remaining());        // seems faster
        } else {
            stream = new ByteBufferInputStream(in);
        }
        return readMsgpack(stream, options);
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
        if (out == null) {
            throw new IllegalArgumentException("Output is null");
        }
        if (options == null) {
            options = DEFAULTWRITEOPTIONS;
        }
        new CborWriter(out, options, this).write(this);
        return out;
    }

    /**
     * Write the Json object in the Msgpack format to the specified output
     * @param out the output
     * @param options the JsonWriteOptions to use when writing, or null to use the default
     * @return the "out" parameter
     * @throws IOException if an IOException is thrown while writing
     * @since 3
     */
    public OutputStream writeMsgpack(OutputStream out, JsonWriteOptions options) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Output is null");
        }
        if (options == null) {
            options = DEFAULTWRITEOPTIONS;
        }
        new MsgpackWriter(out, options, this).write(this);
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
        new JsonWriter(out, options, this).write(this);
        if (out instanceof Flushable) {
            ((Flushable)out).flush();
        }
        return out;
    }

    /**
     * Create and return a deep copy of this Json tree.
     * Note that ByteBuffers values will <i>not</i> be cloned,
     * and the returned item will have no listeners
     * @return a deep copy of this item
     */
    public Json duplicate() {
        Json json;
        if (isMap()) {
            json = new Json(null);
            json.setFactory(getFactory());
            Map<Object,Json> map = new LinkedHashMap<Object,Json>(((Map)core).size());
            for (Map.Entry<Object,Json> e : mapValue().entrySet()) {
                Object o = e.getKey();
                if (!(o instanceof String)) {
                    json.setNonStringKeys();
                }
                map.put(o, e.getValue().duplicate());
            }
            json.core = map;
        } else if (isList()) {
            json = new Json(null);
            json.setFactory(getFactory());
            List<Json> list = new ArrayList<Json>(((List)core).size());
            for (Json e : listValue()) {
                list.add(e.duplicate());
            }
            json.core = list;
        } else if (isString()) {
            json = new Json(core.toString());
        } else {
            json = new Json(core);
        }
        json.tag = tag;
        json.flags = flags;
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

    static void notifyDuringLoad(Json parent, Object parentkey, Json newvalue) {
        newvalue.parent = parent;
        newvalue.parentkey = parentkey;
    }

    static void notify(Json parent, Object parentkey, Json oldvalue, Json newvalue) {
        if (newvalue == null) {
            if (oldvalue != null) {
                oldvalue.fireEvent(new JsonEvent(oldvalue, null));
                oldvalue.parent = null;
                oldvalue.parentkey = null;
            }
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
     * <code>this.getPath(this.find(node)) == node</code>.
     * </p>
     * <p>
     * <i>Implementation note: this method is implemented by traversing
     * up from the descendant to this object; possible because a
     * Json object can only have one parent. So the operation is O(n).</i>
     * </p>
     * @param descendant the presumed descendant of this object to find in the tree
     * @return the path from this node to the descendant object
     * @since 5 (was called "find" prior to that)
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
                        JsonWriter.writeString(pk, 0, sb);
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
     * Put the specified value into this object with the specified key.
     * Although the key can be any value, it will collapse to a String when serialised as JSON.
     * If the object is not a Json, it will be converted with the factory set by {@link #setFactory setFactory()}, if any.
     * @param key the key - if this object is a list and the key is a non-negative integer, it will be used as the list index. Otherwise this object will be converted to a map. Must not be null.
     * @param value the value to insert, which must not be this or an ancestor of this
     * @return the object that was previously found at that path, which may be null
     */
    public Json put(Object key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }
        Json object;
        if (value == null) {
            object = new Json(null, null);
        } else if (value instanceof Json) {
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
        if (!isMap()) {
            if (isList()) {
                if (key instanceof Number && !(key instanceof Float || key instanceof Double)) {
                    long l = ((Number)key).longValue();
                    if (l >= 0 && l <= Integer.MAX_VALUE) {
                        int index = (int)l;
                        return buildlist(index, _listValue(), this, object);
                    }
                }
                convertToMap();
            } else {
                convertToMap();
            }
        }
        try {
            key = fixKey(key, false, 0);
        } catch (IOException e) {
            // can't happen
        }
        if (!(key instanceof String)) {
            setNonStringKeys();
        }
        Map<Object,Json> m = _mapValue();
        Json oldvalue = m.put(key, object);
        notify(this, key, oldvalue, object);
        return oldvalue;
    }

    /**
     * Return the specified child of this object, or null
     * if no value exists at the specified key
     * @param key the key, which should be an integer (for lists) or any value for maps
     * @return the Json object at that path or null if none exists
     */
    public Json get(Object key) {
        if (isMap()) {
            Map<Object,Json> map = _mapValue();
            return map.get(key);
        } else if (isList()) {
            if (key instanceof Number && !(key instanceof Float || key instanceof Double)) {
                long l = ((Number)key).longValue();
                if (l >= 0 && l <= Integer.MAX_VALUE) {
                    int index = (int)l;
                    List<Json> list = _listValue();
                    if (index >= 0 && index < list.size()) {
                        return list.get(index);
                    }
                }
            }
        }
        return null;
    }

    /**
     * If object is a Json object, return true if this object is a list or map, and contains that value.
     * Otherwise return true if this object is a list or map and contains a non-null/non-undefined object at that entry.
     * @param object the object
     * @return true if this is a list or map and the object is either a Json and present in this object as a value, or is used as a key for a non-null/non-undefined value
     * @since 5
     */
    public boolean has(Object object) {
        if (object instanceof Json) {
            if (isList()) {
                List<Json> list = _listValue();
                for (int i=0;i<list.size();i++) {
                    if (list.get(i) == object) {
                        return true;
                    }
                }
            } else if (isMap()) {
                Map<Object,Json> map = _mapValue();
                for (Iterator<Map.Entry<Object,Json>> i = map.entrySet().iterator();i.hasNext();) {
                    Map.Entry<Object,Json> e = i.next();
                    if (e.getValue() == object) {
                        return true;
                    }
                }
            }
        } else if (isList()) {
            if (object instanceof Number && !(object instanceof Float || object instanceof Double)) {
                long l = ((Number)object).longValue();
                if (l >= 0 && l <= Integer.MAX_VALUE) {
                    int index = (int)l;
                    List<Json> list = _listValue();
                    if (index >= 0 && index < list.size()) {
                        Json oldvalue = list.get(index);
                        if (!oldvalue.isNull() && !oldvalue.isUndefined()) {
                            return true;
                        }
                    }
                }
            }
        } else if (isMap()) {
            Map<Object,Json> map = _mapValue();
            Json oldvalue = map.get(object);
            return oldvalue != null && !oldvalue.isNull() && !oldvalue.isUndefined();
        }
        return false;
    }

    /**
     * Remove the item at the specified path from this object or one of its descendants.
     * Or, if object is a {@link Json}, remove that value from this list or map.
     * If called on an object that is not a list or map, this method has no effect.
     * @param object if a Json object, the value to remove, otherwise the key to remove from this object.
     * @return the object that was removed, or null if nothing was removed
     * @since 5
     */
    public Json remove(Object object) {
        if (object instanceof Json) {
            if (isList()) {
                List<Json> list = _listValue();
                for (int i=0;i<list.size();i++) {
                    if (list.get(i) == object) {
                        notify(this, Integer.valueOf(i), (Json)object, null);
                        list.remove(i);
                        return (Json)object;
                    }
                }
            } else if (isMap()) {
                Map<Object,Json> map = _mapValue();
                for (Iterator<Map.Entry<Object,Json>> i = map.entrySet().iterator();i.hasNext();) {
                    Map.Entry<Object,Json> e = i.next();
                    if (e.getValue() == object) {
                        notify(this, e.getKey(), (Json)object, null);
                        i.remove();
                        return (Json)object;
                    }
                }
            }
        } else if (isList()) {
            if (object instanceof Number && !(object instanceof Float || object instanceof Double)) {
                long l = ((Number)object).longValue();
                if (l >= 0 && l <= Integer.MAX_VALUE) {
                    int index = (int)l;
                    List<Json> list = _listValue();
                    if (index >= 0 && index < list.size()) {
                        Json oldvalue = list.get(index);
                        notify(this, Integer.valueOf(index), oldvalue, null);
                        list.remove(index);
                        return oldvalue;
                    }
                }
            }
        } else if (isMap()) {
            Map<Object,Json> map = _mapValue();
            Json oldvalue = map.get(object);
            notify(this, object, oldvalue, null);
            map.remove(object);
            return oldvalue;
        }
        return null;
    }

    /**
     * Put the specified value into this object or one of its descendants
     * by parsing the specified path.
     * If the path specifies a compound key than any intermediate descendants
     * are created as required. If the path specifies an existing object then
     * the old object (which may be a subtree) is removed and returned.
     * The object will be converted with the factory set by {@link #setFactory setFactory()}, if any.
     * @param path the key, which may be a compound key (e.g "a.b" or "a.b[2]") and must not be null
     * @param value the value to insert, which must not be this or an ancestor of this
     * @return the object that was previously found at that path, which may be null
     * @since 5 - prior that that revision was called "put"
     */
    public Json putPath(String path, Object value) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        Json object;
        if (value == null) {
            object = new Json(null);
        } else if (value instanceof Json) {
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
        if (isList()) {
            try {
                convert = !Character.isDigit(path.charAt(0)) || Integer.parseInt(path) < 0;
            } catch (Exception e) { }
        }
        if (convert) {
            convertToMap();
        }

        if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"' && isMap()) {
            // Shortcut
            path = readQuotedPath(path);
            Map<Object,Json> m = _mapValue();
            Json oldvalue = m.get(path);
            m.put(path, object);
            notify(this, path, oldvalue, object);
            return oldvalue;
        }
        return traverse(path, object);
    }

    /**
     * Return the specified descendant of this object, or null
     * if no value exists at the specified path.
     * @param path the path, which must not be null
     * @return the Json object at that path or null if none exists
     */
    public Json getPath(String path) {
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
                return _mapValue().get(path);
            } else if (isList()) {
                try {
                    return get(Integer.parseInt(path));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Return true if this object has a non-null/non-undefined descendant at the specified path.
     * @param path the path
     * @return true if this object is a list or map, it has the specified descendant and the descendant is not null
     */
    public boolean hasPath(String path) {
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
                    List<Json> l = _listValue();
                    json = ix >= 0 && ix < l.size() ? l.get(ix) : null;
                } catch (NumberFormatException e) { }
            }
            return json != null && !json.isNull() && !json.isUndefined();
        }
        return false;
    }

    /**
     * Remove the item at the specified path from this object or one of its descendants.
     * @param path the key, which may be a compound key (e.g "a.b" or "a.b[2]") and must not be null
     * @return the object that was removed, or null if nothing was removed
     * @since 5 was called remove() prior to version 5
     */
    public Json removePath(String path) {
        Json json = traverse(path, null);
        if (json != null) {
            boolean removed = false;
            Json parent = json.parent();
            if (parent == null) {
                // Should not be possible - ???
            } else if (parent.isList()) {
                int ix = ((Integer)json.getParentKey()).intValue();
                List<Json> list = _listValue();
                Json oldvalue = list.get(ix);
                notify(parent, Integer.valueOf(ix), oldvalue, null);
                list.remove(ix);
                removed = true;
            } else if (parent.isMap()) {
                Map<Object,Json> map = _mapValue();
                Object key = (Object)json.getParentKey();
                Json oldvalue = map.get(key);
                notify(parent, key, oldvalue, null);
                map.remove(key);
                removed = true;
            }
        }
        return json;
    }

    /**
     * Return the size of this object, or zero if this object is a number, string, buffer, boolean or null.
     * @return the size of the object
     */
    public int size() {
        if (isList()) {
            return _listValue().size();
        } else if (isMap()) {
            return _mapValue().size();
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
     * @return this
     * @since 2
     */
    public Json setTag(long tag) {
        this.tag = tag < 0 ? -1 : tag;
        return this;
    }

    /**
     * Return true if this node is a number, string, buffer, boolean, null or an empty map or list.
     * @return true if the node is a leaf node.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Return true if the specified child of this object is of type "null".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isNull()</code>.
     * It is <b>not the same</b> as the key being missing.
     * @param key the key
     * @return true if the child exists and is null
     */
    public boolean isNull(Object key) {
        Json j = get(key);
        return j != null && j.isNull();
    }

    /**
     * Return true if the specified child of this object is of type "undefined".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isUndefined()</code>.
     * It is <b>not the same</b> as the key being missing.
     * @param key the key
     * @return true if the child exists and is undefined
     */
    public boolean isUndefined(Object key) {
        Json j = get(key);
        return j != null && j.isUndefined();
    }

    /**
     * Return true if the specified child of this object is of type "buffer".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isBuffer()</code>.
     * @param key the key
     * @return true if the child exists and is a buffer
     */
    public boolean isBuffer(Object key) {
        Json j = get(key);
        return j != null && j.isBuffer();
    }

    /**
     * Return true if the specified child of this object is of type "string".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isString()</code>.
     * @param key the key
     * @return true if the child exists and is a string
     */
    public boolean isString(Object key) {
        Json j = get(key);
        return j != null && j.isString();
    }

    /**
     * Return true if the specified child of this object is of type "number".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isNumber()</code>
     * @param key the key
     * @return true if the child exists and is a number
     */
    public boolean isNumber(Object key) {
        Json j = get(key);
        return j != null && j.isNumber();
    }

    /**
     * Return true if the specified child of this object is of type "boolean".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isBoolean()</code>
     * @param key the key
     * @return true if the child exists and is a boolean
     */
    public boolean isBoolean(Object key) {
        Json j = get(key);
        return j != null && j.isBoolean();
    }

    /**
     * Return true if the specified child of this object is of type "list".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isList()</code>
     * @param key the key
     * @return true if the child exists and is a list
     */
    public boolean isList(Object key) {
        Json j = get(key);
        return j != null && j.isList();
    }

    /**
     * Return true if the specified wchild of this object is of type "map".
     * Equivalent to <code>get(key) != null &amp;&amp; get(key).isMap()</code>
     * @param key the key
     * @return true if the child exists and is a map
     */
    public boolean isMap(Object key) {
        Json j = get(key);
        return j != null && j.isMap();
    }

    /**
     * Return an Iterator that will descend through every leaf node under this
     * object in a depth-first traveral. The returned keys are converter to Strings; they are
     * relative to this node's path and will always return non-null if passed into {@link #getPath}.
     * If this is called on a leaf nodes, it returns an empty iterator
     * @return an Iterator as described
     */
    public Iterator<Map.Entry<String,Json>> leafIterator() {
        if (!isMap() && !isList()) {
            return Collections.<String,Json>emptyMap().entrySet().iterator();
        }
        return new Iterator<Map.Entry<String,Json>>() {
            List<Iterator<?>> stack;
            List<Object> string;
            Json last;

            {
                stack = new ArrayList<Iterator<?>>();
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
                Iterator<?> i = stack.get(stack.size() - 1);
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

            @Override public boolean hasNext() {
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

            @Override public Map.Entry<String,Json> next() {
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
                String s = sb.toString();
                if (s.startsWith(".")) {
                    s = s.substring(1);
                }
                return new AbstractMap.SimpleImmutableEntry<String,Json>(s, last);
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * For Json objects that are maps, sort the map keys.
     * For other types this is a no-op
     * @return this
     * @since 5
     */
    public Json sort() {
        if (core instanceof Map) {
            Map<Object,Json> o = _mapValue();
            Map<Object,Json> m = new TreeMap<Object,Json>(new Comparator<Object>() {
                public int compare(Object o1, Object o2) {
                    return o1 instanceof Number && o2 instanceof Number ? Double.valueOf(((Number)o1).doubleValue()).compareTo(((Number)o2).doubleValue()) : o1.toString().compareTo(o2.toString());
                }
            });
            m.putAll(o);
            o.clear();
            o.putAll(m);
        }
        return this;
    }

    private void convertToMap() {
        if (!isMap()) {
            Map<Object,Json> map = new LinkedHashMap<Object,Json>();
            if (isList()) {
                List<Json> list = _listValue();
                List<JsonListener> tlisteners = listeners;
                listeners = null;
                for (int i=0;i<list.size();i++) {
                    Json item = list.get(i);
                    Object key = Integer.valueOf(i);
                    map.put(key, item);
                    item.parent = this;
                    item.parentkey = key;
                    setNonStringKeys();
                }
                listeners = tlisteners;
            } else if (!isNull() && !isUndefined()) {
                notify(parent, null, this, null);
            }
            core = map;
            setSimpleString(false);
        }
    }

    private void convertToList() {
        if (!isList() && !isMap()) {
            notify(parent, null, this, null);
            core = new ArrayList<Json>();
            setSimpleString(false);
        }
    }

    private static final Object DOT = new Object() { public String toString() { return "."; } };
    private static final Object LB = new Object() { public String toString() { return "["; } };
    private static final Object RB = new Object() { public String toString() { return "]"; } };

    private static Json buildlist(int ix, List<Json> ilist, Json ctx, Json child) {
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
//        System.out.println("TRAVERSE: "+this+" path="+path);
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
            boolean last = i == len || (i == len -1 && c == ']');
            if (c == '\'' || c == '\"') {
                try {
                    CharSequenceReader r = new CharSequenceReader(path, i + 1, path.length() - i - 1);
                    new JsonReader.JsonStringReader(c, r).readString(); // discard
                    i = r.tell() - 1;
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
            if (stack[0] == DOT && stack[1] instanceof String) {
                // . WORD -
                // if WORD is int and ctx is array, cvt to int and add to array,
                // otherwise cvt to map and add to map
                Json newctx = null;
                String key = (String)stack[1];
                if (ctx.isList()) {
                    try {
                        int ix = Integer.parseInt(key);
                        List<Json> ilist = ctx._listValue();
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
                        Map<Object,Json> map = ctx._mapValue();
                        for (Map.Entry<Object,Json> e : map.entrySet()) {
                            if (key.equals(e.getKey().toString())) {
                                newctx = e.getValue();
                                break;
                            }
                        }
                        if (finalchild != null && (newctx == null || last)) {
                            Json t = map.put(key, newctx = last ? finalchild : new Json(null));
                            if (output == null) {
                                output = t;
                            }
                            notify(ctx, key, t, newctx);
                        }
                    }
                }
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
                        List<Json> list = ctx._listValue();
                        if (finalchild != null) {
                            Json t = buildlist(ix, list, ctx, last ? finalchild : null);
                            if (output == null) {
                                output = t;
                            }
                        }
                        newctx = ix >= 0 && ix < list.size() ? list.get(ix) : null;
                    }
                }
                if (newctx == null) {
                    // [ STR ] - ctx to map, and add STR to map
                    String key = stack[1].toString();
                    if (finalchild != null) {
                        ctx.convertToMap();
                    }
                    if (ctx.isMap()) {
                        Map<Object,Json> map = ctx._mapValue();
                        for (Map.Entry<Object,Json> e : map.entrySet()) {
                            if (key.equals(e.getKey().toString())) {
                                newctx = e.getValue();
                                break;
                            }
                        }
                        if (finalchild != null && (newctx == null || last)) {
                            Json t = map.put(key, newctx = last ? finalchild : new Json(null));
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
     * Return the type of this node, which may be "number", "string", "boolean", "list", "map", "buffer", "null" or (since v5) "undefined"
     * @return the object type
     */
    public String type() {
        if (isBoolean()) {
            return "boolean";
        } else if (isNumber()) {
            return "number";
        } else if (isString()) {
            return "string";
        } else if (isMap()) {
            return "map";
        } else if (isList()) {
            return "list";
        } else if (isBuffer()) {
            return "buffer";
        } else if (isNull()) {
            return "null";
        } else if (isUndefined()) {
            return "undefined";
        } else {
            return "unknown-"+core.getClass().getName();        // Shouldn't happen
        }
    }

    /**
     * Return true if this node is null
     * @return true if the object is null
     */
    public boolean isNull() {
        return core == NULL;
    }

    /**
     * Return true if this node is undefined.
     * Undefined is a CBOR concept; in JSON serialization, this will collapse to null
     * @return true if the object is null
     * @since 5
     */
    public boolean isUndefined() {
        return core == UNDEFINED;
    }

    /**
     * Return true if this node is a "number"
     * @return true if the object is a number
     */
    public boolean isNumber() {
        return core instanceof Number;
    }

    /**
     * Return true if this node is a "boolean"
     * @return true if the object is a boolean
     */
    public boolean isBoolean() {
        return core instanceof Boolean;
    }

    /**
     * Return true if this node is a "string"
     * @return true if the object is a string
     */
    public boolean isString() {
        return core instanceof CharSequence;
    }

    /**
     * Return true if this node is a "buffer"
     * @return true if the object is a buffer
     */
    public boolean isBuffer() {
        return core instanceof ByteBuffer;
    }

    /**
     * Return true if this node is a "map"
     * @return true if the object is a map
     */
    public boolean isMap() {
        return core instanceof Map;
    }

    /**
     * Return true if this node is a "list"
     * @return true if the object is a list
     */
    public boolean isList() {
        return core instanceof List;
    }

    /**
     * Return the value of this Json as a plain Java object,
     * which may be a {@link String}, {@link Boolean}, {@link Number},
     * {@link List}, {@link Map}, {@link ByteBuffer} or null.
     * @return the object value
     */
    public Object value() {
        return core;
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
     * @return this
     */
    public Json setValue(Json json) {
        core = json == null ? NULL : json.core;
        setSimpleString(json != null && json.isSimpleString());
        return this;
    }

    /**
     * Return the value of this node as a String. This method will always
     * succeed. It will return null if this object represents null, but it
     * otherwise identical to {@link #toString}
     * @return the string value of this object
     */
    public String stringValue() {
        if (core == NULL || core == UNDEFINED) {
            return null;
        } else if (core instanceof String) {
            return (String)core;
        } else if (isBuffer()) {
            ByteBuffer buf = bufferValue();
            StringBuilder sb = new StringBuilder(buf.remaining() * 4 / 3 + 2);
            try {
                Base64OutputStream out = new Base64OutputStream(sb, false);
                writeBuffer(out);
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sb.toString();
        } else {
            return core.toString();
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #stringValue} method on it, otherwise return null
     * @return the string value of that object
     * @param key the key
     * @since 4
     */
    public String stringValue(Object key) {
        Json j = get(key);
        return j == null ? null : j.stringValue();
    }

    private static String nice(char c) {
        if (c >= ' ' && c < 0x80) {
            return "\"" + c + "\"";
        } else {
            return "U+" + Integer.toHexString(c);
        }
    }

    private static final int BASE64[] = new int[256];
    static {
        String s = new String(Base64OutputStream.BASE64_NORMAL);
        for (int i=0;i<256;i++) {
            BASE64[i] = s.indexOf(Character.toString((char)i));
        }
        // So we can read any variation
        BASE64['-'] = BASE64['+'];
        BASE64['_'] = BASE64['/'];
        // BASE64[','] = BASE64['/'];    RFC3501 which no-one uses
    }

    /**
     * <p>
     * Return the value of this node as a ByteBuffer. ByteBuffers only exist
     * natively in the CBOR and Msgpack serialization. Note that every ByteBuffer created
     * by the API will be backed by an array of the same size; it is guaranteed
     * that
     * <code>buffer.array().length == buffer.limit() &amp;&amp; buffer.arrayOffset() == 0</code>.
     * </p>
     * <ul>
     * <li>A string will be decoded using Base64 (no padding) and, if valid
     * returned as a ByteBuffer. If invalid, a ClassCastException will be thrown</li>
     * <li>null will return null</li>
     * </ul>
     * @see JsonReadOptions
     * @throws ClassCastException if none of these conditions are met
     * @return the buffer value of this object, which will always have position=0
     * @since 2
     */
    public ByteBuffer bufferValue() {
        if (core == NULL || core == UNDEFINED) {
            return null;
        } else if (core instanceof ByteBuffer) {
            return (ByteBuffer)((Buffer)core).position(0);  // cast for old Java compilation
        } else if (core instanceof CharSequence) {
            CharSequence value = (CharSequence)core;
            int ilen = value.length();
            if (ilen == 0) {
                return ByteBuffer.allocate(0);
            }
            if (ilen > 3) {
                if (value.charAt(ilen - 1) == '=') {
                    ilen--;
                    if (value.charAt(ilen - 1) == '=') {
                        ilen--;
                    }
                }
            }
            int olen = ilen / 4 * 3;
            if ((ilen & 3) == 1) {
                throw new ClassCastException("Base64 failed, length invalid");
            } else if ((ilen & 3) == 2) {
                olen++;
            } else if ((ilen & 3) == 3) {
                olen += 2;
            }
            ByteBuffer buf = ByteBuffer.allocate(olen);
            for (int i=0;i<ilen;i++) {
                int c = value.charAt(i);
                if (c > 255 || (c = BASE64[c]) < 0) {
                    throw new ClassCastException("Base64 failed on " + nice(value.charAt(i))+" at " + i);
                }
                int a = c << 18;
                c = value.charAt(++i);
                if (c > 255 || (c = BASE64[c]) < 0) {
                    throw new ClassCastException("Base64 failed on " + nice(value.charAt(i))+" at " + i);
                }
                a |= c << 12;
                if (++i == ilen) {
                    buf.put((byte)(a >> 16));
                    break;
                } else {
                    c = value.charAt(i);
                    if (c > 255 || (c = BASE64[c]) < 0) {
                        throw new ClassCastException("Base64 failed on " + nice(value.charAt(i))+" at " + i);
                    }
                    a |= c << 6;
                    if (++i == ilen) {
                        buf.put((byte)(a >> 16));
                        buf.put((byte)(a >> 8));
                        break;
                    } else {
                        c = value.charAt(i);
                        if (c > 255 || (c = BASE64[c]) < 0) {
                            throw new ClassCastException("Base64 failed on " + nice(value.charAt(i))+" at " + i);
                        }
                        a |= c;
                        buf.put((byte)(a >> 16));
                        buf.put((byte)(a >> 8));
                        buf.put((byte)a);
                    }
                }
            }
            return buf;
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #bufferValue} method on it, otherwise return null
     * @return the buffer value of that object
     * @param key the key
     * @since 4
     */
    public ByteBuffer bufferValue(Object key) {
        Json j = get(key);
        return j == null ? null : j.bufferValue();
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
        if (core == NULL || core == UNDEFINED) {
            return null;
        } else if (core instanceof Number) {
            return (Number)core;
        } else if (core instanceof Boolean) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert boolean " + core + " to number in strict mode");
            } else {
                return ((Boolean)core).booleanValue() ? 1 : 0;
            }
        } else if (core instanceof CharSequence) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert string \"" + core + "\" to number in strict mode");
            } else {
                CharSequence value = (CharSequence)core;
                SimplestReader r = new SimplestReader(value);
                // Necessary to ensure we only parse exactly what is specified at json.org
                boolean valid = true;
                boolean real = false;
                int c = r.read();
                if (c == '-') {
                    c = r.read();
                }
                if (c >= '1' && c <= '9') {
                    do {
                        c = r.read();
                    } while (c >= '0' && c <= '9');
                } else if (c == '0') {
                    c = r.read();
                } else {
                    valid = false;
                }
                if (valid && c == '.') {
                    real = true;
                    c = r.read();
                    if (c >= '0' && c <= '9') {
                        do {
                            c = r.read();
                        } while (c >= '0' && c <= '9');
                    } else {
                        valid = false;
                    }
                }
                if (valid && (c == 'e' || c == 'E')) {
                    real = true;
                    c = r.read();
                    if (c == '-' || c == '+') {
                        c = r.read();
                    }
                    if (c >= '0' && c <= '9') {
                        do {
                            c = r.read();
                        } while (c >= '0' && c <= '9');
                    } else {
                        valid = false;
                    }
                }
                if (valid && c == -1) {
                    try {
                        if (!real && value.length() < 10) {
                            return Integer.valueOf(value.toString());
                        }
                        if (!real && value.length() < 19) {
                            return Long.valueOf(value.toString());
                        }
                        return Double.valueOf(value.toString());
                    } catch (NumberFormatException e) { }
                }
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Cannot convert ");
                    JsonWriter.writeString(value, 0, sb);
                    sb.append(" to number");
                    throw new ClassCastException(sb.toString());
                } catch (IOException e2) {
                    throw new RuntimeException(e2); // can't happen
                }
            }
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #intValue} method on it, otherwise return null
     * @return the number value of that object
     * @param key the key
     * @since 4
     */
    public Number numberValue(Object key) {
        Json j = get(key);
        return j == null ? null : j.numberValue();
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
        if (core == NULL || core == UNDEFINED) {
            throw new ClassCastException("Value is null");
        } else if (core instanceof Number) {
            return ((Number)core).intValue();
        } else if (core instanceof Boolean) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert boolean " + core + " to integer in strict mode");
            } else {
                return ((Boolean)core).booleanValue() ? 1 : 0;
            }
        } else if (core instanceof CharSequence) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert string \"" + core + "\" to integer in strict mode");
            } else {
                CharSequence value = (CharSequence)core;
                try {
                    if (value.length() < 12) {
                        return Integer.parseInt(value.toString());     // Faster than numberValue.toString()
                    }
                } catch (NumberFormatException e) {}
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Cannot convert ");
                    JsonWriter.writeString(value, 0, sb);
                    sb.append(" to int");
                    throw new ClassCastException(sb.toString());
                } catch (IOException e2) {
                    throw new RuntimeException(e2); // can't happen
                }
            }
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #intValue} method on it, otherwise return 0
     * @return the int value of that object
     * @param key the key
     * @since 4
     */
    public int intValue(Object key) {
        Json j = get(key);
        return j == null ? 0 : j.intValue();
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
        if (core == NULL || core == UNDEFINED) {
            throw new ClassCastException("Value is null");
        } else if (core instanceof Number) {
            return ((Number)core).longValue();
        } else if (core instanceof Boolean) {
            return intValue();
        } else if (core instanceof CharSequence) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert string \"" + core + "\" to long in strict mode");
            } else {
                CharSequence value = core.toString();
                try {
                    if (value.length() < 20) {
                        return Long.parseLong(value.toString()); // Faster than numberValue.toString()
                    }
                } catch (NumberFormatException e) {}
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Cannot convert ");
                    JsonWriter.writeString(value, 0, sb);
                    sb.append(" to long");
                    throw new ClassCastException(sb.toString());
                } catch (IOException e2) {
                    throw new RuntimeException(e2); // can't happen
                }
            }
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #longValue} method on it, otherwise return 0
     * @return the long value of that object
     * @param key the key
     * @since 4
     */
    public long longValue(Object key) {
        Json j = get(key);
        return j == null ? 0 : j.intValue();
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
        if (core == NULL || core == UNDEFINED) {
            throw new ClassCastException("Value is null");
        } else {
            return numberValue().floatValue();
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #floatValue} method on it, otherwise return 0
     * @return the float value of that object
     * @param key the key
     * @since 4
     */
    public float floatValue(Object key) {
        Json j = get(key);
        return j == null ? 0 : j.floatValue();
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
        if (core == NULL || core == UNDEFINED) {
            throw new ClassCastException("Value is null");
        } else {
            return numberValue().doubleValue();
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #doubleValue} method on it, otherwise return 0
     * @return the double value of that object
     * @param key the key
     * @since 4
     */
    public double doubleValue(Object key) {
        Json j = get(key);
        return j == null ? 0 : j.doubleValue();
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
        if (core == NULL || core == UNDEFINED) {
            throw new ClassCastException("Value is null");
        } else if (core instanceof Boolean) {
            return ((Boolean)core).booleanValue();
        } else if (core instanceof CharSequence) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert string \"" + core + "\" to boolean in strict mode");
            } else {
                CharSequence value = (CharSequence)core;
                return !(value.toString().equals("false"));
            }
        } else if (core instanceof Number) {
            if (isStrict()) {
                throw new ClassCastException("Cannot convert number " + core + " to boolean in strict mode");
            } else {
                return floatValue() == 0;
            }
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified child of this object exists call
     * the {@link #booleanValue} method on it, otherwise return false
     * @return the boolean value of that object
     * @param key the key
     * @since 4
     */
    public boolean booleanValue(Object key) {
        Json j = get(key);
        return j == null ? false : j.booleanValue();
    }

    @SuppressWarnings("unchecked") Map<Object,Json> _mapValue() {
        return (Map<Object,Json>)core;
    }

    @SuppressWarnings("unchecked") List<Json> _listValue() {
        return (List<Json>)core;
    }

    /**
     * Return the value of this node as a map. The returned Map is read-only
     * @throws ClassCastException if the node is not a map.
     * @return the read-only map value of this object
     */
    public Map<Object,Json> mapValue() {
        if (core == NULL || core == UNDEFINED) {
            return null;
        } else if (core instanceof Map) {
            return Collections.<Object,Json>unmodifiableMap(_mapValue());
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified descendant of this object exists call
     * the {@link #mapValue} method on it, otherwise return null
     * @return the read-only map value of that object
     * @param key the key
     * @since 4
     */
    public Map<Object,Json> mapValue(Object key) {
        Json j = get(key);
        return j == null ? null : j.mapValue();
    }

    /**
     * Return the value of this node as a list. The returned List is read-only
     * @throws ClassCastException if the node is not a list
     * @return the read-only list value of this object
     */
    public List<Json> listValue() {
        if (core == NULL || core == UNDEFINED) {
            return null;
        } else if (core instanceof List) {
            return Collections.<Json>unmodifiableList(_listValue());
        } else {
            throw new ClassCastException("Value is a " + type());
        }
    }

    /**
     * If the specified wchild of this object exists call
     * the {@link #listValue} method on it, otherwise return null
     * @return the read-only list value of that object
     * @param key the key
     * @since 4
     */
    public List<Json> listValue(Object key) {
        Json j = get(key);
        return j == null ? null : j.listValue();
    }

    /**
     * Return this Json value as a plain object. Unlike {@link #value} this
     * method will traverse through its descendants, converting Json objects
     * to plain objects so that the returned object is guaranteed to have
     * no reference to this package. The returned value is as follows
     * <ul>
     * <li>If this object {@link #isNull is null}, return null</li>
     * <li>If a {@link getFactory factory} is set and {@link JsonFactory#fromJson JsonFactory.fromJson()} returns a non-null value, return that value</li>
     * <li>If this value is a string, buffer, number or boolean, return the value from {@link #value()}</li>
     * <li>If this value is a list or map, populate the map values with the output of this method and return as a Map&lt;String,Object&gt; or List&lt;Object&gt;</li>
     * </ul>
     * @return a String, Number, Boolean, Map&lt;String,Object&gt;, List&lt;Object&gt; or null as described
     */
    public Object objectValue() {
        if (isNull()) {
            return null;
        } else if (isUndefined()) {
            return UNDEFINED;
        }
        if (factory != null) {
            Object o = factory.fromJson(this);
            if (o != null) {
                return o;
            }
        }
        if (isMap()) {
            Map<Object,Object> out = new LinkedHashMap<Object,Object>(_mapValue());
            for (Map.Entry<Object,Object> e : out.entrySet()) {
                e.setValue(((Json)e.getValue()).objectValue());
            }
            return out;
        } else if (isList()) {
            List<Object> out = new ArrayList<Object>(size());
            for (Json o : _listValue()) {
                out.add(o.objectValue());
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
        return core == NULL || core == UNDEFINED ? 0 : core.hashCode();
    }

    /**
     * Return true if the specified object is a Json object and has an equal {@link #value() value} and {@link #getTag tag}
     * @param o the object to compare to
     */
    public boolean equals(Object o) {
        if (o instanceof Json) {
            Json j = (Json)o;
            if (j.getTag() != getTag()) {
                return false;
            }
            if (core == NULL || core == UNDEFINED) {
                return j.core == core;
            } else if (core.getClass() == j.core.getClass()) {
                return core.equals(j.core);
            } else if (core instanceof Number) {
                if (j.core instanceof Number) {
                    Number n1 = (Number)core;
                    Number n2 = (Number)j.core;
                    // Ensure we only care about the numeric value, not the storage type
                    BigDecimal b1 = n1 instanceof BigDecimal ? (BigDecimal)n1 : n1 instanceof Float || n1 instanceof Double ? BigDecimal.valueOf(n1.doubleValue()) : n1 instanceof BigInteger ? new BigDecimal((BigInteger)n1) : BigDecimal.valueOf(n1.longValue());
                    BigDecimal b2 = n2 instanceof BigDecimal ? (BigDecimal)n2 : n2 instanceof Float || n2 instanceof Double ? BigDecimal.valueOf(n2.doubleValue()) : n2 instanceof BigInteger ? new BigDecimal((BigInteger)n2) : BigDecimal.valueOf(n2.longValue());
                    return b1.compareTo(b2) == 0;
                }
            } else if (core instanceof CharSequence && j.core instanceof CharSequence) {
                CharSequence c1 = (CharSequence)core;
                CharSequence c2 = (CharSequence)j.core;
                int s = c1.length();
                if (s == c2.length()) {
                    for (int i=0;i<s;i++) {
                        if (c1.charAt(i) != c2.charAt(i)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            } else if (isBuffer()) {
                if (j.isBuffer()) {
                    return ((Buffer)bufferValue()).position(0).equals(((Buffer)j.bufferValue()).position(0));
                }
            } else {
                return core.equals(j.core);
            }
        }
        return false;
    }

    /**
     * Return a String representation of this Json object. Roughly equivalent
     * to calling <code>return {@link #write write}(new StringBuilder(), null).toString()</code>
     * @return the serialized object
     */
    public String toString() {
        return toString(null);
    }

    /**
     * Return a String representation of this Json object with the specified
     * serialization options. Equivalent
     * to calling <code>return {@link #write write}(new StringBuilder(), options).toString()</code>
     * @param options the {@link JsonWriteOptions} to use for serializing, or null to use the default
     * @return the serialized object
     * @since 5
     */
    public String toString(JsonWriteOptions options) {
        if (options == null) {
            options = new JsonWriteOptions().setAllowNaN(true);
        }
        try {
            StringBuilder sb = new StringBuilder();
            /*
            if (getTag() >= 0) {
                sb.append(getTag() + "(");
            }
            */
            write(sb, options);
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

    /**
     * Return a Cbor representation of this Json object.
     * @return the Cbor representation as an array-backed ByteBuffer
     * @since 5
     */
    public ByteBuffer toCbor() {
        return toCbor(new JsonWriteOptions());
    }

    /**
     * Return a Cbor representation of this Json object with the specified options
     * @param options the {@link JsonWriteOptions} to use for serializing, or null to use the default
     * @return the Cbor representation as an array-backed ByteBuffer
     * @since 5
     */
    public ByteBuffer toCbor(JsonWriteOptions options) {
        if (options == null) {
            options = new JsonWriteOptions();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeCbor(out, options);
            return ByteBuffer.wrap(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write this objects BufferValue to the specified OutputStream.
     * Can be overridden if the BufferValue needs to be derived externally,
     * perhaps because it's very large and of indeterminate length.
     * @since 4
     */
    protected void writeBuffer(OutputStream out) throws IOException {
        ByteBuffer buf = (ByteBuffer)((Buffer)bufferValue()).position(0); // cast for old Java compilation
        Channels.newChannel(out).write(buf);
    }

    /**
     * Write this objects StringValue to the specified Appendable.
     * Can be overridden if the Appendable needs to be derived externally,
     * perhaps because it's very large and of indeterminate length.
     * @since 4
     */
    protected void writeString(Appendable out) throws IOException {
        out.append((CharSequence)core);
    }

    private static class SimplestReader {
        private final CharSequence s;
        private int i;
        SimplestReader(CharSequence s) {
            this.s = s;
        }
        int read() {
            return i == s.length() ? -1 : s.charAt(i++);
        }
    }

}
