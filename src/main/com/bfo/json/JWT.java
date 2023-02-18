package com.bfo.json;

import java.util.*;
import java.io.*;
import java.math.*;
import java.net.*;
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
 * A trivial Java Web Token class.
 * Supports all standard JWT algorithms.
 *
 * <h2>Examples</h2>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * JWT jwt = new JWT(Json.parse("{....}"));
 * jwt.sign(key, "HS256");           // Sign using a symmetric key
 * jwt = new JWT(jwt.toString());    // Encode then decode
 * assert jwt.verify(key, null);     // Verify using the same symmetric key
 *
 * byte[] pubkey = ...
 * byte[] prikey = ...
 * jwt.getHeader().put("x5u", ...);       // Add custom content to header
 * jwt.sign(prikey, "ES256");             // Sign using a asymmetric key
 * assert jwt.verify(pubkey, "ES256");    // Verify using corresponding key
 *
 * jwt.getPayload().clear();              // Modify the payload
 * assert !jwt.verify(pubkey, "ES256");   // Signature is no longer valid
 *
 * System.out.println(jwt.getPayload());
 * System.out.println(jwt.getAlgorithm());
 * </pre>
 * @since 4
 */
public class JWT {

    private final Json header, payload;
    private byte[] signature;
    private Provider provider;

    /**
     * Create a new JWT with no payload and the "none" algorithm.
     */
    public JWT() {
        this(Json.read("{}"));
    }

    /**
     * Create a new JWT with the specified payload and the "none" algorithm.
     * @param payload the payload object to embed in the JWT
     */
    public JWT(Json payload) {
        this.payload = payload;
        this.header = Json.read("{}");
        header.put("typ", "JWT");
        header.put("alg", "none");
        this.signature = new byte[0];
    }

    /**
     * Create a new JWT from the encoded representation
     * @param in the encoded JWT
     * @throws IllegalArgumentException if the string is not a valid JWT
     */
    public JWT(CharSequence in) {
        String s = in.toString();
        int i = s.indexOf('.');
        if (i < 0) {
            throw new IllegalArgumentException("No header, not a JWT");
        }
        int j = s.indexOf('.', i + 1);
        if (j < 0) {
            j = s.length();
        }
        header = Json.read(new String(base64decode(s.substring(0, i)), StandardCharsets.UTF_8));
        payload = Json.read(new String(base64decode(s.substring(i + 1, j)), StandardCharsets.UTF_8));
        signature = j == s.length() ? new byte[0] : base64decode(s.substring(j + 1, s.length()));
    }

    /**
     * Set the Provider to be used for any cryptographic operations
     * @param provider the crypto Provider to use, or null to use the default
     * @return this
     */
    public JWT setProvider(Provider provider) {
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
     * Return the encoded JWT
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(base64encode(header.toString()));
        sb.append(".");
        sb.append(base64encode(payload.toString()));
        sb.append(".");
        if (signature.length > 0) {
            sb.append(base64encode(signature));
        }
        return sb.toString();
    }

