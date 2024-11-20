package com.bfo.json;

import java.nio.*;
import java.nio.channels.ReadableByteChannel;
import java.io.*;
import java.util.*;

/**
 * A JSON writer
 */
public class JsonWriter implements JsonStream {
    
    private static final char[] B64STD_C, B64URL_C;
    private static final int STATE_ROOT         = 0x0001;
    private static final int STATE_LIST         = 0x0002;
    private static final int STATE_MAP_KEY      = 0x0004;
    private static final int STATE_MAP_VALUE    = 0x0008;
    private static final int STATE_STRING       = 0x0040;
    private static final int STATE_BUFFER       = 0x0080;
    private static final int STATE_JUSTCLOSED   = 0x0100;
    private static final int STATE_DONE         = 0x0200;

    private static final int NONE = 0;
    private static final int HEX = 1;
    private static final int B64STD = 2;
    private static final int B64URL = 3;
    private static final int B64STDPAD = 6;
    private static final int B64URLPAD = 7;
    private static final int B64PAD = 4;

    private State state;
    private Appendable out;
    private int optionCborDiag;
    private char[] b64 = B64URL_C;
    private boolean sorted, pretty;
    private int maxArraySize, maxStringLength, stringLength, b64_0, b64_1, indent;
    private boolean optionAllowNaN;
    private String floatFormat = "%.8g", doubleFormat = "%.16g";
    private String colon = ":", comma = ",";

    private final class State {
        final State parent;
        int mode, size, indent;
        Appendable out;
        long tag;
        Object prevKey; // Only for returning from keys(), not used internally

        State(State parent, int mode) {
            this.parent = parent;
            this.mode = mode;
            this.out = parent == null ? null : parent.out;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            if ((mode & STATE_ROOT) != 0) sb.append("root+");
            if ((mode & STATE_LIST) != 0) sb.append("list+");
            if ((mode & STATE_MAP_KEY) != 0) sb.append("map-key+");
            if ((mode & STATE_MAP_VALUE) != 0) sb.append("map-value+");
            if ((mode & STATE_STRING) != 0) sb.append("string+");
            if ((mode & STATE_BUFFER) != 0) sb.append("buffer+");
            if ((mode & STATE_DONE) != 0) sb.append("done+");
            if ((mode & STATE_JUSTCLOSED) != 0) sb.append("just-closed+");
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }
            if (tag != 0) {
                sb.append(" tag="+(tag - 1));
            }
            sb.append('}');
            return sb.toString();
        }

        void setTag(long tag) {
            this.tag = tag + 1;
        }
        boolean isMidString() {
            return (mode & STATE_STRING) != 0;
        }
        boolean isMidBuffer() {
            return (mode & STATE_BUFFER) != 0;
        }
        boolean isJustClosed() {
            return (mode & STATE_JUSTCLOSED) != 0;
        }
        boolean isDone() {
            return mode == STATE_DONE;
        }
        boolean isRoot() {
            return (mode & STATE_ROOT) != 0;
        }
        boolean isList() {
            return (mode & STATE_LIST) != 0;
        }
        boolean isMapValue() {
            return (mode & STATE_MAP_VALUE) != 0;
        }
        boolean isMapKey() {
            return (mode & STATE_MAP_KEY) != 0;
        }

        void preKey() throws IOException {
            if (isMidString()) {
                throw new IllegalStateException("Invalid state (mid-string)");
            } else if (isMidBuffer()) {
                throw new IllegalStateException("Invalid state (mid-buffer)");
            } else if (isMapKey()) {
                if (size++ > 0) {
                    out.append(comma);
                    if (size < 0) {
                        size = 1; // Catch wrap;
                    }
                }
                if (pretty) {
                    out.append('\n');
                    if (indent == 0) {
                        for (State s=parent;s!=null;s=s.parent) {
                            indent++;
                        }
                        indent *= JsonWriter.this.indent;
                    }
                    for (int i=0;i<indent;i++) {
                        out.append(' ');
                    }
                }
                mode = STATE_MAP_VALUE;
            } else {
                throw new IllegalStateException("Invalid state");
            }
        }

