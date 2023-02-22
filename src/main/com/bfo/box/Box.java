package com.bfo.box;

import com.bfo.json.*;
import java.util.*;
import java.io.*;
import java.math.BigInteger;

/**
 * <p>
 * A general class for ISO base media boxes, eg M4V, M4A, quicktime, as defined in ISO14496,
 * and also JUMBox as defined in ISO19566.
 * This is the format of many modern media types (video/mp4, audio/aac, image/jp2) and is
 * also used for <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html">C2PA</a>.
 * </p>
 * <p>Each box is encoded like so:</p>
 * <ul>
 *  <li><code>bytes 0-3</code> - length of this box, including the length field and type, 0 for "to the end of the stream", or 1 for "extended length".
 *  <li><code>bytes 4-7</code> - four-byte tag, interpreted as a 4-character ASCII string</li>
 *  <li><code>bytes 7-15 (optional)</code> - if length == 1, this 8 bytes is the "extended length". These are supported for reading but not writing by this API</li>
 *  <li><code>remaining</code> - ths box data, which may be a list of other boxes (for "container" boxes), or other types of data
 * </ul>
 * <p>
 * Most types of boxes are loaded but not stored; you can traverse the tree, but attempting to write them out
 * again will fail (this is intentional; we don't want to store a multi-GB movie in memory when all you want is the metadata).
 * Some particular types of boxes can also be created from scratch; currently this only applies to C2PA related boxes.
 * Some simple examples.
 * </p>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * Box box = Box.load(inputstream, null);
 * for (Box child=box.first();box!=null;box=box.next()) {
 *   System.out.println(box.type());         // A 4-character string
 *   System.out.println(box.isContainer());  // if true, box.first() may be non-null
 *   assert box.parent() == box;
 *   if (box instanceof XMPBox) {
 *     byte[] xmpdata = ((XMPBox)box).data(); // box subclasses are more interesting
 *   }
 *   byte[] data = box.getEncoded(); // if box and all its descendents are stored, write as a byte array.
 * }
 * </pre>
 * @see C2PAStore
 * @since 5
 */
public class Box {

    private static final boolean debug = false; // true to load boxes into memory and check save==load

    private int type;
    private long len;   // store because not every Box is going to get parsed on read
    private Box parent, first, next;
    private byte[] pad; // not convinced this is necessary

    private static final Map<String,Class<? extends Box>> registry = new HashMap<String,Class<? extends Box>>();
    private static final Set<String> containers = new HashSet<String>();
    static {
        String[] s = new String[] {
            // origin of this list unsure; mostly ISO14496 but there will be others
            "moov", "trak", "edts", "mdia", "minf", "dinf", "stbl", "mp4a",
            "mvex", "moof", "traf", "mfra", "udta", "ipro", "sinf", /*"meta",*/
            "ilst", "----", "?alb", "?art", "aART", "?cmt", "?day", "?nam",
            "?gen", "gnre", "trkn", "disk", "?wrt", "?too", "tmpo", "cprt",
            "cpil", "covr", "rtng", "?grp", "stik", "pcst", "catg", "keyw",
            "purl", "egid", "desc", "?lyr", "tvnn", "tvsh", "tven", "tvsn",
            "tves", "purd", "pgap",
            "jumb"  // from ISO19566
        };
        for (int i=0;i<s.length;i++) {
            containers.add(s[i]);
        }
    }

    /*
    private static Map<String,Field> tag2field = new HashMap<String,Field>();
    private static {
        tag2field.put("moov.udta.meta.ilst.\u00A9alb", Field.Album);
        tag2field.put("moov.udta.meta.ilst.aART", Field.AlbumArtist);
        tag2field.put("moov.udta.meta.ilst.\u00A9ART", Field.AlbumArtist);
        tag2field.put("moov.udta.meta.ilst.\u00A9cmt", Field.Comment);
        tag2field.put("moov.udta.meta.ilst.\u00A9day", Field.Year);
        tag2field.put("moov.udta.meta.ilst.\u00A9nam", Field.Title);
        tag2field.put("moov.udta.meta.ilst.\u00A9wrt", Field.Composer);
        tag2field.put("moov.udta.meta.ilst.\u00A9too", Field.Encoder);
        tag2field.put("moov.udta.meta.ilst.cprt", Field.Copyright);
        tag2field.put("moov.udta.meta.ilst.\u00A9grp", Field.Group);
        tag2field.put("moov.udta.meta.ilst.\u00A9gen", Field.Genre);
        tag2field.put("moov.udta.meta.ilst.gnre", Field.Genre);
        tag2field.put("moov.udta.meta.ilst.trkn", Field.Track);
        tag2field.put("moov.udta.meta.ilst.disk", Field.Disc);
        tag2field.put("moov.udta.meta.ilst.tmpo", Field.BPM);
        tag2field.put("moov.udta.meta.ilst.cpil", Field.Compilation);
        tag2field.put("moov.udta.meta.ilst.covr", Field.ImageOffset);
        tag2field.put("moov.udta.meta.ilst.pgap", Field.Gapless);
        tag2field.put("moov.udta.meta.ilst.com.apple.iTunes.iTunNORM", Field.Gain);
        tag2field.put("moov.udta.meta.ilst.com.apple.iTunes.iTunes_CDDB_1", Field.CDDB);
    }
    */

