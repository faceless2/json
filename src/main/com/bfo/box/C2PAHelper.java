package com.bfo.box;

import java.io.*;
import java.util.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import com.bfo.json.*;

/**
 * A general Helper class for C2PA which functions as a main method, provides
 * utility methods for embedding C2PA in files
 */
public class C2PAHelper {

    /**
     * Given a JPEG, return any {@link C2PAStore} found in the file or null
     * @param in an InputStream containing a JPEG. The stream will be fully read but not closed
     * @return the C2PAStore of found, or null
     * @throws IOException if the file failed to read.
     */
    public static C2PAStore extractFromJPEG(InputStream in) throws IOException {
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        CountingInputStream cin = new CountingInputStream(in);
        final Map<Integer,UsefulByteArrayOutputStream> app11 = new HashMap<Integer,UsefulByteArrayOutputStream>();
        Callback cb = new Callback() {
            public boolean segment(final int table, int length, final CountingInputStream in) throws IOException {
                if (table == 0xFFEB) {
                    // 0xFFEB - app11 marker (already read)
                    // 2 byte length (already read)
                    // 2 byte ID, which is always 0x4a50
                    // 2 byte box instance number "disambiguates between JPEG XT marker segments"
                    // 4 byte packet sequence number, which C2PA requires they're in order
                    // 4 byte box length
                    // 4 byte box type
                    // optional 8-byte extended box length if box length=1
                    // data
                    length -= 2;
                    if (length > 15) {
                        byte[] data = new byte[length];
                        int p = 0, v;
                        while (p < data.length && (v=in.read(data, p, data.length - p)) >= 0) {
                            p += v;
                        }
                        if (data[0] == 0x4a && data[1] == 0x50) {
                            int id = (data[2]&0xFF)<<8 | (data[3]*0xFF);
                            int seq = ((data[4]&0xFF)<<24) | ((data[5]&0xFF)<<16) | ((data[6]&0xFF)<<8) | ((data[7]&0xFF)<<0);
                            int boxlen = ((data[8]&0xFF)<<24) | ((data[9]&0xFF)<<16) | ((data[10]&0xFF)<<8) | ((data[11]&0xFF)<<0);
                            int boxtype = ((data[12]&0xFF)<<24) | ((data[13]&0xFF)<<16) | ((data[14]&0xFF)<<8) | ((data[15]&0xFF)<<0);
                            //System.out.println("seglen="+length+" id="+id+" seq="+seq+" boxlen="+boxlen+" boxtype="+Box.typeToString(boxtype));
                            if (boxtype == 0x6a756d62) { // "jumb"
                                int skip = 8;
                                if (app11.get(id) == null) {
                                    app11.put(id, new UsefulByteArrayOutputStream());
                                } else {
                                    skip += 8;   // because boxlen and boxtype are repeated (with identical length) for every packet
                                    if (boxlen == 1) {
                                        skip += 8;
                                    }
                                }
                                app11.get(id).write(data, skip, data.length - skip);
                            }
                        }
                    }
                }
                return true;
            }
        };
        readJPEG(new CountingInputStream(in), cb);
        if (!app11.isEmpty()) {
            // Pick the first
            InputStream in2 = app11.values().iterator().next().toInputStream();
            return (C2PAStore)new BoxFactory().load(in2);
        }
        return null;
    }

    private static interface Callback {
        /**
         * A JPEG segment has been read.
         * @param table the table
         * @param length the length of this segment, excluding the two-byte table header. May be 0 if no length applies to this table
         * @param in the stream to read from, which will be limited to "length" bytes
         * @return true if reading should continue
         */
        boolean segment(int table, int length, CountingInputStream in) throws IOException;
    }

