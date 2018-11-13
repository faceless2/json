package com.bfo.json; 
import java.util.*;

/**
 * <p>
 * This class determines how the JSON read with {@link Json#read} is handled.
 * The various flags set on this class can control the read process, and in
 * some cases (eg {@link #setStrictTypes}) control how the objects that are
 * read behave. <b>All flags default to false.</b>
 * </p><p>
 * The class uses a "fluent" style to enable setting multiple options at once, eg.
 * </p>
 * <pre>
 *  json.read(out, new JsonReadOptions().setAllowUnquotedKey(true).setAllowComments(true));
 * </pre>
 */
public class JsonReadOptions {

    private boolean nfc;
    private boolean unquotedKey;
    private boolean trailingComma;
    private boolean comments;
    private boolean bigDecimal;
    private byte storeOptions;
    static final byte FLAG_STRICT = 1;
    static final byte FLAG_LOOSEEMPTY = 2;

    /**
     * Whether to allow unquotedKey keys in the JSON input. This
     * is not officially allowed, but still common. The default is false.
     * @param unquotedKey the flag
     * @return this
     */
    public JsonReadOptions setAllowUnquotedKey(boolean unquotedKey) {
        this.unquotedKey = unquotedKey;
        return this;
    }

    /**
     * Return the value of the "unquotedKey" flag as set by {@link #setAllowUnquotedKey}
     * @return the flag
     */
    public boolean isAllowUnquotedKey() {
        return unquotedKey;
    }

    /**
     * Whether to allow trailing commas in the JSON input, eg <code>["a",]</code>. This
     * is not officially allowed, but still common.
     * @param trailingComma the flag
     * @return this
     */
    public JsonReadOptions setAllowTrailingComma(boolean trailingComma) {
        this.trailingComma = trailingComma;
        return this;
    }

    /**
     * Return the value of the "trailingComma" flag as set by {@link #setAllowTrailingComma}
     * @return the flag
     */
    public boolean isAllowTrailingComma() {
        return trailingComma;
    }

    /**
     * Whether to allow comments in the JSON input, eg <code>["a"]/&#x2a;comment&#x2a;/</code>. This
     * is not officially allowed, but still common.
     * @param comments the flag
     * @return this
     */
    public JsonReadOptions setAllowComments(boolean comments) {
        this.comments = comments;
        return this;
    }

    /**
     * Return the value of the "comments" flag as set by {@link #setAllowComments}
     * @return the flag
     */
    public boolean isAllowComments() {
        return comments;
    }

    /**
     * Set whether to normalize all Strings (including map keys) to Unicode {@link java.text.Normalizer.Form#NFC normal form C}
     * @param nfc the flag
     * @return this
     */
    public JsonReadOptions setNFC(boolean nfc) {
        this.nfc = nfc;
        return this;
    }

    /**
     * Return the value of the "nfc" flag as set by {@link #setNFC}
     * @return the flag
     */
    public boolean isNFC() {
        return nfc;
    }

    /**
     * Set whether real numbers that cannot be guaranteed to be exactly the same when stored as
     * a {@link java.lang.Double} should be stored as a {@link java.math.BigDecimal}.
     * The default is false, which means every real number read by the JsonReader will
     * be converted to a Double.
     * @param bigDecimal the flag
     * @return this
     */
    public JsonReadOptions setBigDecimal(boolean bigDecimal) {
        this.bigDecimal = bigDecimal;
        return this;
    }

    /**
     * Return the value of the "bigDecimal" flag as set by {@link #setBigDecimal}
     * @return the flag
     */
    public boolean isBigDecimal() {
        return bigDecimal;
    }

    /**
     * Determines whether objects read by the JsonReader will be loosely typed. With this
     * flag set, calling {@link Json#intValue} on the string "123" will result in a
     * {@link ClassCastException} being thrown rather than a silent conversion to integer.
     * @param strictTypes the flag
     * @return this
     */
    public JsonReadOptions setStrictTypes(boolean strictTypes) {
        if (strictTypes) {
            storeOptions |= FLAG_STRICT;
        } else {
            storeOptions &= ~FLAG_STRICT;
        }
        return this;
    }

    /**
     * Return the value of the "strictTypes" flag as set by {@link #setStrictTypes}
     * @return the flag
     */
    public boolean isStrictTypes() {
        return (storeOptions & FLAG_STRICT) != 0;
    }

    /**
     * When the {@link #setStrictTypes strictTypes} flag is not set, this flag will determine
     * whether empty strings evaluate to zero when treated as a number and false when treated
     * as a boolean.
     * @param looseEmptyStrings the flag
     * @return this
     */
    public JsonReadOptions setLooseEmptyStrings(boolean looseEmptyStrings) {
        if (looseEmptyStrings) {
            storeOptions |= FLAG_LOOSEEMPTY;
        } else {
            storeOptions &= ~FLAG_LOOSEEMPTY;
        }
        return this;
    }

    /**
     * Return the value of the "looseEmptyString" flag as set by {@link #setLooseEmptyStrings}
     * @return the flag
     */
    public boolean isLooseEmptyStrings() {
        return (storeOptions & FLAG_LOOSEEMPTY) != 0;
    }

    byte storeOptions() {
        return storeOptions;
    }

}
