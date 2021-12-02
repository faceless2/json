package com.bfo.json;

import java.io.*;
import java.util.*;
import java.math.*;

class INumber extends Core {

    private final Number value;
    private final byte flags;

    INumber(Number value, JsonReadOptions options) {
        this.value = value;
        this.flags = options == null ? 0 : options.storeOptions();
    }

    @Override Object value() {
        return value;
    }

    @Override String type() {
        return "number";
    }

    @Override boolean booleanValue(Json json) {
        if ((flags & JsonReadOptions.FLAG_STRICT) != 0) {
            throw new ClassCastException("Cannot convert number " + value + " to boolean in strict mode");
        }
        return value.intValue() != 0;
    }

    @Override Number numberValue(Json json) {
        return value;
    }

    @Override public boolean equals(Object o) {
        if (o instanceof INumber) {
            Number n = ((INumber)o).value;
            if (n.getClass() == value.getClass()) {
                return n.equals(value);
            }
            // Ensure we only care about the numeric value, not the storage type
            BigDecimal b1 = n instanceof BigDecimal ? (BigDecimal)n : n instanceof Float || n instanceof Double ? BigDecimal.valueOf(n.doubleValue()) : n instanceof BigInteger ? new BigDecimal((BigInteger)n) : BigDecimal.valueOf(n.longValue());
            BigDecimal b2 = value instanceof BigDecimal ? (BigDecimal)value : value instanceof Float || value instanceof Double ? BigDecimal.valueOf(value.doubleValue()) : value instanceof BigInteger ? new BigDecimal((BigInteger)value) : BigDecimal.valueOf(value.longValue());
            return b1.compareTo(b2) == 0;
        }
        return false;
    }

    @Override void write(Json json, Appendable sb, SerializerState state) throws IOException {
        StringBuilder tsb = new StringBuilder();
        if (value instanceof Float) {
            Float n = (Float)value;
            if (n.isNaN() || n.isInfinite()) {
                if (state.options.isAllowNaN()) {
                    sb.append("null");
                    return;
                } else {
                    throw new IllegalArgumentException("Infinite or NaN");
                }
            } else {
                new Formatter(tsb, Locale.ENGLISH).format(state.options.getFloatFormat(), n);
            }
        } else if (value instanceof Double) {
            Double n = (Double)value;
            if (n.isNaN() || n.isInfinite()) {
                if (state.options.isAllowNaN()) {
                    sb.append("null");
                    return;
                } else {
                    throw new IllegalArgumentException("Infinite or NaN");
                }
            } else {
                new Formatter(tsb, Locale.ENGLISH).format(state.options.getDoubleFormat(), n);
            }
        } else {
            sb.append(value.toString());
            return;
        }

        // Trim superfluous zeros after decimal point
        int l = tsb.length();
        for (int i=Math.max(0, l-6);i<l;i++) {
            char c = tsb.charAt(i);
            if (c == 'e' || c == 'E') {
                l = i;
                break;
            }
        }
        for (int i=0;i<l;i++) {
            if (tsb.charAt(i) == '.') {
                int j = l - 1;
                while (tsb.charAt(j) == '0') {
                    j--;
                }
                if (j == i) {
                    j--;
                }
//                System.out.println("FLOAT was "+tsb+"  now "+tsb.substring(0, j + 1) + tsb.substring(l, tsb.length()));
                sb.append(tsb, 0, j + 1);
                if (l != tsb.length()) {
                    sb.append(tsb, l, tsb.length());
                }
                return;
            }
        }
        sb.append(tsb);
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
