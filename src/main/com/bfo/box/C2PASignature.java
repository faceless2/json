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
        final List<JUMBox> assertions = claim.getAssertions();
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
        final List<JUMBox> assertions = claim.getAssertions();
        final String alg = claim.getHashAlgorithm();
        MessageDigest digest;
        try  {
            digest = MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        Json l = claim.cbor().get("assertions");
        for (int i=0;i<l.size();i++) {
            String url = l.get(i).stringValue("url");
            JUMBox box = assertions.get(i);
            digest.reset();
            // Hash is over content, not including the header!
            for (Box b=box.first();b!=null;b=b.next()) {
                digest.update(b.getEncoded());
            }
            if (box.getPad() != null) {
                digest.update(box.getPad());
            }
            byte[] digestbytes = digest.digest();
            l.get(i).put("hash", digestbytes);
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
     * Verify the claim.
     * @param key the public key; if null, it will be extracted (if possible) from any certificates in the signature
     * @throws IllegalArgumentException if no key is available to verify
     * @throws IllegalStateException if the signature is not signed or has been incorrectly set up
     * @return true if the supplied key verifies the signature, false otherwise
     */
    public boolean verify(PublicKey key) {
        final COSE cose = cose();
        final C2PAManifest manifest = (C2PAManifest)parent();
        final C2PAClaim claim = manifest.getClaim();
        if (!cose.isInitialized()) {
            throw new IllegalStateException("not signed");
        } else if (!cose.isDetached()) {
            throw new IllegalStateException("not detached");
        }
        if (key == null && cose.getCertificates() != null && !cose.getCertificates().isEmpty()) {
            key = cose.getCertificates().get(0).getPublicKey();
        } else if (key == null) {
            throw new IllegalArgumentException("no key supplied and no certificates included in the signature");
        }
        ByteBuffer payload = generatePayload(getMinSize());
        cose.setPayload(payload, true);
        return cose.verify(key) >= 0;
    }

}
