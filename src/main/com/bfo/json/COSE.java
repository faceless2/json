package com.bfo.json;

import java.util.*;
import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.interfaces.*;
import java.security.spec.*;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * A basic COSE class. Currently supports COSE signatures only, not encryption.
 *
 * <h2>Examples</h2>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * COSE cose = new COSE();
 * cose.setPayload(ByteBuffer.wrap("Hello, World".getBytes("UTF-8")));
 * cose.sign(privateKey);               // Sign using a private key, eg ES256
 * cose.writeCBOR(..., null);           // Save COSE
 *
 * json = Json.readCBOR(..., null);     // Reload COSE
 * cose = new COSE(json);
 * String s = new String(cose.getPayload().array(), "UTF-8"); // Hello, World
 * assert jwt.verify(publicKey) == 0;   // Verify with the public key
 * </pre>
 * @since 5
 */
public class COSE extends Json {
    // https://datatracker.ietf.org/doc/html/rfc9052    replaced 8152

    private static final int TAG_COSE_SIGN              = 98;
    private static final int TAG_COSE_SIGN1             = 18;
    private static final int TAG_COSE_COUNTERSIG        = 19;

    private static final int HEADER_ALG         = 1;
    private static final int HEADER_CRIT        = 2;
    private static final int HEADER_CONTENTTYPE = 3;
    private static final int HEADER_KID         = 4;
    private static final int HEADER_IV          = 5;
    private static final int HEADER_PARTIALIV   = 6;
    private static final int HEADER_COUNTERSIG  = 7;
    private static final int HEADER_COUNTERSIG2 = 11;   // https://datatracker.ietf.org/doc/html/rfc9338
    private static final int HEADER_COUNTERSIG02= 12;   // https://datatracker.ietf.org/doc/html/rfc9338

    private static final int ALGORITHM_ES256    = -7;
    private static final int ALGORITHM_ES384    = -35;
    private static final int ALGORITHM_ES512    = -36;
    private static final int ALGORITHM_EDDSA    = -8;
    private static final int ALGORITHM_HMAC256_64 = 4;
    private static final int ALGORITHM_HMAC256  = 5;
    private static final int ALGORITHM_HMAC384  = 6;
    private static final int ALGORITHM_HMAC512  = 7;
    private static final int ALGORITHM_PS256    = -37;  // This is RSASSA-PSS w SHA256 - https://datatracker.ietf.org/doc/rfc8230/
    private static final int ALGORITHM_PS384    = -38;  // This is RSASSA-PSS w SHA384 - https://datatracker.ietf.org/doc/rfc8230/
    private static final int ALGORITHM_PS512    = -39;  // This is RSASSA-PSS w SHA512 - https://datatracker.ietf.org/doc/rfc8230/

    private ByteBuffer payload;
    private boolean detached;
    private Json unprotectedAtts, protectedAtts, externalProtectedAtts;
    private List<X509Certificate> certs;
    private Provider provider;

    /**
     * Create a new, uninitialized COSE object
     */
    public COSE() {
        super(Collections.<Json>emptyList());
    }

    /**
     * Create a new COSE from one that has already been read, eg
     * <code>COSE cose = new COSE(Json.readCbor(..., null))</code>
     * @param cose the object
     */
    public COSE(Json cose) {
        super(cose);
    }

