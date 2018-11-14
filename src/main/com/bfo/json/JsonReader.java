package com.bfo.json;

import java.lang.reflect.Array;
import java.util.*;
import java.text.*;
import java.math.*;
import java.io.*;

/**
 * Class to read/write objects as JSON.
 * Can be overridden to support custom serialization. For writing this should involve
 * overriding the {@link #getCustomWrite} method like so:
 * <pre class="code">
 * public Object getCustomWrite(Object o) {
 *     if (o instanceof Widget) {
 *         Map m = new HashMap();
 *         m.put("type", "widget");
 *         m.put("name", widget.getName());
 *         return m;
 *     } else {
 *         return super.getCustomWrite(o);
 *     }
 * }
 * </pre>
 * and for reading the {@link #getCustomRead} methods:
 * <pre class="code">
 * public Object getCustomRead(Map m) {
 *     if ("widget".equals(m.get("type"))) {
 *         return Widget.getByName((String)m.get("name"));
 *     } else {
 *         return super.getCustomRead();
 .model2*     }
 * }
 * </pre>
 *
 * This class is thread safe and can be used in multiple threads simultaneously (presuming
 * the "lax" and "pretty" values are not being continually modified)
 */
class JsonReader {

    private final static Object ENDARRAY = new Object() { public String toString() { return "]"; } };
    private final static Object ENDMAP = new Object() { public String toString() { return "}"; } };
    private final static Object COMMA = new Object() { public String toString() { return ","; } };
    private final static Object COLON = new Object() { public String toString() { return ":"; } };

    /**
     * Parse a JSON serialized object from the Reader and return the Object it represents
     */
    static Object read(Reader reader, JsonReadOptions options) throws IOException {
        Object o = readToken(reader, null, false, options);
        int c;
        if ((c=stripBlanks(reader, options))!=-1) {
            throw new IllegalArgumentException("Unexpected trailing character '"+((char)c)+"'");
        }
        return o;
    }

