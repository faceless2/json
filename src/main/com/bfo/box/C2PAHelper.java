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
        CountingInputStream cin = new CountingInputStream(in);
        return readJPEG(new CountingInputStream(in), false);
    }

    /**
     * if out is null, read an entire JPEG, extract any C2PA data and return it
     * if out is not null, copy until the first marker after the initial JFIF marker(s) then stop
     */
    private static C2PAStore readJPEG(CountingInputStream in, boolean stop) throws IOException {
        long start = in.tell();
        Map<Integer,UsefulByteArrayOutputStream> app11 = new HashMap<Integer,UsefulByteArrayOutputStream>();
        int table;
        do {
            in.mark(2);
            table = in.read();
            if (table < 0) {
                break;
            }
            int v = in.read();
            if (v < 0) {
                throw new EOFException(Long.toString(in.tell()));
            }
            table = (table<<8) | v;
            if (table != 0xFFE0 && table != 0xFFD8 && stop) {
                in.reset();
                return null;
            }
            long end = start + 2;
            if (table == 0xFFDA) {
                break;
            } else if (table != 0xFF01 && table != 0xFFDA && (table < 0xFFD0 || table > 0xFFD8)) {
                int len = in.read();
                if (len < 0) {
                    throw new EOFException();
                }
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                len = (len<<8) | v;
                end += len;
            }
            // System.out.println("* read table " + Integer.toHexString(table)+" at " + start+ " end="+end);
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
                int count = (int)(end - in.tell());
                if (count > 15) {
                    byte[] data = new byte[count];
                    int p = 0;
                    while (p < data.length && (v=in.read(data, p, data.length - p)) >= 0) {
                        p += v;
                    }
                    if (data[0] == 0x4a && data[1] == 0x50) {
                        int id = (data[2]&0xFF)<<8 | (data[3]*0xFF);
                        int seq = ((data[4]&0xFF)<<24) | ((data[5]&0xFF)<<16) | ((data[6]&0xFF)<<8) | ((data[7]&0xFF)<<0);
                        int boxlen = ((data[8]&0xFF)<<24) | ((data[9]&0xFF)<<16) | ((data[10]&0xFF)<<8) | ((data[11]&0xFF)<<0);
                        int boxtype = ((data[12]&0xFF)<<24) | ((data[13]&0xFF)<<16) | ((data[14]&0xFF)<<8) | ((data[15]&0xFF)<<0);
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
            byte[] data = new byte[8192];
            int p = 0;
            while (in.tell() < end && (v=in.read(data, 0, (int)Math.min(data.length, end - in.tell()))) >= 0) {
            }
            start = end;
        } while (table != 0xFFDA);
        if (!app11.isEmpty()) {
            // pick the first
            InputStream in2 = app11.values().iterator().next().toInputStream();
            //FileOutputStream fout = new FileOutputStream("/tmp/t2");
            //int c;
            //while ((c=in2.read())>=0) {
            //    fout.write(c);
            //}
            //fout.close();
            //in2 = app11.values().iterator().next().toInputStream();
            return (C2PAStore)new BoxFactory().load(in2);
        }
        return null;
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
     * @throws IOException if the JPEG could not be written or read.
     * @return the bytes representing the C2PAStore object that was embedded.
     */
    public static byte[] signJPEG(C2PAStore store, PrivateKey key, List<X509Certificate> certs, InputStream input, OutputStream out) throws IOException {
        // We have to read the stream twice, once to digest, once to write out.
        // So we have to take a copy.
        UsefulByteArrayOutputStream tmp = new UsefulByteArrayOutputStream();
        tmp.write(input);
        // contract says we leave it open, so do

        if (store == null) {
            store = new C2PAStore();
        }
        if (store.getManifests().isEmpty()) {
            store.getManifests().add(new C2PAManifest(UUID.randomUUID().toString()));
        }
        C2PAManifest manifest = store.getManifests().get(store.getManifests().size() - 1);
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

        // Begin signing process
        CountingInputStream in = new CountingInputStream(tmp.toInputStream());
        readJPEG(in, true);
        final int start = (int)in.tell();       // We stopped at the point to insert the app11 marker(s)
        // Dummy sign to determine length
        manifest.setInputStream(new ByteArrayInputStream(new byte[0]));
        manifest.getSignature().sign(key, certs);
        byte[] dummydata = store.getEncoded();
        int length = dummydata.length;
        final int maxsegmentlength = 65530;     // 65535, but headroom feels nicer
        final int segheaderlen = 12;            // bytes of overhead per segment
        int numsegments = (int)Math.ceil(length / (float)(maxsegmentlength - segheaderlen));
        hash.setExclusions(new long[] { start, length + (numsegments * segheaderlen) });
        // Sign a second time now we have set exclusions
        manifest.setInputStream(tmp.toInputStream());
        manifest.getSignature().sign(key, certs);
        byte[] data = store.getEncoded();
        if (data.length != length) {
            //System.out.println(((C2PAStore)new BoxFactory().load(new ByteArrayInputStream(dummydata))).toJson());
            //System.out.println(((C2PAStore)new BoxFactory().load(new ByteArrayInputStream(data))).toJson());
            throw new IllegalStateException("Expected " + length + " bytes, second signing gave us " + data.length);
        }

        // Begin writing process; copy the bit we've already read
        input = tmp.toInputStream();
        for (int i=0;i<start;i++) {
            out.write(input.read());
        }
        // Write our C2PA as segments
        for (int i=0;i<numsegments;i++) {
            int start2 = i * (maxsegmentlength - segheaderlen);
            int len = Math.min(maxsegmentlength - segheaderlen, data.length - start2);
            int seglen = len + segheaderlen - 2;  // excluding marker
            out.write(0xff);    // app11
            out.write(0xeb);
            out.write(seglen>>8); // segment length
            out.write(seglen);
            out.write(0x4a);    // 0x4a50 constant
            out.write(0x50);
            out.write(0);       // two byte box instance number
            out.write(0);
            out.write(i>>24); // four byte sequence number
            out.write(i>>16);
            out.write(i>>8);
            out.write(i);
            out.write(data, start2, len);
        }
        // Write rest of file
        byte[] buf = new byte[8192];
        while ((length=input.read(buf, 0, buf.length)) >= 0) {
            out.write(buf, 0, length);
        }
        out.flush();
        return data;
    }

    public static void main(String[] args) throws Exception {
        String storepath = null, password = "", storetype = "pkcs12", alias = null, alg = null, cw = null, outname = null;
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        PrivateKey key = null;
        boolean sign = false, debug = false;

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
                }
                do {
                    String inname = args[i];
                    System.out.println(inname);
                    InputStream in = new FileInputStream(inname);
                    if (sign) {
                        if (key == null) {
                            throw new IllegalStateException("no key");
                        }
                        if (certs.isEmpty()) {
                            throw new IllegalStateException("no certs");
                        }
                        C2PAStore c2pa = new C2PAStore();
                        C2PAManifest manifest = new C2PAManifest(new File(inname).toURI().toString());
                        c2pa.getManifests().add(manifest);
                        manifest.getClaim().setFormat("image/jpeg");
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
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(outname));
                        byte[] encoded = signJPEG(c2pa, key, certs, in, out);
                        if (debug) {
                            //FileOutputStream fout = new FileOutputStream("/tmp/t1");
                            //fout.write(encoded);
                            //fout.close();
                            System.out.println(c2pa.toJson().toString(new JsonWriteOptions().setPretty(true).setCborDiag("hex")));
                        }
                        System.out.println("# signed and wrote to \"" + outname + "\"");
                        out.close();
                        out = null;
                    } else {
                        C2PAStore c2pa = extractFromJPEG(in);
                        if (c2pa != null) {
                            C2PAManifest manifest = c2pa.getManifests().get(c2pa.getManifests().size() - 1);
                            in = new FileInputStream(inname);
                            manifest.setInputStream(in);
                            if (debug) {
                                System.out.println(c2pa.toJson().toString(new JsonWriteOptions().setPretty(true).setCborDiag("hex")));
                                System.out.println("# active manifest \"" + manifest.label() + "\"");
                            }
                            for (C2PA_Assertion a : manifest.getClaim().getAssertions()) {
                                if (a != null) {
                                    try {
                                        a.verify();
                                        System.out.println("# assertion \"" + a.asBox().label() + "\" verified");
                                    } catch (UnsupportedOperationException e) {
                                        System.out.println("# assertion \"" + a.asBox().label() + "\" unsupported");
                                    } catch (Exception e) {
                                        System.out.println("# assertion \"" + a.asBox().label() + "\" failed: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                } else {
                                    System.out.println("# assertion is null (shouldn't happen)");
                                }
                            }
                            try {
                                if (manifest.getSignature().verify(null)) {
                                    System.out.println("# signature verified");
                                } else {
                                    System.out.println("# signature verification failed");
                                }
                            } catch (Exception e) {
                                System.out.println("# signature failed");
                                e.printStackTrace();
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
                        } else if (s.equals("--sign")) {
                            sign = true;
                        } else if (s.equals("--verify")) {
                            sign = false;
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
        System.out.println("   --keystore <path>       if signing, the path to Keystore to load credentials from");
        System.out.println("   --keystoretype <type>   if signing, the keystore type - pkcs12 (default), jks or jceks");
        System.out.println("   --alias <name>          if signing, the alias from the keystore (default is the first one");
        System.out.println("   --password <password>   if signing, the password to open the keystore");
        System.out.println("   --alg <algorithm>       if signing, the hash algorithm");
        System.out.println("   --creativework <path>   if signing, filename containing a JSON schema to embed");
        System.out.println("   --out <path>            if signing, filename to write signed output to (default will derive from input");
        System.out.println("   <path>                  the filename to sign or verify");
        System.out.println();
        System.exit(0);
    }

}
