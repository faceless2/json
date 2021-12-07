package com.bfo.json;

import java.lang.reflect.Array;
import java.util.*;
import java.text.*;
import java.math.*;
import java.io.*;

/**
 * Class to read/write objects as JSON.
 */
class JsonReader {

    private final Reader reader;
    private final JsonReadOptions options;
    private final JsonReadOptions.Filter filter;
    private final boolean strict;

    JsonReader(Reader reader, JsonReadOptions options) {
        this.reader = reader;
        this.options = options;
        this.filter = options.getFilter() != null ? options.getFilter() : new JsonReadOptions.Filter() {};
        this.strict = options.isStrictTypes();
    }
 
    /**
     * Parse a JSON serialized object from the Reader and return the Object it represents
     */
    Json read() throws IOException {
        Json j = readToken(0);
        int c;
        if ((c=stripBlanks()) != -1) {
            unexpected("trailing ", c);
        }
        return j;
    }

    private void unexpected(String type, int c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unexpected");
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
        sb.append(" at ");
        sb.append(reader);
        throw new IllegalArgumentException(sb.toString());
    }


    private Json readToken(int special) throws IOException {
        Json out = null;
        int c = stripBlanks();
        if (c == special && special > 0) {
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
                Json.notify(out, Integer.valueOf(size), null, child);
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
            Map<String,Json> map = out._mapValue();
            while (true) {
                c = stripBlanks();
                if (c < 0) {
                    throw new EOFException();
                } else if (c == '}' && (map.size() == 0 || options.isAllowTrailingComma())) {
                    break;
                } else {
                    String key;
                    if (c == '"') {
                        key = new JsonStringReader((char)c, reader).readString();
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
                    } else {
                        unexpected("", c);
                        key = null;
                    }
                    c = stripBlanks();
                    if (c != ':') {
                        unexpected("", c);
                    }
                    if (options.isNFC()) {
                        key = Normalizer.normalize(key, Normalizer.Form.NFC);
                    }
                    filter.enter(out, key);
                    Json child = readToken(0);
                    filter.exit(out, key);
                    map.put(key, child);
                    Json.notify(out, key, null, child);
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
        } else if (c == '"') {
            out = filter.createString(new JsonStringReader((char)c, reader), -1);
            if (options.isNFC() && out.isString()) {
                String s = out.stringValue();
                s = Normalizer.normalize(s, Normalizer.Form.NFC);
                out.setValue(new Json(s));
            }
        } else if (c == 't' || c == 'f' || c == 'n') {
            StringBuilder sb = new StringBuilder();
            sb.append((char)c);
            reader.mark(1);
            while ((c=reader.read())>=0 && c>='a' && c<='z' && sb.length() < 5) {
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
            } else {
                throw new IllegalArgumentException("Invalid token \""+q+"\" at "+reader);
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

    static class JsonStringReader extends FilterReader {

        private static final boolean[] literal = new boolean[128];
        static {
            for (int c=0;c<128;c++) {
                literal[c] = c == 127 || !(Character.isISOControl(c) || c == '\\' || c == '"' || c == '\'');
            }
        }
        private final char quote;
        boolean inquotes = true;
        private boolean eof;

        JsonStringReader(char quote, Reader reader) {
            super(reader);
            this.quote = quote;
        }

        private int hexnibble(int c) {
            if (c>='0' & c<='9') {
                return c - '0';
            } else if (c<='F' & c>='A') {
                return c - 'A' + 10;
            } else if (c<='f' & c>='a') {
                return c - 'a' + 10;
            } else if (c < 0) {
                throw new IllegalArgumentException("Unexpected EOF in hex string at " + in);
            } else {
                throw new IllegalArgumentException("Invalid hex digit 0x" + Integer.toHexString(c) + " at " + in);
            }
        }

        @Override public int read() throws IOException {
            if (eof) {
                return -1;
            }
            int c = in.read();
            if (c < 0) {
                throw new IllegalArgumentException("Unterminated string at " + in);
            } else if (c < 128 && literal[c]) {
                return c;
            } else if (c == quote) {
                eof = true;
                return -1;
            } else if (c == '\\') {
                c = in.read();
                switch (c) {
                    case '\\':
                    case '\"':
                    case '/':
                        return c;
                    case 'u':
                        int v = (hexnibble(in.read()) << 12) | (hexnibble(in.read()) << 8) | (hexnibble(in.read()) << 4) | hexnibble(in.read());
                        return v;
                    case 'n':
                        return '\n';
                    case 'r':
                        return '\r';
                    case 't':
                        return '\t';
                    case 'b':
                        return '\b';
                    case 'f':
                        return '\f';
                    default:
                        throw new IOException("Invalid trailing backslash in string at " + in);
                }
            } else if (!Character.isISOControl(c)) {
                return c;
            } else {
                throw new IllegalArgumentException("Invalid string character 0x" + Integer.toHexString(c) + " at " + in);
            }
        }

        @Override public long skip(long n) throws IOException {
            int c = 0;
            while (c < n && read() > 0) {
                c++;
            }
            return c;
        }

        @Override public int read(char[] buf, int off, int len) throws IOException {
            for (int i=0;i<len;i++) {
                int v = read();
                if (v < 0) {
                    return i == 0 ? -1 : i;
                }
                buf[off++] = (char)v;
            }
            return len;
        }

        @Override public void close() {
        }

        String readString() throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c=read()) >= 0) {
                sb.append((char)c);
            }
            return sb.toString();
        }
    }

}
