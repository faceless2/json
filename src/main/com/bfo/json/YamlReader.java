package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.bfo.json.*;

/**
 * A Yaml reader, almost entirely based on the Tokenizer from the <a href="https://github.com/EsotericSoftware/yamlbeans">YamlBeans</a>
 * project. <b>Experimental.</b>. Tags, anchors and other weird aspects of Yaml are ignored, and in particular there is no use of
 * reflection for class creation.
 */
public class YamlReader extends AbstractReader {

    /**
     * This class is basically the "Tokenizer.java" class from YamlBeans, 
     * https://github.com/EsotericSoftware/yamlbeans
     *
     * With some changes
     *  -- braces on all blocks
     *  -- moved reading into "Input" inner class, decoupled from Reader
     *  -- moved various token types single to private inner class
     *  -- added block at the end turning Tokenizer tokens into BFO Json events
     *
     * Copyright (c) 2008 Nathan Sweet, Copyright (c) 2006 Ola Bini
     * 
     * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
     * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
     * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
     * is furnished to do so, subject to the following conditions:
     * 
     * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
     * 
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
     * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
     * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
     * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
     *
     * ----- Tokenizer.java
     *
     * Interprets a YAML document as a stream of tokens.
     * @author <a href="mailto:misc@n4te.com">Nathan Sweet</a>
     * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a> 
     */
    private final static String LINEBR = "\n\u0085\u2028\u2029";
    private final static String NULL_BL_LINEBR = "\0 \r\n\u0085";
    private final static String NULL_BL_T_LINEBR = "\0 \t\r\n\u0085";
    private final static String NULL_OR_OTHER = NULL_BL_T_LINEBR;
    private final static String NULL_OR_LINEBR = "\0\r\n\u0085";
    private final static String FULL_LINEBR = "\r\n\u0085";
    private final static String BLANK_OR_LINEBR = " \r\n\u0085";
    private final static String S4 = "\0 \t\r\n\u0028[]{}";
    private final static String ALPHA = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_";
    private final static String STRANGE_CHAR = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-#;/?:@&=+$,_.!~*'()[]";
    private final static String RN = "\r\n";
    private final static String BLANK_T = " \t";
    private final static String SPACES_AND_STUFF = "'\"\\\0 \t\r\n\u0085";
    private final static String DOUBLE_ESC = "\"\\";
    private final static String NON_ALPHA_OR_NUM = "\0 \t\r\n\u0085?:,]}%@`";
    private final static Pattern NON_PRINTABLE = Pattern.compile("[^\u0009\n\r\u0020-\u007E\u0085\u00A0-\u00FF]");
    private final static Pattern NOT_HEXA = Pattern.compile("[^0-9A-Fa-f]");
    private final static Pattern NON_ALPHA = Pattern.compile("[^-0-9A-Za-z_]");
    private final static Pattern R_FLOWZERO = Pattern.compile("[\0 \t\r\n\u0085]|(:[\0 \t\r\n\u0085])");
    private final static Pattern R_FLOWNONZERO = Pattern.compile("[\0 \t\r\n\u0085\\[\\]{},:?]");
    private final static Pattern END_OR_START = Pattern.compile("^(---|\\.\\.\\.)[\0 \t\r\n\u0085]$");
    private final static Pattern ENDING = Pattern.compile("^---[\0 \t\r\n\u0085]$");
    private final static Pattern START = Pattern.compile("^\\.\\.\\.[\0 \t\r\n\u0085]$");
    private final static Pattern BEG = Pattern
        .compile("^([^\0 \t\r\n\u0085\\-?:,\\[\\]{}#&*!|>'\"%@]|([\\-?:][^\0 \t\r\n\u0085]))");

    private final static Map<Character, String> ESCAPE_REPLACEMENTS = new HashMap<Character,String>();
    private final static Map<Character, Integer> ESCAPE_CODES = new HashMap<Character,Integer>();

    static {
        ESCAPE_REPLACEMENTS.put('0', "\u0000");
        ESCAPE_REPLACEMENTS.put('a', "\u0007");
        ESCAPE_REPLACEMENTS.put('b', "\u0008");
        ESCAPE_REPLACEMENTS.put('t', "\u0009");
        ESCAPE_REPLACEMENTS.put('\t', "\u0009");
        ESCAPE_REPLACEMENTS.put('n', "\n");
        ESCAPE_REPLACEMENTS.put('v', "\u000B");
        ESCAPE_REPLACEMENTS.put('f', "\u000C");
        ESCAPE_REPLACEMENTS.put('r', "\r");
        ESCAPE_REPLACEMENTS.put('e', "\u001B");
        ESCAPE_REPLACEMENTS.put(' ', "\u0020");
        ESCAPE_REPLACEMENTS.put('"', "\"");
        ESCAPE_REPLACEMENTS.put('\\', "\\");
        ESCAPE_REPLACEMENTS.put('N', "\u0085");
        ESCAPE_REPLACEMENTS.put('_', "\u00A0");
        ESCAPE_REPLACEMENTS.put('L', "\u2028");
        ESCAPE_REPLACEMENTS.put('P', "\u2029");

        ESCAPE_CODES.put('x', 2);
        ESCAPE_CODES.put('u', 4);
        ESCAPE_CODES.put('U', 8);
    }

    private boolean done = false;
    private int flowLevel = 0;
    private int tokensTaken = 0;
    private int indent = -1;
    private boolean allowSimpleKey = true;
    private final Input in;
    private final List<Token> tokens = new LinkedList<Token>();        // Usually a queue, but inserts randomly into middle too!
    private final Deque<Indent> indents = new ArrayDeque<Indent>();
    private final Map<Integer, SimpleKey> possibleSimpleKeys = new HashMap<>();
    private boolean docStart = false;

    public YamlReader() {
        this.in = new Input();
        fetchStreamStart();
    }

    boolean push(int c) {
        return in.push(c);
    }

    private static class Indent {
        final int indent;
        final boolean fromList;
        Indent(int indent, boolean fromList) {
            this.indent = indent;
            this.fromList = fromList;
        }
        public String toString() {
            return "{indent:"+indent+",fromList:"+fromList+"}";
        }
    }

    private class Input {
        private char[] buf = new char[8192];
        private int head, tail;
        private int lineNumber = 0;
        private int column = 0;
        private int pointer = 0;
        private boolean eof;
        private CharSource source;

