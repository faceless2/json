package com.bfo.json;

import javax.json.*;
import java.math.*;

class JSRJsonNumber implements JsonNumber {
    private final Number value;
    private BigDecimal bd;

    JSRJsonNumber(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("Number is null");
        }
        this.value = value;
    }

    @Override public int hashCode() {
        return bigDecimalValue().hashCode();
    }
    @Override public boolean equals(Object o) {
        if (o instanceof JSRJsonNumber) {
            if (((JSRJsonNumber)o).value.equals(value))  {
                return true;
            }
        }
        return o instanceof JsonNumber ? ((JsonNumber)o).bigDecimalValue().equals(bigDecimalValue()) : false;
    }
    @Override public JsonValue.ValueType getValueType() {
        return JsonValue.ValueType.NUMBER;
    }
    @Override public synchronized BigDecimal bigDecimalValue() {
        if (bd == null) {
            this.bd = value instanceof BigDecimal ? (BigDecimal)value : value instanceof BigInteger ? new BigDecimal((BigInteger)value) : value instanceof Float || value instanceof Double ? BigDecimal.valueOf(value.doubleValue()) : BigDecimal.valueOf(value.longValue());
        }
        return bd;
    }
    @Override public BigInteger bigIntegerValue() {
        return value instanceof BigInteger ? (BigInteger)value : BigInteger.valueOf(value.longValue());
    }
    @Override public BigInteger bigIntegerValueExact() {
        return value instanceof BigInteger ? (BigInteger)value : value instanceof BigDecimal ? ((BigDecimal)value).toBigIntegerExact() : value instanceof Float || value instanceof Double ? BigDecimal.valueOf(value.doubleValue()).toBigIntegerExact() : BigInteger.valueOf(value.longValue());
    }
    @Override public double doubleValue() {
        return value.doubleValue();
    }
    @Override public int intValue() {
        return value.intValue();
    }
    @Override public int intValueExact() {
        if (value instanceof BigInteger) {
            return ((BigInteger)value).intValueExact();
        } else if (value instanceof BigDecimal) {
            return ((BigDecimal)value).intValueExact();
        } else if (value.doubleValue() == value.intValue()) {
            return value.intValue();
        } else {
            throw new ArithmeticException();
        }
    }
    @Override public boolean isIntegral() {
        boolean integral = value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte || value instanceof BigInteger || (value instanceof BigDecimal && bigDecimalValue().scale() <= 0);
        return integral;
    }
    @Override public long longValue() {
        return value.longValue();
    }
    @Override public long longValueExact() {
        if (value instanceof BigInteger) {
            return ((BigInteger)value).longValueExact();
        } else if (value instanceof BigDecimal) {
            return ((BigDecimal)value).longValueExact();
        } else if (value.doubleValue() == value.longValue()) {
            return value.longValue();
        } else {
            throw new ArithmeticException();
        }
    }
    @Override public Number numberValue() {
        return value;
    }
    @Override public String toString() {
        return value.toString();
    }
}