    /**
     * @hidden
     */
    protected static long readLong(InputStream in) throws IOException {
        long v = 0;
        for (int i=0;i<8;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            v = (v<<8) | q;
        }
        return v;
    }

    /**
     * @hidden
     */
    protected static int readInt(InputStream in) throws IOException {
        int v = 0;
        for (int i=0;i<4;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            v = (v<<8) | q;
        }
        return v;
    }

    /**
     * @hidden
     */
    protected byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int l;
        while ((l=in.read(tmp, 0, tmp.length)) >= 0) {
            out.write(tmp, 0, l);
        }
        return out.toByteArray();
    }

    /**
     * @hidden
     */
    protected static float readFixed16(InputStream in) throws IOException {
        return (readInt(in) & 0xFFFFFFFFl) / 65535f;
    }

    /**
     * @hidden
     */
    protected static int readShort(InputStream in) throws IOException {
        int v = 0;
        for (int i=0;i<2;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            v = (v<<8) | q;
        }
        return v;
    }

    /**
     * @hidden
     * @param s the string
     * @return the type
     */
    public static int stringToType(String s) {
        int v = (s.length() < 1 ? 0 : s.charAt(0) & 0xFF) << 24;
        v |=    (s.length() < 2 ? 0 : s.charAt(1) & 0xFF) << 16;
        v |=    (s.length() < 3 ? 0 : s.charAt(2) & 0xFF) << 8;
        v |=    (s.length() < 4 ? 0 : s.charAt(3) & 0xFF);
        return v;
    }

    /**
     * @hidden
     * @param type the type
     * @return the string
     */
    public static String typeToString(int type) {
        char[] c = new char[4];
        c[0] = (char)((type>>24)&0xFF);
        c[1] = (char)((type>>16)&0xFF);
        c[2] = (char)((type>>8)&0xFF);
        c[3] = (char)(type&0xFF);
        return new String(c);
    }

    /**
     * @hidden
     * @param type the type to register
     * @param clazz the class of Box
     */
    public static void register(String type, boolean container, Class<? extends Box> clazz) {
        registry.put(type, clazz);
        if (type.length() == 4 && container) {
            containers.add(type);
        }
    }

    /**
     * Read a Box from this InputStream
     * @param stream the InputStream
     * @param box the Box that we're loading, or <code>null</code> to auto-type and create
     * @return the box, or <code>null</code> if the stream is fully consumed
     * @throws IOException if the stream is corrupt or fails to read
     */
    public static Box load(InputStream stream, Box box) throws IOException {
        CountingInputStream in = stream instanceof CountingInputStream ? (CountingInputStream)stream : new CountingInputStream(stream);
        long off = in.tell();
        long len = in.read();
        if (len < 0) {
            return null;
        }
        for (int i=0;i<3;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            len = (len<<8) | q;
        }

        // If debug is on, take a local copy of the table
        // into a byte array, then compare it later to
        // the same table when we write it out.
        byte[] localcopy = null;
        if (debug) {
            localcopy = new byte[(int)len];
            int tmpoff = 0;
            localcopy[tmpoff++] = (byte)(len>>24);
            localcopy[tmpoff++] = (byte)(len>>16);
            localcopy[tmpoff++] = (byte)(len>>8);
            localcopy[tmpoff++] = (byte)(len>>0);
            while (tmpoff < len) {
                localcopy[tmpoff++] = (byte)in.read();
            }
            in = new CountingInputStream(new ByteArrayInputStream(localcopy));
            off = 0;
            in.limit(len);
            in.skip(4);
        }

        int typeval = readInt(in);
        if (len == 1) {
            len = readLong(in);
        }
        long limit = in.limit();
        in.limit(len == 0 ? -1 : off + len);

        String type = typeToString(typeval);
        String subtype = null;
        if (type.equals("jumb") || type.equals("uuid")) {
            in.mark(256);
            ExtensionBox desc = (ExtensionBox)load(in, new ExtensionBox() { public String toString() { return "ignore"; }});
            subtype = desc.subtype();
            in.reset();
        }
        if (box == null) {
            Class<? extends Box> cl = null;
            if (subtype != null) {
                cl = registry.get(type + "." + subtype);
            }
            if (cl == null) {
                cl = registry.get(type);
            }
            try {
                if (cl != null) {
                    box = (Box)cl.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {}
            if (box == null) {
                box = new Box();
            }
        }
        box.len = len;
        box.type = typeval;
        box.read(in);
        long skip = len == 0 ? Integer.MAX_VALUE : in.limit() - in.tell();
        if (skip > 0) {
            // It's not clear whether this is an error or not? 
            UsefulByteArrayOutputStream out = new UsefulByteArrayOutputStream();
            byte[] t = new byte[8192];
            int l;
            while (skip > 0 && (l=in.read(t, 0, (int)Math.min(t.length, skip))) >= 0) {
                out.write(t, 0, l);
                skip -= l;
            }
            byte[] pad = out.toByteArray();
            if (pad.length > 0) {
                box.setPad(pad);
            }
        }
        in.limit(limit);
        if (debug && !box.toString().equals("ignore")) {
            byte[] writecopy = box.getEncoded();
            if (!Arrays.equals(writecopy, localcopy)) {
                /*
                System.out.println("MISMATCH: box="+box);
                if (box instanceof JumdBox) {
                    System.out.println("INN="+hex(localcopy));
                    System.out.println("OUT="+hex(writecopy));
                }
                */
            }
        }
        return box;
    }

    //------------------------------------------------------------------------------------------------

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    protected Box() {
    }

    /**
     * Create a new Box
     * @param type the type, which must be a four-letter alphanumeric string
     */
    protected Box(String type) {
        if (type == null || type.length() != 4) {
            throw new IllegalArgumentException("bad type");
        }
        this.type = stringToType(type);
    }

    /**
     * Add this box to the end of the list of children
     * @param box the box
     */
    public void add(Box box) {
        if (box.parent != null) {
            throw new IllegalStateException("already added");
        } else if (first == null) {
            first = box;
            box.parent = this;
        } else {
            Box b = first;
            while (b.next != null) {
                b = b.next;
            }
            b.next = box;
            box.parent = this;
        }
    }

    /**
     * Remove this box from its parent, and if other != null, replace it with "other"
     * @param other the Box to replace this box with, or null to remove this box
     * @return true if the tree changed as a result, false otherwise
     */
    public boolean replace(Box other) {
        if (other != null && other.parent != null) {
            throw new IllegalStateException("already added");
        }
        if (parent != null) {
            if (parent.first == this) {
                if (other == null) {
                    parent.first = next;
                } else {
                    parent.first = other;
                    other.next = next;
                    other.parent = parent;
                }
                parent = null;
                return true;
            } else {
                Box b = parent.first;
                while (b.next != null && b.next != this) {
                    b = b.next;
                }
                if (b.next == this) {
                    if (other == null) {
                        b.next = next;
                    } else {
                        b.next = other;
                        other.next = next;
                        other.parent = parent;
                    }
                    parent = null;
                    return true;
                }
                parent = null;  // shouldn't get here
            }
        }
        return false;
    }

    /**
     * Read the box content from the stream
     * The type/length have already been read from the stream.
     * The stream will return EOF when this box's data is read.
     * @param in the stream
     */
    protected void read(InputStream in) throws IOException {
        if (isContainer()) {
            Box b;
            while ((b=load(in, null)) != null) {
                add(b);
            }
        }
    }

    /**
     * Write the box content to the specified stream
     * @param out the OutputStream
     */
    private final void dowrite(OutputStream out) throws IOException {
        UsefulByteArrayOutputStream bout = out instanceof UsefulByteArrayOutputStream ? (UsefulByteArrayOutputStream)out : new UsefulByteArrayOutputStream();
        int start = bout.tell();
        bout.writeInt(0);
        bout.writeInt(stringToType(type()));
        write(bout);
        int end = bout.tell();
        bout.seek(start);
        bout.writeInt(end - start);
        bout.seek(end);
        if (out != bout) {
            bout.writeTo(out);
        }
    }

    /**
     * Write the box content. For boxes that are not {@link #isContainer containers},
     * this method must be overridden otherwise the box cannot be encoded
     * @param out the OutputStream to write to
     * @throws UnsupportedOperationException if the box is not a container box and this method hasn't been overridden
     */
    protected void write(OutputStream out) throws IOException {
        if (isContainer()) {
            for (Box b=first;b!=null;b=b.next) {
                b.dowrite(out);
            }
        } else {
            throw new UnsupportedOperationException("Box is not a container and write() was not overridden: " + this);
        }
    }

    /**
     * Return a <code>byte[]</code> array containing the encoded box structure.
     * All descendents that are not container boxes must override {@link #write}
     * @return the encoded box
     * @throws UnsupportedOperationException if any descendent of this box is not a container box and {@link #write} hasn't been overridden
     */
    public final byte[] getEncoded() {
        UsefulByteArrayOutputStream out = new UsefulByteArrayOutputStream();
        try {
            dowrite(out);
            if (pad != null) {
                out.write(pad);
            }
        } catch (IOException e) {}
        return out.toByteArray();
    }

    /**
     * If the box was loaded with additional padding bytes following its content,
     * return those bytes. They will be preserved when the box is written too.
     * This is necessary when computing signatures based on boxes.
     * @return the pad, which will be an array greater than zero, or null
     * @hidden
     */
    public byte[] getPad() {
        return pad;
    }

    /**
     * Set the padding bytes
     * @param pad the padding bytes, or zero. They will be stored as supplied, not cloned
     * @hidden
     */
    public void setPad(byte[] pad) {
        this.pad = pad;
    }

    /**
     * Return the type of this box, a four-character string
     * @return the Box type
     */
    public String type() {
        return typeToString(type);
    }

    /**
     * Return the full hierarchy of types from the root box to this
     * box, separated by dots. For example if this box has type "udat"
     * and the parent box has type "moov", this method returns "moov.udat"
     * @return the Box type hierarchy
     */
    public String fullType() {
        return parent() == null ? type() : parent.type() + "." + type();
    }

    /**
     * Return the length of this box in bytes, or 0 for "until end of file".
     * Boxes that have not been read from disk will always have zero length
     * @return the Box length
     */
    public long length() {
        return len;
    }

    /**
     * Return true if this box is a container of other boxes
     * @return whether this box is a container
     */
    public boolean isContainer() {
        return containers.contains(typeToString(type));
    }

    /**
     * Return the parent of this box, or null if this is the root
     * @return the parent
     */
    public Box parent() {
        return parent;
    }

    /**
     * Return the next box in the list, or null if this is the last.
     * @return the next sibling
     */
    public Box next() {
        return next;
    }

    /**
     * Return the first child of this box, or null if this box has no children
     * @return the first child
     */
    public Box first() {
        return first;
    }

    /**
     * Return a String representation of this Box, which will be parseable as JSON
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"");
        sb.append(type());
        sb.append("\"");
        if (getClass() != Box.class) {
            String name = getClass().getName();
            name = name.substring(name.lastIndexOf(".") + 1);
            sb.append(",\"class\":\"");
            sb.append(name);
            sb.append("\"");
        }
        if (getPad() != null) {
            sb.append(",\"pad\":\"");
            sb.append(hex(getPad()));
            sb.append("\"");
        }
        if (length() > 0) {
            sb.append(",\"size\":");
            sb.append(length());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * A convenience method to dump the box trree
     * @param prefix the prefix to add to each line, or null for none
     * @param out the Appendable to write to, or null to create a new StringBuilder
     * @return the appendable
     * @throws RuntimeException wrapping an IOException if encountered while writing to the Appendable.
     */
    public Appendable dump(String prefix, Appendable out) {
        try {
            if (prefix == null) {
                prefix = "";
            }
            if (out == null) {
                out = new StringBuilder();
            }
            out.append(prefix);
            out.append(this.toString());
            out.append("\n");
            prefix += " ";
            for (Box box = first();box!=null;box=box.next()) {
                box.dump(prefix, out);
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i=0;i<b.length;i++) {
            int v = b[i] & 0xFF;
            int v1 = (v >> 4) & 0xf;
            sb.append((char)(v1 < 10 ? '0' + v1 : v1 - 10 + 'a'));
            v1 = v & 0xf;
            sb.append((char)(v1 < 10 ? '0' + v1 : v1 - 10 + 'a'));
        }
        return sb.toString();
    }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i=0;i<s.length();i+=2) {
            b[i/2] = (byte)Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return b;
    }

    static {
        register("jumb", true, JUMBox.class);
        register("jumd", false, JumdBox.class);
        register("cbor", false, CborBox.class);                        // Raw CBOR box with no children
        register("json", false, JsonBox.class);                        // Raw JSON box with no children
        register("xml ", false, XmlBox.class);
        register("tkhd", false, TrackHeaderBox.class);
        register("bfdb", false, DataBox.class);
        register("bidb", false, DataBox.class);
        register("jumb.cbor", true, CborContainerBox.class);          // Jumb CBOR box - has [jumd, cbor]
        register("jumb.json", true, JsonContainerBox.class);          // Jumb JSON box - has [jumd, json]
        register("jumb.c2pa", true, C2PAStore.class);
        register("jumb.c2ma", true, C2PAManifest.class);
        register("jumb.c2cl", true, C2PAClaim.class);
        register("jumb.c2cs", true, C2PASignature.class);
        register("uuid.be7acfcb97a942e89c71999491e3afac", false, XMPBox.class);// wrong order, magnificently prioritised in XMP spec
        register("uuid.cbcf7abea997e8429c71999491e3afa", false, XMPBox.class); // byte order FFS
    }

}