        void setSource(CharSource source) {
            this.source = source;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            int l = 0;
            for (int i=head;i!=tail;) {
                char c = buf[i];
                switch (c) {
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '"': sb.append("\\\""); break;
                    case 0: sb.append("<NUL>"); break;
                    default: sb.append(c);
                }
                if (++i == buf.length) {
                    i = 0;
                }
                l++;
            }
            sb.append("\" len="+l);
            return sb.toString();
        }

        /**
         * Return true if we are ready to try and read tokens
         */
        boolean pull() throws IOException {
            if (eof) {
                return true;
            }
            if (source == null) {
                requestCharSource();
            }
            // Read one beyond - we want to push a -1 at the end;
            int c;
            do {
                if (push(c = source.get())) {
                    break;
                }
            } while (c >= 0);
            return true;
        }

        boolean push(final int c) {
            if (c < 0) {
                if (eof) {
                    return true;
                } else {
                    eof = true;
                }
            }
            buf[tail++] = c < 0 ? 0 : (char)c;
            if (tail == buf.length) {
                tail = 0;
            } else if (tail == head) {      // buffer is full
                char[] t = new char[buf.length * 2];
                int j = head;
                for (int i=0;i<buf.length;i++) {
                    char v = buf[j++];
                    if (j == buf.length) {
                        j = 0;
                    }
                    t[i] = v;
                }
                head = 0;
                tail = buf.length;
                buf = t;
            }
            // Return true when we know we have at least one token remaining, but
            // when? Might be in the middle of a long string. When does a string
            // end? No idea!
            // See https://stackoverflow.com/questions/3790454/how-do-i-break-a-string-in-yaml-over-multiple-lines
            // So just return "ready" at EOF or when we have 8K of data - so no strings longer than 8K please.
            return eof || available() == 8192;
        }

        int available() {
            int avail = tail - head;
            if (avail < 0) {
                avail += buf.length;
            }
            return avail;
        }

        // Get character (pointer + 1) without advancing, or 0 if past eof
        char peek() throws IOException {
            return peek(0);
        }

        // Get character (point + index + 1) without advancing, or 0 if past eof
        char peek(int index) throws IOException {
            if (index + 1 > available()) {
                if (eof) {
                    return 0;
                } else {
                    throw new IllegalStateException("Too far");
                }
            }
            int ix = head + index;
            if (ix > buf.length) {
                ix -= buf.length;
            }
            return buf[ix];
        }

        // Get characters (pointer <= i < pointer.length) without advancing pointer.
        // Prefix will be have zeros appended to make up length? Not sure this is intentional
        String prefix(int length) throws IOException {
            if (length > available()) {
                length = available();
            }
            char[] tmp = new char[length];
            for (int i=0;i<length;i++) {
                tmp[i] = peek(i);
            }
            return new String(tmp);
        }

        // Get characters (pointer <= i < pointer.length), advancing pointer
        // Prefix will be have zeros appended to make up length? Not sure this is intentional
        String prefixForward(int length) throws IOException {
            String p = prefix(length);
            for (int i=0;i<p.length();i++) {
                forward();
            }
            return p;
        }

        // Move pointer forward 1
        void forward() throws IOException {
            if (available() == 0) {
                throw new IllegalStateException("Too far");
            }
            int c = peek();
            head++;
            if (head == buf.length) {
                head = 0;
            } else if (head == tail) {
                head = tail = 0;
            }
            if (c == '\n') {
                column = 0;
                lineNumber++;
            } else {
                column++;
            }
        }

        // Move pointer forward "length"
        void forward(int length) throws IOException {
            for (int i=0;i<length;i++) {
                forward();
            }
        }

        // Column, starting at zero
        int column() {
            return column;
        }
    }

    //------------------------------------

    private Token peekNextToken() throws IOException {
        while (needMoreTokens()) {
            fetchMoreTokens();
        }
        return tokens.isEmpty() ? null : tokens.get(0);
    }

    private Token getNextToken() throws IOException {
        while (needMoreTokens()) {
            fetchMoreTokens();
        }
        Token token = null;
        if (!tokens.isEmpty()) {
            tokensTaken++;
            token = tokens.remove(0);
        }
        return token;
    }

    private boolean needMoreTokens() throws IOException {
        return !done && tokens.isEmpty() || nextPossibleSimpleKey() == tokensTaken;
    }

    private Token fetchMoreTokens() throws IOException {
        scanToNextToken();
        unwindIndent(in.column());
        char ch = in.peek();
        boolean colz = in.column() == 0;
        switch (ch) {
            case 0:
                return fetchStreamEnd();
            case '\'':
                return fetchSingle();
            case '"':
                return fetchDouble();
            case '?':
                if (flowLevel != 0 || NULL_OR_OTHER.indexOf(in.peek(1)) != -1) {
                    return fetchKey();
                }
                break;
            case ':':
                if (flowLevel != 0 || NULL_OR_OTHER.indexOf(in.peek(1)) != -1) {
                    return fetchValue();
                }
                break;
            case '%':
                if (colz) {
                    return fetchDirective();
                }
                break;
            case '-':
                if ((colz || docStart) && ENDING.matcher(in.prefix(4)).matches()) {
                    return fetchDocumentStart();
                } else if (NULL_OR_OTHER.indexOf(in.peek(1)) != -1) {
                    return fetchBlockEntry();
                }
                break;
            case '.':
                if (colz && START.matcher(in.prefix(4)).matches()) {
                    return fetchDocumentEnd();
                }
                break;
            case '[':
                return fetchFlowSequenceStart();
            case '{':
                return fetchFlowMappingStart();
            case ']':
                return fetchFlowSequenceEnd();
            case '}':
                return fetchFlowMappingEnd();
            case ',':
                return fetchFlowEntry();
            case '*':
                return fetchAlias();
            case '&':
                return fetchAnchor();
            case '!':
                return fetchTag();
            case '|':
                if (flowLevel == 0) {
                    return fetchLiteral();
                }
                break;
            case '>':
                if (flowLevel == 0) {
                    return fetchFolded();
                }
                break;
        }
        if (BEG.matcher(in.prefix(2)).find()) {
            return fetchPlain();
        }
        if (ch == '\t') {
            throw new IOException("Tabs cannot be used for indentation.");
        }
        throw new IOException("While scanning for the next token, a character that cannot begin a token was found: " + ch(ch));
    }

