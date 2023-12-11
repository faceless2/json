package com.bfo.json;

import java.lang.reflect.Array;
import java.util.*;
import java.text.*;
import java.math.*;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Class to read/write objects as JSON.
 */
class JsonReader {

    final Reader reader;
    final JsonReadOptions options;
    private final JsonReadOptions.Filter filter;
    private final JsonFactory factory;
    private final boolean strict;
    private final boolean cborDiag;

    JsonReader(Reader reader, JsonReadOptions options) {
        this.reader = reader;
        this.options = options;
        this.filter = options.getFilter() != null ? options.getFilter() : new JsonReadOptions.Filter() {};
        this.factory = options.getFactory();
        this.strict = options.isStrictTypes();
        this.cborDiag = options.isCborDiag();
    }
 
    /**
     * Parse a JSON serialized object from the Reader and return the Object it represents
     */
    Json read() throws IOException {
        filter.initialize();
        Json j = readObject(next());
        int c;
        if ((c=next()) != -1) {
            unexpected("trailing ", c);
        }
        filter.complete(j);
        return j;
    }

    void unexpected(String type, int c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unexpected");
        if (c < 0) {
            sb.append(" EOF");
        } else {
            sb.append(type);
            sb.append(" character ");
            if (c >= ' ' && c < 127) {
                sb.append('\'');
                sb.append((char)c);
                sb.append('\'');
            } else {
                sb.append("0x");
                sb.append(Integer.toHexString(c));
            }
        }
        sb.append(" at ");
        sb.append(reader);
        throw new IllegalArgumentException(sb.toString());
    }

