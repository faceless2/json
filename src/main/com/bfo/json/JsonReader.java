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

    private final Reader reader;
    private final JsonReadOptions options;
    private final JsonReadOptions.Filter filter;
    private final boolean strict;
    private final boolean cborDiag;

    JsonReader(Reader reader, JsonReadOptions options) {
        this.reader = reader;
        this.options = options;
        this.filter = options.getFilter() != null ? options.getFilter() : new JsonReadOptions.Filter() {};
        this.strict = options.isStrictTypes();
        this.cborDiag = options.isCborDiag();
    }
 
    /**
     * Parse a JSON serialized object from the Reader and return the Object it represents
     */
    Json read() throws IOException {
        filter.initialize();
        Json j = readToken(0);
        int c;
        if ((c=stripBlanks()) != -1) {
            unexpected("trailing ", c);
        }
        filter.complete(j);
        return j;
    }

    private void unexpected(String type, int c) {
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


    private Json readToken(int special) throws IOException {
        reader.mark(8);
        Json out = null;
        int c = stripBlanks();
        if (special > 0 && c == special) {
            return null;
        } else if (c < 0) {
            throw new EOFException();
        } else if (c == '[') {
            // Because passing a populated collection into Json() clones items
            out = filter.createList();
            List<Json> list = out._listValue();
            special = ']';
            while (true) {
                int size = list.size();
                filter.enter(out, size);
                Json child = readToken(special);
                filter.exit(out, size);
                if (child  == null) {
                    break;
                }
                list.add(child);
                Json.notifyDuringLoad(out, size, child);
                c = stripBlanks();
                if (c == ']') {
                    break;
                } else if (c != ',') {
                    unexpected("", c);
                }
                special = 0;
            }
        } else if (c == '{') {
            // Because passing a populated collection into Json() clones items
            out = filter.createMap();
            Map<Object,Json> map = out._mapValue();
            while (true) {
                reader.mark(1);
                c = stripBlanks();
                if (c < 0) {
                    throw new EOFException();
                } else if (c == '}' && (map.size() == 0 || options.isAllowTrailingComma())) {
                    break;
                } else {
                    Object key;
                    if (c == '"') {
                        StringBuilder sb = new StringBuilder();
                        readString(reader, c, Integer.MAX_VALUE, sb);
                        key = sb.toString();
                    } else if (options.isAllowUnquotedKey() && "-0123456789{}[].".indexOf(c) < 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append((char)c);
                        reader.mark(1);
                        while ((c=reader.read()) >= 0 && ((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='_' || c=='-' || c=='.')) {

                            reader.mark(1);
                            sb.append((char)c);
                        }
                        reader.reset();
                        key = sb.toString();
                    } else if (cborDiag) {
                        reader.reset();
                        Json j = readToken(0);
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
                    } else {
                        unexpected("", c);
                        key = null;
                    }
                    c = stripBlanks();
                    if (c != ':') {
                        unexpected("", c);
                    }
                    if (options.isNFC() && key instanceof String) {
                        key = Normalizer.normalize((String)key, Normalizer.Form.NFC);
                    }
                    filter.enter(out, key);
                    Json child = readToken(0);
                    filter.exit(out, key);
                    map.put(key, child);
                    Json.notifyDuringLoad(out, key, child);
                    c = stripBlanks();
                    if (c == '}') {
                        break;
                    } else if (c != ',') {
                        unexpected("", c);
                    }
                }
            }
        } else if ((c <= '9' && c >= '0') || c == '-') {
            long v = 0;
            StringBuilder sb = null;
            boolean real = false;
            boolean negzero = false;
            short exp = 0;
            Number n;

            // Wringing the pips out of this one.
            // Optimized for most common case, where we're parsing an integer or long
            if (c == '0') {                             // "0"
                reader.mark(1);
                c = reader.read();
            } else if (c == '-') {
                c = reader.read();
                if (c == '0') {                         // "-0"
                    negzero = true;
                    reader.mark(1);
                    c = reader.read();
                } else if (c <= '9' && c >= '0') {      // "-3"
                    reader.mark(1);
                    v = '0' - c;
                    while ((c = reader.read()) <= '9' && c >= '0') {
                        reader.mark(1);
                        long q = v * 10 - (c - '0');
                        if (q >= 0) {   // long overflow
                            sb = new StringBuilder();
                            sb.append(v);
                            sb.append((char)c);
                            while ((c = reader.read()) <= '9' && c >= '0') {
                                reader.mark(1);
                                sb.append((char)c);
                            }
                            break;
                        } else {
                            v = q;
                        }
                    }
                } else if (c == 'I' && cborDiag) {
                    reader.mark(10);
                    if (reader.read() == 'n' && reader.read() == 'f' && reader.read() == 'i' && reader.read() == 'n' && reader.read() == 'i' && reader.read() == 't' && reader.read() == 'y') {
                        out = new Json(Float.NEGATIVE_INFINITY);
                    } else {
                        reader.reset();
                        throw new IllegalArgumentException("Invalid token \"-\" at "+reader);
                    }
                } else {
                    reader.reset();
                    throw new IllegalArgumentException("Invalid token \"-\" at "+reader);
                }
            } else if (c <= '9' && c >= '0') {          // "3"
                reader.mark(1);
                v = c - '0';
                while ((c = reader.read()) <= '9' && c >= '0') {
                    reader.mark(1);
                    long q = v * 10 + (c - '0');
                    if (q < 0) {   // long overflow
                        sb = new StringBuilder();
                        sb.append(v);
                        sb.append((char)c);
                        while ((c = reader.read()) <= '9' && c >= '0') {
                            reader.mark(1);
                            sb.append((char)c);
                        }
                        break;
                    } else {
                        v = q;
                    }
                }
            }
            if (c == '(' && cborDiag && out == null) {
                // We have read a tag.
                Json j = readToken(0);
                if (j != null) {
                    reader.mark(1);
                    c = reader.read();
                    if (c != ')') {
                        reader.reset();
                        throw new IllegalArgumentException("Invalid CBOR-diag tag \""+sb+"(" + j + c + "\" at "+reader);
                    } else {
                        out = j;
                        j.setTag(v);
                    }
                } else {
                    reader.reset();
                    throw new IllegalArgumentException("Invalid CBOR-diag tag \""+sb+"(\" at "+reader);
                }
            }
            if (out == null) {
                if (c == '.') {
                    real = true;
                    if (sb == null) {
                        sb = new StringBuilder();
                        if (negzero) {
                            sb.append('-');
                        }
                        sb.append(v);
                    }
                    sb.append((char)c);
                    reader.mark(1);
                    c = reader.read();
                    if (c >= '0' && c <= '9') {
                        sb.append((char)c);
                        reader.mark(1);
                        while ((c = reader.read()) <= '9' && c >= '0') {
                            reader.mark(1);
                            sb.append((char)c);
                        }
                    } else {
                        reader.reset();
                        throw new IllegalArgumentException("Invalid number \""+sb+"\" at "+reader);
                    }
                }
                if (c == 'e' || c == 'E') {
                    exp = 1;
                    if (sb == null) {
                        sb = new StringBuilder();
                        sb.append(v);
                    }
                    sb.append('E');
                    reader.mark(1);
                    c = reader.read();
                    if (c == '+') {
                        reader.mark(1);
                        c = reader.read();
                    } else if (c == '-') {
                        sb.append((char)c);
                        reader.mark(1);
                        real = true;
                        c = reader.read();
                    }
                    if (c >= '0' && c <= '9') {
                        reader.mark(1);
                        sb.append((char)c);
                        while ((c = reader.read()) <= '9' && c >= '0') {
                            reader.mark(1);
                            sb.append((char)c);
                            exp++;
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid number \""+sb+"\" at "+reader);
                    }
                }
                if (sb == null) {
                    int iv = (int)v;
                    if (v == iv) {
                        n = Integer.valueOf(iv);
                    } else {
                        n = Long.valueOf(v);
                    }
                } else {
                    String s = sb.toString();
                    try {
                        if (real) {
                            Double d = null;
                            try {
                                if (exp < 2) {
                                    d = Double.valueOf(s);
                                    if (d.isInfinite()) {
                                        d = null;
                                    } else if (!options.isBigDecimal() && !d.toString().equals(s)) {
                                        d = null;
                                    }
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
                        } else if (exp != 0) {
                            n = new BigDecimal(s).toBigInteger();
                        } else {
                            n = new BigInteger(s);
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid number \""+s+"\" at "+reader, e);
                    }
                }
                reader.reset();
                out = filter.createNumber(n).setStrict(strict);
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
        out.setStrict(strict);
        return out;
    }

    private int stripBlanks() throws IOException {
        int c;
        while ((c=reader.read()) >= 0 && (c==' ' || c=='\n' || c=='\t' || c=='\r')) {
//            System.out.println("-> skip(1)="+((char)c));
        }
        if (c == '/') {
            reader.mark(1);
            if ((c=reader.read()) == '*') {
                if (!options.isAllowComments()) {
                    reader.reset();
                    throw new IllegalArgumentException("Comments disallowed");
                }
                StringBuilder comment = new StringBuilder();
                comment.append("/*");
                int lastc = reader.read();
                while ((c=reader.read())>=0 && (c != '/' || lastc != '*')) {
                    comment.append((char)lastc);
                    lastc = c;
                }
                if (lastc < 0) {
                    throw new IllegalArgumentException("Unterminated comment");
                }
                comment.append("*/");
//                System.out.println("Read Comment \""+comment+"\": c="+((char)c)+" "+((int)c));
                return stripBlanks();
            } else if (c < 0) {
                throw new EOFException("Trailing /");
            } else {
                reader.reset();
            }
        }
        return c;
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
                            c |= v;
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
                        throw new IOException("Invalid trailing backslash in string at " + in);
                }
            } else if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("Invalid string character 0x" + Integer.toHexString(c) + " at " + in);
            }
            sb.append((char)c);
        }
        return -1;
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

}