    private static Object readToken(Reader reader, Object additional, boolean iskey, JsonReadOptions options) throws IOException {
        Object out = null;
        int c = stripBlanks(reader, options);
        if (c < 0) {
            throw new EOFException();
        } else if (c=='[') {
            Json list = new Json(new IList());
            Object o;
            while (true) {
                o = readToken(reader, ENDARRAY, false, options);
                if (o == ENDARRAY) {
                    if (list.size() == 0 || options.isAllowTrailingComma()) {
                        break;
                    } else {
                        throw new IllegalArgumentException("Unexpected token "+o+" at "+reader);
                    }
                } else if (o instanceof Json) {
                    Json child = (Json)o;
                    int size = list.size();
                    list._listValue().add(child);
                    Json.notify(list, Integer.valueOf(size), null, child);      // To set parent
                } else {
                    throw new IllegalArgumentException("Unexpected response "+o);
                }
                if ((o = readEnd(reader, options)) == ENDARRAY) {
                    break;
                } else if (o != COMMA) {
                    throw new IllegalArgumentException("Unexpected token "+o+" at "+reader);
                }
            }
            out = list;
        } else if (c=='{') {
            Json map = new Json(new IMap());
            Object o;
            while (true) {
                Object key = readToken(reader, ENDMAP, true, options);
                if (key == ENDMAP) {
                    if (map.size() == 0 || options.isAllowTrailingComma()) {
                        break;
                    } else {
                        throw new IllegalArgumentException("Unexpected token "+key+" at "+reader);
                    }
                }
                if (!(key instanceof String)) {
                    throw new IllegalArgumentException("Invalid object key "+key+" at "+reader);
                }
                if ((o = readEnd(reader, options)) != COLON) {
                    throw new IllegalArgumentException("Unexpected token "+o+" at "+reader);
                }
                Json child = (Json)readToken(reader, null, false, options);
                if (options.isNFC()) {
                    key = Normalizer.normalize((String)key, Normalizer.Form.NFC);
                }
                map._mapValue().put((String)key, child);
                Json.notify(map, key, null, child);     // To set parent
                if ((o = readEnd(reader, options)) == ENDMAP) {
                    break;
                } else if (o != COMMA) {
                    throw new IllegalArgumentException("Unexpected token "+o+" at "+reader);
                }
            }
            out = map;
        } else if ((c <= '9' && c >= '0') || c == '-') {
            long v = 0;
            StringBuilder sb = null;
            boolean real = false;
            short exp = 0;

            // Wringing the pips out of this one.
            // Optimized for most common case, where we're parsing an integer or long
            if (c == '0') {                             // "0"
                reader.mark(1);
                c = reader.read();
            } else if (c == '-') {
                c = reader.read();
                if (c == '0') {                         // "-0"
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
                    out = new Json(new INumber(Integer.valueOf(iv), options.storeOptions()));
                } else {
                    out = new Json(new INumber(Long.valueOf(v), options.storeOptions()));
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
                            if (Double.isFinite(d2) && bd.equals(new BigDecimal(d2))) {
                                out = new Json(new INumber(d, options.storeOptions()));
                            } else {
                                out = new Json(new INumber(bd, options.storeOptions()));
                            }
                        } else {
                            out = new Json(new INumber(d, options.storeOptions()));
                        }
                    } else if (exp != 0) {
                        out = new Json(new INumber(new BigDecimal(s).toBigInteger(), options.storeOptions()));
                    } else {
                        out = new Json(new INumber(new BigInteger(s), options.storeOptions()));
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number \""+s+"\" at "+reader, e);
                }
            }
            reader.reset();
        } else if (c == '"') {
            if (out == null) {
                try {
                    StringBuilder sb = new StringBuilder();
                    IString.parseString((char)c, reader, sb);
                    out = sb.toString();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(e.getMessage()+" at "+reader);
                }
            }
            if (!iskey) {
                if (options.isNFC()) {
                    out = Normalizer.normalize((String)out, Normalizer.Form.NFC);
                }
                out = new Json(new IString((String)out, options.storeOptions()));
            }
        } else if ((c=='t' || c=='f' || c=='n') && !iskey) {
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
                out = new Json(new IBoolean(true, options.storeOptions()));
            } else if (q.equals("false")) {
                out = new Json(new IBoolean(false, options.storeOptions()));
            } else if (q.equals("null")) {
                out = new Json(null);
            } else {
                throw new IllegalArgumentException("Invalid token \""+q+"\" at "+reader);
            }
        } else if (c==']' && additional==ENDARRAY) {
            out = ENDARRAY;
        } else if (c=='}' && additional==ENDMAP) {
            out = ENDMAP;
        } else if (iskey && options.isAllowUnquotedKey()) {
            StringBuilder sb = new StringBuilder();
            sb.append((char)c);
            reader.mark(1);
            while ((c=reader.read()) >= 0 && ((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='_' || c=='-' || c=='.')) {
                
                reader.mark(1);
                sb.append((char)c);
            }
            reader.reset();
            out = sb.toString();
        } else {
            throw new IllegalArgumentException("Unexpected token '"+((char)c)+"' at "+reader);
        }
//        System.out.println("-> read(1) "+out);
        return out;
    }

    private static Object readEnd(Reader reader, JsonReadOptions options) throws IOException, IllegalArgumentException {
        Object out;
        int c = stripBlanks(reader, options);
        if (c==']') {
            out = ENDARRAY;
        } else if (c=='}') {
            out = ENDMAP;
        } else if (c==',') {
            out = COMMA;
        } else if (c==':') {
            out = COLON;
        } else if (c==-1) {
            throw new EOFException();
        } else {
            throw new IllegalArgumentException("Unexpected character '"+((char)c)+"' at "+reader);
        }
//        System.out.println("--> read(2) "+out);
        return out;
    }

    private static int stripBlanks(Reader reader, JsonReadOptions options) throws IOException {
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
                return stripBlanks(reader, options);
            } else if (c < 0) {
                throw new EOFException("Trailing /");
            } else {
                reader.reset();
            }
        }
        return c;
    }

}
