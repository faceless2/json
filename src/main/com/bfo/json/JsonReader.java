package com.bfo.json;

import java.util.*;
import java.text.*;
import java.math.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

/**
 * Class to read/write objects as JSON.
 */
public class JsonReader extends AbstractReader {

    private State state;
    private CharSource in;
    private Deque<JsonStream.Event> eq;

    private boolean optionBigDecimal;
    private boolean optionTrailingComma;
    private boolean optionUnquotedKey;
    private boolean optionComments;
    private boolean optionCborDiag;

    public JsonReader() {
        this.state = new InitialState();
        this.eq = new ArrayDeque<JsonStream.Event>();
    }

    public JsonReader setTrailingComma(boolean option) {
        this.optionTrailingComma = option;
        return this;
    }

    public JsonReader setBigDecimal(boolean option) {
        this.optionBigDecimal = option;
        return this;
    }

    public JsonReader setCborDiag(boolean option) {
        this.optionCborDiag = option;
        return this;
    }

    @Override public JsonReader setInput(Readable in) {
        return (JsonReader)super.setInput(in);
    }
    @Override public JsonReader setInput(CharSequence in) {
        return (JsonReader)super.setInput(in);
    }
    @Override public JsonReader setInput(ReadableByteChannel in) {
        return (JsonReader)super.setInput(in);
    }
    @Override public JsonReader setInput(InputStream in) {
        return (JsonReader)super.setInput(in);
    }
    @Override public JsonReader setInput(ByteBuffer in) {
        return (JsonReader)super.setInput(in);
    }
    @Override public JsonReader setFinal() {
        return (JsonReader)super.setFinal();
    }
    @Override public JsonReader setPartial() {
        return (JsonReader)super.setPartial();
    }
    @Override public JsonReader setDraining() {
        return (JsonReader)super.setDraining();
    }
    @Override public JsonReader setNonDraining() {
        return (JsonReader)super.setNonDraining();
    }
    @Override void setSource(CharSource source) {
        in = source;
    }

    private void setState(State state) throws IOException {
//        System.out.println("Set state="+state);
        this.state = state;
        if (state instanceof FinalState) {
            state.process();
        }
    }

    private void enqueue(JsonStream.Event event) {
        eq.addLast(event);
    }

    @Override public JsonStream.Event next() throws IOException {
        if (eq.isEmpty()) {
            hasNext();
        }
        return eq.removeFirst();
    }

    @Override public boolean hasNext() throws IOException {
        if (!eq.isEmpty()) {
            return true;
        }
        while (state.process() && eq.isEmpty());
        if (in != null && in.available() == 0 && isFinal()) {
            if (state instanceof NumberState) {
                ((NumberState)state).close();
            } else if (state instanceof TokenState) {
                ((TokenState)state).close();
            }
        }
        if (eq.isEmpty()) {
            if (requestCharSource()) {
                return hasNext();
            }
        }
        return !eq.isEmpty();
    }

    private abstract class State {
        State parent;
        abstract boolean process() throws IOException;
        State popTo() throws IOException {
            return this;
        }
    }

    private class InitialState extends State {
        @Override boolean process() throws IOException {
            if (!requestCharSource()) {
                return false;
            }
            setState(new ObjectState(new FinalState(), TYPE_ROOT));
            return true;
        }
    }

    private class FinalState extends State {
        boolean done;
        @Override boolean process() throws IOException {
            return false;
        }
        @Override State popTo() throws IOException {
            int c;
            in.mark(1);
            while ((c=in.get()) >= 0) {
                if (c != '\r' && c != ' ' && c != '\t' && c != '\n') {
                    in.reset();
                    if (isDraining()) {
                        throw new IOException("Trailing content");
                    }
                    break;
                }
                in.mark(1);
            }
            return this;
        }
    }