    private int nextPossibleSimpleKey() {
        for (SimpleKey key : possibleSimpleKeys.values()) {
            if (key.tokenNumber > 0) {
                return key.tokenNumber;
            }
        }
        return -1;
    }

    private void savePossibleSimpleKey() {
        if (allowSimpleKey) {
            possibleSimpleKeys.put(flowLevel, new SimpleKey(tokensTaken + tokens.size(), in.column()));
        }
    }

    private void unwindIndent(int col) {
        if (flowLevel != 0) {
            return;
        }
        while (indent > col) {
            Indent i = indents.removeFirst();
            indent = i.indent;
            tokens.add(Token.BLOCK_END);
        }
    }

    private boolean addIndent(int col, boolean fromList) {
        // The case this change is designed to fix is "mb-yaml-001"
        // This change ensures https is a sibling of foo, not a child.
        // 
        if (indent < col || (fromList && indent == col && (indents.isEmpty() || !indents.peekFirst().fromList))) {
            Indent i = new Indent(indent, fromList);
            indents.addFirst(i);
            indent = col;
            return true;
        }
        return false;
    }

    private Token fetchStreamStart() {
        docStart = true;
        tokens.add(Token.STREAM_START);
        return Token.STREAM_START;
    }

    private Token fetchStreamEnd() throws IOException {
        unwindIndent(-1);
        allowSimpleKey = false;
        possibleSimpleKeys.clear();
        tokens.add(Token.STREAM_END);
        done = true;
        return Token.STREAM_END;
    }

    private Token fetchDirective() throws IOException {
        unwindIndent(-1);
        allowSimpleKey = false;
        Token tok = scanDirective();
        tokens.add(tok);
        return tok;
    }

    private Token fetchDocumentStart() throws IOException {
        docStart = false;
        return fetchDocumentIndicator(Token.DOCUMENT_START);
    }

    private Token fetchDocumentEnd() throws IOException {
        return fetchDocumentIndicator(Token.DOCUMENT_END);
    }

    private Token fetchDocumentIndicator (Token tok) throws IOException {
        unwindIndent(-1);
        allowSimpleKey = false;
        in.forward(3);
        tokens.add(tok);
        return tok;
    }

    private Token fetchFlowSequenceStart() throws IOException {
        return fetchFlowCollectionStart(Token.FLOW_SEQUENCE_START);
    }

    private Token fetchFlowMappingStart() throws IOException {
        return fetchFlowCollectionStart(Token.FLOW_MAPPING_START);
    }

    private Token fetchFlowCollectionStart (Token tok) throws IOException {
        savePossibleSimpleKey();
        flowLevel++;
        allowSimpleKey = true;
        in.forward(1);
        tokens.add(tok);
        return tok;
    }

    private Token fetchFlowSequenceEnd() throws IOException {
        return fetchFlowCollectionEnd(Token.FLOW_SEQUENCE_END);
    }

    private Token fetchFlowMappingEnd() throws IOException {
        return fetchFlowCollectionEnd(Token.FLOW_MAPPING_END);
    }

    private Token fetchFlowCollectionEnd (Token tok) throws IOException {
        flowLevel--;
        allowSimpleKey = false;
        in.forward(1);
        tokens.add(tok);
        return tok;
    }

    private Token fetchFlowEntry() throws IOException {
        allowSimpleKey = true;
        in.forward(1);
        tokens.add(Token.FLOW_ENTRY);
        return Token.FLOW_ENTRY;
    }

    private Token fetchBlockEntry() throws IOException {
        if (flowLevel == 0) {
            if (!allowSimpleKey) {
                throw new IOException("Found a sequence entry where it is not allowed.");
            }
            if (addIndent(in.column(), true)) {
                tokens.add(Token.BLOCK_SEQUENCE_START);
            }
        }
        allowSimpleKey = true;
        in.forward();
        tokens.add(Token.BLOCK_ENTRY);
        return Token.BLOCK_ENTRY;
    }

    private Token fetchKey() throws IOException {
        if (flowLevel == 0) {
            if (!allowSimpleKey) {
                throw new IOException("Found a mapping key where it is not allowed.");
            }
            if (addIndent(in.column(), false)) {
                tokens.add(Token.BLOCK_MAPPING_START);
            }
        }
        allowSimpleKey = flowLevel == 0;
        in.forward();
        tokens.add(Token.KEY);
        return Token.KEY;
    }

    private Token fetchValue() throws IOException {
        SimpleKey key = possibleSimpleKeys.get(flowLevel);
        if (key == null) {
            if (flowLevel == 0 && !allowSimpleKey) {
                throw new IOException("Found a mapping value where it is not allowed.");
            }
        } else {
            possibleSimpleKeys.remove(flowLevel);
            tokens.add(key.tokenNumber - tokensTaken, Token.KEY);
            if (flowLevel == 0 && addIndent(key.column, false)) {
                tokens.add(key.tokenNumber - tokensTaken, Token.BLOCK_MAPPING_START);
            }
            allowSimpleKey = false;
        }
        in.forward();
        tokens.add(Token.VALUE);
        return Token.VALUE;
    }

    private Token fetchAlias() throws IOException {
        savePossibleSimpleKey();
        allowSimpleKey = false;
        Token tok = scanAnchor(new Token(Type.ALIAS));
        tokens.add(tok);
        return tok;
    }

    private Token fetchAnchor() throws IOException {
        savePossibleSimpleKey();
        allowSimpleKey = false;
        Token tok = scanAnchor(new Token(Type.ANCHOR));
        tokens.add(tok);
        return tok;
    }

    private Token fetchTag() throws IOException {
        savePossibleSimpleKey();
        allowSimpleKey = false;
        Token tok = scanTag();
        tokens.add(tok);
        return tok;
    }

    private Token fetchLiteral() throws IOException {
        return fetchBlockScalar('|');
    }

    private Token fetchFolded() throws IOException {
        return fetchBlockScalar('>');
    }

    private Token fetchBlockScalar(char style) throws IOException {
        allowSimpleKey = true;
        Token tok = scanBlockScalar(style);
        tokens.add(tok);
        return tok;
    }

    private Token fetchSingle() throws IOException {
        return fetchFlowScalar('\'');
    }

    private Token fetchDouble() throws IOException {
        return fetchFlowScalar('"');
    }

    private Token fetchFlowScalar(char style) throws IOException {
        savePossibleSimpleKey();
        allowSimpleKey = false;
        Token tok = scanFlowScalar(style);
        tokens.add(tok);
        return tok;
    }