    private Json readObject(int c) throws IOException {
        Json out = null;
        if (c == '[') {
            out = filter.createList();
            List<Json> list = out._listValue();
            int state = 0;      // 0=expecting value-or-end, 1=expecting comma-or-end, 2=expecting-value
            int size = 0;
            while (true) {
                c = next();
                if (c == ']') {
                    if (state == 0 || state == 1 || options.isAllowTrailingComma()) {
                        break;
                    } else {
                        unexpected("", c);
                    }
                } else if (c == ',') {
                    if (state == 1) {
                        state = 2;
                    } else {
                        unexpected("", c);
                    }
                } else if (c == '}' || c == ':' || c == ')') {
                    unexpected("", c);
                } else if (state == 0 || state == 2) {
                    filter.enter(out, size);
                    Json child = readObject(c);
                    filter.exit(out, size);
                    list.add(child);
                    Json.notifyDuringLoad(out, size, child);
                    size++;
                    state = 1;
                } else {
                    unexpected("", c);
                }
            }
        } else if (c == '{') {
            out = filter.createMap();
            Map<Object,Json> map = out._mapValue();
            int state = 0;      // 0=expecting key-or-end, 1=expecting colon, 2=expecting value, 3=expecting comma-or-endbrace, 4=expecting key
            Object key = null;
            while (true) {
                c = next();
                if (c == '}') {
                    if (state == 0 || state == 3 || (state == 4 && options.isAllowTrailingComma())) {
                        break;
                    } else {
                        unexpected("", c);
                    }
                } else if (c == ':') {
                    if (state == 1) {
                        state = 2;
                    } else {
                        unexpected("", c);
                    }
                } else if (c == ',') {
                    if (state == 3) {
                        state = 4;
                    } else {
                        unexpected("", c);
                    }
                } else if (c == ']' || c == ')') {
                    unexpected("", c);
                } else if (state == 0 || state == 4) {
                    StringBuilder sb = new StringBuilder();
                    if (c == '"') {
                        readString(reader, c, Integer.MAX_VALUE, sb);
                        key = sb.toString();
                    } else if (cborDiag) {
                        Json j = readObject(c);
                        if (j == null) {
                            unexpected("", c);
                            key = null;
                        } else if (j.isNumber()) {
                            key = j.numberValue();
                        } else if (j.isBoolean()) {
                            key = j.booleanValue();
                        } else {
                            unexpected(j.toString(), c);
                            key = null;
                        }
                    } else if (options.isAllowUnquotedKey() && "-0123456789{}[].".indexOf(c) < 0) {
                        readUnquotedString(reader, c, sb);
                        key = sb.toString();
                    } else {
                        unexpected("", c);
                    }
                    state = 1;
                    if (options.isNFC() && key instanceof String) {
                        key = Normalizer.normalize((String)key, Normalizer.Form.NFC);
                    }
                } else if (state == 2) {
                    filter.enter(out, key);
                    Json child = readObject(c);
                    filter.exit(out, key);
                    map.put(key, child);
                    Json.notifyDuringLoad(out, key, child);
                    state = 3;
                } else {
                    unexpected("", c);
                }
            }
        } else if ((c <= '9' && c >= '0') || c == '-') {
            out = filter.createNumber(readNumber(c));
            if (cborDiag && (out.numberValue() instanceof Integer || out.numberValue() instanceof Long)) {
                reader.mark(1);
                c = reader.read();
                if (c == '(') {
                    final long tag = out.longValue();
                    c = reader.read();
                    Json j = readObject(c);
                    if (j != null) {
                        reader.mark(1);
                        c = reader.read();
                        if (c != ')') {
                            reader.reset();
                            throw new IllegalArgumentException("Invalid CBOR-diag tag \""+tag+"(" + j + c + "\" at "+reader);
                        } else {
                            out = j;
                            out.setTag(tag);
                        }
                    } else {
                        reader.reset();
                        throw new IllegalArgumentException("Invalid CBOR-diag tag \""+tag+"(\" at "+reader);
                    }
                } else {
                    reader.reset();
                }
            }
        } else if (c == '"') {
            out = filter.createString(new JsonStringReader(c, reader), -1);
            if (options.isNFC() && out.isString()) {
                String s = out.stringValue();
                s = Normalizer.normalize(s, Normalizer.Form.NFC);
                out.setValue(new Json(s));
            }
        } else if (c == 't' || c == 'f' || c == 'n' || (cborDiag && (c == '+' || c == 'N' || c == 'u'))) {
            StringBuilder sb = new StringBuilder();
            sb.append((char)c);
            reader.mark(1);
            while ((c=reader.read())>=0 && c>='a' && c<='z' && sb.length() < 12) {
                sb.append((char)c);
                reader.mark(1);
            }
            reader.reset();
            String q = sb.toString();
            if (q.equals("true")) {
                out = filter.createBoolean(true);
            } else if (q.equals("false")) {
                out = filter.createBoolean(false);
            } else if (q.equals("null")) {
                out = filter.createNull();
            } else if (q.equals("+Infinity")) {
                out = new Json(Float.POSITIVE_INFINITY);
            } else if (q.equals("NaN")) {
                out = new Json(Float.NaN);
            } else if (q.equals("undefined")) {
                out = new Json(Json.UNDEFINED);
            } else {
                throw new IllegalArgumentException("Invalid token \""+q+"\" at "+reader);
            }
        } else if (cborDiag && c == 'h') {
            reader.mark(1);
            if (reader.read() == '\'') {
                // hex encoded buffer
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                reader.mark(1);
                int v = -1;
                while ((c=reader.read()) >= 0 && ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
                    int n = c <= '9' ? c - '0' : c <= 'F' ? c - 'A' + 10 : c - 'a' + 10;
                    if (v < 0) {
                        v = n<<4;
                    } else {
                        v |= n;
                        bout.write(v);
                        v =- 1;
                    }
                }
                if (c != '\'') {
                    throw new IllegalArgumentException("Invalid hex-buffer (c=" + c + ") at "+reader);
                }
                out = new Json(ByteBuffer.wrap(bout.toByteArray()), null);
            }
        } else if (cborDiag && c == 'b') {
            if (reader.read() == '6' && reader.read() == '4' && reader.read() == '\'') {
                // base64 encoded buffer
                StringBuilder sb = new StringBuilder();
                while ((c=reader.read()) >= 0 && c != '\'') {
                    sb.append((char)c);
                }
                if (c == '\'') {
                    out = new Json(ByteBuffer.wrap(JWT.base64decode(sb.toString())), null);
                } else {
                    throw new IllegalArgumentException("Invalid b64-buffer (c=" + c + ") at "+reader);
                }
            }
        } else {
            unexpected("", c);
        }
        if (factory != null && out != null) {
            out._setFactory(factory);
        }
        return out;
    }

