package com.bfo.json;

import java.nio.ByteBuffer;
import java.io.*;
import java.text.*;

class IString extends Core {

    private static final byte FLAG_SIMPLE = 64; // If true we can write this string without escaping
    private final String value;
    byte flags; // not final, we may set FLAG_SIMPLE

    IString(String value, JsonReadOptions options) {
        this.value = value;
        this.flags = options == null ? 0 : options.storeOptions();
    }

    @Override Object value() {
        return value;
    }

    @Override String type() {
        return "string";
    }

    @Override String stringValue() {
        return value;
    }

    private static final int BASE64[] = new int[256];
    static {
        String s = new String(IBuffer.BASE64);
        for (int i=0;i<256;i++) {
            BASE64[i] = s.indexOf(Character.toString((char)i));
        }
    }

    @Override ByteBuffer bufferValue() {
        int ilen = value.length();
        int olen = ilen / 4 * 3;
        if ((ilen & 3) == 1) {
            throw new ClassCastException("Base64 failed, length invalid");
        } else if ((ilen & 3) == 2) {
            olen++;
        } else if ((ilen & 3) == 3) {
            olen += 2;
        }
        ByteBuffer buf = ByteBuffer.allocate(olen);
        for (int i=0;i<ilen;) {
            int c = value.charAt(i);
            if (c > 255) {
                throw new ClassCastException("Base64 failed at " + i);
            }
            c = BASE64[c];
            if (c < 0) {
                throw new ClassCastException("Base64 failed at " + i);
            }
            int a = c << 18;
            if (i == ilen) {
                throw new ClassCastException("Base64 EOF at " + i);
            }
            c = value.charAt(i);
            if (c > 255) {
                throw new ClassCastException("Base64 failed at " + i);
            }
            c = BASE64[c];
            if (c < 0) {
                throw new ClassCastException("Base64 failed at " + i);
            }
            a |= c << 12;
            if (i == ilen) {
                buf.put((byte)(a >> 16));
                break;
            } else {
                c = value.charAt(i);
                if (c > 255) {
                    throw new ClassCastException("Base64 failed at " + i);
                }
                c = BASE64[c];
                if (c < 0) {
                    throw new ClassCastException("Base64 failed at " + i);
                }
                a |= c << 6;
                if (i == ilen) {
                    buf.put((byte)(a >> 16));
                    buf.put((byte)(a >> 8));
                    break;
                } else {
                    c = value.charAt(i);
                    if (c > 255) {
                        throw new ClassCastException("Base64 failed at " + i);
                    }
                    c = BASE64[c];
                    if (c < 0) {
                        throw new ClassCastException("Base64 failed at " + i);
                    }
                    a |= c;
                    buf.put((byte)(a >> 16));
                    buf.put((byte)(a >> 8));
                    buf.put((byte)a);
                }
            }
        }
        return buf;
    }

    private boolean flagTest(String type) {
        if ((flags & JsonReadOptions.FLAG_STRICT) != 0) {
            throw new ClassCastException("Cannot convert string \""+value+"\" to "+type+" in strict mode");
        }
        if (value.trim().toString().length() == 0) {
            if ((flags & JsonReadOptions.FLAG_LOOSEEMPTY) == 0) {
                throw new ClassCastException("Cannot convert empty string to "+type+" in strict mode");
            }
            return true;
        }
        return false;
    }

    @Override boolean booleanValue() {
        if (flagTest("boolean")) {
            return  false;
        }
        return !("false".equals(value) || floatValue() == 0);
    }

