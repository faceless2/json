package com.bfo.json;

import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/**
 * <p>
 * This class controls the details of the {@link Json#read Json.read()} call.
 * The various flags set on this class can control the read process, and in
 * some cases (e.g. {@link #setStrictTypes}) determines how the objects behave
 * after they are read.
 * </p><p>
 * The class uses a "fluent" style to enable setting multiple options at once, eg.
 * </p>
 * <pre>
 *  json.read(out, new JsonReadOptions().setAllowUnquotedKey(true).setAllowComments(true));
 * </pre>
 * <p>
 * All flags default to false.
 * </p>
 */
public class JsonReadOptions {

    private boolean nfc;
    private boolean unquotedKey;
    private boolean trailingComma;
    private boolean comments;
    private boolean bigDecimal;
    private boolean cborFailOnUnknownTypes;
    private boolean failOnNonStringKeys;
    private boolean nocontext;
    private byte storeOptions;
    private int fastStringLength = 262144;
    private Filter filter;
    private CodingErrorAction codingErrorAction = CodingErrorAction.REPLACE;

    static final byte FLAG_STRICT = 1;
    static final byte FLAG_LOOSEEMPTY = 2;
    static final byte FLAG_CBOR = 4;

    /**
     * Whether to allow unquotedKey keys in the JSON input. This
     * is not officially allowed, but still common. The default is false.
     * @param unquotedKey the flag
     * @return this
     */
    public JsonReadOptions setAllowUnquotedKey(boolean unquotedKey) {
        this.unquotedKey = unquotedKey;
        return this;
    }

    /**
     * Return the value of the "unquotedKey" flag as set by {@link #setAllowUnquotedKey}
     * @return the flag
     */
    public boolean isAllowUnquotedKey() {
        return unquotedKey;
    }

    /**
     * Whether to allow trailing commas in the JSON input, eg <code>["a",]</code>. This
     * is not officially allowed, but still common.
     * @param trailingComma the flag
     * @return this
     */
    public JsonReadOptions setAllowTrailingComma(boolean trailingComma) {
        this.trailingComma = trailingComma;
        return this;
    }

    /**
     * Return the value of the "trailingComma" flag as set by {@link #setAllowTrailingComma}
     * @return the flag
     */
    public boolean isAllowTrailingComma() {
        return trailingComma;
    }

    /**
     * Whether to allow comments in the JSON input, eg <code>["a"]/&#x2a;comment&#x2a;/</code>. This
     * is not officially allowed, but still common.
     * @param comments the flag
     * @return this
     */
    public JsonReadOptions setAllowComments(boolean comments) {
        this.comments = comments;
        return this;
    }

    /**
     * Return the value of the "comments" flag as set by {@link #setAllowComments}
     * @return the flag
     */
    public boolean isAllowComments() {
        return comments;
    }

    /**
     * Whether to turn off tracking of the line, column and context of the reader when
     * reading from a file, which is used when reporting errors. This is on by default,
     * but slows down reading by about 1.5%
     * @param nocontext the flag
     * @return this
     */
    public JsonReadOptions setContextFree(boolean nocontext) {
        this.nocontext = nocontext;
        return this;
    }

    /**
     * Return the value of the "nocontext" flag as set by {@link #setContextFree}
     * @return the flag
     */
    public boolean isContextFree() {
        return nocontext;
    }

    /**
     * Set whether to normalize all Strings (including map keys) to Unicode {@link java.text.Normalizer.Form#NFC normal form C}
     * @param nfc the flag
     * @return this
     */
    public JsonReadOptions setNFC(boolean nfc) {
        this.nfc = nfc;
        return this;
    }

    /**
     * Return the value of the "nfc" flag as set by {@link #setNFC}
     * @return the flag
     */
    public boolean isNFC() {
        return nfc;
    }

    /**
     * Set whether real numbers that cannot be guaranteed to be exactly the same when stored as
     * a {@link java.lang.Double} should be stored as a {@link java.math.BigDecimal}.
     * The default is false, which means every real number read by the JsonReader will
     * be converted to a Double.
     * @param bigDecimal the flag
     * @return this
     */
    public JsonReadOptions setBigDecimal(boolean bigDecimal) {
        this.bigDecimal = bigDecimal;
        return this;
    }

    /**
     * Return the value of the "bigDecimal" flag as set by {@link #setBigDecimal}
     * @return the flag
     */
    public boolean isBigDecimal() {
        return bigDecimal;
    }

    /**
     * Determines whether objects read by the JsonReader will be loosely typed. With this
     * flag set, calling {@link Json#intValue} on the string "123" will result in a
     * {@link ClassCastException} being thrown rather than a silent conversion to integer.
     * @param strictTypes the flag
     * @return this
     */
    public JsonReadOptions setStrictTypes(boolean strictTypes) {
        if (strictTypes) {
            storeOptions |= FLAG_STRICT;
        } else {
            storeOptions &= ~FLAG_STRICT;
        }
        return this;
    }