    /**
     * Verify the JWT.
     * @param key the key. A raw HMAC key, or DER/PEM encoded public key. Missing keys or keys of the wrong type will cause this method to return false.
     * @param alg the algorithm name. If <code>null</code> it will default to {@link #getAlgorithm}. <b>Should always be specified</b> to avoid security risks.
     * @return true if the JWT is verified, false if it failed to verify.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying or if the key failed to decode.
     */
    public boolean verify(byte[] key, String alg) {
        try {
            return verify(JWK.toKey(key, alg != null ? alg : getAlgorithm(), true, provider), alg);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verify the JWT.
     * @param key the key. A {@link SecretKey}, {@link PublicKey}, or null if the algorithm is "none". Missing keys or keys of the wrong type will cause this method to return false; specifically, if the algorithm is "none" the key <i>must</i> be null.
     * @param alg the algorithm name. If <code>null</code> it will default to {@link #getAlgorithm}. <b>Should always be specified</b> to avoid security risks.
     * @return true if the JWT is verified, false if it failed to verify.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying.
     */
    public boolean verify(Key key, String alg) {
        String typ = header.isString("typ") ? header.get("typ").stringValue() : null;
        if ("JWT".equalsIgnoreCase(typ)) {
            if (alg == null) {
                alg = getAlgorithm();
                if (alg == null) {
                    throw new NullPointerException("Algorithm is null");
                }
            }
            byte[] data = ((base64encode(header.toString()) + "." + base64encode(payload.toString())).getBytes(StandardCharsets.UTF_8));
            try {
                if (alg.equals("none")) {
                    return key == null && signature.length == 0;
                } else if (key instanceof SecretKey) {
                    // Symmetric
                    Mac m = JWK.createMac(alg, key, provider);
                    m.init((SecretKey)key);
                    return Arrays.equals(signature, m.doFinal(data));
                } else if (key instanceof PublicKey) {
                    // Asymmetric
                    Signature sig = JWK.createSignature(alg, key, provider);
                    sig.initVerify((PublicKey)key);
                    sig.update(data);
                    byte[] signature = this.signature;
                    if (alg.startsWith("ES")) {
                        signature = cat2der(signature);
                    }
                    return sig.verify(signature);
                }
                return false;
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Unsupported typ \"" + typ + "\"");
        }
    }

    /**
     * Sign the JWT. Sets the "alg" key in the header and updates the signature.
     * @param key the key. A raw HMAC key, a DER/PEM encoded private key, or null if the algorithm is "none". Required.
     * @param alg the algorithm name. Required.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing or if the key failed to decode.
     * @return this
     */
    public JWT sign(byte[] key, String alg) {
        try {
            return sign(JWK.toKey(key, alg, false, provider), alg);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sign the JWT. Sets the "alg" key in the header and updates the signature.
     * @param key the key. A {@link SecretKey} or {@link PrivateKey}, or null if the algorithm is "none". Required.
     * @param alg the algorithm name. Required.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     * @return this
     */
    public JWT sign(Key key, String alg) {
        if (alg == null) {
            alg = new JWK(key).getAlgorithm();
            if (alg == null) {
                throw new IllegalArgumentException("algorithm not specified and cannot derive from " + new JWK(key));
            }
        }
        Json newheader = Json.read(header.toString());
        newheader.put("alg", alg);
        byte[] data = ((base64encode(newheader.toString()) + "." + base64encode(payload.toString())).getBytes(StandardCharsets.UTF_8));
        byte[] signature = null;
        try {
            if (alg.equals("none")) {
                signature = new byte[0];
            } else if (key instanceof SecretKey) {
                // Symmetric
                Mac m = JWK.createMac(alg, key, provider);
                m.init((SecretKey)key);
                signature = m.doFinal(data);
            } else if (key instanceof PrivateKey) {
                // Asymmetric
                Signature sig = JWK.createSignature(alg, key, provider);
                sig.initSign((PrivateKey)key);
                sig.update(data);
                signature = sig.sign();
                if (alg.equals("ES256")) {
                    signature = der2cat(signature, 32);
                } else if (alg.equals("ES384")) {
                    signature = der2cat(signature, 48);
                } else if (alg.equals("ES512")) {
                    signature = der2cat(signature, 66);     // 66 not 64
                }
            }
            header.put("alg", alg);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        if (signature == null) {
            throw new IllegalStateException("Missing or incorrect key for alg \"" + alg + "\"");
        }
        this.signature = signature;
        return this;
    }

    /**
     * Return the algorithm name.
     * @return the algorithm name
     */
    public String getAlgorithm() {
        return header.isString("alg") ? header.get("alg").stringValue() : null;
    }

    /**
     * Return the payload object. The {@link #sign} method should be
     * called after any modifications to the returned object to update the signature.
     * @return the payload object
     */
    public Json getPayload() {
        return payload;
    }

    /**
     * Return the header object. The {@link #sign} method should be
     * called after any modifications to the returned object to update the signature.
     * @return the header object
     */
    public Json getHeader() {
        return header;
    }

    /**
     * Return the signature object. Any modifications to the returned object will
     * invalidate the signature.
     * @return the signature bytes, which will be zero-length if the algorithm is "none"
     */
    public byte[] getSignature() {
        return signature;
    }

    static byte[] base64decode(String in) {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        return decoder.decode(in);
    }

    static String base64encode(String in) {
        return base64encode(in.getBytes(StandardCharsets.UTF_8));
    }

    static String base64encode(byte[] in) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(in);
    }

    static String hex(byte[] in) {
        char[] c = new char[in.length * 2];
        for (int i=0;i<in.length;i++) {
            int v = in[i] & 0xFF;
            int q = v >> 4;
            c[i*2] = (char)(q < 10 ? q + '0' : q + 'A' - 10);
            q = v & 0xF;
            c[i*2+1] = (char)(q < 10 ? q + '0' : q + 'A' - 10);
        }
        return new String(c);
    }

    // Convert raw P1363 encoding of two integers to DER
    static byte[] cat2der(byte[] in) {
        byte[] b = new byte[in.length / 2];
        System.arraycopy(in, 0, b, 0, b.length);
        BigInteger b1 = new BigInteger(1, b);
        System.arraycopy(in, b.length, b, 0, b.length);
        BigInteger b2 = new BigInteger(1, b);
//        System.out.println("CAT2DER: in="+hex(in)+" out="+b1.toString(16)+" "+b2.toString(16));
        byte[] r = b1.toByteArray();
        byte[] s = b2.toByteArray();
        int l = r.length + s.length + 4;
        byte[] out = new byte[(l < 128 ? 2 : 3) + l];
        int i = 0;
        out[i++] = (byte)0x30;   // Constructed sequence
        if (l >= 128) {
            out[i++] = (byte)0x81;
        }
        out[i++] = (byte)l;
        out[i++] = 2;   // Integer
        out[i++] = (byte)r.length;
        System.arraycopy(r, 0, out, i, r.length);
        i += r.length;
        out[i++] = 2;   // Integer
        out[i++] = (byte)s.length;
        System.arraycopy(s, 0, out, i, s.length);
        i += s.length;
        return out;
    }

    // Convert DER to two raw P1363 encoding of two integers
    static byte[] der2cat(byte[] in, int keylen) {
        byte[] out = new byte[keylen * 2];
        int i = in[1] == (byte)0x81 ? 5 : 4;
        int l = in[i - 1] & 0xff;
        int j = i + l + 2;
        System.arraycopy(in, l > keylen ? i + 1 : i, out, keylen - Math.min(keylen, l), Math.min(keylen, l));
        i += l + 2;
        l = in[i - 1] & 0xff;
        System.arraycopy(in, l > keylen ? i + 1 : i, out, keylen + keylen - Math.min(keylen, l), Math.min(keylen, l));
//        System.out.println("DER2CAT: in="+in.length+"/"+keylen+" out="+new BigInteger(1, out, 0, out.length / 2) + " " + new BigInteger(1, out, out.length/2, out.length/2));
        return out;
    }

}