    private Token fetchPlain() throws IOException {
        savePossibleSimpleKey();
        allowSimpleKey = false;
        Token tok = scanPlain();
        tokens.add(tok);
        return tok;
    }

    private void scanToNextToken() throws IOException {
        while (true) {
            while (in.peek() == ' ') {
                in.forward();
            }
            if (in.peek() == '#') while (NULL_OR_LINEBR.indexOf(in.peek()) == -1) {
                in.forward();
            }
            if (scanLineBreak().length() != 0) {
                if (flowLevel == 0) {
                    allowSimpleKey = true;
                }
            } else {
                break;
            }
        }
    }

    private Token scanDirective() throws IOException {
        in.forward();
        String name = scanDirectiveName();
        String value = null;
        if (name.equals("YAML")) {
            value = scanYamlDirectiveValue();
        } else if (name.equals("TAG")) {
            value = scanTagDirectiveValue();
        } else {
            StringBuilder tmp = new StringBuilder();
            while (true) {
                char ch = in.peek();
                if (NULL_OR_LINEBR.indexOf(ch) != -1) {
                    break;
                }
                tmp.append(ch);
                in.forward();
            }
            value = tmp.toString().trim();
        }
        scanDirectiveIgnoredLine();
        return Token.newDirective(name, value);
    }

    private String scanDirectiveName() throws IOException {
        int length = 0;
        char ch = in.peek(length);
        boolean zlen = true;
        while (ALPHA.indexOf(ch) != -1) {
            zlen = false;
            length++;
            ch = in.peek(length);
        }
        if (zlen) {
            throw new IOException("While scanning for a directive name, expected an alpha or numeric character but found: " + ch(ch));
        }
        String value = in.prefixForward(length);
        // in.forward(length);
        if (NULL_BL_LINEBR.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning for a directive name, expected an alpha or numeric character but found: " + ch(ch));
        }
        return value;
    }

    private String scanYamlDirectiveValue() throws IOException {
        while (in.peek() == ' ') {
            in.forward();
        }
        String major = scanYamlDirectiveNumber();
        if (in.peek() != '.') {
            throw new IOException("While scanning for a directive value, expected a digit or '.' but found: " + ch(in.peek()));
        }
        in.forward();
        String minor = scanYamlDirectiveNumber();
        if (NULL_BL_LINEBR.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning for a directive value, expected a digit or '.' but found: " + ch(in.peek()));
        }
        return major + "." + minor;
    }

    private String scanYamlDirectiveNumber() throws IOException {
        char ch = in.peek();
        if (!Character.isDigit(ch)) {
            throw new IOException("While scanning for a directive number, expected a digit but found: " + ch(ch));
        }
        int length = 0;
        while (Character.isDigit(in.peek(length))) {
            length++;
        }
        String value = in.prefixForward(length);
        // in.forward(length);
        return value;
    }

    private String scanTagDirectiveValue() throws IOException {
        while (in.peek() == ' ') {
            in.forward();
        }
        String handle = scanTagDirectiveHandle();
        while (in.peek() == ' ') {
            in.forward();
        }
        String prefix = scanTagDirectivePrefix();
        return handle + " " + prefix;
    }

    private String scanTagDirectiveHandle() throws IOException {
        String value = scanTagHandle("directive");
        if (in.peek() != ' ') {
            throw new IOException("While scanning for a directive tag handle, expected ' ' but found: " + ch(in.peek()));
        }
        return value;
    }

    private String scanTagDirectivePrefix() throws IOException {
        String value = scanTagUri("directive");
        if (NULL_BL_LINEBR.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning for a directive tag prefix, expected ' ' but found: " + ch(in.peek()));
        }
        return value;
    }

    private String scanDirectiveIgnoredLine() throws IOException {
        while (in.peek() == ' ') {
            in.forward();
        }
        if (in.peek() == '"') while (NULL_OR_LINEBR.indexOf(in.peek()) == -1) {
            in.forward();
        }
        char ch = in.peek();
        if (NULL_OR_LINEBR.indexOf(ch) == -1) {
            throw new IOException("While scanning a directive, expected a comment or line break but found: " + ch(in.peek()));
        }
        return scanLineBreak();
    }

    private Token scanAnchor(Token tok) throws IOException {
        char indicator = in.peek();
        String name = indicator == '*' ? "alias" : "anchor";
        in.forward();
        int length = 0;
        int chunk_size = 16;
        Matcher m = null;
        while (true) {
            String chunk = in.prefix(chunk_size);
            if ((m = NON_ALPHA.matcher(chunk)).find()) {
                break;
            }
            chunk_size += 16;
        }
        length = m.start();
        if (length == 0) {
            throw new IOException("While scanning an " + name + ", a non-alpha, non-numeric character was found.");
        }
        String value = in.prefixForward(length);
        // in.forward(length);
        if (NON_ALPHA_OR_NUM.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning an " + name + ", expected an alpha or numeric character but found: " + ch(in.peek()));
        }
        if (tok.type == Type.ANCHOR || tok.type == Type.ALIAS) {
            tok.value = value;
        }
        return tok;
    }

    private Token scanTag() throws IOException {
        char ch = in.peek(1);
        String handle = null;
        String suffix = null;
        if (ch == '<') {
            in.forward(2);
            suffix = scanTagUri("tag");
            if (in.peek() != '>') {
                throw new IOException("While scanning a tag, expected '>' but found: " + ch(in.peek()));
            }
            in.forward();
        } else if (NULL_BL_T_LINEBR.indexOf(ch) != -1) {
            suffix = "!";
            in.forward();
        } else {
            int length = 1;
            boolean useHandle = false;
            while (NULL_BL_T_LINEBR.indexOf(ch) == -1) {
                if (ch == '!') {
                    useHandle = true;
                    break;
                }
                length++;
                ch = in.peek(length);
            }
            handle = "!";
            if (useHandle) {
                handle = scanTagHandle("tag");
            } else {
                handle = "!";
                in.forward();
            }
            suffix = scanTagUri("tag");
        }
        if (NULL_BL_LINEBR.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning a tag, expected ' ' but found: " + ch(in.peek()));
        }
        return Token.newTag(handle, suffix);
    }

