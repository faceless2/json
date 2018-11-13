package com.bfo.json;

import java.io.*;
import java.util.*;

class INumber extends Core {

    private final Number value;
    private final byte flags;

    INumber(Number value, byte flags) {
        this.value = value;
        this.flags = flags;
        if (value instanceof Float) {
            Float n = (Float)value;
            if ((n.isNaN() || n.isInfinite())) {
                throw new IllegalArgumentException("Infinite or NaN");

            }
        } else if (value instanceof Double) {
            Double n = (Double)value;
            if ((n.isNaN() || n.isInfinite())) {
                throw new IllegalArgumentException("Infinite or NaN");
            }
        }
    }

    @Override Object value() {
        return value;
    }

    @Override String type() {
        return "number";
    }

    @Override boolean booleanValue() {
        if ((flags & JsonReadOptions.FLAG_STRICT) != 0) {
            throw new ClassCastException("Cannot convert number "+value+" to boolean in strict mode");
        }
        return value.intValue() != 0;
    }

    @Override Number numberValue() {
        return value;
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        StringBuilder tsb = new StringBuilder();
        if (value instanceof Float) {
            Float n = (Float)value;
            if ((n.isNaN() || n.isInfinite()) && !state.options.isAllowNaN()) {
                throw new IllegalArgumentException("Infinite or NaN");
            }
            new Formatter(tsb, Locale.ENGLISH).format(state.options.floatFormat(), n);
        } else if (value instanceof Double) {
            Double n = (Double)value;
            if ((n.isNaN() || n.isInfinite()) && !state.options.isAllowNaN()) {
                throw new IllegalArgumentException("Infinite or NaN");
            }
            new Formatter(tsb, Locale.ENGLISH).format(state.options.doubleFormat(), n);
        } else {
            sb.append(value.toString());
            return;
        }
        int l = tsb.length() - 1;
        while (tsb.charAt(l) == '0') {
            l--;
        }
        if (tsb.charAt(l) == '.') {
            l--;
            sb.append(tsb, 0, l + 1);
        } else {
            sb.append(value.toString());
        }
        return;
    }

    /**
     * Hooray for this! Input is any sequence that begins with a digit or a '.' and includes
     * only the characters 0..9a..fA..Fxe-.
     * @return a Number or BigInteger/BigDecimal, or null
    static Object parseNumericLiteral(CharSequence s) {
        state transitions
        0  = initial            initialzero | decimalpoint | integer19
        1  = initialzero         x | decimalpoint | eof
        2  = decimalpoint       fraction09 | e | eof
        3  = fraction09         fraction09 | e | eof
        4  = integer19          integer09 | decimalpoint | e | eof
        5  = integer09          integer09 | decimalpoint | e | eof
        6  = e                  exp09 | expsign 
        7  = exp09              exp09 | eof
        8  = expsign            exp09
        9  = x                  hexdigit
        a  = hexdigit           hexdigit eof
        b  = eof

        int state = 0;
        boolean floating = false;
        boolean hex = false;
        for (int i=0;i<s.length() && state >= 0;i++) {
//            char c = s.charAt(i);
            System.out.print("# i="+i+" c="+c+" state="+state);
            if (state == 0) {
                if (c == '0') state = 1;
                else if (c == '.') { state = 2; floating = true; }
                else if (c >= '1' && c <= '9') state = 4;
                else state = -1;
            } else if (state == 1) {
                if (c == 'x' || c == 'X') state = 9;
                else if (c == '.') { state = 2; floating = true; }
                else state = -1;
            } else if (state == 2) {
                if (c == 'e' || c == 'E') state = 6;
                else if (c >= '0' && c <= '9') state = 3;
                else state = -1;
            } else if (state == 3) {
                if (c == 'e' || c == 'E') state = 6;
                else if (c >= '0' && c <= '9') state = 3;
                else state = -1;
            } else if (state == 4) {
                if (c == 'e' || c == 'E') state = 6;
                else if (c >= '0' && c <= '9') state = 5;
                else if (c == '.') { state = 2; floating = true; }
                else state = -1;
            } else if (state == 5) {
                if (c == 'e' || c == 'E') state = 6;
                else if (c >= '0' && c <= '9') state = 5;
                else if (c == '.') { state = 2; floating = true; }
                else state = -1;
            } else if (state == 6) {
                floating = true;
                if (c == '+' || c == '-') state = 8;
                else if (c >= '0' && c <= '9') state = 7;
                else state = -1;
            } else if (state == 7) {
                if (c >= '0' && c <= '9') state = 7;
                else state = -1;
            } else if (state == 8) {
                if (c >= '0' && c <= '9') state = 7;
                else state = -1;
            } else if (state == 9) {
                hex = true;
                if ((c >= '0' && c <= '9') || (c >='a' && c <= 'f') || (c >= 'A' && c <= 'F')) state = 10;
                else state = -1;
            } else if (state == 10) {
                if ((c >= '0' && c <= '9') || (c >='a' && c <= 'f') || (c >= 'A' && c <= 'F')) state = 10;
                else state = -1;
            }
//            System.out.println("->"+state);
        }
        if (state == -1 || state == 9 || state == 8 || state == 6) {
            return null;
        }
        if (hex) {
            if (s.length() <= 6) {
                try { return Integer.valueOf(s.toString().substring(2), 16); } catch (NumberFormatException e) {}
            }
            if (s.length() <= 10) {
                try { return Long.valueOf(s.toString().substring(2), 16); } catch (NumberFormatException e) {}
            }
        }
        if (floating) {
            if (s.length() < 8) {       // arbitrary
                try { return Float.valueOf(s.toString()); } catch (NumberFormatException e) {}
            }
            if (s.length() < 17) {
                try { return Double.valueOf(s.toString()); } catch (NumberFormatException e) {}
            }
            return new BigDecimal(s.toString());
        } else {
            if (s.length() < 11) {
                try { return Integer.valueOf(s.toString()); } catch (NumberFormatException e) {}
            }
            if (s.length() < 21) {
                try { return Long.valueOf(s.toString()); } catch (NumberFormatException e) {}
            }
            return new BigInteger(s.toString());
        }
    }

    public static void main(String[] args) {
        for (int i=0;i<args.length;i++) {
            Object o = parseNumber(args[i]);
            System.out.println(args[i]+" = "+(o==null?null:o.getClass().getName())+" = "+o);
        }
    }
     */

}