    @Override int intValue() {
        if (flagTest("integer")) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("Cannot convert ");
                write(value, 0, sb);
                sb.append(" to int");
                throw new ClassCastException(sb.toString());
            } catch (IOException e2) {
                throw new RuntimeException(e2); // can't happen
            }
        }
    }

    @Override long longValue() {
        if (flagTest("long")) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("Cannot convert ");
                write(value, 0, sb);
                sb.append(" to long");
                throw new ClassCastException(sb.toString());
            } catch (IOException e2) {
                throw new RuntimeException(e2); // can't happen
            }
        }
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

    @Override Number numberValue() {
        if (flagTest("number")) {
            return Integer.valueOf(0);
        }
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
                    return Integer.valueOf(value);
                }
                if (!real && value.length() < 19) {
                    return Long.valueOf(value);
                }
                return Double.valueOf(value);
            } catch (NumberFormatException e) { }
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Cannot convert ");
            write(value, 0, sb);
            sb.append(" to number");
            throw new ClassCastException(sb.toString());
        } catch (IOException e2) {
            throw new RuntimeException(e2); // can't happen
        }
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        int maxLength = state.options.getMaxStringLength();
        if (state.options.isNFC()) {
            String v = Normalizer.normalize(value, Normalizer.Form.NFC);
            if (write(v, maxLength, sb) && v.equals(value)) {
                flags |= FLAG_SIMPLE;
            }
        } else {
            if ((flags & FLAG_SIMPLE) != 0) {
                sb.append('"');
                if (maxLength == 0 || value.length() < maxLength) {
                    sb.append(value);
                } else {
                    sb.append(value, 0, maxLength);
                    sb.append("...");
                }
                sb.append('"');
            } else if (write(value, maxLength, sb)) {
                flags |= FLAG_SIMPLE;
            }
        }
    }

    static boolean write(String value, int maxLength, Appendable sb) throws IOException {
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

    private static int hexnibble(int c) {
        if (c>='0' & c<='9') {
            return c - '0';
        } else if (c<='F' & c>='A') {
            return c - 'A' + 10;
        } else if (c<='f' & c>='a') {
            return c - 'a' + 10;
        } else if (c < 0) {
            throw new IllegalArgumentException("Unexpected EOF in hex string");
        } else {
            throw new IllegalArgumentException("Invalid hex digit 0x"+Integer.toHexString(c));
        }
    }

    private static final boolean[] literal = new boolean[128];
    static {
        for (int c=0;c<128;c++) {
            literal[c] = c == 127 || !(Character.isISOControl(c) || c == '\\' || c == '"' || c == '\'');
        }
    }

    /**
     * When a quote is read from a Reader, this method can
     * be called to read to the end of the string and return it
     * @param quote the initial quote character to match. Note this must be " in json
     * @param r the reader
     * @param sb the StringBuilder to populate
     * @return the number of characters read
     */
    static int parseString(char quote, Reader reader, StringBuilder sb) throws IOException {
        boolean inquotes = true;
        int count = 0;
        int c;
        while ((c=reader.read()) >= 0) {
            count++;
            if (c < 128 && literal[c]) {
                sb.append((char)c);
            } else if (c == quote) {
               inquotes = false;
               break;
            } else if (c=='\\') {
                c = reader.read();
                count++;
                if (c=='\\' || c=='\"' || c=='/') {
                    sb.append((char)c);
                } else if (c=='b') {
                    sb.append('\b');
                } else if (c=='f') {
                    sb.append('\f');
                } else if (c=='n') {
                    sb.append('\n');
                } else if (c=='r') {
                    sb.append('\r');
                } else if (c=='t') {
                    sb.append('\t');
                } else if (c=='v') {
                    sb.append('\u000B');
                } else if (c=='u') {
                    int v = (hexnibble(reader.read()) << 12) | (hexnibble(reader.read()) << 8) | (hexnibble(reader.read()) << 4) | hexnibble(reader.read());
                    count += 4;
                    sb.append((char)v);
                /*
                } else if (c=='x') {
                    int v = (hexnibble(reader.read()) << 4) | hexnibble(reader.read());
                    count += 2;
                    sb.append((char)v);
                */
                } else {
                    throw new IllegalArgumentException("Invalid trailing backslash in string");
                }
            } else if (!Character.isISOControl(c)) {
                sb.append((char)c);
            } else {
                throw new IllegalArgumentException("Invalid string character 0x"+Integer.toHexString(c));
            }
        }
        if (inquotes) {
            throw new IllegalArgumentException("Unterminated string \""+sb+"\"");
        }
        return count;
    }

}