    private Token scanBlockScalar(char style) throws IOException {
        boolean folded = style == '>';
        StringBuilder chunks = new StringBuilder();
        in.forward();
        Object[] chompi = scanBlockScalarIndicators();
        int chomping = ((Integer)chompi[0]).intValue();
        int increment = ((Integer)chompi[1]).intValue();
        scanBlockScalarIgnoredLine();
        int minIndent = indent + 1;
        if (minIndent < 1) {
            minIndent = 1;
        }
        String breaks = null;
        int maxIndent = 0;
        int ind = 0;
        if (increment == -1) {
            Object[] brme = scanBlockScalarIndentation();
            breaks = (String)brme[0];
            maxIndent = ((Integer)brme[1]).intValue();
            if (minIndent > maxIndent) {
                ind = minIndent;
            } else {
                ind = maxIndent;
            }
        } else {
            ind = minIndent + increment - 1;
            breaks = scanBlockScalarBreaks(ind);
        }

        String lineBreak = "";
        while (in.column() == ind && in.peek() != 0) {
            chunks.append(breaks);
            boolean leadingNonSpace = BLANK_T.indexOf(in.peek()) == -1;
            int length = 0;
            while (NULL_OR_LINEBR.indexOf(in.peek(length)) == -1) {
                length++;
            }
            chunks.append(in.prefixForward(length));
            // in.forward(length);
            lineBreak = scanLineBreak();
            breaks = scanBlockScalarBreaks(ind);
            if (in.column() == ind && in.peek() != 0) {
                if (folded && lineBreak.equals("\n") && leadingNonSpace && BLANK_T.indexOf(in.peek()) == -1) {
                    if (breaks.length() == 0) chunks.append(" ");
                } else {
                    chunks.append(lineBreak);
                }
            } else {
                break;
            }
        }

        if (chomping == 0) {
            chunks.append(lineBreak);
        } else if (chomping == 2) {
            chunks.append(lineBreak);
            chunks.append(breaks);
        }

        return Token.newScalar(chunks.toString(), style);
    }

    private Object[] scanBlockScalarIndicators() throws IOException {
        int chomping = 0;  // 0 = clip, 1 = strip, 2 = keep
        int increment = -1;
        char ch = in.peek();
        if (ch == '-' || ch == '+') {
            chomping = ch == '-' ? 1 : 2;
            in.forward();
            ch = in.peek();
            if (Character.isDigit(ch)) {
                increment = Integer.parseInt(("" + ch));
                if (increment == 0) {
                    throw new IOException("While scanning a black scaler, expected indentation indicator between 1 and 9 but found: 0");
                }
                in.forward();
            }
        } else if (Character.isDigit(ch)) {
            increment = Integer.parseInt(("" + ch));
            if (increment == 0) {
                throw new IOException("While scanning a black scaler, expected indentation indicator between 1 and 9 but found: 0");
            }
            in.forward();
            ch = in.peek();
            if (ch == '-' || ch == '+') {
                chomping = ch == '-' ? 1 : 2;
                in.forward();
            }
        }
        if (NULL_BL_LINEBR.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning a block scalar, expected chomping or indentation indicators but found: " + ch(in.peek()));
        }
        return new Object[] {Integer.valueOf(chomping), increment};
    }

    private String scanBlockScalarIgnoredLine() throws IOException {
        while (in.peek() == ' ') {
            in.forward();
        }
        if (in.peek() == '#') {
            while (NULL_OR_LINEBR.indexOf(in.peek()) == -1) {
                in.forward();
            }
        }
        if (NULL_OR_LINEBR.indexOf(in.peek()) == -1) {
            throw new IOException("While scanning a block scalar, expected a comment or line break but found: " + ch(in.peek()));
        }
        return scanLineBreak();
    }

    private Object[] scanBlockScalarIndentation() throws IOException {
        StringBuilder chunks = new StringBuilder();
        int maxIndent = 0;
        while (BLANK_OR_LINEBR.indexOf(in.peek()) != -1) {
            if (in.peek() != ' ') {
                chunks.append(scanLineBreak());
            } else {
                in.forward();
                if (in.column() > maxIndent) {
                    maxIndent = in.column();
                }
            }
        }
        return new Object[] {chunks.toString(), maxIndent};
    }

    private String scanBlockScalarBreaks(int indent) throws IOException {
        StringBuilder chunks = new StringBuilder();
        while (in.column() < indent && in.peek() == ' ') {
            in.forward();
        }
        while (FULL_LINEBR.indexOf(in.peek()) != -1) {
            chunks.append(scanLineBreak());
            while (in.column() < indent && in.peek() == ' ') {
                in.forward();
            }
        }
        return chunks.toString();
    }

    private Token scanFlowScalar(char style) throws IOException {
        boolean dbl = style == '"';
        StringBuilder chunks = new StringBuilder();
        char quote = in.peek();
        in.forward();
        chunks.append(scanFlowScalarNonSpaces(dbl));
        while (in.peek() != quote) {
            chunks.append(scanFlowScalarSpaces());
            chunks.append(scanFlowScalarNonSpaces(dbl));
        }
        in.forward();
        return Token.newScalar(chunks.toString(), style);
    }

    private String scanFlowScalarNonSpaces (boolean dbl) throws IOException {
        StringBuilder chunks = new StringBuilder();
        for (;;) {
            int length = 0;
            while (SPACES_AND_STUFF.indexOf(in.peek(length)) == -1) {
                length++;
            }
            if (length != 0) {
                chunks.append(in.prefixForward(length));
            }
            // in.forward(length);
            char ch = in.peek();
            if (!dbl && ch == '\'' && in.peek(1) == '\'') {
                chunks.append("'");
                in.forward(2);
            } else if (dbl && ch == '\'' || !dbl && DOUBLE_ESC.indexOf(ch) != -1) {
                chunks.append(ch);
                in.forward();
            } else if (dbl && ch == '\\') {
                in.forward();
                ch = in.peek();
                if (ESCAPE_REPLACEMENTS.containsKey(ch)) {
                    chunks.append(ESCAPE_REPLACEMENTS.get(ch));
                    in.forward();
                } else if (ESCAPE_CODES.containsKey(ch)) {
                    length = ESCAPE_CODES.get(ch);
                    in.forward();
                    String val = in.prefix(length);
                    if (NOT_HEXA.matcher(val).find()) {
                        throw new IOException("While scanning a double quoted scalar, expected an escape sequence of " + length + " hexadecimal numbers but found: " + ch(in.peek()));
                    }
                    chunks.append(Character.toChars(Integer.parseInt(val, 16)));
                    in.forward(length);
                } else if (FULL_LINEBR.indexOf(ch) != -1) {
                    scanLineBreak();
                    chunks.append(scanFlowScalarBreaks());
                } else {
                    throw new IOException("While scanning a double quoted scalar, found unknown escape character: " + ch(ch));
                }
            } else {
                return chunks.toString();
            }
        }
    }