        Appendable preValue() throws IOException {
            if (isMidString()) {
                throw new IllegalStateException("Invalid state (mid-string)");
            } else if (isMidBuffer()) {
                throw new IllegalStateException("Invalid state (mid-buffer)");
            } else if (isList()) {
                if (size++ > 0) {
                    out.append(comma);
                }
                if (pretty && parent != null) {
                    out.append('\n');
                    if (indent == 0) {
                        for (State s=parent;s!=null;s=s.parent) {
                            indent++;
                        }
                        indent *= JsonWriter.this.indent;
                    }
                    for (int i=0;i<indent;i++) {
                        out.append(' ');
                    }
                }
                if (size < 0) {
                    size = 1; // Catch wrap;
                } else if (maxArraySize > 0 && size > maxArraySize) {
                    size = maxArraySize;
                    out.append(comma + "...");
                    out = new ZeroAppendable();
                }
            } else if (isMapValue()) {
                out.append(colon);
                mode = STATE_MAP_KEY;
            } else if (isRoot()) {
                mode = STATE_DONE;
            } else {
                throw new IllegalStateException("Invalid state");
            }
            if (tag != 0) {
                writeNumber(tag - 1, out);
                out.append('(');
            }
            return out;
        }

        void postValue() throws IOException {
            if (tag != 0) {
                out.append(')');
            }
        }

