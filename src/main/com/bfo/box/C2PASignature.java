package com.bfo.box;

import java.io.*;
import java.util.*;
import java.nio.*;
import java.security.*;
import java.security.cert.*;
import com.bfo.json.*;

/**
 * The <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_digital_signatures">C2PA signature</a>
 * is applied to each {@link C2PAManifest manifest} to sign it. There is one signature per manifest.
 * @since 5
 */
public class C2PASignature extends CborContainerBox {

    private int minsize = -1;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    protected C2PASignature() {
    }

    C2PASignature(Json cbor) {
        super("c2cs", "c2pa.signature", cbor);
    }
    
    public String toString() {
        if (getMinSize() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());
            sb.setCharAt(sb.length() - 1, ',');
            sb.append("\"padto\":");
            sb.append(getMinSize());
            sb.append("}");
            return sb.toString();
        } else {
            return super.toString();
        }
    }

    /**
     * Return the COSE object which actually contains the signature.
     * While useful for retrieving cryptographic details from the signature,
     * don't call sign/verify on the returned object; call them on this class
     * instead.
     * @return the COSE object
     */
    public COSE cose() {
        if (!(cbor() instanceof COSE)) {
            COSE cose = new COSE(cbor());
            getBox().setCbor(cose);
        }
        return (COSE)cbor();
    }

    /**
     * If the signature is padded to a specific length, get the length, otherwise return 0
     */
    public int getMinSize() {
        if (minsize == -1) {
            minsize = 0;
            COSE cose = cose();
            Json unprot = cose.getUnprotectedAttributes();
            if (unprot != null && unprot.isBuffer("pad")) {
//                minsize = unprot.bufferValue("pad").array().length;
            }
        }
        return minsize;
    }

    public void setMinSize(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        this.minsize = length;
    }

    /**
     * Sign the claim. Before signing
     * <ul>
     * <li>If the {@link C2PAClaim#getAssertions claim's assertions} are empty, it will be initialized to all the {@link C2PAManifest#getAssertions manifest's assertions}</li>
     * <li>The assertions must be non-empty and include a "hash" type assertion</li>
     * <li>The {@link C2PAClaim#getFormat claim format} must be set</li>
     * <li>The {@link C2PAClaim#getInstanceID claim instance id} must be set</li>
     * <li>If the {@link C2PAClaim#getHashAlgorithm hash algorithm} is not set, it will be initialized to a default value</li>
     * <li>If the {@link C2PAClaim#getGenerator generator} is not set, it will be initialized to a default value</li>
     * <li>The claim object is finalized and signed</li>
     * </ul>
     * @param key the PrivateKey
     * @param certs a list of X.509 certificates to include in the COSE object.
     * @throws RuntimeException wrapping a GeneralSecurityException if signing fails
     */
    public void sign(PrivateKey key, List<X509Certificate> certs) /*throws GeneralSecurityException*/ {
        final COSE cose = cose();
        final C2PAManifest manifest = (C2PAManifest)parent();
        final C2PAClaim claim = manifest.getClaim();
        final List<C2PA_Assertion> assertions = claim.getAssertions();
        if (claim.getFormat() == null) {
            throw new IllegalStateException("claim has no format");
        }
        boolean foundHash = false;
        if (assertions.isEmpty()) {
            assertions.addAll(manifest.getAssertions());
        }
        for (int i=0;i<assertions.size();i++) {
            if (assertions.get(i) == null) {
                assertions.remove(i--);
//            } else if (claim instanceof DataHashBox || claim instanceof BMFFHashBox) {
//                foundHash = true;
            }
        }
        if (!foundHash) {
            // throw new IllegalStateException("No DataHash or BMFFHash assertion");
        }
        if (claim.getGenerator() == null) {
            claim.setGenerator("BFO Json library", null);
        }
        if (claim.getHashAlgorithm() == null) {
            claim.setHashAlgorithm("SHA256");
        }
        if (getMinSize() > 0) {
            Json j = cose.getUnprotectedAttributes();
            if (j == null) {
                j = Json.read("{}");
            }
            j.put("pad", new byte[getMinSize()]);
        }
        claim.cbor().put("signature", "self#jumbf=" + manifest.find(this));
        cose.setPayload(generatePayload(getMinSize()), true);
        cose.setCertificates(certs);
        cose.sign(key, null);
    }

    private ByteBuffer generatePayload(int padlength) {
        final C2PAManifest manifest = (C2PAManifest)parent();
        final C2PAClaim claim = manifest.getClaim();
        final List<C2PA_Assertion> assertions = claim.getAssertions();
        MessageDigest digest;
        Json l = claim.cbor().get("assertions");
        for (int i=0;i<l.size();i++) {
            digestHashedURL(l.get(i), manifest, false);
        }
        byte[] b = claim.cbor().toCbor().array();
        if (padlength > b.length) {
            byte[] b2 = new byte[padlength];
            System.arraycopy(b, 0, b2, 0, b.length);
            b = b2;
        }
        return ByteBuffer.wrap(b);
    }

    /**
     * Verify the cryptographic aspects of the claim. Note for full verification, each
     * asseration in the claim's list must also be verified.
     * @param key the public key; if null, it will be extracted (if possible) from any certificates in the signature
     * @throws IllegalArgumentException if no key is available to verify
     * @throws IllegalStateException if the signature is not signed or has been incorrectly set up
     * @return true if the supplied key verifies the signature, false otherwise
     * @see C2PA_Assertion#veriy
     */
    public boolean verify(PublicKey key) {
        // It does say that it a) must be a SIGN1 and b) must have exactly one "credential" (X509 cert)
        final COSE cose = cose();
        final C2PAManifest manifest = (C2PAManifest)parent();
        final C2PAClaim claim = manifest.getClaim();
        if (!cose.isInitialized()) {
            throw new IllegalStateException("not signed");
        } else if (!cose.isDetached()) {
            // "The payload field of Sig_structure shall be the serialized CBOR of the claim document,
            // and shall use detached content mode."
            //  -- https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_signing_a_claim
            throw new IllegalStateException("not detached");
        }
        for (Box b=manifest.first();b!=null;b=b.next()) {
            if (b instanceof C2PAClaim && b != claim) {
                throw new IllegalStateException("too many claim boxes [claim.multiple]");
            }
        }
        if (!claim.cbor().isString("signature") || manifest.find(claim.cbor().stringValue("signature")) != this) {
            throw new IllegalStateException("signature not in claim [claimSignature.missing]");
        }
        if (key == null && cose.getCertificates() != null && !cose.getCertificates().isEmpty()) {
            key = cose.getCertificates().get(0).getPublicKey();
        } else if (key == null) {
            throw new IllegalArgumentException("no key supplied and no certificates included in the signature");
        }
        ByteBuffer payload = generatePayload(getMinSize());
        cose.setPayload(payload, true);
        return cose.verify(key) >= 0;   // claimSignature.mismatch
    }

    /**
     * Given a Json object with {"uri":x}, calculate the digest
     * and update it. If it already has a digest and it differs,
     * or the URL cannot be found, throw an Exception
     */
    static void digestHashedURL(Json hasheduri, C2PAManifest manifest, boolean ingredient) {
        String url = hasheduri.stringValue("url");
        JUMBox box = manifest.find(url);
        if (box == null) {
            throw new IllegalStateException("URL \"" + url + "\" not in manifest [assertion.missing]");
        }
        Json j = hasheduri;
        while (j != null && !j.isString("alg")) {
            j = j.parent();
        }
        String alg = j != null ? j.stringValue("alg") : manifest.getClaim().getHashAlgorithm();
        MessageDigest digest;
        try  {
            digest = MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("hash alg \"" + alg + "\" not found [algorithm.unsupported]", e);
        }
        // Hash is over content, not including the header!
        if (true) {
            // "When creating a URI reference to an assertion (i.e., as part of
            //  constructing a Claim), a W3C Verifiable Credential or other C2PA
            //  structure stored as a JUMBF box, the hash shall be performed
            //  over the contents of the structure’s JUMBF superbox, which
            //  includes both the JUMBF Description Box and all content boxes
            //  therein (but does not include the structure’s JUMBF superbox
            //  header).
            //    https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_hashing_jumbf_boxes
            for (Box b=box.first();b!=null;b=b.next()) {
                digest.update(b.getEncoded());
            }
        }
        byte[] digestbytes = digest.digest();
        if (hasheduri.isBuffer("hash")) {
            byte[] olddigestbytes = hasheduri.bufferValue("hash").array();
            if (!Arrays.equals(digestbytes, olddigestbytes)) {
                debugMismatch(box);
                String code = ingredient ? "ingredient.hashedURI.mismatch" : "assertion.hashedURI.mismatch";
                throw new IllegalStateException("hash mismatch for \"" + box.label() + "\" [" + code + "]");
            }
        }
        hasheduri.put("hash", digestbytes);
    }

    // Boxes differ, debug details of why as best we can
    // Will only do anything if "box.debugReadBytes" is not null
    // and that only happens if factory.debug was true
    private static boolean debugMismatch(Box box) {
        try {
            for (Box b=box.first();b!=null;b=b.next()) {
                if (debugMismatch(b)) {
                    return true;
                }
            }
            byte[] oldbytes = box.debugReadBytes;
            byte[] newbytes = box.getEncoded();
            if (oldbytes != null && !Arrays.equals(oldbytes, newbytes)) {
                Box oldbox = new BoxFactory().load(new ByteArrayInputStream(oldbytes));
                Box newbox = new BoxFactory().load(new ByteArrayInputStream(newbytes));
                String oldstring = oldbox.toString();
                String newstring = newbox.toString();
                if (newbox.equals(oldbox)) {
                    oldstring = oldbox.dump(null, null).toString();
                    newstring = newbox.dump(null, null).toString();
                    if (newbox.equals(oldbox)) {
                        oldstring = hex(oldbytes);
                        newstring = hex(newbytes);
                    }
                }
                System.out.println("MISMATCH old=" + oldstring);
                System.out.println("MISMATCH new=" + newstring);
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

// TODO
// * add hard bindings (datahash)
// * require certs in key
// * look at timestamps; sigTst?
// * look at update manifests
// * add credential store: https://c2pa.org/specifications/specifications/1.0/specs/C2PA_Specification.html#_credential_storage