    /**
     * if out is null, read an entire JPEG, extract any C2PA data and return it
     * if out is not null, copy until the first marker after the initial JFIF marker(s) then stop
     */
    private static void readJPEG(CountingInputStream in, Callback callback) throws IOException {
        int table;
        do {
            long start = in.tell();
            table = in.read();
            if (table < 0) {
                break;
            }
            int v = in.read();
            if (v < 0) {
                throw new EOFException(Long.toString(in.tell()));
            }
            table = (table<<8) | v;
            int length = 0;
            if (table == 0xFFDA) {
                length = -1;
            } else if (table != 0xFF01 && (table < 0xFFD0 || table > 0xFFD8)) {
                int len = in.read();
                if (len < 0) {
                    throw new EOFException();
                }
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                length = (len<<8) | v;
            }
            final long end = length < 0 ? -1 : start + length + 2;
            // System.out.println("* seg=0x"+Integer.toHexString(table)+" start="+start+" len="+length+" end="+end);
            in.limit(end);
            if (callback != null && !callback.segment(table, length, in)) {
                return;
            }
            in.limit(-1);
            if (end < 0) {
                // WTF - skip past EOF seems to fail on FileInputStream
                while (in.read() >= 0);
            } else {
                while (in.tell() < end) {
                    if (in.read() < 0) {
                        break;
                    }
                }
            }
        } while (table != 0xFFDA);
    }

    /**
     * Sign a JPEG by inserting a C2PA into the middle. The supplied store will have a manifest
     * and data-hash assertions added if necessary. The supplied key will be used to sign twice,
     * the first time with dummy data to determine the size of the object to insert.
     * Once signed the C2PA store will be inserted as APP11 segment(s) after the initial JFIF
     * marker.
     *
     * @param store the C2PA store, which will be created if null. If it already contains one or more
     * manifests, the final manifest will be the one that's signed
     * @param key the private key to sign with
     * @param certs the certificates corresponding to that key to include
     * @param input the InputStream containing the JPEG file, which will be fully read but left open
     * @param out the OutputStream to write the signed JPEG file to, which will be flushed but left open
     * @param rawout an optional OutputStream to write the raw C2PAStore object that was embedded, which will be flushed but left open
     * @throws IOException if the JPEG could not be written or read.
     * @return a list of status codes during signing, which should contain no error codes
     */
    public static List<C2PAStatus> signJPEG(C2PAStore store, PrivateKey key, List<X509Certificate> certs, InputStream input, OutputStream out, OutputStream rawout) throws IOException {
        List<C2PAStatus> status;
        // We have to read the stream twice, once to digest, once to write out.
        // So we have to take a copy. Copy it without any 0xFFED or 0xFFE1 segments
        final UsefulByteArrayOutputStream tmp = new UsefulByteArrayOutputStream();
        final int[] headerStart = new int[1];

        readJPEG(new CountingInputStream(input), new Callback() {
            boolean header = true;
            @Override public boolean segment(int table, int length, CountingInputStream in) throws IOException {
                if (header) {
                    if (table != 0xFFE0 && table != 0xFFD8) {
                        header = false;
                    }
                    headerStart[0] = (int)in.tell() - 4; // (int)in.tell() + length - 2;
                }
                if (table != 0xFFEB && table != 0xFFE1) {
                    tmp.write(table>>8);
                    tmp.write(table);
                    if (length > 0) {
                        tmp.write(length>>8);
                        tmp.write(length);
                    }
                    long t = in.tell();
                    int wrote = 0;
                    byte[] buf = new byte[8192];
                    int l;
                    while ((l=in.read(buf)) >= 0) {
                        tmp.write(buf, 0, l);
                        wrote += l;
                    }
                }
                return true;
            }
        });
        final byte[] inputBytes = tmp.toByteArray();
        // Now "inputbytes" is JPEG without any app1/app11 markers, and headerStart[0] is where to put our segments

        // contract says we leave it open, so do

        // Initialize the store/manifest data
        if (store == null) {
            store = new C2PAStore();
        }
        if (store.getManifests().isEmpty()) {
            store.getManifests().add(new C2PAManifest("urn:uuid:" + UUID.randomUUID().toString()));
        }
        C2PAManifest manifest = store.getManifests().get(store.getManifests().size() - 1);
        if (manifest.getClaim().getInstanceID() == null) {
            manifest.getClaim().setInstanceID("urn:uuid:" + UUID.randomUUID().toString());
        }
        C2PA_AssertionHashData hash = null;
        for (C2PA_Assertion a : manifest.getAssertions()) {
            if (a instanceof C2PA_AssertionHashData) {
                hash = (C2PA_AssertionHashData)a;
                break;
            }
        }
        if (hash == null) {
            manifest.getAssertions().add(hash = new C2PA_AssertionHashData());
        }
        boolean writexmp = true;        // c2patool needs this if there is > 1 manifest in store
        byte[] xmpbytes = new byte[0];
        if (writexmp) {
            String s = "http://ns.adobe.com/xap/1.0/\u0000";
            s += "<?xpacket begin=\"\ufeff\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\" xmlns:dcterms=\"http://purl.org/dc/terms/\" dcterms:provenance=\"" + store.find(manifest) + "\"/></rdf:RDF></x:xmpmeta><?xpacket end=\"r\"?>";
            byte[] b = s.getBytes("UTF-8");
            int datalen = b.length + 2;
            xmpbytes = new byte[datalen + 2];
            xmpbytes[0] = (byte)0xff;
            xmpbytes[1] = (byte)0xe1;
            xmpbytes[2] = (byte)(datalen>>8);
            xmpbytes[3] = (byte)datalen;
            System.arraycopy(b, 0, xmpbytes, 4, b.length);
        }

        // Dummy sign to determine length
        manifest.setInputStream(new ByteArrayInputStream(new byte[0]));
        manifest.getSignature().sign(key, certs);
        byte[] dummydata = store.getEncoded();
        int siglength = dummydata.length;
        final int maxsegmentlength = 64012;     // 65535 plus one for luck
        final int segheaderlen = 20;            // bytes of overhead per segment
        int numsegments = (int)Math.ceil((siglength - 8) / (float)(maxsegmentlength - segheaderlen));
        hash.setExclusions(new long[] { headerStart[0], (siglength - 8) + (numsegments * segheaderlen) });
        // Sign a second time now we have set exclusions
        manifest.setInputStream(new SequenceInputStream(new ByteArrayInputStream(inputBytes, 0, headerStart[0]), new SequenceInputStream(new ByteArrayInputStream(xmpbytes), new ByteArrayInputStream(inputBytes, headerStart[0], inputBytes.length - headerStart[0]))));
        status = manifest.getSignature().sign(key, certs);
        byte[] data = store.getEncoded();
        if (data.length != siglength) {
            //System.out.println(((C2PAStore)new BoxFactory().load(new ByteArrayInputStream(dummydata))).toJson());
            //System.out.println(((C2PAStore)new BoxFactory().load(new ByteArrayInputStream(data))).toJson());
            throw new IllegalStateException("Expected " + siglength + " bytes, second signing gave us " + data.length);
        }

        // Begin writing process; copy the bit we've already read
        out.write(inputBytes, 0, headerStart[0]);
        // Write our C2PA as segments
        int segid = 0;
        for (int i=0;i<numsegments;) {
            int start2 = 8 + (i * (maxsegmentlength - segheaderlen));
            int len = Math.min(maxsegmentlength - segheaderlen, data.length - start2);
            // System.out.println("WRITE " + i+"/"+numsegments+" start2="+start2+" len="+len+" to " + (start2+len)+"/"+data.length);
            i++;        // c2patool wants packets to start at 1
            int seglen = len + segheaderlen - 2;  // excluding marker
            out.write(0xff);    // app11
            out.write(0xeb);
            out.write(seglen>>8); // segment length
            out.write(seglen);
            out.write(0x4a);    // 0x4a50 constant
            out.write(0x50);
            out.write(segid>>8);       // two byte box instance number
            out.write(segid);
            out.write(i>>24); // four byte sequence number
            out.write(i>>16);
            out.write(i>>8);
            out.write(i);
            out.write(data, 0, 8);      // this must be repeated each segment
            out.write(data, start2, len);
        }
        out.write(xmpbytes);

        out.write(inputBytes, headerStart[0], inputBytes.length - headerStart[0]);
        out.flush();
        if (rawout != null) {
            rawout.write(data);
            rawout.flush();
        }
        return status;
    }