    int next() throws IOException {
        int c;
        while ((c=reader.read()) >= 0 && (c == ' ' || c == '\n' || c == '\t' || c == '\r')) {
//            System.out.println("-> skip(1)="+((char)c));
        }
        if (c == '/' && options.isAllowComments()) {
            reader.mark(1);
            c = reader.read();
            if (c == '*') {
                // StringBuilder comment = new StringBuilder();
                // comment.append("/*");
                int lastc = reader.read();
                while ((c=reader.read())>=0 && (c != '/' || lastc != '*')) {
                    // comment.append((char)lastc);
                    lastc = c;
                }
                if (lastc < 0) {
                    throw new IllegalArgumentException("Unterminated comment");
                }
                // comment.append("*/");
                // System.out.println("Read Comment \""+comment+"\");
                return next();
            }
            reader.reset();
        }
        return c;
    }

    private static void readUnquotedString(Reader reader, int c, StringBuilder sb) throws IOException {
        sb.append((char)c);
        reader.mark(1);
        while ((c=reader.read()) >= 0 && ((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='_' || c=='-' || c=='.')) {
            reader.mark(1);
            sb.append((char)c);
        }
        reader.reset();
    }

    /**
     * Read a String from the supplied reader, stopping when the matching quote is met or when we've read "maxlen".
     * The returned character is the number or characters read, or -1 for "we hit maxlen"
     * Benchmarking shows this is faster than using any sort of FilteredReader
     */
    static int readString(Reader in, int quote, int maxlen, Appendable sb) throws IOException {
        for (int i=0;i<maxlen;i++) {
            int c = in.read();
            if (c < 0) {
                throw new IllegalArgumentException("Unterminated string at " + in);
            } else if (c < 128 && literal[c]) {
                // noop
            } else if (c == quote) {
                return i;
            } else if (c == '\\') {
                c = in.read();
                switch (c) {
                    case '\\':
                    case '\"':
                    case '/':
                        break;
                    case 'u': {
                            int v = in.read();
                            int q = v <= '9' && v >= '0' ? v - '0' : v <= 'F' && v >= 'A' ? v - 'A' + 10 : v <= 'f' && v >= 'a' ? v - 'a' + 10 : -1;
                            if (q < 0) {
                                throw new IllegalArgumentException("Invalid hex digit 0x" + Integer.toHexString(v) + " in string at " + in);
                            }
                            c = q << 12;
                            v = in.read();
                            q = v <= '9' && v >= '0' ? v - '0' : v <= 'F' && v >= 'A' ? v - 'A' + 10 : v <= 'f' && v >= 'a' ? v - 'a' + 10 : -1;
                            if (q < 0) {
                                throw new IllegalArgumentException("Invalid hex digit 0x" + Integer.toHexString(v) + " in string at " + in);
                            }
                            c |= q << 8;
                            v = in.read();
                            q = v <= '9' && v >= '0' ? v - '0' : v <= 'F' && v >= 'A' ? v - 'A' + 10 : v <= 'f' && v >= 'a' ? v - 'a' + 10 : -1;
                            if (q < 0) {
                                throw new IllegalArgumentException("Invalid hex digit 0x" + Integer.toHexString(v) + " in string at " + in);
                            }
                            c |= q << 4;
                            v = in.read();
                            q = v <= '9' && v >= '0' ? v - '0' : v <= 'F' && v >= 'A' ? v - 'A' + 10 : v <= 'f' && v >= 'a' ? v - 'a' + 10 : -1;
                            if (q < 0) {
                                throw new IllegalArgumentException("Invalid hex digit 0x" + Integer.toHexString(v) + " in string at " + in);
                            }
                            c |= q;
                        }
                        break;
                    case 'n':
                        c = '\n';
                        break;
                    case 'r':
                        c = '\r';
                        break;
                    case 't':
                        c = '\t';
                        break;
                    case 'b':
                        c = '\b';
                        break;
                    case 'f':
                        c = '\f';
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid trailing backslash in string at " + in);
                }
            } else if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("Invalid string character 0x" + Integer.toHexString(c) + " at " + in);
            }
            sb.append((char)c);
        }
        return -1;
    }