        State close() throws IOException {
            if (isList()) {
                if (pretty) {
                    indent -= JsonWriter.this.indent;
                    out.append('\n');
                    for (int i=0;i<indent;i++) {
                        out.append(' ');
                    }
                }
                out.append(']');
                return parent;
            } else if (isMapKey()) {
                if (pretty) {
                    indent -= JsonWriter.this.indent;
                    out.append('\n');
                    for (int i=0;i<indent;i++) {
                        out.append(' ');
                    }
                }
                out.append('}');
                return parent;
            } else {
                throw new IllegalStateException("Invalid state");
            }
        }
    }

    public JsonWriter() {
    }

    /**
     * Request that map keys are sorted before writing.
     * @param sorted true if keys should be sorted
     * @return this
     */
    public JsonWriter setSorted(boolean sorted) {
        this.sorted = sorted;
        return this;
    }

    public boolean isSorted() {
        return sorted;
    }

    /**
     * Configure this JsonWriter to write CBOR-diag format.
     * The specified value controls how buffer values will be serialised,
     * either has hex or one of two variations of base64.
     * @param option either "hex", "base64", "base64-standard", or null for normal Json
     * @return this
     */
    public JsonWriter setCborDiag(String option) {
        if (option == null) {
            this.optionCborDiag = NONE;
            this.b64 = B64URL_C;
        } else if ("hex".equalsIgnoreCase(option)) {
            this.optionCborDiag = HEX;
            this.b64 = B64URL_C;
        } else if ("base64".equalsIgnoreCase(option)) {
            this.optionCborDiag = B64URL;
            this.b64 = B64URL_C;
        } else if ("base64-pad".equalsIgnoreCase(option)) {
            this.optionCborDiag = B64URLPAD;
            this.b64 = B64URL_C;
        } else if ("base64-standard".equalsIgnoreCase(option)) {
            this.optionCborDiag = B64STD;
            this.b64 = B64STD_C;
        } else if ("base64-pad-standard".equalsIgnoreCase(option)) {
            this.optionCborDiag = B64STDPAD;
            this.b64 = B64STD_C;
        } else {
            throw new IllegalArgumentException("Unrecognised option \"" + option + "\"");
        }
        return this;
    }

    /**
     * The String format to use when formatting a float. The default is "%.8g"
     * Note that superfluous trailing zeros will trimmed from any formatted value.
     * @param format the format, which will be passed to {@link DecimalFormat}
     * @return this
     */
    public JsonWriter setFloatFormat(String format) {
        floatFormat = format;
        return this;
    }

    /**
     * The String format to use when formatting a double. The default is "%.16g".
     * Note that superfluous trailing zeros will trimmed from any formatted value.
     * @param format the format, which will be passed to {@link DecimalFormat}
     * @return this
     */
    public JsonWriter setDoubleFormat(String format) {
        doubleFormat = format;
        return this;
    }

    /**
     * Whether to allow NaN and Infinite values in the output.
     * Both NaN and infinite values are disallowed in RFC8259.
     * With this flag set, Infinite or NaN values are serialized
     * as null, which matches web-browser behaviour. With this flag not set,
     * an IOException is thrown during serialization
     * @param nan the flag
     * @return this
     */
    public JsonWriter setAllowNaN(boolean allow) {
        optionAllowNaN = allow;
        return this;
    }

    /**
     * Set whether the output is pretty-printed with newlines and indenting.
     * A value of zero (the default) means no pretty-printing
     * @param size the number of spaces to indent, or 0 for no pretty-printing
     * @return this
     */
    public JsonWriter setIndent(int size) {
        indent = size;
        pretty = indent > 0;
        return this;
    }

    /**
     * Set whether to add a space after colons when printing
     * @param space the flag
     * @return this
     */
    public JsonWriter setSpaceAfterColon(boolean space) {
        colon = space ? ": " : ":";
        return this;
    }

    /**
     * Set whether to add a space after commas when printing. Ignored for pretty-printing,
     * as commas are followed by newlines.
     * @param space the flag
     * @return this
     */
    public JsonWriter setSpaceAfterComma(boolean space) {
        comma = space ? ", " : ",";
        return this;
    }

    /**
     * Set the maximum number of items to print in an array. Additional entries will
     * be replaced with an ellipsis; the result will not be valid Json, but is useful
     * for use in toString()
     * @param size the maximum number of items to print, or 0 for no limit (the default)
     * @return this
     */
    public JsonWriter setMaxArraySize(int size) {
        this.maxArraySize = size <= 0 || size >= 0xFFFF ? 0 : size - 1;
        return this;
    }

    /**
     * Set the maximum length of a string. Additional characters will be replaced with an ellipsis
     * @param size the maximum length of a string to print, or 0 for no limit (the default)
     * @return this
     */
    public JsonWriter setMaxStringLength(int size) {
        this.maxStringLength = size;
        return this;
    }

    /**
     * Set the Appendable to write to
     * @param out the output
     * @return this
     */
    public JsonWriter setOutput(Appendable out) {
        this.out = out;
        return this;
    }

    /**
     * Set the CharBuffer to write to
     * @param out the output
     * @return this
     */
    public JsonWriter setOutput(final CharBuffer buf) {
        this.out = new Writer() {
            public void write(int v) {
                buf.put((char)v);
            }
            public void write(char[] v, int off, int len) {
                buf.put(v, off, len);
            }
            public void flush() {
            }
            public void close() {
            }
        };
        return this;
    }

    /*
    public void flush() throws IOException {
        if (out instanceof Flushable) {
            ((Flushable)out).flush();
        }
    }

    public void close() throws IOException {
        if (out instanceof Closeable) {
            ((Closeable)out).close();
        }
    }
    */

    private String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (State s=state;s!=null;s=s.parent) {
            if (s != state) {
                sb.append(", ");
            }
            sb.append(s);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Return true if the writer is currently writing to a list
     */
    protected boolean isList() {
        return state.isList();
    }

    /**
     * Return true if the writer is currently writing to a map
     */
    protected boolean isMap() {
        return state.isMapKey() || state.isMapValue();
    }

    /**
     * <p>
     * Return the currently open keys as a stack, with the most recent at the end.
     * For example, when processing <code>{"a":[{"b":true}]}</code> the stack would
     * be <code>["a", 0, "b"]</code> when processing the event for the boolean value.
     * The <code>next</code> parameter is the next event to be processed - this method
     * is intended to be called from the {@link #event} method <i>before</i> the call
     * to <code>super.event()</code>.
     * </p><p>
     * This method is mostly useful when filtering output - for example, to remove any
     * "password" values from the output:
     * </p>
     * <pre class="brush:java">
     * JsonWriter writer = new JsonWriter() {
     *   boolean instring;
     *   @Override public boolean event(JsonStream.Event e) throws IOException {
     *     List&lt;Object&gt; keys = keys(e);
     *     Object mostRecentKey = keys.isEmpty() ? null : keys.get(keys.size() - 1);
     *     if (instring) {
     *       instring = e.type() != e.TYPE_STRING_END;
     *       return false;
     *     } else if (e.type() == e.TYPE_PRIMITIVE &amp;&amp; "password".equals(mostRecentKey)) {
     *       return super.event(JsonStream.Event.stringValue("***", 3));
     *     } else if (e.type() == e.TYPE_STRING_START &amp;&amp; "password".equals(mostRecentKey)) {
     *       instring = true;
     *       return super.event(JsonStream.Event.stringValue("***", 3));
     *     } else {
     *       return super.event(e);
     *     }
     *   }
     * };
     * </pre>
     * </p>
     * @param next the next event to be processed
     */
    protected List<Object> keys(JsonStream.Event next) {
        List<Object> l = new ArrayList<Object>();
        int type = next == null ? -1 : next.type();
        for (State s = state;s!=null;s=s.parent) {
            if (type == JsonStream.Event.TYPE_LIST_END || type == JsonStream.Event.TYPE_MAP_END) {
                // skip first before a close
            } else if (s.isJustClosed() && s.isList()) {
                l.add(Integer.valueOf(s.size - 1));
            } else if (s.isMapKey() && s != state) {
                l.add(s.prevKey);
            } else if (s.isJustClosed() && s.isMapKey() && (type == JsonStream.Event.TYPE_MAP_END || type == JsonStream.Event.TYPE_LIST_END)) {
                l.add(s.prevKey);
            } else if (s.isMapValue()) {
                l.add(s.prevKey);
            } else if (s.isList()) {
                l.add(Integer.valueOf(s.size - (s == state ? 0 : 1)));
            }
            type = -1;
        }
        Collections.reverse(l);
//        l.add("<dump="+dump()+">");
        return l;
    }

    @Override public boolean event(JsonStream.Event event) throws IOException {
//        System.out.println("* e="+event+" " + dump());
        if (state == null) {
            state = new State(null, STATE_ROOT);
            state.out = out;
            if (pretty) {
                comma = ",";
            }
        } else if (state.isDone()) {
            throw new IllegalStateException("Completed");
        }
        state.mode &= ~STATE_JUSTCLOSED;
        final int type = event.type();
        Appendable out = state.out;
        switch(type) {
            case JsonStream.Event.TYPE_MAP_START:
                out = state.preValue();
                state = new State(state, STATE_MAP_KEY);
                out.append('{');
                break;
            case JsonStream.Event.TYPE_LIST_START:
                out = state.preValue();
                state = new State(state, STATE_LIST);
                out.append('[');
                break;
            case JsonStream.Event.TYPE_MAP_END:
                state = state.close();
                state.postValue();
                state.mode |= STATE_JUSTCLOSED;
                break;
            case JsonStream.Event.TYPE_LIST_END:
                state = state.close();
                state.postValue();
                state.mode |= STATE_JUSTCLOSED;
                break;
            case JsonStream.Event.TYPE_PRIMITIVE:
                Object value = event.value();
                if (state.isMapKey()) {
                    state.preKey();
                    if (optionCborDiag == NONE) {
                        value = value.toString();
                    }
                    state.prevKey = value;
                } else {
                    out = state.preValue();
                }
                if (value instanceof Number) {
                    writeNumber((Number)value, out);
                } else if (Boolean.TRUE.equals(value)) {
                    out.append("true");
                } else if (Boolean.FALSE.equals(value)) {
                    out.append("false");
                } else if (value == Json.NULL) {
                    out.append("null");
                } else if (value == Json.UNDEFINED) {
                    out.append(optionCborDiag != NONE ? "undefined" : "null");
                } else if (value instanceof CharSequence) {   // If we've made it so in state.isKey above
                    out.append('"');
                    CharSequence s = (CharSequence)value;
                    if (maxStringLength == 0 || s.length() < maxStringLength) {
                        writeString((CharSequence)value, out, s.length());
                    } else {
                        writeString((CharSequence)value, out, maxStringLength);
                        out.append("...");
                    }
                    out.append('"');
                } else {
                    throw new IllegalStateException("Unknown data " + (value == null ? null : value.getClass().getName()));
                }
                state.postValue();
                break;
            case JsonStream.Event.TYPE_STRING_START:
                if (state.isMapKey()) {
                    state.preKey();
                    state.prevKey = null;
                } else {
                    out = state.preValue();
                }
                out.append('"');
                state.mode |= STATE_STRING;
                stringLength = 0;
                break;
            case JsonStream.Event.TYPE_STRING_DATA:
                if (state.isMidString()) {
                    CharSequence seq = event.stringValue();
                    if (seq != null) {
                        int l = maxStringLength == 0 ? seq.length() : Math.min(seq.length(), maxStringLength - stringLength);
                        writeString(seq, out, l);
                        stringLength += l;
                    } else {
                        Readable r = event.readableValue();
                        CharBuffer buf = CharBuffer.allocate(8192);
                        while (r.read(buf) > 0 && (maxStringLength == 0 || stringLength < maxStringLength)) {
                            ((Buffer)buf).flip();
                            int l = maxStringLength == 0 ? ((Buffer)buf).remaining() : Math.min(((Buffer)buf).remaining(), maxStringLength - stringLength);
                            writeString(buf, out, l);
                            stringLength += l;
                        }
                        if (r instanceof Closeable) {
                            ((Closeable)r).close();
                        }
                    }
                } else {
                    throw new IllegalStateException("String not started");
                }
                break;
            case JsonStream.Event.TYPE_STRING_END:
                if (state.isMidString()) {
                    if (maxStringLength != 0 && stringLength == maxStringLength) {
                        out.append("...");
                    }
                    out.append('"');
                    state.mode &= ~STATE_STRING;
                    if (!state.isMapKey()) {
                        state.postValue();
                    }
                } else {
                    throw new IllegalStateException("String not started");
                }
                break;
            case JsonStream.Event.TYPE_BUFFER_START:
                if (state.isMapKey()) {
                    state.preKey();
                    state.prevKey = null;
                } else {
                    out = state.preValue();
                }
                if (optionCborDiag == HEX) {
                    out.append("h'");
                } else if (optionCborDiag != NONE) {
                    out.append("b'");
                } else {
                    out.append('"');
                }
                state.mode |= STATE_BUFFER;
                stringLength = 0;
                b64_0 = b64_1 = -1;
                break;
            case JsonStream.Event.TYPE_BUFFER_DATA:
                if (state.isMidBuffer()) {
                    ByteBuffer buf = event.bufferValue();
                    if (buf != null) {
                        int l = maxStringLength == 0 ? ((Buffer)buf).remaining() : Math.min(((Buffer)buf).remaining(), maxStringLength - stringLength);
                        writeByteBuffer(buf, out, l);
                        stringLength += l;
                    } else {
                        ReadableByteChannel r = event.readableByteChannelValue();
                        buf = ByteBuffer.allocate(8192);
                        while (r.read(buf) > 0 && (maxStringLength == 0 || stringLength < maxStringLength)) {
                            ((Buffer)buf).flip();
                            int l = maxStringLength == 0 ? ((Buffer)buf).remaining() : Math.min(((Buffer)buf).remaining(), maxStringLength - stringLength);
                            writeByteBuffer(buf, out, l);
                            stringLength += l;
                        }
                        r.close();
                    }
                } else {
                    throw new IllegalStateException("Buffer not started");
                }
                break;
            case JsonStream.Event.TYPE_BUFFER_END:
                if (state.isMidBuffer()) {
                    if (maxStringLength != 0 && stringLength == maxStringLength) {
                        out.append("...");
                    } else if (b64_1 >= 0) {
                        writeb64(b64_0, b64_1, 0, 3);
                    } else if (b64_0 >= 0) {
                        writeb64(b64_0, 0, 0, 2);
                    }
                    out.append(optionCborDiag == NONE ? '"' : '\'');
                    state.mode &= ~STATE_BUFFER;
                    if (!state.isMapKey()) {
                        state.postValue();
                    }
                } else {
                    throw new IllegalStateException("Buffer not started");
                }
                break;
            case JsonStream.Event.TYPE_TAG:
                if (optionCborDiag != NONE) {
                    state.setTag(event.tagValue());
                }
                break;
            default:
                throw new IllegalStateException("Unknown event 0x" + type);
        }
        return state.isDone();
    }

    private static final char hex(int v) {
        if (v >= 0 && v < 10) {
            return (char)('0' + v);
        } else if (v < 16) {
            return (char)('A' + v - 10);        // Use UC for consistancy with earlier versions
        } else {
            throw new IllegalArgumentException(Integer.toString(v));
        }
    }

    private static void writeString(CharSequence seq, Appendable out, int l) throws IOException {
        int s = 0;
        for (int i=0;i<l;i++) {
            char c = seq.charAt(i);
            if (c < 0x20 || c == '"' || c == '\\') {
                if (s < i) {
                    out.append(seq, s, i);
                }
                String v;
                switch (c) {
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    case '\"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    default:
                        out.append("\\u00");
                        out.append(hex(c>>4));
                        out.append(hex(c&0xF));
                }
                s = i + 1;
            } else if (c >= 0x80 && c < 0xA0) {
                if (s < i) {
                    out.append(seq, s, i);
                }
                out.append("\\u00");
                out.append(hex(c>>4));
                out.append(hex(c&0xF));
                s = i + 1;
            }
        }
        if (s < l) {
            out.append(seq, s, l);
        }
    }

    private void writeByteBuffer(ByteBuffer buf, Appendable out, int length) throws IOException {
        if (optionCborDiag != HEX) {
            int i = 0;
            if (b64_0 >= 0 && b64_1 >= 0) {
                int b2 = buf.get() & 0xff;
                writeb64(b64_0, b64_1, b2, 4);
                i++;
            } else if (b64_0 >= 0) {
                int b1 = buf.get() & 0xff;
                int b2 = buf.get() & 0xff;
                i+=2;
                writeb64(b64_0, b1, b2, 4);
            }
            for (i=0;i+2<length;i+=3) {
                int b0 = buf.get() & 0xff;
                int b1 = buf.get() & 0xff;
                int b2 = buf.get() & 0xff;
                writeb64(b0, b1, b2, 4);
            }
            b64_0 = b64_1 = -1;
            if (i++ < length) {
                b64_0 = buf.get() & 0xff;
                if (i++ < length) {
                    b64_1 = buf.get() & 0xff;
                }
            }
        } else {
            for (int i=0;i<length;i++) {
                int b = buf.get() & 0xff;
                int v0 = b>>4;
                int v1 = b&0xf;
                out.append(hex(v0));
                out.append(hex(v1));
            }
        }
    }

    private void writeb64(int b0, int b1, int b2, int c) throws IOException {
        out.append(b64[(b0>>2) & 0x3f]);
        out.append(b64[((b0<<4) | (b1>>4)) & 0x3f]);
        if (c > 2) {
            out.append(b64[((b1<<2) | (b2>>6)) & 0x3f]);
            if (c > 3) {
                out.append(b64[b2 & 0x3f]);
            } else if ((optionCborDiag & B64PAD) != 0) {
                out.append('=');
            }
        } else if ((optionCborDiag & B64PAD) != 0) {
            out.append("==");
        }
    }

    private void writeNumber(Number value, Appendable out) throws IOException {
        StringBuilder temp = null;
        if (value instanceof Float) {
            Float n = (Float)value;
            if (n.isNaN() || n.isInfinite()) {
                if (optionCborDiag != NONE) {
                    if (n.isNaN()) {
                        out.append("NaN");
                    } else if (n.floatValue() == Float.POSITIVE_INFINITY) {
                        out.append("Infinity");
                    } else if (n.floatValue() == Float.NEGATIVE_INFINITY) {
                        out.append("-Infinity");
                    }
                } else if (optionAllowNaN) {
                    out.append("null");
                } else {
                    throw new IllegalArgumentException("Infinite or NaN");
                }
            } else {
                temp = new StringBuilder();
                new Formatter(temp, Locale.ENGLISH).format(floatFormat, n);
            }
        } else if (value instanceof Double) {
            Double n = (Double)value;
            if (n.isNaN() || n.isInfinite()) {
                if (optionCborDiag != NONE) {
                    if (n.isNaN()) {
                        out.append("NaN");
                    } else if (n.floatValue() == Double.POSITIVE_INFINITY) {
                        out.append("Infinity");
                    } else if (n.floatValue() == Double.NEGATIVE_INFINITY) {
                        out.append("-Infinity");
                    }
                } else if (optionAllowNaN) {
                    out.append("null");
                } else {
                    throw new IllegalArgumentException("Infinite or NaN");
                }
            } else {
                temp = new StringBuilder();
                new Formatter(temp, Locale.ENGLISH).format(doubleFormat, n);
            }
        } else {
            out.append(value.toString());
        }
        if (temp != null) {
            // Trim superfluous zeros after decimal point
            int l = temp.length();
            for (int i=Math.max(0, l-6);i<l;i++) {
                char c = temp.charAt(i);
                if (c == 'e' || c == 'E') {
                    l = i;
                    break;
                }
            }
            for (int i=0;i<l;i++) {
                if (temp.charAt(i) == '.') {
                    int k = l - 1;
                    while (temp.charAt(k) == '0') {
                        k--;
                    }
                    if (k == i) {
                        k--;
                    }
                    out.append(temp, 0, k + 1);
                    if (l != temp.length()) {
                        out.append(temp, l, temp.length());
                    }
                    temp = null;
                    break;
                }
            }
            if (temp != null) {
                out.append(temp);
            }
        }
    }

    private static class ZeroAppendable implements Appendable {
        public Appendable append(char c) { return this; }
        public Appendable append(CharSequence c) { return this; }
        public Appendable append(CharSequence c, int off, int len) { return this; }
    }


    /*
    public static void main(String[] args) throws Exception {
        String s = "5AC05E289D5D0E1B0A7F048A5D2B643813DED50BC9E49220F4F7278F85F19D4A77D655C9D3B51E805A74B099E1E085AACD97FC29D72F887E8802BB6650CCEB2C";
        byte[] b = Json.hex(s);

        for (String s : args) {
            JsonStreamReader reader = new JsonStreamReader().setOptionCborDiag(true);
            JsonStream.EventBuilder builder = new JsonStream.EventBuilder();
            reader.setSink(builder);
            //reader.setSink(new JsonStreamDebugger(builder));
            reader.setOptionCborDiag(true);
            reader.read(new StringReader(s));
            reader.complete();
            Json j = builder.complete();
            System.out.println("----");
//            Json j = Json.read(new StringReader(s), new JsonReadOptions().setCborDiag(true));
            Appendable out = new StringBuilder();
            JsonWriter writer = new JsonWriter();
            writer.setCborDiag("base64");
//            writer.setIndent(1).setSpaceAfterComma(true).setSpaceAfterColon(true);
            writer.setOutput(out);
            JsonStreamSender.send(j, writer);
            System.out.println(out);
        }
    }
    */

    static {
        char[] b64 = new char[64];
        for (int i=0;i<64;i++) {
            b64[i] = (char)(i < 26 ? 'A' + i : i < 52 ? 'a' + i - 26 : i < 62 ? '0' + i - 52 : i == 62 ? '-' : '_');
        }
        B64URL_C = b64;
        B64STD_C = Arrays.copyOf(b64, b64.length);
        B64STD_C[62] = '+';
        B64STD_C[63] = '/';
    }

}