    /**
     * Set the Provider to be used for any cryptographic operations
     * @param provider the crypto Provider to use, or null to use the default
     * @return this
     */
    public COSE setProvider(Provider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Return the Provider set by {@link #setProvider}
     * @return the provider
     */
    public Provider getProvider() {
        return provider;
    }

    /**
     * Return true if this COSE object is initialized.
     * Initialized objects are typically passed into the constructor,
     * or created by a call to {@link #sign}
     * @return true if this object is initialized
     */
    public boolean isInitialized() {
        return !isEmpty();
    }

    /**
     * Get the payload from this COSE object.
     * @throws IllegalStateException if the COSE object has not been set or the payload has not been set
     * @return the payload (the original, not a clone)
     */
    public ByteBuffer getPayload() {
        if (payload != null) {
            return payload;
        } else if (isInitialized()) {
            return this.isNull(2) ? null : this.get(2).bufferValue();
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }

    /**
     * Return true if the payload was set to "detached".
     * @return if the payload is detached
     */
    public boolean isDetached() {
        return (isInitialized() && this.isNull(2)) || detached;
    }

    /**
     * Set the payload to sign. When verifying an existing COSE object where {@link #isDetached} is true,
     * this method must be called <i>before verify</i> to set the payload to verify.
     * @param payload the payload
     * @param detached if true, the payload will be "detached" - it will not be stored in the COSE structure. If false, it will be embedded.
     * @return this
     * @throws IllegalStateException if the COSE object has already been set
     */
    public COSE setPayload(ByteBuffer payload, boolean detached) {
        if (detached && (!isInitialized() || isDetached())) {
            this.payload = payload;
            this.detached = true;
        } else if (isInitialized()) {
            throw new IllegalStateException("Already initialized");
        } else {
            this.payload = payload;
            this.detached = false;
        }
        return this;
    }

    /**
     * Return the unprotected attributes, which may be null
     * @return the attributes
     */
    public Json getUnprotectedAttributes() {
        if (isInitialized()) {
            Json j = this.get(1);
            return j.isEmpty() ? null : j;
        } else {
            return unprotectedAtts;
        }
    }

    /**
     * Set the unprotected attributes on this object.
     * This can be called any time.
     * Calling this method will reset the list of certificates returned from {@link #getCertificates}
     * @param atts the attributes, or null
     * @return this
     */
    public COSE setUnprotectedAttributes(Json atts) {
        if (isInitialized()) {
            this.put(1, atts);
        } else {
            unprotectedAtts = atts;
        }
        this.certs = null;
        return this;
    }

    /**
     * Return the protected attributes, which may be null
     * @return the attributes
     */
    public Json getProtectedAttributes() {
        if (isInitialized()) {
            try {
                Json j = Json.readCbor(this.get(0).bufferValue().position(0), null);
                return j.isEmpty() ? null : j;
            } catch (Exception e) {
                 return null;
            }
        } else {
            return protectedAtts;
        }
    }

    /**
     * Set the protected attributes on this object.
     * @param atts the attributes, or null
     * @return this
     * @throws IllegalStateException if the COSE object has already been set
     */
    public COSE setProtectedAttributes(Json atts) {
        if (isInitialized()) {
            throw new IllegalStateException("Already initialized");
        } else {
            protectedAtts = atts;
        }
        return this;
    }

    /**
     * Return the external protected attributes, as set by {@link #setExternalProtectedAttributes}
     * @return the attributes
     */
    public Json getExternalProtectedAttributes() {
        return externalProtectedAtts;
    }

    /**
     * Set the external protected attributes on this object, which may be null.
     * This can be called any time.
     * @param atts the attributes, or null
     * @return this
     */
    public COSE setExternalProtectedAttributes(Json atts) {
        externalProtectedAtts = atts;
        return this;
    }

    /**
     * Return the Signature algorithm used for the specified signature - 0 for the first signature, 1 for the second etc.
     * @param signature the index of the signature to query, from 0 to {@link #getNumSignatures}-1
     * @return the Signature algorithm
     * @throws IllegalArgumentException if the specified signature is out of range
     * @throws IllegalStateException if the COSE object has not been set
     */
    public String getAlgorithm(int signature) {
        int alg;
        if (isInitialized()) {
            final boolean single = this.getTag() == TAG_COSE_SIGN1;
            Json j = null;
            if (single) {
                if (signature == 0) {
                    try {
                        j = Json.readCbor(this.get(0).bufferValue().position(0), null);
                    } catch (IOException e) {}
                    alg = j != null && j.isNumber(1) ? j.intValue(1) : 0;
                } else {
                    throw new IllegalArgumentException("Invalid signature index " + signature + ": single signature");
                }
            } else {
                Json sigs = this.get(3);    // array of COSE_Signature (SIGN) or single signature (SIGN1)
                if (signature >= 0 && signature < sigs.size()) {
                    try {
                        j = Json.readCbor(sigs.get(signature).get(0).bufferValue().position(0), null);
                    } catch (IOException e) {}
                    alg = j != null && j.isNumber(1) ? j.intValue(1) : 0;
                } else {
                    throw new IllegalArgumentException("Invalid signature index " + signature + ": not between 0.." + (sigs.size() - 1));
                }
            }
        } else {
            throw new IllegalStateException("Not initialized");
        }
        return alg == 0 ? null : JWK.fromCOSEAlgorithm(new Json(alg)).stringValue();
    }

    /**
     * Return the number of Sigantures on this COSE object
     * @return the number of signatures
     * @throws IllegalStateException if the COSE object has not been set
     */
    public int getNumSignatures() {
        if (isInitialized()) {
            final boolean single = this.getTag() == TAG_COSE_SIGN1;
            return single ? 1 : this.get(3).size();
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }

    /**
     * If the COSE object contains X.509 Certificates in the header, as defined by 
     * <a href="https://datatracker.ietf.org/doc/html/rfc9360">RFC9360</a>, then
     * return the list. If the certificate is referenced by URL, it will be downloaded.
     * RFC9630 implies the certificates can be stored as protected or unprotected attributes,
     * or even a combination. This method will check all possibilities and combine into
     * a single list.
     * The return value will be cached until reset (by calling either {@link #setCertificates setCertificates} or {@link #setUnprotectedAttributes}.
     * @return the list of certificates, or an empty list if none are specified.
     */
    public List<X509Certificate> getCertificates() {
        if (certs == null) {
            Json protatts = getProtectedAttributes();
            Json unprotatts = getUnprotectedAttributes();
            Json list = null;
            list = list != null ? list : protatts != null ? protatts.get(33) : null;       // x5chain
            list = list != null ? list : protatts != null ? protatts.get(32) : null;       // x5bag
            list = list != null ? list : protatts != null ? protatts.get("x5chain") : null;       // x5chain
            list = list != null ? list : protatts != null ? protatts.get("x5bag") : null;       // x5bag
            if (list != null) {
                certs = JWK.extractCertificates(list);
            }
            list = list != null ? list : unprotatts != null ? unprotatts.get(33) : null;       // x5chain
            list = list != null ? list : unprotatts != null ? unprotatts.get(32) : null;       // x5bag
            list = list != null ? list : unprotatts != null ? unprotatts.get("x5chain") : null;       // x5chain
            list = list != null ? list : unprotatts != null ? unprotatts.get("x5bag") : null;       // x5bag
            if (list != null) {
                if (certs != null) {
                    certs = new ArrayList<X509Certificate>();
                    certs.addAll(JWK.extractCertificates(list));
                } else {
                    certs = JWK.extractCertificates(list);
                }
            }
            if (certs == null) {
                Json url = null, sha256 = null;
                url = url != null ? url : protatts != null ? protatts.get(35) : null;       // x5u
                url = url != null ? url : protatts != null ? protatts.get("x5u") : null;
                url = url != null ? url : unprotatts != null ? unprotatts.get(35) : null;
                url = url != null ? url : unprotatts != null ? unprotatts.get("x5u") : null;
                sha256 = sha256 != null ? sha256 : protatts != null ? protatts.get(34) : null;       // x5t
                sha256 = sha256 != null ? sha256 : protatts != null ? protatts.get("x5t") : null;       // x5t
                sha256 = sha256 != null ? sha256 : unprotatts != null ? unprotatts.get(34) : null;       // x5t
                sha256 = sha256 != null ? sha256 : unprotatts != null ? unprotatts.get("x5t") : null;       // x5t
                if (url != null && url.isString()) {
                    try {
                        certs = JWK.downloadCertificates(url, sha256, null);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed downloading certificate from \"" + url.stringValue() + "\"", e);
                    }
                }
            }
            if (certs == null) {
                certs = Collections.<X509Certificate>emptyList();
            }
            certs = Collections.<X509Certificate>unmodifiableList(certs);
        }
        return certs;
    }

    /**
     * Set a list of X.509 Certificates to be included in the COSE header, or null.
     * Certificates will be merged into the unprotected attributes on save; for more control,
     * they can be set manually in the protected/unproteected attribute list.
     * This can be called any time.
     * Calling this method will reset the list of certificates returned from {@link #getCertificates}
     * @param certs the list of X.509 certificates, or null
     * @return this
     */
    public COSE setCertificates(List<X509Certificate> certs) {
        this.certs = certs;
        return this;
    }

    /**
     * Verify a COSE that is {@link #isInitialized initialized}
     * @param key the key. Should be a {@link PublicKey}
     * @return if the COSE is verified, return which Key in the COSE structure this is - 0 for the first key, 1 for the second etc. If it doesn't verify, return -1
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying.
     * @throws IllegalStateException if the COSE object has not been set
     */
    public int verify(PublicKey key) {
        try {
            if (!isInitialized()) {
                throw new IllegalStateException("Not initialized");
            } else if (this.getTag() == TAG_COSE_SIGN || this.getTag() == TAG_COSE_SIGN1) {
                final boolean single = this.getTag() == TAG_COSE_SIGN1;
                Json sigs = this.get(3);    // array of COSE_Signature (SIGN) or single signature (SIGN1)
                for (int i=0;i<(single?1:sigs.size());i++) {
                    ByteBuffer protectedAtts = this.get(0).bufferValue();
                    ByteBuffer sigProtectedAtts = single ? protectedAtts : sigs.get(i).get(0).bufferValue();
                    ByteBuffer signature = single ? this.get(3).bufferValue() : sigs.get(i).get(2).bufferValue();
                    ByteBuffer payload = getPayload();;
                    if (payload == null) {
                        throw new IllegalStateException("Payload is detached, must call {@link #setPayload setPayload(payload, true)} before verifying");
                    }
                    String type = single ? "Signature1" : "Signature";
                    if (verifySignature(type, protectedAtts, sigProtectedAtts, externalProtectedAtts, payload, signature, (PublicKey)key, provider)) {
                        return i;
                    }

                }
            } else {
                throw new IllegalStateException("Not a signed type (" + this.getTag() + ")");
            }
            return -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param type one of Signature, Signature1 or CounterSignature
     * @param prot the main protected attributes
     * @param sigProtectedAtts the signature protected attributes (for Signature1, sigProtectedAtts == prot)
     * @param appprot the external protected attributes, or null
     * @param payload the payload
     * @param signature the siganture bytes
     * @param key the key to verify
     * @param provider the Provider
     */
    private static boolean verifySignature(String type, ByteBuffer protectedAtts, ByteBuffer sigProtectedAtts, Json externalProtectedAtts, ByteBuffer payload, ByteBuffer signature, PublicKey key, Provider provider) throws IOException {
        Json protectedAttsMap = Json.readCbor(sigProtectedAtts.position(0), null);
        String alg = JWK.fromCOSEAlgorithm(protectedAttsMap.get(1)).stringValue();    // "alg" is key 1 in header. This gives us (eg) "ES256"
        Signature sig ;
        try {
            sig = JWK.createSignature(alg, key, provider);
            sig.initVerify(key);
        } catch (Exception e) {
            sig = null;
        }
        if (sig != null) {
            Json sigStructure = Json.read("[]");
            int j = 0;
            sigStructure.put(j++, type);
            sigStructure.put(j++, new Json(protectedAtts));           // Protected attributes
            if (protectedAtts != sigProtectedAtts) {
                sigStructure.put(j++, new Json(sigProtectedAtts));    // Signer protected attributes
            }
            sigStructure.put(j++, new Json(externalProtectedAtts == null ? ByteBuffer.wrap(new byte[0]) : externalProtectedAtts.toCbor()));
            sigStructure.put(j++, new Json(payload));           // payload
            byte[] tbs = sigStructure.toCbor().array();
            byte[] signatureBytes = signature.array();
            if (key.getAlgorithm().equals("EC")) {
                signatureBytes = JWT.cat2der(signatureBytes);
            }
            try {
                sig.update(tbs);
                return sig.verify(signatureBytes);
            } catch (Exception e) { 
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Sign the COSE. The payload must have been set with {@link #setPayload}.
     * @param key the key - should be a {@link PrivateKey}
     * @param algorithm the algorithm name, or null to use the recommended algorithm for that Key type.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     * @throws IllegalStateException if the COSE object has already set or the payload has not been set
     * @return this
     */
    public COSE sign(Key key, String algorithm) {
        return sign(Collections.<Key,String>singletonMap(key, algorithm));
    }

    /**
     * Sign the COSE with multiple signatures. The payload must have been set with {@link #setPayload}.
     * @param keysAndAlgorithms a Map of [key,algorithm name] to sign with. Currently anything other than Private keys will be ignored. Algorithms may be null, if they are the recommended algorithm for each Key type will be used.
     * @throws IllegalStateException if the COSE object has already set or the payload has not been set
     * @throws IllegalArgumentException if no suitable Keys are supplied
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     * @return this
     */
    public COSE sign(Map<Key,String> keysAndAlgorithms) {
        if (isInitialized()) {
            while (size() > 0) {
                remove(size() - 1);
            }
        }
        if (payload == null) {
            throw new IllegalStateException("No payload");
        } else {
            Map<PrivateKey,String> keys = new LinkedHashMap<PrivateKey,String>();
            for (Iterator<Map.Entry<Key,String>> i=keysAndAlgorithms.entrySet().iterator();i.hasNext();) {
                Map.Entry<Key,String> e = i.next();
                if (e.getKey() instanceof PrivateKey) {
                    PrivateKey key = (PrivateKey)e.getKey();
                    String alg = e.getValue();
                    if (alg == null) {
                        alg = new JWK(key).getAlgorithm();
                    }
                    if (alg != null && !alg.startsWith("RS")) { // We can't handle RSnnn algorithm types
                        keys.put(key, alg);
                    }
                }
            }
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("No suitable keys");
            }
            if (protectedAtts == null) {
                protectedAtts = Json.read("{}");
            }
            if (unprotectedAtts == null) {
                unprotectedAtts = Json.read("{}");
            }
            if (certs != null && !certs.isEmpty()) {
                Json j = Json.read("[]");
                for (X509Certificate cert : certs) {
                    try {
                        j.put(j.size(), cert.getEncoded());
                    } catch (CertificateEncodingException e) {
                        throw new RuntimeException(e);  // doubt we'll see this
                    }
                }
                // Note that 20230204 the state of play is: use "x5chain" in unprotected.
                // With RFC9360 and the upcoming C2PA draft, it will be: use 33 in protected
                // -- watch https://github.com/contentauth/c2pa-rs/issues/189
                // protectedAtts.put(33, j);              // x5chain
                unprotectedAtts.put("x5chain", j)
            }
            if (keys.size() == 1) {
                PrivateKey key = keys.keySet().iterator().next();
                String algorithm = keys.get(key);
                JWK jwk = new JWK(key);
                jwk.put("alg", algorithm);
                protectedAtts.put(1, jwk.toCOSEKey().get(3));   // Set "alg" (key 1 in header) based on "alg" (key 3 in COSE key). Copy don't move!
                byte[] signature = signSignature("Signature1", protectedAtts, protectedAtts, externalProtectedAtts, payload, key, algorithm, provider);
                this.setValue(Json.read("[]"));
                this.put(0, new Json(protectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : protectedAtts.toCbor()));
                this.put(1, unprotectedAtts);
                this.put(2, new Json(detached ? null : payload));
                this.put(3, new Json(signature));
                this.setTag(TAG_COSE_SIGN1);
            } else {
                Json sigs = Json.read("[]");
                for (PrivateKey key : keys.keySet()) {
                    String algorithm = keys.get(key);
                    JWK jwk = new JWK(key);
                    jwk.put("alg", algorithm);
                    Json sigProtectedAtts = Json.read("{}");
                    Json sigUnprotectedAtts = Json.read("{}");      // TODO?
                    sigProtectedAtts.put(1, jwk.toCOSEKey().get(3));   // Set "alg" (key 1 in header) based on "alg" (key 3 in COSE key)
                    byte[] signature = signSignature("Signature", protectedAtts, sigProtectedAtts, externalProtectedAtts, payload, key, algorithm, provider);
                    Json sig = Json.read("[]");
                    sig.put(0, new Json(sigProtectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : sigProtectedAtts.toCbor()));
                    sig.put(1, sigUnprotectedAtts);
                    sig.put(2, new Json(signature));
                    sigs.put(sigs.size(), sig);
                }
                this.setValue(Json.read("[]"));
                this.put(0, new Json(protectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : protectedAtts.toCbor()));
                this.put(1, unprotectedAtts);
                this.put(2, new Json(detached ? null : payload));
                this.put(3, sigs);
                this.setTag(TAG_COSE_SIGN);
            }
            unprotectedAtts = null;
            protectedAtts = null;
            payload = null;
        }
        return this;
    }

    private static byte[] signSignature(String type, Json protectedAtts, Json sigProtectedAtts, Json externalProtectedAtts, ByteBuffer payload, PrivateKey key, String algorithm, Provider provider) {
        if (sigProtectedAtts == null) {
            sigProtectedAtts = protectedAtts;
        }
        try {
            if (algorithm == null) {
                algorithm = new JWK(key).getAlgorithm();
                if (algorithm == null) {
                    throw new IllegalStateException("Cannot determine algorithm from key");
                }
            }
            if (algorithm.startsWith("RS")) {
                // Tried converting RS256 to PS256 but no dice.
                throw new IllegalStateException("Algorithm \"" + algorithm + "\" cannot be used with COSE");
            }
            Signature sig = JWK.createSignature(algorithm, key, provider);
            sig.initSign(key);
            Json sigStructure = Json.read("[]");
            int j = 0;
            sigStructure.put(j++, type);
            sigStructure.put(j++, new Json(protectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : protectedAtts.toCbor()));
            if (protectedAtts != sigProtectedAtts) {
                sigStructure.put(j++, new Json(sigProtectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : sigProtectedAtts.toCbor()));
            }
            sigStructure.put(j++, new Json(externalProtectedAtts == null || externalProtectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : externalProtectedAtts.toCbor()));
            sigStructure.put(j++, new Json(payload));           // payload
            byte[] tbs = sigStructure.toCbor().array();
            // System.out.println("sigS="+sigStructure+ " sigProtectedAtts="+sigProtectedAtts+" TBS="+JWT.hex(tbs));
            sig.update(tbs);
            byte[] signatureBytes = sig.sign();
            if (key.getAlgorithm().equals("EC")) {
                String s = sig.getAlgorithm();
                int keylen = 0;
                if (s.startsWith("SHA256")) {
                    keylen = 32;
                } else if (s.startsWith("SHA384")) {
                    keylen = 48;
                } else if (s.startsWith("SHA512")) {
                    keylen = 66;    // Not 64, 66
                } else {
                    throw new IllegalStateException("Bad EC alg " + sig.getAlgorithm());
                }
                signatureBytes = JWT.der2cat(signatureBytes, keylen);
            }
            return signatureBytes;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