    public static void main(String[] args) throws Exception {
        String storepath = null, password = "", storetype = "pkcs12", alias = null, alg = null, cw = null, outname = null, outc2pa = null;
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        PrivateKey key = null;
        boolean sign = false, debug = false, boxdebug = false;

        // No apologies for this, it's a quick and nasty test
        KeyStore keystore = null;
        for (int i=0;i<args.length;i++) {
            String s = args[i];
            if (s.equals("--keystore")) {
                storepath = args[++i];
            } else if (s.equals("--help")) {
                help();
            } else if (s.equals("--password")) {
                password = args[++i];
            } else if (s.equals("--sign")) {
                sign = true;
            } else if (s.equals("--verify")) {
                sign = false;
            } else if (s.equals("--debug")) {
                debug = true;
            } else if (s.equals("--boxdebug")) {
                boxdebug = true;
            } else if (s.equals("--nodebug")) {
                debug = false;
            } else if (s.equals("--noboxdebug")) {
                boxdebug = false;
            } else if (s.equals("--keystoretype")) {
                storetype = args[++i];
            } else if (s.equals("--alias")) {
                alias = args[++i];
            } else if (s.equals("--alg")) {
                alg = args[++i];
            } else if (s.equals("--creativework")) {
                cw = args[++i];
            } else if (s.equals("--out")) {
                outname = args[++i];
            } else if (s.equals("--c2pa")) {
                outc2pa = args[++i];
            } else {
                if (sign) {
                    keystore = KeyStore.getInstance(storetype);
                    if (storepath == null) {
                        throw new IllegalStateException("no keystore");
                    }
                    keystore.load(new FileInputStream(storepath), password.toCharArray());
                    if (alias == null) {
                        for (Enumeration<String> e = keystore.aliases();e.hasMoreElements();) {
                            alias = e.nextElement();
                            try {
                                key = (PrivateKey)keystore.getKey(alias, password.toCharArray());
                                break;
                            } catch (Exception e2) {
                                alias = null;
                            }
                        }
                    } else {
                        key = (PrivateKey)keystore.getKey(alias, password.toCharArray());
                    }
                    for (Certificate c : keystore.getCertificateChain(alias)) {
                        certs.add((X509Certificate)c);
                    }
                    if (certs.size() > 1) {
                        // "The trust anchor’s certificate (also called the root certificate) should not be included."
                        certs.remove(certs.size() - 1);
                    }
                }
                do {
                    String inname = args[i];
//                    System.out.println(inname);
                    InputStream in = new FileInputStream(inname);
                    if (sign) {
                        if (key == null) {
                            throw new IllegalStateException("no key");
                        }
                        if (certs.isEmpty()) {
                            throw new IllegalStateException("no certs");
                        }
                        C2PAStore c2pa = new C2PAStore();
                        /*
                        C2PAStore original = extractFromJPEG(in);
                        C2PAManifest lastmanifest = null;
                        List<C2PAStatus> laststatus = null;
                        if (original != null) {
                            lastmanifest = original.getActiveManifest();
                            lastmanifest.setInputStream(new FileInputStream(inname));
                            laststatus = lastmanifest.getSignature().verify(null);
                            for (C2PAManifest mf : original.getManifests()) {
                                lastmanifest = (C2PAManifest)mf.duplicate();
                                c2pa.getManifests().add(lastmanifest);
                            }
                        }
                        */
                        C2PAManifest manifest = new C2PAManifest("urn:uuid:" + UUID.randomUUID().toString());
                        c2pa.getManifests().add(manifest);
                        manifest.getClaim().setFormat("image/jpeg");
                        /*
                        if (original != null) {
                            C2PA_AssertionIngredient ing;
                            manifest.getAssertions().add(ing = new C2PA_AssertionIngredient());
                            ing.setTargetManifest("parentOf", lastmanifest, laststatus);
                            C2PA_AssertionActions act = new C2PA_AssertionActions();
                            manifest.getAssertions().add(act);
                            act.add("c2pa.repackaged", ing, null);
                        }
                        */
                        if (alg != null) {
                            manifest.getClaim().setHashAlgorithm(alg);
                        }
                        if (cw != null) {
                            Json j = Json.read(new FileInputStream(cw), null);
                            manifest.getAssertions().add(new C2PA_AssertionSchema("stds.schema-org.CreativeWork", j));
                        }
                        if (outname == null) {
                            outname = inname.indexOf(".") > 0 ? inname.substring(0, inname.lastIndexOf(".")) + "-signed" + inname.substring(inname.lastIndexOf(".")) : inname + "-signed.jpg";
                        }
                        in = new FileInputStream(inname);
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(outname));
                        OutputStream rawout = null;
                        if (outc2pa != null) {
                            rawout = new BufferedOutputStream(new FileOutputStream(outc2pa));
                        }
                        List<C2PAStatus> status = signJPEG(c2pa, key, certs, in, out, rawout);
                        if (debug) {
                            System.out.println(c2pa.toJson().toString(new JsonWriteOptions().setPretty(true).setCborDiag("hex")));
                        }
                        if (boxdebug) {
                            System.out.println(c2pa.dump(null, null));
                        }
                        boolean ok = true;
                        for (C2PAStatus st : status) {
                            ok &= st.isOK();
                            System.out.println("# " + st);
                        }
                        if (ok) {
                            System.out.println(inname + ": SIGNED, wrote to \"" + outname + "\"");
                        } else {
                            System.out.println(inname + ": SIGNED WITH ERRORS, wrote to \"" + outname + "\"");
                        }
                        System.out.println();
                        out.close();
                        out = null;
                        if (rawout != null) {
                            rawout.close();
                            outc2pa = null;
                        }
                    } else {
                        C2PAStore c2pa = extractFromJPEG(in);
                        if (c2pa != null) {
                            if (outc2pa != null) {
                                OutputStream rawout = new BufferedOutputStream(new FileOutputStream(outc2pa));
                                rawout.write(c2pa.getEncoded());
                                rawout.close();
                                outc2pa = null;
                            }
//                            List<C2PAManifest> manifests = c2pa.getManifests();
                            List<C2PAManifest> manifests = Collections.<C2PAManifest>singletonList(c2pa.getActiveManifest());
                            for (C2PAManifest manifest : manifests) {
                                in = new FileInputStream(inname);
                                manifest.setInputStream(in);
                                if (debug) {
                                    System.out.println(c2pa.toJson().toString(new JsonWriteOptions().setPretty(true).setCborDiag("hex")));
                                }
                                if (boxdebug) {
                                    System.out.println(c2pa.dump(null, null));
                                }
                                System.out.println("# verifying " + (manifest == c2pa.getActiveManifest() ? "active " : "") + "manifest \"" + manifest.label() + "\"");
                                List<C2PAStatus> status = manifest.getSignature().verify(null);
                                boolean ok = true;
                                for (C2PAStatus st : status) {
                                    ok &= st.isOK();
                                    System.out.println("# " + st);
                                }
                                if (ok) {
                                    System.out.println(inname + ": VALIDATED");
                                } else {
                                    System.out.println(inname + ": VALIDATION FAILED");
                                }
                                System.out.println();
                            }
                        } 
                    }
                    while (++i < args.length) {
                        s = args[i];
                        if (s.equals("--alg")) {
                            alg = args[++i];
                        } else if (s.equals("--creativework")) {
                            cw = args[++i];
                        } else if (s.equals("--out")) {
                            outname = args[++i];
                        } else if (s.equals("--c2pa")) {
                            outc2pa = args[++i];
                        } else if (s.equals("--sign")) {
                            sign = true;
                        } else if (s.equals("--verify")) {
                            sign = false;
                        } else if (s.equals("--debug")) {
                            debug = true;
                        } else if (s.equals("--boxdebug")) {
                            boxdebug = true;
                        } else if (s.equals("--nodebug")) {
                            debug = false;
                        } else if (s.equals("--noboxdebug")) {
                            boxdebug = false;
                        } else if (s.equals("--help")) {
                            help();
                        } else {
                            break;
                        }
                    }
                } while (i < args.length);
            }
        }
    }

    private static void help() {
        System.out.println("java com.bfo.box.C2PAHelper args...");
        System.out.println("   --verify                switch to verify mode (the default)");
        System.out.println("   --sign                  switch to signing mode");
        System.out.println("   --debug                 turn on debug to dump the c2pa store as CBOR-diag");
        System.out.println("   --boxdebug              turn on debug to dump the c2pa store as a box tree");
        System.out.println("   --nodebug               turn off --debug");
        System.out.println("   --noboxdebug            turn off --boxdebug");
        System.out.println("   --keystore <path>       if signing, the path to Keystore to load credentials from");
        System.out.println("   --keystoretype <type>   if signing, the keystore type - pkcs12 (default), jks or jceks");
        System.out.println("   --alias <name>          if signing, the alias from the keystore (default is the first one");
        System.out.println("   --password <password>   if signing, the password to open the keystore");
        System.out.println("   --alg <algorithm>       if signing, the hash algorithm");
        System.out.println("   --creativework <path>   if signing, filename containing a JSON schema to embed");
        System.out.println("   --out <path>            if signing, filename to write signed output to (default will derive from input");
        System.out.println("   --c2pa <path>           if signing, filename to dump the C2PA object to (default is not dumped");
        System.out.println("   <path>                  the filename to sign or verify");
        System.out.println();
        System.exit(0);
    }

}