    private String scanFlowScalarSpaces() throws IOException {
        StringBuilder chunks = new StringBuilder();
        int length = 0;
        while (BLANK_T.indexOf(in.peek(length)) != -1) {
            length++;
        }
        String whitespaces = in.prefixForward(length);
        // in.forward(length);
        char ch = in.peek();
        if (ch == 0) {
            throw new IOException("While scanning a quoted scalar, found unexpected end of stream.");
        } else if (FULL_LINEBR.indexOf(ch) != -1) {
            String lineBreak = scanLineBreak();
            String breaks = scanFlowScalarBreaks();
            if (!lineBreak.equals("\n")) {
                chunks.append(lineBreak);
            } else if (breaks.length() == 0) {
                chunks.append(" ");
            }
            chunks.append(breaks);
        } else {
            chunks.append(whitespaces);
        }
        return chunks.toString();
    }

    private String scanFlowScalarBreaks() throws IOException {
        StringBuilder chunks = new StringBuilder();
        String pre = null;
        for (;;) {
            pre = in.prefix(3);
            if ((pre.equals("---") || pre.equals("...")) && NULL_BL_T_LINEBR.indexOf(in.peek(3)) != -1) {
                throw new IOException("While scanning a quoted scalar, found unexpected document separator.");
            }
            while (BLANK_T.indexOf(in.peek()) != -1) {
                in.forward();
            }
            if (FULL_LINEBR.indexOf(in.peek()) != -1) {
                chunks.append(scanLineBreak());
            } else {
                return chunks.toString();
            }
        }
    }

    private Token scanPlain() throws IOException {
        /*
         * See the specification for details. We add an additional restriction for the flow context: plain scalars in the flow
         * context cannot contain ',', ':' and '?'. We also keep track of the `allow_simple_key` flag here. Indentation rules are
         * loosed for the flow context.
         */
        StringBuilder chunks = new StringBuilder();
        int ind = indent + 1;
        String spaces = "";
        boolean f_nzero = true;
        Pattern r_check = R_FLOWNONZERO;
        if (flowLevel == 0) {
            f_nzero = false;
            r_check = R_FLOWZERO;
        }
        while (in.peek() != '#') {
            int length = 0;
            int chunkSize = 32;
            Matcher m = null;
            while (!(m = r_check.matcher(in.prefix(chunkSize))).find()) {
                chunkSize += 32;
            }
            length = m.start();
            char ch = in.peek(length);
            if (f_nzero && ch == ':' && S4.indexOf(in.peek(length + 1)) == -1) {
                in.forward(length);
                throw new IOException("While scanning a plain scalar, found unexpected ':'. See: http://pyyaml.org/wiki/YAMLColonInFlowContext");
            }
            if (length == 0) {
                break;
            }
            allowSimpleKey = false;
            chunks.append(spaces);
            chunks.append(in.prefixForward(length));
            // in.forward(length);
            spaces = scanPlainSpaces();
            if (spaces.length() == 0 || flowLevel == 0 && in.column() < ind) {
                break;
            }
        }
        return Token.newScalar(chunks.toString());
    }

    private String scanPlainSpaces() throws IOException {
        StringBuilder chunks = new StringBuilder();
        int length = 0;
        // YAML recognizes two white space characters: space and tab.
        while (in.peek(length) == ' ' || in.peek(length) == '\t') {
            length++;
        }
        String whitespaces = in.prefixForward(length);
        // in.forward(length);
        char ch = in.peek();
        if (FULL_LINEBR.indexOf(ch) != -1) {
            String lineBreak = scanLineBreak();
            allowSimpleKey = true;
            if (END_OR_START.matcher(in.prefix(4)).matches()) {
                return "";
            }
            StringBuilder breaks = new StringBuilder();
            while (BLANK_OR_LINEBR.indexOf(in.peek()) != -1) {
                if (' ' == in.peek()) {
                    in.forward();
                } else {
                    breaks.append(scanLineBreak());
                    if (END_OR_START.matcher(in.prefix(4)).matches()) return "";
                }
            }
            if (!lineBreak.equals("\n")) {
                chunks.append(lineBreak);
            } else if (breaks.length() == 0) {
                chunks.append(" ");
            }
            chunks.append(breaks);
        } else {
            chunks.append(whitespaces);
        }
        return chunks.toString();
    }

    private String scanTagHandle(String name) throws IOException {
        char ch = in.peek();
        if (ch != '!') {
            throw new IOException("While scanning a " + name + ", expected '!' but found: " + ch(ch));
        }
        int length = 1;
        ch = in.peek(length);
        if (ch != ' ') {
            while (ALPHA.indexOf(ch) != -1) {
                length++;
                ch = in.peek(length);
            }
            if ('!' != ch) {
                in.forward(length);
                throw new IOException("While scanning a " + name + ", expected '!' but found: " + ch(ch));
            }
            length++;
        }
        String value = in.prefixForward(length);
        // in.forward(length);
        return value;
    }

    private String scanTagUri(String name) throws IOException {
        StringBuilder chunks = new StringBuilder();
        int length = 0;
        char ch = in.peek(length);
        while (STRANGE_CHAR.indexOf(ch) != -1) {
            if ('%' == ch) {
                chunks.append(in.prefixForward(length));
                // in.forward(length);
                length = 0;
                chunks.append(scanUriEscapes(name));
            } else {
                length++;
            }
            ch = in.peek(length);
        }
        if (length != 0) {
            chunks.append(in.prefixForward(length));
        }
        // in.forward(length);

        if (chunks.length() == 0) {
            throw new IOException("While scanning a " + name + ", expected a URI but found: " + ch(ch));
        }
        return chunks.toString();
    }