    Number readNumber(int c) throws IOException {
        boolean neg = false;
        Number n = null;
        if (c == '-') {
            neg = true;
            c = reader.read();
            if (c == 'I' && cborDiag) {
                if (reader.read() == 'n' && reader.read() == 'f' && reader.read() == 'i' && reader.read() == 'n' && reader.read() == 'i' && reader.read() == 't' && reader.read() == 'y') {
                    n = Float.NEGATIVE_INFINITY;
                } else {
                    unexpected("", c);
                }
            } else if (c < '0' || c > '9') {
                unexpected("", c);
            }
        }
        if (n == null) {
            // First, try parsing directly to a long with no buffer
            StringBuilder sb = null;
            long lv = 0;
            if (c >= '1' && c <= '9') {
                do {
                    lv = lv * 10 + c - '0';
                    reader.mark(1);
                    c = reader.read();
                } while (c >= '0' && c <= '9' && lv < Long.MAX_VALUE / 10);
            } else {
                reader.mark(1);
                c = reader.read();
            }
            if (c == '.' || c == 'e' || c == 'E' || lv >= Long.MAX_VALUE / 10) {
                boolean decimal = false;
                sb = new StringBuilder(32);
                if (neg) {
                    sb.append('-');
                }
                sb.append(lv);
                while (c >= '0' && c <= '9') {
                    sb.append((char)c);
                    reader.mark(1);
                    c = reader.read();
                }
                if (c == '.') {
                    decimal = true;
                    sb.append('.');
                    c = reader.read();
                    if (c >= '0' && c <= '9') {
                        do {
                            sb.append((char)c);
                            reader.mark(1);
                            c = reader.read();
                        } while (c >= '0' && c <= '9');
                    } else {
                        unexpected("", c);
                    }
                }
                if (c == 'e' || c == 'E') {
                    sb.append('e');
                    c = reader.read();
                    if (c == '+') {
                        // noop
                        c = reader.read();
                    } else if (c == '-') {
                        decimal = true;
                        sb.append('-');
                        c = reader.read();
                    }
                    if (c >= '0' && c <= '9') {
                        do {
                            sb.append((char)c);
                            reader.mark(1);
                            c = reader.read();
                        } while (c >= '0' && c <= '9');
                    } else {
                        unexpected("", c);
                    }
                }
                reader.reset();
                String s = sb.toString();
                try {
                    if (decimal) {
                        Double d = null;
                        try {
                            d = Double.valueOf(s);
                            if (d.isInfinite()) {
                                d = null;
                            } else if (!options.isBigDecimal() && !d.toString().equalsIgnoreCase(s)) {
                                d = null;
                            }
                        } catch (NumberFormatException e) { }
                        if (d == null) {
                            BigDecimal bd = new BigDecimal(s);
                            double d2 = bd.doubleValue();
                            if (d2 == d2 && !Double.isInfinite(d2) && bd.equals(new BigDecimal(d2))) {
                                n = d2;
                            } else {
                                n = bd;
                            }
                        } else {
                            n = d;
                        }
                    } else {
                        n = new BigDecimal(s);
                        try {
                            n = Long.valueOf(((BigDecimal)n).longValueExact());
                        } catch (Exception e) {
                            n = ((BigDecimal)n).toBigIntegerExact();
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number \"" + s + "\" at " + reader, e);
                }
            } else if (c == ' ' || c == ']' || c == ',' || c == '}' || c == ':' || c == '\n' || c == '\t' || c == '\r' || c == '(' || c < 0 || c == ')') {
                if (c >= 0) {
                    reader.reset();
                }
                if (neg) {
                    lv = -lv;
                }
                if (lv > Integer.MAX_VALUE || lv < Integer.MIN_VALUE) {
                    n = lv;
                } else {
                    n = (int)lv;
                }
            } else {
                unexpected("", c);
            }
        }
        return n;
    }

    private static final boolean[] literal = new boolean[128];
    static {
        for (int c=0;c<128;c++) {
            literal[c] = c == 127 || !(Character.isISOControl(c) || c == '\\' || c == '"' || c == '\'');
        }
    }

    static class JsonStringReader extends FilterReader {
        private final int quote;
        private boolean eof;

        JsonStringReader(int quote, Reader reader) {
            super(reader);
            this.quote = quote;
        }

        @Override public int read() throws IOException {
            char[] c = new char[1];
            return read(c, 0, 1) == 1 ? c[0] : -1;
        }

        @Override public int read(final char[] buf, final int off, int len) throws IOException {
            if (eof) {
                return -1;
            }
            int c = JsonReader.readString(in, quote, len, new Appendable() {
                int i = off;
                public Appendable append(char c) {
                    buf[i++] = c;
                    return this;
                }
                public Appendable append(CharSequence s) {
                    throw new Error();
                }
                public Appendable append(CharSequence s, int off, int len) {
                    throw new Error();
                }
            });
            if (c >= 0) {
                eof = true;
                return c;
            } else {
                return len;
            }
        }

        @Override public long skip(long n) throws IOException {
            int ml = (int)Math.min(Integer.MAX_VALUE, n);
            int c = JsonReader.readString(in, quote, ml, new Appendable() {
                public Appendable append(char c) {
                    return this;
                }
                public Appendable append(CharSequence s) {
                    return this;
                }
                public Appendable append(CharSequence s, int off, int len) {
                    return this;
                }
            });
            if (c >= 0) {
                return c;
            } else {
                return ml;
            }
        }

        @Override public void close() {
        }

        String readString() throws IOException {
            StringBuilder sb = new StringBuilder();
            JsonReader.readString(in, quote, Integer.MAX_VALUE, sb);
            return sb.toString();
        }
    }

    static Reader createReader(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("InputStream is null");
        }
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        in.mark(3);
        int v = in.read();
        Reader reader;
        if (v < 0) {
            throw new EOFException("Empty file");
        } else if (v == 0xEF) {
            if ((v = in.read()) == 0xBB) {
                if ((v = in.read()) == 0xBF) {
                    reader = new InputStreamReader(in, "UTF-8");
                } else {
                    throw new IOException("Invalid Json (begins with 0xEF 0xBB 0x"+Integer.toHexString(v));
                }
            } else {
                throw new IOException("Invalid Json (begins with 0xEF 0x"+Integer.toHexString(v));
            }
        } else if (v == 0xFE) {
            if ((v = in.read()) == 0xFF) {
                reader = new InputStreamReader(in, "UTF-16BE");
            } else {
                throw new IOException("Invalid Json (begins with 0xFE 0x"+Integer.toHexString(v));
            }
        } else if (v == 0xFF) {
            if ((v = in.read()) == 0xFE) {
                reader = new InputStreamReader(in, "UTF-16LE");
            } else {
                throw new IOException("Invalid Json (begins with 0xFF 0x"+Integer.toHexString(v));
            }
        } else if (v == 0) {
            if ((v = in.read()) >= 0x20) { // Sniff: probably UTF-16BE
                in.reset();
                reader = new InputStreamReader(in, "UTF-16BE");
            } else {
                throw new IOException("Invalid Json (begins with 0x0 0x"+Integer.toHexString(v));
            }
        } else {
            if (in.read() == 0x0) { // Sniff: probably UTF-16LE
                in.reset();
                reader = new InputStreamReader(in, "UTF-16LE");
            } else {
                in.reset();
                reader = new InputStreamReader(in, "UTF-8");
            }
        }
        return reader;
    }

}
