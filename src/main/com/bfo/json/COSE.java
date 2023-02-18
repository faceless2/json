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
 * Json json = cose.get();
 * json.writeCBOR(..., null);           // Save COSE
 *
 * json = Json.readCBOR(..., null);     // Reload COSE
 * cose = new COSE(json);
 * String s = new String(cose.getPayload().array(), "UTF-8"); // Hello, World
 * assert jwt.verify(publicKey) == 0;   // Verify with the public key
 * </pre>
 * @since 5
 */
public class COSE {
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

    private Json cose;
    private ByteBuffer payload;
    private Json unprotectedAtts, protectedAtts, applicationProtectedAtts;
    private Provider provider;

    /**
     * Create a new COSE
     */
    public COSE() {
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
     * Return the COSE object as a Json.
     * If it has not been initialized, either by calling {@link #set} or by
     * calling the various other set methods then {@link #sign}, this will return null
     * @return the COSE object as a json
     */
    public Json get() {
        return cose;
    }

    /**
     * Set the COSE object, which can then be verified
     * @param cose the COSE object
     * @return this
     */
    public COSE set(Json cose) {
        this.cose = cose;
        return this;
    }

    /**
     * Get the payload from this COSE object.
     * @throws IllegalStateException if the COSE object has not been set or the payload has not been set
     * @return the payload (the original, not a clone)
     */
    public ByteBuffer getPayload() {
        if (payload != null) {
            return payload;
        } else if (cose != null) {
            return cose.get(2).bufferValue();
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }

    /**
     * Set the payload to sign.
     * @param payload the payload
     * @return this
     * @throws IllegalStateException if the COSE object has already been set
     */
    public COSE setPayload(ByteBuffer payload) {
        if (cose != null) {
            throw new IllegalStateException("Already initialized");
        } else {
            this.payload = payload;
        }
        return this;
    }

    /**
     * Return the unprotected attributes, which may be null
     * @return the attributes
     */
    public Json getUnprotectedAttributes() {
        if (cose != null) {
            Json j = cose.get(1);
            return j.isEmpty() ? null : j;
        } else {
            return unprotectedAtts;
        }
    }

    /**
     * Set the unprotected attributes on this object.
     * This can be called any time.
     * @param atts the attributes, or null
     * @return this
     */
    public COSE setUnprotectedAttributes(Json atts) {
        if (cose != null) {
            cose.put(1, atts);
        } else {
            unprotectedAtts = atts;
        }
        return this;
    }

    /**
     * Return the protected attributes, which may be null
     * @return the attributes
     */
    public Json getProtectedAttributes() {
        if (cose != null) {
            try {
                Json j = Json.readCbor(cose.get(0).bufferValue(), null);
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
        if (cose != null) {
            throw new IllegalStateException("Already initialized");
        } else {
            protectedAtts = atts;
        }
        return this;
    }

    /**
     * Return the application protected attributes, as set by {@link #setApplicationProtectedAttributes}
     * @return the attributes
     */
    public Json getApplicationProtectedAttributes() {
        return applicationProtectedAtts;
    }

    /**
     * Set the application protected attributes on this object, which may be null.
     * This can be called any time.
     * @param atts the attributes, or null
     * @return this
     */
    public COSE setApplicationProtectedAttributes(Json atts) {
        applicationProtectedAtts = atts;
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
        if (cose != null) {
            final boolean single = cose.getTag() == TAG_COSE_SIGN1;
            Json j = null;
            if (single) {
                if (signature == 0) {
                    try {
                        j = Json.readCbor(cose.get(0).bufferValue(), null);
                    } catch (IOException e) {}
                    alg = j != null && j.isNumber(1) ? j.intValue(1) : 0;
                } else {
                    throw new IllegalArgumentException("Invalid signature index " + signature + ": single signature");
                }
            } else {
                Json sigs = cose.get(3);    // array of COSE_Signature (SIGN) or single signature (SIGN1)
                if (signature >= 0 && signature < sigs.size()) {
                    try {
                        j = Json.readCbor(sigs.get(signature).get(0).bufferValue(), null);
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
        if (cose != null) {
            final boolean single = cose.getTag() == TAG_COSE_SIGN1;
            return single ? 1 : cose.get(3).size();
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }

    /**
     * Verify a COSE that has previously been set with {@link #set} or {@link #sign}
     * @param key the key. Should be a {@link PublicKey}
     * @return if the COSE is verified, return which Key in the COSE structure this is - 0 for the first key, 1 for the second etc. If it doesn't verify, return -1
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying.
     * @throws IllegalStateException if the COSE object has not been set
     */
    public int verify(PublicKey key) {
        try {
            if (cose == null) {
                throw new IllegalStateException("Not initialized");
            } else if (cose.getTag() == TAG_COSE_SIGN || cose.getTag() == TAG_COSE_SIGN1) {
                final boolean single = cose.getTag() == TAG_COSE_SIGN1;
                Json sigs = cose.get(3);    // array of COSE_Signature (SIGN) or single signature (SIGN1)
                for (int i=0;i<(single?1:sigs.size());i++) {
                    ByteBuffer protectedAtts = cose.get(0).bufferValue();
                    ByteBuffer sigProtectedAtts = single ? protectedAtts : sigs.get(i).get(0).bufferValue();
                    ByteBuffer signature = single ? cose.get(3).bufferValue() : sigs.get(i).get(2).bufferValue();
                    String type = single ? "Signature1" : "Signature";
                    if (verifySignature(type, protectedAtts, sigProtectedAtts, applicationProtectedAtts, cose.get(2).bufferValue(), signature, (PublicKey)key, provider)) {
                        return i;
                    }

                }
            } else {
                throw new IllegalStateException("Not a signed type (" + cose.getTag() + ")");
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
     * @param appprot the application protected attributes, or null
     * @param payload the payload
     * @param signature the siganture bytes
     * @param key the key to verify
     * @param provider the Provider
     */
    private static boolean verifySignature(String type, ByteBuffer protectedAtts, ByteBuffer sigProtectedAtts, Json applicationProtectedAtts, ByteBuffer payload, ByteBuffer signature, PublicKey key, Provider provider) throws IOException {
        Json protectedAttsMap = Json.readCbor(sigProtectedAtts, null);
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
            sigStructure.put(j++, new Json(applicationProtectedAtts == null ? ByteBuffer.wrap(new byte[0]) : applicationProtectedAtts.toCbor()));
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
        if (cose != null) {
            throw new IllegalStateException("Already initialized");
        } else if (payload == null) {
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
            if (keys.size() == 1) {
                PrivateKey key = keys.keySet().iterator().next();
                String algorithm = keys.get(key);
                JWK jwk = new JWK(key);
                jwk.put("alg", algorithm);
                protectedAtts.put(1, jwk.toCOSEKey().get(3));   // Set "alg" (key 1 in header) based on "alg" (key 3 in COSE key)
                byte[] signature = signSignature("Signature1", protectedAtts, protectedAtts, applicationProtectedAtts, payload, key, algorithm, provider);
                cose = Json.read("[]");
                cose.put(0, new Json(protectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : protectedAtts.toCbor()));
                cose.put(1, unprotectedAtts);
                cose.put(2, new Json(payload));
                cose.put(3, new Json(signature));
                cose.setTag(TAG_COSE_SIGN1);
            } else {
                Json sigs = Json.read("[]");
                for (PrivateKey key : keys.keySet()) {
                    String algorithm = keys.get(key);
                    JWK jwk = new JWK(key);
                    jwk.put("alg", algorithm);
                    Json sigProtectedAtts = Json.read("{}");
                    Json sigUnprotectedAtts = Json.read("{}");      // TODO?
                    sigProtectedAtts.put(1, jwk.toCOSEKey().get(3));   // Set "alg" (key 1 in header) based on "alg" (key 3 in COSE key)
                    byte[] signature = signSignature("Signature", protectedAtts, sigProtectedAtts, applicationProtectedAtts, payload, key, algorithm, provider);
                    Json sig = Json.read("[]");
                    sig.put(0, new Json(sigProtectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : sigProtectedAtts.toCbor()));
                    sig.put(1, sigUnprotectedAtts);
                    sig.put(2, new Json(signature));
                    sigs.put(sigs.size(), sig);
                }
                cose = Json.read("[]");
                cose.put(0, new Json(protectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : protectedAtts.toCbor()));
                cose.put(1, unprotectedAtts);
                cose.put(2, new Json(payload));
                cose.put(3, sigs);
                cose.setTag(TAG_COSE_SIGN);
            }
            unprotectedAtts = null;
            protectedAtts = null;
            payload = null;
        }
        return this;
    }

    private static byte[] signSignature(String type, Json protectedAtts, Json sigProtectedAtts, Json applicationProtectedAtts, ByteBuffer payload, PrivateKey key, String algorithm, Provider provider) {
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
            sigStructure.put(j++, new Json(applicationProtectedAtts == null || applicationProtectedAtts.isEmpty() ? ByteBuffer.wrap(new byte[0]) : applicationProtectedAtts.toCbor()));
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