    private static final int TYPE_ROOT = 0, TYPE_LIST = 1, TYPE_MAP = 2;
    private static final int NONE = 0, VALUE = 0x01, KEY = 0x02, COMMA = 0x04, COLON = 0x08, ENDLIST = 0x10, ENDMAP = 0x20;
    private class ObjectState extends State {
        private final int type;
        private int mode;
        ObjectState(State parent, int type) {
            this.parent = parent;
            this.type = type;
            this.mode = type == TYPE_LIST ? VALUE|ENDLIST : type == TYPE_MAP ? KEY|ENDMAP : VALUE;
        }
        @Override State popTo() throws IOException {
            return type == TYPE_ROOT ? parent.popTo() : this;
        }
        @Override boolean process() throws IOException {
            int c;
            while ((c=in.get()) >=0 ) {
                switch (c) {
                    case ' ':
                    case '\t':
                    case '\r':
                    case '\n':
                        break;
                    case '[':
                        setState(newList());
                        enqueue(JsonStream.Event.startList(-1));
                        return false;
                    case ']':
                        setState(closeList());
                        enqueue(JsonStream.Event.endList());
                        return false;
                    case '{':
                        setState(newMap());
                        enqueue(JsonStream.Event.startMap(-1));
                        return false;
                    case '}':
                        setState(closeMap());
                        enqueue(JsonStream.Event.endMap());
                        return false;
                    case ',':
                        comma();
                        break;
                    case ':':
                        colon();
                        break;
                    case '"':
                        setState(newString());
                        enqueue(JsonStream.Event.startString(-1));
                        return false;
                    default:
                        if (c <= '9' && (c >= '0' || c == '-')) {
                            setState(newNumber(c));
                            return true;
                        } else if (c == 't' || c == 'f' || c == 'n' || (optionCborDiag && (c == 'h' || c == 'b' || c == 'u' || c == 'N' || c == 'I'))) {
                            setState(newToken(c));
                            return true;
                        } else {
                            throw new IOException("Unexpected character '" + format(Character.toString((char)c)) + "'");
                        }
                }
            }
            return false;
        }
        void comma() throws IOException {
            if ((mode & COMMA) != 0) {
                mode = type == TYPE_LIST ? VALUE : KEY;
            } else {
                throw new IOException("Unexpected comma");
            }
        }
        void colon() throws IOException {
            if ((mode & COLON) != 0) {
                mode = VALUE;
            } else {
                throw new IOException("Unexpected colon");
            }
        }
        State newList() throws IOException {
            if ((mode & VALUE) != 0) {
                mode = type == TYPE_LIST ? COMMA+ENDLIST : type == TYPE_MAP ? COMMA+ENDMAP : NONE;
                return new ObjectState(this, TYPE_LIST);
            } else {
                throw new IOException("Unexpected array-start");
            }
        }
        State newMap() throws IOException {
            if ((mode & VALUE) != 0) {
                mode = type == TYPE_LIST ? COMMA+ENDLIST : type == TYPE_MAP ? COMMA+ENDMAP : NONE;
                return new ObjectState(this, TYPE_MAP);
            } else {
                throw new IOException("Unexpected object-start");
            }
        }
        State closeList() throws IOException {
            if ((mode & ENDLIST) != 0) {
                return parent.popTo();
            } else {
                throw new IOException("Unexpected array-end");
            }
        }
        State closeMap() throws IOException {
            if ((mode & ENDMAP) != 0) {
                return parent.popTo();
            } else {
                throw new IOException("Unexpected object-end");
            }
        }
        State newString() throws IOException {
            if ((mode & VALUE) != 0) {
                mode = type == TYPE_LIST ? COMMA+ENDLIST : type == TYPE_MAP ? COMMA+ENDMAP : NONE;
                return new StringState(this);
            } else if ((mode & KEY) != 0) {
                mode = COLON;
                return new StringState(this);
            } else {
                throw new IOException("Unexpected string");
            }
        }
        State newNumber(int c) throws IOException {
            if ((mode & VALUE) != 0) {
                mode = type == TYPE_LIST ? COMMA+ENDLIST : type == TYPE_MAP ? COMMA+ENDMAP : NONE;
                return new NumberState(this, c);
            } else if ((mode & KEY) != 0 && optionCborDiag) {
                mode = COLON;
                return new NumberState(this, c);
            } else {
                throw new IOException("Unexpected number");
            }
        }
        State newToken(int c) throws IOException {
            if ((mode & VALUE) != 0) {
                mode = type == TYPE_LIST ? COMMA+ENDLIST : type == TYPE_MAP ? COMMA+ENDMAP : NONE;
                return new TokenState(this, (char)c);
            } else if ((mode & KEY) != 0 && optionCborDiag) {
                mode = COLON;
                return new TokenState(this, (char)c);
            } else {
                throw new IOException("Unexpected token");
            }
        }
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append(type == TYPE_ROOT?"root":type==TYPE_LIST?"list":"map");
            if ((mode & VALUE) != 0) sb.append(" value");
            if ((mode & KEY) != 0) sb.append(" key");
            if ((mode & COMMA) != 0) sb.append(" comma");
            if ((mode & COLON) != 0) sb.append(" colon");
            if ((mode & ENDLIST) != 0) sb.append(" endlist");
            if ((mode & ENDMAP) != 0) sb.append(" endmap");
            sb.append("}");
            return sb.toString();
        }
    }

    private class StringState extends State {
        private int esc = 0, val = 0;
        StringState(State parent) {
            this.parent = parent;
        }
        @Override boolean process() throws IOException {
//            System.out.println("IN: esc="+esc+" val="+val+" av="+in.available());
            if (esc == 0) {
                int available = in.available();
                if (available == 0) {
                    in.mark(1);
                    if (in.get() < 0) {
                        return false;
                    }
                    in.reset();
                }
                in.mark(available);
                int i = 0, c = 0;
                for (i=0;i<available;i++) {
                    c = in.get();
//                    System.out.println("-- i="+i+"/"+available+" c="+c);
                    if (c == '"') {
                        break;
                    } else if (c == '\\') {
                        esc = 1;
                        break;
                    } else if (c < 0x20 || (c >= 0x80 && c < 0xa0)) {
                        throw new IOException(format("Invalid string character " + c));
                    }
                }
                if (i > 0) {
                    in.reset();
                    CharSequence seq = in.get(i);
                    if (i < available) {
                        in.get();
                    }
                    enqueue(JsonStream.Event.stringData(seq));       // if i==available we have missed one call to in.get()
                }
                if (c == '"') {
                    enqueue(JsonStream.Event.endString());
                    setState(parent.popTo());
                }
                return true;
            } else if (esc == 1) {
                int c = in.get();
                switch (c) {
                    case -1:   return false;
                    case '\\': enqueue(JsonStream.Event.stringData("\\")); esc = 0; break;
                    case '\"': enqueue(JsonStream.Event.stringData("\"")); esc = 0; break;
                    case '/':  enqueue(JsonStream.Event.stringData("/")); esc = 0; break;
                    case 'n':  enqueue(JsonStream.Event.stringData("\n")); esc = 0; break;
                    case 'r':  enqueue(JsonStream.Event.stringData("\r")); esc = 0; break;
                    case 't':  enqueue(JsonStream.Event.stringData("\t")); esc = 0; break;
                    case 'b':  enqueue(JsonStream.Event.stringData("\b")); esc = 0; break;
                    case 'f':  enqueue(JsonStream.Event.stringData("\f")); esc = 0; break;
                    case 'u':  esc = 2; break;
                    default:   throw new IOException("Invalid string escape \"\\" + format(Character.toString((char)c)) + "\"");
                }
                return true;
            } else {
                int c = in.get();
                if (c < 0) {
                    return false;
                }
                if (c <= '9' && c >= '0') {
                    val = val*10 + c - '0';
                } else if (c <= 'F' && c >= 'A') {
                    val = val*10 + c - 'A' + 10;
                } else if (c <= 'f' && c >= 'a') {
                    val = val*10 + c - 'a' + 10;
                } else {
                    throw new IOException("Invalid string unicode digit " + c);
                }
                if (esc++ == 5) {
                    enqueue(JsonStream.Event.stringData(Character.toString((char)val)));
                    esc = val = 0;
                }
                return true;
            }
        }
    }

    private static final int INIT = 0, INT0 = 1, INT = 2, DP = 3, FRAC = 4, EXP = 5, EXPSIGN = 6, EXPDIGIT = 7;
    private class NumberState extends State {
        private int mode;
        private long lval;
        private boolean negated;
        private StringBuilder sb;
        NumberState(State parent,  int c) {
            this.parent = parent;
            if (c == '-') {
                negated = true;
                mode = INIT;
            } else if (c == '0') {
                lval = 0;
                mode = INT0;
            } else if (c > '0' && c <= '9') {
                lval = c - '0';
                mode = INT;
            } else {
                throw new IllegalArgumentException();
            }
        }
        @Override boolean process() throws IOException {
            in.mark(1);
            int c = in.get();
            if (c < 0) {
                return false;
            }
            if (mode == INIT && (c < '0' || c > '9')) {
                // Hyphen followed by something not a digit
                if (c == 'I' && optionCborDiag) {
                    setState(new TokenState(parent, "-" + ((char)c)));
                    return true;
                } else {
                    throw new IOException("Unexpected hyphen");
                }
            }
            while (c != ' ' && c != ']' && c != ',' && c != '}' && c != ':' && c != '\n' && c != '\t' && c != '\r') {
                switch (mode) {
                    case INIT:
                        if (c == '0') {
                            mode = INT0;
                            lval = 0;
                        } else if (c >= '1' && c <= '9') {
                            mode = INT;
                            lval = c - '0';
                        } else {
                            throw new IOException("Invalid number " + format((negated ? "-" : "") + (char)c));
                        }
                        break;
                    case INT0:
                        if (c == '.' || c == 'e' || c == 'E') {
                            sb = new StringBuilder();
                            sb.append(negated ? "-0" : "0");
                            sb.append((char)c);
                            mode = c == '.' ? DP : EXP;
                        } else if (c == '(' && optionCborDiag) {
                            setState(new ObjectState(new TagState(parent, 0), TYPE_ROOT));
                            return true;
                        } else {
                            throw new IOException("Invalid number " + format("0" + (char)c));
                        }
                        break;
                    case INT:
                        if (c >= '0' && c <= '9') {
                            if (sb == null) {
                                lval = lval * 10 + c - '0';
                                if (lval < Long.MAX_VALUE / 10) {
                                    sb = new StringBuilder();
                                    if (negated) {
                                        sb.append('-');
                                    }
                                    sb.append(lval);
                                }
                            } else {
                                sb.append((char)c);
                            }
                        } else if (c == '.' || c == 'e' || c == 'E') {
                            if (sb == null) {
                                sb = new StringBuilder();
                                if (negated) {
                                    sb.append('-');
                                }
                                sb.append(lval);
                            }
                            sb.append((char)c);
                            mode = c == '.' ? DP : EXP;
                        } else if (c == '(' && optionCborDiag) {
                            if (sb != null) {
                                try {
                                    lval = new BigInteger(sb.toString()).longValueExact();
                                } catch (Exception e) {
                                    throw new IOException("Tag \"" + sb + "\" too large");
                                }
                            }
                            setState(new ObjectState(new TagState(parent, lval), TYPE_ROOT));
                            return true;
                        } else {
                            if (sb == null) {
                                sb = new StringBuilder();
                                if (negated) {
                                    sb.append('-');
                                }
                                sb.append(lval);
                            }
                            sb.append((char)c);
                            throw new IOException("Invalid number " + format(sb));
                        }
                        break;
                    case DP:
                        sb.append((char)c);
                        if (c >= '0' && c <= '9') {
                            mode = FRAC;
                        } else {
                            throw new IOException("Invalid number " + format(sb));
                        }
                        break;
                    case FRAC:
                        sb.append((char)c);
                        if (c >= '0' && c <= '9') {
                            mode = FRAC;
                        } else if (c == 'e' || c == 'E') {
                            mode = EXP;
                        } else {
                            throw new IOException("Invalid number " + format(sb));
                        }
                        break;
                    case EXP:
                        sb.append((char)c);
                        if (c >= '0' && c <= '9') {
                            mode = EXPDIGIT;
                        } else if (c == '+' || c == '-') {
                            mode = EXPSIGN;
                        } else {
                            throw new IOException("Invalid number " + format(sb));
                        }
                        break;
                    case EXPSIGN:
                    case EXPDIGIT:
                        sb.append((char)c);
                        if (c < '0' || c > '9') {
                            throw new IOException("Invalid number " + format(sb));
                        }
                        mode = EXPDIGIT;
                        break;
                    default:
                        throw new IllegalStateException("nsate="+mode);   // not possible
                }
                in.mark(1);
                c = in.get();
                if (c < 0) {
                    return false;
                }
            }
            in.reset();
            close();
            return false;
        }
        void close() throws IOException {
            String s = sb == null ? null : sb.toString();
            try {
                Number n = null;
                switch (mode) {
                    case DP:
                    case EXP:
                    case EXPSIGN:
                        throw new IOException("Invalid number " + format(s));
                    case INT0:
                        n = Integer.valueOf(0);
                        break;
                    case INT:
                        if (s != null) {
                            BigDecimal bd = new BigDecimal(s);
                            try {
                                n = Long.valueOf(bd.longValueExact());
                            } catch (Exception e) {
                                n = bd.toBigIntegerExact();
                            }
                        } else {
                            if (negated) {
                                lval = -lval;
                            }
                            if (lval <= Integer.MAX_VALUE && lval >= Integer.MIN_VALUE) {
                                n = Integer.valueOf((int)lval);
                            } else {
                                n = Long.valueOf(lval);
                            }
                        }
                        break;
                    default:
                        try {
                            Double d = Double.valueOf(s);
                            if (!d.isInfinite() && (!optionBigDecimal || d.toString().equalsIgnoreCase(s))) {
                                n = d;
                            }
                        } catch (NumberFormatException e) { }
                        if (n == null) {
                            BigDecimal bd = new BigDecimal(s);
                            double d = bd.doubleValue();
                            if (d == d && !Double.isInfinite(d) && bd.equals(new BigDecimal(d))) {
                                n = Double.valueOf(d);
                            } else {
                                n = bd;
                            }
                        }
                }
                enqueue(JsonStream.Event.numberValue(n));
                setState(parent.popTo());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid number " + format(s));
            }
        }
    }

    private class TokenState extends State {
        final StringBuilder sb;
        TokenState(State parent, String s) {
            this.parent = parent;
            sb = new StringBuilder(16);
            sb.append(s);
        }
        TokenState(State parent, char c) {
            this.parent = parent;
            sb = new StringBuilder(16);
            sb.append((char)c);
        }
        @Override boolean process() throws IOException {
            in.mark(1);
            int c = in.get();
            if (c < 0) {
                return false;
            }
            if (c == '\'' && optionCborDiag && sb.length() == 1) {
                c = sb.charAt(0);
                if (c == 'h') {
                    setState(new HexBufferState(parent));
                    return true;
                } else if (c == 'b') {
                    setState(new B64BufferState(parent));
                    return true;
                } else {
                    sb.append((char)c);
                    throw new IOException("Invalid token \"" + format(sb) + "\"");
                }
            }
            while (c != ' ' && c != ']' && c != ',' && c != '}' && c != ':' && c != '\n' && c != '\t' && c != '\r' && sb.length() < 14) {
                sb.append((char)c);
                in.mark(1);
                c = in.get();
                if (c < 0) {
                    return false;
                }
            }
            in.reset();
            if (sb.length() == 14) {
                sb.append("...");
            }
            close();
            return false;
        }
        void close() throws IOException {
            String s = sb.toString();
            if ("true".equals(s)) {
                enqueue(JsonStream.Event.booleanValue(true));
            } else if ("false".equals(s)) {
                enqueue(JsonStream.Event.booleanValue(false));
            } else if ("null".equals(s)) {
                enqueue(JsonStream.Event.nullValue());
            } else if ("undefined".equals(s) && optionCborDiag) {
                enqueue(JsonStream.Event.undefinedValue());
            } else if ("NaN".equals(s) && optionCborDiag) {
                enqueue(JsonStream.Event.numberValue(Float.NaN));
            } else if ("Infinity".equals(s) && optionCborDiag) {
                enqueue(JsonStream.Event.numberValue(Float.POSITIVE_INFINITY));
            } else if ("-Infinity".equals(s) && optionCborDiag) {
                enqueue(JsonStream.Event.numberValue(Float.NEGATIVE_INFINITY));
            } else {
                throw new IOException("Unexpected token \"" + format(s) + "\"");
            }
            setState(parent.popTo());
        }
    }

    private class TagState extends State {
        TagState(State parent, long tag) {
            this.parent = parent;
            enqueue(JsonStream.Event.tagNext(tag));
        }
        @Override boolean process() throws IOException {
            int c;
            while ((c=in.get()) >=0 ) {
                switch (c) {
                    case ' ':
                    case '\t':
                    case '\r':
                    case '\n':
                        break;
                    case ')':
                        setState(parent.popTo());
                        return true;
                    default:
                        throw new IOException("Missing tag-close");
                }
            }
            return false;
        }
    }

    private class HexBufferState extends State {
        HexBufferState(State parent) {
            this.parent = parent;
            enqueue(JsonStream.Event.startBuffer(-1));
        }
        @Override boolean process() throws IOException {
            if (in.available() == 0) {
                in.mark(1);
                if (in.get() < 0) {
                    return false;
                }
                in.reset();
            }
            final int available = in.available();
            ByteBuffer buf = ByteBuffer.allocate(available / 2 + 1);
            for (int i=0;i<available;i++) {
                int c = in.get();
                if (c == '\'') {
                    buf.flip();
                    enqueue(JsonStream.Event.bufferData(buf));
                    enqueue(JsonStream.Event.endBuffer());
                    setState(parent.popTo());
                    return true;
                } else if (i + 1 < available) {
                    if ((c <= '9' && c >= '0') || (c <= 'F' && c >= 'A') || (c <= 'f' && c >= 'a')) {
                        int d = in.get();
                        if ((d <= '9' && d >= '0') || (d <= 'F' && d >= 'A') || (d <= 'f' && d >= 'a')) {
                            int v1 = c <= '9' ? c - '0' : c >= 'a' ? c - 'a' + 10 : c - 'A' + 10;
                            int v2 = d <= '9' ? d - '0' : d >= 'a' ? d - 'a' + 10 : d - 'A' + 10;
                            int v = (v1 << 4) | v2;
                            buf.put((byte)((v1 << 4) | v2));
                        } else {
                            throw new IOException("Invalid hex digit '" + format(Character.toString((char)d)) + "'");
                        }
                    } else {
                        throw new IOException("Invalid hex digit '" + format(Character.toString((char)c)) + "'");
                    }
                } else {
                    break;
                }
            }
            buf.flip();
            if (buf.hasRemaining()) {
                enqueue(JsonStream.Event.bufferData(buf));
            }
            return true;
        }
    }

    private class B64BufferState extends State {
        private int count = 0, b0 = 0, b1 = 0, b2 = 0, eqcount = 0;
        B64BufferState(State parent) {
            this.parent = parent;
            enqueue(JsonStream.Event.startBuffer(-1));
        }
        @Override boolean process() throws IOException {
            if (in.available() == 0) {
                in.mark(1);
                if (in.get() < 0) {
                    return false;
                }
                in.reset();
            }
            final int available = in.available();
            ByteBuffer buf = ByteBuffer.allocate(available * 3 / 4 + 1);
            for (int i=0;i<available;i++) {
                int c = in.get();
                if (c == '\'') {
                    if (count == 2) {
                        buf.put((byte)b0);
                    } else if (count == 3) {
                        buf.put((byte)b0);
                        buf.put((byte)b1);
                    }
                    buf.flip();
                    enqueue(JsonStream.Event.bufferData(buf));
                    enqueue(JsonStream.Event.endBuffer());
                    setState(parent.popTo());
                    return true;
                } else {
                    int c0 = c;
                    if (c >= 'A' && c <= 'Z') {
                        c -= 'A';
                    } else if (c >= 'a' && c <= 'z') {
                        c -= 'a' - 26;
                    } else if (c >= '0' && c <= '9') {
                        c -= '0' - 52;
                    } else if (c == '+' || c == '-') {
                        c = (char)62;
                    } else if (c == '/' || c == '_') {
                        c = (char)63;
                    } else if (c == '=' && ++eqcount < 3) {
                        continue;
                    } else {
                        throw new IOException("Invalid base64 digit '" + format(Character.toString((char)c)) + "'");
                    }
                    if (count == 0) {
                        b0 = (byte)(c<<2);
                        b1 = b2 = 0;
                        count = 1;
                    } else if (count == 1) {
                        b0 |= (byte)(c>>4);
                        b1 = (byte)(c<<4);
                        count = 2;
                    } else if (count == 2) {
                        b1 |= (byte)(c>>2);
                        b2 |= (byte)(c<<6);
                        count = 3;
                    } else if (count == 3) {
                        b2 |= (byte)(c);
                        buf.put((byte)b0);
                        buf.put((byte)b1);
                        buf.put((byte)b2);
                        count = 0;
                    }
                }
            }
            buf.flip();
            if (buf.hasRemaining()) {
                enqueue(JsonStream.Event.bufferData(buf));
            }
            return true;
        }
    }

    private String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (State s=state;state != null;state=state.parent) {
            sb.append(state);
        }
        return sb.toString();
    }

    private static String format(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c < 0x20 || c > 0x7f) {
                sb.append("\\u");
                String t = Integer.toHexString(c);
                for (int j=t.length();j<4;j++) {
                    sb.append('0');
                }
                sb.append(t);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

}