    /**
     * Return the value of the "strictTypes" flag as set by {@link #setStrictTypes}
     * @return the flag
     */
    public boolean isStrictTypes() {
        return (storeOptions & FLAG_STRICT) != 0;
    }

    /**
     * When the {@link #setStrictTypes strictTypes} flag is not set, this flag will determine
     * whether empty strings evaluate to zero when treated as a number and false when treated
     * as a boolean.
     * @param looseEmptyStrings the flag
     * @return this
     */
    public JsonReadOptions setLooseEmptyStrings(boolean looseEmptyStrings) {
        if (looseEmptyStrings) {
            storeOptions |= FLAG_LOOSEEMPTY;
        } else {
            storeOptions &= ~FLAG_LOOSEEMPTY;
        }
        return this;
    }

    /**
     * Return the value of the "looseEmptyString" flag as set by {@link #setLooseEmptyStrings}
     * @return the flag
     */
    public boolean isLooseEmptyStrings() {
        return (storeOptions & FLAG_LOOSEEMPTY) != 0;
    }

    byte storeOptions() {
        return storeOptions;
    }

    /**
     * When reading CBOR/Msgpack, if a String is encountered where the UTF-8 encoding
     * is incorrect, the specified action will be performed.
     * The default is {@link CodingErrorAction#REPLACE}
     * @param action the action
     * @return this
     * @since 2
     */
    public JsonReadOptions setCborStringCodingErrorAction(CodingErrorAction action) {
        this.codingErrorAction = action;
        return this;
    }

    /**
     * Return the CodingErrorAction set by {@link #setCborStringCodingErrorAction}
     * is incorrect, the specified action will be performed.
     * The default is {@link CodingErrorAction#REPLACE}, which is also the fastest
     * option.
     * @return the action
     * @since 2
     */
    public CodingErrorAction getCborStringCodingErrorAction() {
        return codingErrorAction;
    }

    /**
     * When reading a CBOR stream that contains undefined, but technically valid types,
     * by default these will be collapsed to null with a tag set on them to indicate the
     * original type. Setting this flag will, instead, cause the stream to fail.
     * @param flag the flag
     * @return this
     * @since 2
     */
    public JsonReadOptions setCborFailOnUnknownTypes(boolean flag) {
        cborFailOnUnknownTypes = flag;
        return this;
    }

    /**
     * Return the value of the "cborFailOnUnknownTypes" flag, as set by
     * {@link setCborFailOnUnknownTypes}
     * @since 2
     * @return the flag
     */
    public boolean isCborFailOnUnknownTypes() {
        return cborFailOnUnknownTypes;
    }

    /**
     * When reading CBOR/Msgpack and a map key is encountered that is not a String,
     * fail rather than converting it silently to a String. This API (and Json) only
     * allow map keys to be strings.
     * @param flag the flag
     * @return this
     * @since 3
     */
    public JsonReadOptions setFailOnNonStringKeys(boolean flag) {
        failOnNonStringKeys = flag;
        return this;
    }

    /**
     * Return the value of the "failOnNonStringKeys" flag, as set by
     * {@link #setFailOnNonStringKeys}
     * @since 3
     * @return the flag
     */
    public boolean isFailOnNonStringKeys() {
        return failOnNonStringKeys;
    }

    /**
     * Set the value of the "fastStringLength" value - strings loaded from a binary source
     * less than this value will be loaded directly into memory, whereas above this length
     * they will be streamed. The default is 262144 (256KB).
     * @param len the length
     * @since 4
     * @return this
     */
    public JsonReadOptions setFastStringLength(int len) {
        fastStringLength = len;
        return this;
    }

    /**
     * Return the value of the "fastStringLength" as set by {@link #setFastStringLength}
     * @since 4
     * @return the fastStringLength value
     */
    public int getFastStringLength() {
        return fastStringLength;
    }