    private String scanUriEscapes(String name) throws IOException {
        StringBuilder bytes = new StringBuilder();
        while (in.peek() == '%') {
            in.forward();
            try {
                bytes.append(Character.toChars(Integer.parseInt(in.prefix(2), 16)));
            } catch (NumberFormatException nfe) {
                throw new IOException("While scanning a " + name + ", expected a URI escape sequence of 2 hexadecimal numbers but found: " + ch(in.peek(1)) + " and " + ch(in.peek(2)));
            }
            in.forward(2);
        }
        return bytes.toString();
    }

    private String scanLineBreak() throws IOException {
        // Transforms:
        // '\r\n' : '\n'
        // '\r' : '\n'
        // '\n' : '\n'
        // '\x85' : '\n'
        // default : ''
        char val = in.peek();
        if (FULL_LINEBR.indexOf(val) != -1) {
            if (RN.equals(in.prefix(2))) {
                in.forward(2);
            } else {
                in.forward();
            }
            return "\n";
        }
        return "";
    }

    private static String ch(char ch) {
        return "'" + ch + "' (" + (int)ch + ")";
    }

    //---------------------------------------------------------------------------

    private static class SimpleKey {
        private final int tokenNumber;
        private final int column;

        private SimpleKey(int tokenNumber, int column) {
            this.tokenNumber = tokenNumber;
            this.column = column;
        }
    }

    static class Token {
        final static Token DOCUMENT_START = new Token(Type.DOCUMENT_START);
        final static Token DOCUMENT_END = new Token(Type.DOCUMENT_END);
        final static Token BLOCK_MAPPING_START = new Token(Type.BLOCK_MAPPING_START);
        final static Token BLOCK_SEQUENCE_START = new Token(Type.BLOCK_SEQUENCE_START);
        final static Token BLOCK_ENTRY = new Token(Type.BLOCK_ENTRY);
        final static Token BLOCK_END = new Token(Type.BLOCK_END);
        final static Token FLOW_ENTRY = new Token(Type.FLOW_ENTRY);
        final static Token FLOW_MAPPING_END = new Token(Type.FLOW_MAPPING_END);
        final static Token FLOW_MAPPING_START = new Token(Type.FLOW_MAPPING_START);
        final static Token FLOW_SEQUENCE_END = new Token(Type.FLOW_SEQUENCE_END);
        final static Token FLOW_SEQUENCE_START = new Token(Type.FLOW_SEQUENCE_START);
        final static Token KEY = new Token(Type.KEY);
        final static Token VALUE = new Token(Type.VALUE);
        final static Token STREAM_END = new Token(Type.STREAM_END);
        final static Token STREAM_START = new Token(Type.STREAM_START);

        public final Type type;
        private final String key;
        private String value;
        private final int style;

        private Token(Type type, String key, String value, int style) {
            this.type = type;
            this.key = key;
            this.value = value;
            this.style = style;
        }

        Token(Type type) {
            this(type, null, null, 0);
        }

        static Token newDirective(String directive, String value) {
            return new Token(Type.DIRECTIVE, directive, value, 0);
        }
        static Token newTag(String handle, String suffix) {
            return new Token(Type.TAG, handle, suffix, 0);
        }
        static Token newScalar(String value) {
            return new Token(Type.SCALAR, null, value, -1);
        }
        static Token newScalar(String value, char style) {
            return new Token(Type.SCALAR, null, value, style);
        }
        void setValue(String s) {
            if (type == Type.ANCHOR || type == Type.ALIAS) {
                value = s;
            } else {
                throw new IllegalStateException();
            }
        }
        public String getKey() {
            return key;   // "handle" for tag, "directive" for directive, null otherwise
        }
        public String getValue() {
            return value;
        }
        public boolean isPlain() {
            return style < 0;
        }
        public char getStyle() {
            return style < 0 ? 0 : (char)style;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('<');
            sb.append(type);
            if (type == Type.ALIAS || type == Type.ANCHOR || type == Type.DIRECTIVE) {
                sb.append(" name=\"" + value + "\"");
            } else if (type == Type.DIRECTIVE) {
                sb.append(" directive=\"" + key + "\" value=\"" + value + "\"");
            } else if (type == Type.TAG) {
                sb.append(" handle=\"" + key + "\" suffix=\"" + value + "\"");
            } else if (type == Type.SCALAR && style < 0) {
                sb.append(" value=\"" + value + "\"");
            } else if (type == Type.SCALAR) {
                sb.append(" value=\"" + value + "\" style=\"" + ((char)style) + "\"");
            }
            sb.append('>');
            return sb.toString();
        }
    }

    enum Type {
        DOCUMENT_START, //
        DOCUMENT_END, //
        BLOCK_MAPPING_START, //
        BLOCK_SEQUENCE_START, //
        BLOCK_ENTRY, //
        BLOCK_END, //
        FLOW_ENTRY, //
        FLOW_MAPPING_END, //
        FLOW_MAPPING_START, //
        FLOW_SEQUENCE_END, //
        FLOW_SEQUENCE_START, //
        KEY, //
        VALUE, //
        STREAM_END, //
        STREAM_START, //
        ALIAS, //
        ANCHOR, //
        DIRECTIVE, //
        SCALAR, //
        TAG;
    }

    //--------------------------------------------------------------------------
    // JSON API bit below here - converting YamlBean tokens to BFO Json events
    //--------------------------------------------------------------------------

    private static final int PRESTART = 0, ROOT = 1, LIST=2, MAPKEY=3, MAPVAL=4, EOF=5;
    private CharSource source;
    private Deque<JsonStream.Event> eq = new ArrayDeque<JsonStream.Event>();
    private int jstate = PRESTART;
    private int[] jstack = new int[64];
    private int jstacklen;

    @Override public JsonStream.Event next() throws IOException {
        if (eq.isEmpty()) {
            hasNext();
        }
        return eq.removeFirst();
    }

    private void enqueue(JsonStream.Event event) {
        eq.add(event);
    }

