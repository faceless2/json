package com.bfo.json;

import java.util.*;
import java.text.*;

/**
 * <p>
 * This class determines how the Json written with {@link Json#write Json.write()} is formatted.
 * </p><p>
 * The class uses a "fluent" style to enable setting multiple options at once, eg.
 * </p>
 * <pre>
 *  json.write(out, new JsonWriteOptions().setPretty(true).setSorted(true));
 * </pre>
 * <p>
 * All boolean options are false by default.
 * </p>
 */
public class JsonWriteOptions {

    private String floatformat = "%.8g", doubleformat = "%.16g";
    private boolean pretty, allownan, sorted, nfc;
    private Filter filter;
    private int maxArraySize = 0, maxStringLength = 0;

    /**
     * The String format to use when formatting a float.
     * The default is "%.8g"
     * Note that superfluous trailing zeros will trimmed from any formatted value.
     * @param format the format, which will be passed to {@link DecimalFormat}
     * @return this
     */
    public JsonWriteOptions setFloatFormat(String format) {
        this.floatformat = format;
        return this;
    }

    /**
     * The String format to use when formatting a double.
     * The default is "%.16g". 
     * Note that superfluous trailing zeros will trimmed from any formatted value.
     * @param format the format, which will be passed to {@link DecimalFormat}
     * @return this
     */
    public JsonWriteOptions setDoubleFormat(String format) {
        this.doubleformat = format;
        return this;
    }

    /**
     * Whether to allow NaN and Infinite values in the output.
     * Both NaN and infinite values are disallowed in RFC8259.
     * With this flag set, Infinite or NaN values are serialized
     * as null, which matches web-browser behaviour. With this flag not set,
     * an IOException is thrown during serialization
     * @param nan the flag
     * @return this
     */
    public JsonWriteOptions setAllowNaN(boolean nan) {
        this.allownan = nan;
        return this;
    }

    /**
     * Whether to pretty-print the Json, using newlines and
     * indentation.
     * @param pretty the flag
     * @return this
     */
    public JsonWriteOptions setPretty(boolean pretty) {
        this.pretty = pretty;
        return this;
    }

    /**
     * Whether to sort the keys in each map alphabetically
     * when writing. This is recommended, but not required
     * for CBOR serialization
     * @param sorted the flag
     * @return this
     */
    public JsonWriteOptions setSorted(boolean sorted) {
        this.sorted = sorted;
        return this;
    }

    /**
     * Set whether to normalize all Strings (including map keys) to Unicode {@link java.text.Normalizer.Form#NFC normal form C}
     * when writing. Note that collapsing Map keys down to NFC could theoretically result in two keys of the
     * same value being written out. This is not tested for during writing.
     * @param nfc the flag
     * @return this
     */
    public JsonWriteOptions setNFC(boolean nfc) {
        this.nfc = nfc;
        return this;
    }

    /**
     * Set the maximum number of items to print in an array. Additional entries will
     * be replaced with an ellipsis; the result will not be valid Json, but is useful
     * for use in toString()
     * @param size the maximum number of items to print, or 0 for no limit (the default)
     * @return this
     */
    public JsonWriteOptions setMaxArraySize(int size) {
        this.maxArraySize = size;
        return this;
    }

    /**
     * Set the maximum length of a string. Additional characters will be replaced with
     * an ellipsis
     * @param size the maximum length of a string to print, or 0 for no limit (the default)
     * @return this
     */
    public JsonWriteOptions setMaxStringLength(int size) {
        this.maxStringLength = size;
        return this;
    }


    /**
     * Return the "nfc" flag as set by {@link #setNFC}
     * @return the flag
     */
    public boolean isNFC() {
        return nfc;
    }

    /**
     * Return the "pretty" flag as set by {@link #setPretty}
     * @return the flag
     */
    public boolean isPretty() {
        return pretty;
    }

    /**
     * Set a Filter on the writer, which can be used
     * to restrict which nodes are written
     * @param filter the Filter to apply, or null for no filtering
     * @return this
     */
    public JsonWriteOptions setFilter(Filter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Return the Filter as set by {@link #setFilter}
     * @return the filter
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Return the float format as set by {@link #setFloatFormat}
     * @return the format
     */
    public String getFloatFormat() {
        return floatformat;
    }

    /**
     * Return the float format as set by {@link #setDoubleFormat}
     * @return the format
     */
    public String getDoubleFormat() {
        return doubleformat;
    }

    /**
     * Return the "allowNaN" flag as set by {@link #setAllowNaN}
     * @return the flag
     */
    public boolean isAllowNaN() {
        return allownan;
    }

    /**
     * Return the "sorted" flag as set by {@link #setSorted}
     * @return the flag
     */
    public boolean isSorted() {
        return sorted;
    }

    /**
     * Return the "maxArraySize" field as set by {@link #setMaxArraySize}
     * @return the value
     */
    public int getMaxArraySize() {
        return maxArraySize;
    }

    /**
     * Return the "maxStringLength" field as set by {@link #setMaxStringLength}
     * @return the value
     */
    public int getMaxStringLength() {
        return maxStringLength;
    }

    /**
     * A Filter which can be used to restrict which nodes are written
     */
    public interface Filter {
        /**
         * Called once when the Json writing begins
         * @param context the root node of the writing process
         */
        public void initialize(Json context);

        /**
         * Called before printing each entry in a Map, this method can control
         * which entries from the map are printed.
         * @param key the key of the current entry in the map
         * @param child the value of the current entry in the map
         * @return child if the child is to be printed, null if its not.
         */
        public Json enter(String key, Json child);

        /**
         * Called after printing each entry in a Map.
         * @param key the key of the current entry in the map
         * @param child the value of the current entry in the map
         */
        public void exit(String key, Json child);
    }

    /**
     * Return a simple Filter that will only print nodes that are
     * either in, or not in the specified set
     * @param set the set of Json objects
     * @param include if true, only print nodes in the set. If false, only print nodes not in the set.
     * @return a new Filter instance
     */
    public static Filter createNodeSetFilter(final Collection<Json> set, final boolean include) {
        return new Filter() {
            int depth, okdepth;

            public void initialize(Json context) {
                depth = 0;
                okdepth = Integer.MAX_VALUE;
            }

            public Json enter(String key, Json child) {
                depth++;
                if (depth > okdepth) {
                    return child;
                } else if (set.contains(child) == include) {
                    okdepth = depth;
                    return child;
                } else {
                    return null;
                }
            }

            public void exit(String key, Json child) {
                if (depth-- == okdepth) {
                    okdepth = Integer.MAX_VALUE;
                }
            }
        };
    }

}