    /**
     * Set the {@link Filter} that will be used to convert objects when reading
     * @param filter the filter, or null for no filter
     * @return this
     * @since 4
     */
    public JsonReadOptions setFilter(Filter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Return the value of the Filter as set by {@link #setFilter}
     * @since 4
     * @return the filter
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * A Filter which can be applied during reading of data. The
     * interface will have its enter/exit methods called to show
     * where in the input data is being read, and the various
     * "create" methods can be overridden if necessary. For example,
     * if a field is known to be particularly large:
     * <pre>
     * JsonReadOptions.Filter f = new JsonReadOptions.Filter() {
     *   public void enter(Json parent, Json key) {
     *     where.append("." + key);
     *   }
     *   public void exit(Json parent, Json key) {
     *     where = where.substring(0, where.lastIndexOf("."));
     *   }
     *   public void createBuffer(InputStream in, long length) {
     *     if (where.toString().equals("large.file.data")) {
     *       final Path path = Paths.get("largefile");
     *       Json j = new Json(ByteBuffer.allocate(0)) {
     *         protected void writeBuffer(OutputStream out) {
     *           Files.copy(path, out);
     *         }
     *       };
     *       Files.copy(in, path);
     *     } else {
     *       return super.createBuffer(in, length);
     *     }
     *   }
     * }
     * </pre>
     * @since 4
     */
    public static class Filter {

        /**
         * Called once when the Json reading begins
         * @throws IOException if an error occurs during processing
         */
        public void initialize() throws IOException {
        }

        /**
         * Called once when the Json reading ends
         * @param json the completed object
         * @throws IOException if an error occurs during processing
         */
        public void complete(Json json) throws IOException {
        }

        /**
         * Called before reading each entry in a Map.
         * @param parent the current map
         * @param key the key of the next entry in the map
         * @throws IOException if an error occurs during processing
         */
        public void enter(Json parent, String key) throws IOException {
        }

        /**
         * Called after reading each entry in a Map.
         * @param parent the current map
         * @param key the key of the entry just read in the map
         * @throws IOException if an error occurs during processing
         */
        public void exit(Json parent, String key) throws IOException {
        }

        /**
         * Called before reading each entry in a List.
         * @param parent the current list
         * @param key the key of the next entry in the list
         * @throws IOException if an error occurs during processing
         */
        public void enter(Json parent, int key) throws IOException {
        }

        /**
         * Called after reading each entry in a List.
         * @param parent the current list
         * @param key the key of the entry just read in the list
         * @throws IOException if an error occurs during processing
         */
        public void exit(Json parent, int key) throws IOException {
        }

        /**
         * Create a new "map" object
         * @return the new Json object
         * @throws IOException if an error occurs during reading
         */
        public Json createMap() throws IOException {
            return new Json(Collections.EMPTY_MAP, null);
        }

        /**
         * Create a new "list" object
         * @return the new Json object
         * @throws IOException if an error occurs during reading
         */
        public Json createList() throws IOException {
            return new Json(Collections.EMPTY_LIST, null);
        }

        /**
         * Create a new "null" object
         * @return the new Json object
         * @throws IOException if an error occurs during reading
         */
        public Json createNull() throws IOException {
            return new Json(null, null);
        }

        /**
         * Create a new "boolean" object
         * @param b the boolean
         * @return the new Json object
         * @throws IOException if an error occurs during reading
         */
        public Json createBoolean(boolean b) throws IOException {
            return new Json(b, null);
        }

        /**
         * Create a new "number" object
         * @param n the number
         * @return the new Json object
         * @throws IOException if creation failed
         */
        public Json createNumber(Number n) throws IOException {
            return new Json(n, null);
        }

        /**
         * Create a new "string" object
         * @param in the reader to read the string from
         * @param length the number of characters that will be read from reader, or -1 if unknown
         * @return the new Json object
         * @throws IOException if an error occurs during reading
         */
        public Json createString(Reader in, long length) throws IOException {
            if (in instanceof StringReader) {
                String s = in.toString();
                return new Json(s, null);
            } else if (in instanceof JsonReader.JsonStringReader) {
                return new Json(((JsonReader.JsonStringReader)in).readString(), null);
            } else if (length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Can't allocate "+length+" String");
            } else if (length >= 0) {
                char[] buf = new char[(int)length];
                int c, off = 0;
                while ((c=in.read(buf, off, buf.length - off)) >= 0) {
                    off += c;
                }
                if (off != buf.length) {
                    throw new EOFException();
                }
                return new Json(java.nio.CharBuffer.wrap(buf, 0, buf.length), null);
            } else {
                StringBuilder sb = new StringBuilder(8192);
                char[] buf = new char[8192];
                int c;
                while ((c=in.read(buf, 0, buf.length)) >= 0) {
                    sb.append(buf, 0, c);
                }
                return new Json(sb.toString(), null);
            }
        }

        /**
         * Create a new "buffer" object
         * @param in the InputStream to read the buffer from
         * @param length the number of bytes that will be read from reader, or -1 if unknown
         * @return the new Json object
         * @throws IOException if an error occurs during reading
         */
        public Json createBuffer(InputStream in, long length) throws IOException {
            if (length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Can't allocate "+length+" ByteBuffer");
            } else if (length >= 0) {
                byte[] buf = new byte[(int)length];
                int c, off = 0;
                while ((c=in.read(buf, off, buf.length - off)) >= 0) {
                    off += c;
                }
                if (off != buf.length) {
                    throw new EOFException();
                }
                return new Json(ByteBuffer.wrap(buf, 0, buf.length), null);
            } else {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int c;
                while ((c=in.read(buf)) >= 0) {
                    out.write(buf, 0, c);
                }
                buf = out.toByteArray();
                return new Json(ByteBuffer.wrap(buf, 0, buf.length), null);
            }
        }

    }

}
