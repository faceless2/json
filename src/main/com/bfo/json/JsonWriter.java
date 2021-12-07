package com.bfo.json;

import java.io.*;
import java.util.*;
import java.text.*;
import java.nio.ByteBuffer;

class JsonWriter {

    private final Appendable out;
    private final StringBuilder prefix;
    private final JsonWriteOptions options;
    private final JsonWriteOptions.Filter filter;
    private final Appendable stringWriter;
    private boolean simple;

    JsonWriter(Appendable out, JsonWriteOptions options, Json root) {
        this.out = out;
        this.options = options;
        this.prefix = options.isPretty() ? new StringBuilder("\n") : null;
        this.filter = options.initializeFilter(root);
        this.stringWriter = new Appendable() {
             final int maxlen = options.getMaxStringLength();
             public Appendable append(char c) throws IOException {
                 throw new UnsupportedOperationException("Must be a whole string");
             }
             public Appendable append(CharSequence s, int off, int len) throws IOException {
                 return append(s.subSequence(off, len));
             }
             public Appendable append(CharSequence s) throws IOException {
                 simple = writeString(s, maxlen, out);
                 return this;
             }
        };
    }

    void write(Json j) throws IOException {
        if (j == null || j.isNull()) {
            out.append("null");
        } else if (j.isBoolean()) {
            out.append(j.booleanValue() ? "true" : "false");
        } else if (j.isBuffer()) {
            out.append('"');
            int len = options.getMaxStringLength();
            if (len == 0) {
                Base64OutputStream bout = new Base64OutputStream(out);
                j.writeBuffer(bout);
                bout.close();
            } else {
                ByteBuffer buf = j.bufferValue();
                out.append("(" + buf.limit() + " bytes)");
            }
            out.append('"');
        } else if (j.isString()) {
            int len = options.getMaxStringLength();
            if (options.isNFC()) {
                CharSequence orig = (CharSequence)j.value();
                String v = Normalizer.normalize(orig.toString(), Normalizer.Form.NFC);
                if (writeString(v, len, out) && v.equals(orig)) {
                    j.setSimpleString(true);
                }
            } else if (j.isSimpleString()) {
                out.append('"');
                Appendable a = out;
                if (len != 0 && len < j.stringValue().length()) {
                    a = new Appendable() {
                        int r = len;
                        public Appendable append(CharSequence s, int off, int len) throws IOException {
                            if (r < 0) {
                                // noop
                            } else if (len > r) {
                                out.append(s, off, r);
                                out.append("...");
                                r = -1;
                            } else {
                                out.append(s, off, len);
                                r -= s.length();
                            }
                            return this;
                        }
                        public Appendable append(CharSequence s) throws IOException {
                            return append(s, 0, s.length());
                        }
                        public Appendable append(char c) throws IOException {
                            throw new UnsupportedOperationException("Must be entire string");
                        }
                    };
                }
                j.writeString(a);
                out.append('"');
            } else {
                simple = true;
                j.writeString(stringWriter);
                if (simple && j.value() instanceof String) {
                    j.setSimpleString(true);
                }
            }
        } else if (j.isNumber()) {
            Number value = j.numberValue();
            StringBuilder temp = null;
            if (value instanceof Float) {
                temp = new StringBuilder();
                Float n = (Float)value;
                if (n.isNaN() || n.isInfinite()) {
                    if (options.isAllowNaN()) {
                        out.append("null");
                        return;
                    } else {
                        throw new IllegalArgumentException("Infinite or NaN");
                    }
                } else {
                    new Formatter(temp, Locale.ENGLISH).format(options.getFloatFormat(), n);
                }
            } else if (value instanceof Double) {
                temp = new StringBuilder();
                Double n = (Double)value;
                if (n.isNaN() || n.isInfinite()) {
                    if (options.isAllowNaN()) {
                        out.append("null");
                        return;
                    } else {
                        throw new IllegalArgumentException("Infinite or NaN");
                    }
                } else {
                    new Formatter(temp, Locale.ENGLISH).format(options.getDoubleFormat(), n);
                }
            } else {
                out.append(value.toString());
                return;
            }

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
                    return;
                }
            }
            out.append(temp);
        } else if (j.isList()) {
            List<Json> list = j._listValue();
            out.append("[");
            if (prefix != null) {
                prefix.append("  ");
            }
            int len = options.getMaxArraySize();
            int ll = list.size();
            if (len == 0 || len > ll) {
                len = ll;
            }
            for (int i=0;i<len;i++) {
                if (i > 0) {
                    out.append(',');
                }
                if (prefix != null) {
                    out.append(prefix);
                }
                String key = Integer.toString(i);
                Json ochild = list.get(i);
                Json child = filter.enter(key, ochild);
                write(child);
                filter.exit(key, ochild);
            }
            if (ll > len) {
                out.append(",...");
            }
            if (prefix != null) {
                prefix.setLength(prefix.length() - 2);
                out.append(prefix);
            }
            out.append("]");
        } else if (j.isMap()) {
            Map<String,Json> map = j._mapValue();
            if (options.isSorted()) {
                map = new TreeMap<String,Json>(map);
            }
            out.append("{");
            if (prefix != null) {
                prefix.append("  ");
                out.append(prefix);
            }
            boolean first = true;
            for (Map.Entry<String,Json> e : map.entrySet()) {
                String key = e.getKey();
                Json ovalue = e.getValue();
                Json value = filter.enter(key, ovalue);
                if (value != null) {
                    if (first) {
                        first = false;
                    } else {
                        out.append(",");
                        if (prefix != null) {
                            out.append(prefix);
                        }
                    }
                    if (options.isNFC()) {
                        writeString(Normalizer.normalize(key, Normalizer.Form.NFC), 0, out);
                    } else {
                        writeString(key, 0, out);
                    }
                    out.append(':');
                    write(value);
                }
                filter.exit(key, ovalue);
            }
            if (prefix != null) {
               prefix.setLength(prefix.length() - 2);
               out.append(prefix);
            }
            out.append("}");
        }
    }

    static boolean writeString(CharSequence value, int maxLength, Appendable sb) throws IOException {
        sb.append('"');
        int len = value.length();
        if (maxLength == 0 || maxLength > len) {
            maxLength = len;
        }
        boolean testsimple = true;
        for (int i = 0; i < maxLength; i++) {
            char c = value.charAt(i);
            if (c >= 0x30 && c < 0x80 && c != 0x5c) {        // Optimize for most common case
                sb.append(c);
            } else {
                switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    testsimple = false;
                    break;
                case '\b':
                    sb.append("\\b");
                    testsimple = false;
                    break;
                case '\t':
                    sb.append("\\t");
                    testsimple = false;
                    break;
                case '\n':
                    sb.append("\\n");
                    testsimple = false;
                    break;
                case '\f':
                    sb.append("\\f");
                    testsimple = false;
                    break;
                case '\r':
                    sb.append("\\r");
                    testsimple = false;
                    break;
                default:
                    if (c < 0x20 || (c >= 0x80 && c < 0xA0) || c == 0x2028 || c == 0x2029) {
                        String t = Integer.toHexString(c);
                        sb.append("\\u");
                        switch(t.length()) {
                            case 1: sb.append('0');
                            case 2: sb.append('0');
                            case 3: sb.append('0');
                            default:
                        }
                        sb.append(t);
                        testsimple = false;
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        if (maxLength != len) {
            sb.append("...");
        }
        sb.append('"');
        return testsimple;
    }

}