    private String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int v;
        for (int i=0;i<jstacklen;i++) {
            v = jstack[i];
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(v==PRESTART?"prestart":v==ROOT?"root":v==LIST?"list":v==MAPKEY?"mapkey":v==MAPVAL?"mapval":"root");
        }
        sb.append("] ");
        v = jstate;
        sb.append(v==PRESTART?"prestart":v==ROOT?"root":v==LIST?"list":v==MAPKEY?"mapkey":v==MAPVAL?"mapval":"root");
        return sb.toString();
    }

    @Override public boolean hasNext() throws IOException {
        if (!eq.isEmpty()) {
            return true;
        }
        if (!in.pull()) {
            return false;
        }
        while (eq.isEmpty() && jstate != EOF && peekNextToken() != null) {
            Token t = getNextToken();
//            System.out.println("    T="+t+" "+dump());
            switch (t.type) {
                case ANCHOR:
                    throw new UnsupportedOperationException("Yaml anchors are not supported");
                case TAG:
                    throw new UnsupportedOperationException("Yaml tags are not supported");
                case BLOCK_MAPPING_START:
                case FLOW_MAPPING_START:
                    jstack[jstacklen++] = jstate == MAPVAL ? MAPKEY : jstate == PRESTART ? ROOT : jstate;
                    jstate = MAPKEY;
                    enqueue(JsonStream.Event.startMap(-1));
                    break;
                case BLOCK_SEQUENCE_START:
                case FLOW_SEQUENCE_START:
                    jstack[jstacklen++] = jstate == MAPVAL ? MAPKEY : jstate == PRESTART ? ROOT : jstate;
                    jstate = LIST;
                    enqueue(JsonStream.Event.startList(-1));
                    break;
                case FLOW_MAPPING_END:
                    jstate = jstack[--jstacklen];
                    enqueue(JsonStream.Event.endMap());
                    break;
                case FLOW_SEQUENCE_END:
                    jstate = jstack[--jstacklen];
                    enqueue(JsonStream.Event.endList());
                    break;
                case BLOCK_ENTRY:
                    // Means the start of a list item.
                    if (jstate != LIST) {
                        jstack[jstacklen++] = jstate == MAPVAL ? MAPKEY : jstate;
                        jstate = LIST;
                        enqueue(JsonStream.Event.startList(-1));
                    }
                    break;
                case BLOCK_END:
                    if (jstate == MAPKEY) {
                        enqueue(JsonStream.Event.endMap());
                        jstate = jstack[--jstacklen];
                    } else if (jstate == LIST) {
                        enqueue(JsonStream.Event.endList());
                        jstate = jstack[--jstacklen];
                    } else if (jstate == MAPVAL) {
                        enqueue(JsonStream.Event.nullValue());
                        jstate = MAPKEY;
                    }
                    break;
                case KEY:
                    if (jstate == LIST) {
                        enqueue(JsonStream.Event.endList());
                        jstate = jstack[--jstacklen];
                    }
                    break;
                case SCALAR:
                    String v = t.getValue();
                    if (t.isPlain() && v.length() > 0) {
                        if ("true".equals(v)) {
                            enqueue(JsonStream.Event.booleanValue(true));
                        } else if ("false".equals(v)) {
                            enqueue(JsonStream.Event.booleanValue(false));
                        } else if ("null".equals(v)) {
                            enqueue(JsonStream.Event.booleanValue(false));
                        } else if ("null".equals(v)) {
                            enqueue(JsonStream.Event.nullValue());
                        } else if (".inf".equals(v)) {
                            enqueue(JsonStream.Event.numberValue(Float.POSITIVE_INFINITY));
                        } else if (Character.isDigit(v.charAt(0)) || v.charAt(0) == '-') {
                            if (v.startsWith("0o")) {
                                enqueue(JsonStream.Event.numberValue(Integer.parseInt(v.substring(2), 8)));
                            } else if (v.startsWith("0x")) {
                                enqueue(JsonStream.Event.numberValue(Integer.parseInt(v.substring(2), 16)));
                            } else if (v.indexOf(".") >= 0 || v.indexOf("e") >= 0) {
                                try {
                                    Number n = Double.parseDouble(v);
                                    if (!v.equals(n.toString())) {      // Poor-mans precision check
                                        n = Double.parseDouble(v);
                                    }
                                    enqueue(JsonStream.Event.numberValue(n));
                                } catch (Exception e) {
                                    enqueue(JsonStream.Event.stringValue(v, -1));
                                }
                            } else {
                                try {
                                    long l = Long.parseLong(v);
                                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                                        enqueue(JsonStream.Event.numberValue((int)l));
                                    } else {
                                        enqueue(JsonStream.Event.numberValue(l));
                                    }
                                } catch (Exception e) {
                                    enqueue(JsonStream.Event.stringValue(v, -1));
                                }
                            }
                        } else {
                            enqueue(JsonStream.Event.stringValue(v, -1));
                        }
                    } else {
                        enqueue(JsonStream.Event.stringValue(v, -1));
                    }
                    if (jstate == MAPKEY) {
                        jstate = MAPVAL;
                    } else if (jstate == MAPVAL) {
                        jstate = MAPKEY;
                    } else if (jstate != LIST) {
                        jstate = ROOT;
                    }
                    break;
                case DOCUMENT_START:
                    if (jstate != PRESTART) {
                        jstate = EOF;
                    }
                    break;
                case STREAM_END:
                    if (jstate == MAPVAL) {
                        enqueue(JsonStream.Event.nullValue());
                        jstate = MAPKEY;
                    }
                    do {
                        if (jstate == LIST) {
                            enqueue(JsonStream.Event.endList());
                        } else if (jstate == MAPKEY) {
                            enqueue(JsonStream.Event.endMap());
                        }
                        if (jstacklen > 0) {
                            jstate = jstack[--jstacklen];
                        }
                    } while (jstacklen > 0);
                    break;
            }
        }
        // System.out.println("Q="+eq);
        return !eq.isEmpty();
    }

    @Override public YamlReader setInput(Readable in) {
        return (YamlReader)super.setInput(in);
    }
    @Override public YamlReader setInput(CharSequence in) {
        return (YamlReader)super.setInput(in);
    }
    @Override public YamlReader setInput(ReadableByteChannel in) {
        return (YamlReader)super.setInput(in);
    }
    @Override public YamlReader setInput(InputStream in) {
        return (YamlReader)super.setInput(in);
    }
    @Override public YamlReader setInput(ByteBuffer in) {
        return (YamlReader)super.setInput(in);
    }
    @Override public YamlReader setFinal() {
        return (YamlReader)super.setFinal();
    }
    @Override public YamlReader setPartial() {
        throw new IllegalStateException("Not possible with Yaml");
    }
    @Override public YamlReader setDraining() {
        return (YamlReader)super.setDraining();
    }
    @Override public YamlReader setNonDraining() {
        throw new IllegalStateException("Not possible with Yaml");
    }
    @Override void setSource(CharSource source) {
        in.setSource(source);
    }

}
