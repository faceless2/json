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
 * A trivial JWT (Json Web Token) implementation
 * Supports all signature algorithms supported by {@link JWK}.
 *
 * <h2>Examples</h2>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * JWT jwt = new JWT(Json.parse("{....}"));
 * SecretKey key = new JWK(bytearray, "HS256").getSecretKey();
 * jwt.sign(key);                    // Sign using a symmetric key
 * jwt = new JWT(jwt.toString());    // Encode then decode
 * assert jwt.verify(key);           // Verify using the same symmetric key
 *
 * PublicKey pubkey = ...
 * PrivateKey privkey = ...
 * jwt.getHeader().put("x5u", ...);       // Add custom content to header
 * jwt.sign(prikey);                      // Sign using a asymmetric key
 * assert jwt.verify(pubkey);             // Verify using corresponding key
 *
 * jwt.getPayload().clear();              // Modify the payload
 * assert !jwt.verify(pubkey);            // Signature is no longer valid
 *
 * assert jwt.isValidAt(jwt.getIssuedAt()); // check JWT time is not expired
 *
 * System.out.println(jwt.getPayload());
 * System.out.println(jwt.getAlgorithm());
 * </pre>
 * @since 4
 * @see COSE
 * @see JWK
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
     * @param key the key. A {@link SecretKey}, {@link PublicKey}, or null if the algorithm is "none". Missing keys or keys of the wrong type will cause this method to return false; specifically, if the algorithm is "none" the key <i>must</i> be null.
     * @return true if the JWT is verified, false if it failed to verify.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying.
     */
    public boolean verify(Key key) {
        String typ = header.isString("typ") ? header.get("typ").stringValue() : null;
        if ("JWT".equalsIgnoreCase(typ)) {
            String alg = getAlgorithm();
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
     * @param key the key. A {@link SecretKey} or {@link PrivateKey}, or null if the algorithm is to be "none"
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     * @return this
     */
    public JWT sign(Key key) {
        String alg = key == null ? "none" : new JWK(key).getAlgorithm();
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
                } else if (alg.equals("ES256K")) {
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
     * Return the <i>issued at claim</i> ("iat") in <b>milliseconds</b> since the epoch.
     * @return the time or 0 if not set
     * @since 5
     */
    public long getIssuedAt() {
        return payload.isNumber("iat") ? payload.get("iat").longValue() : 0;
    }

    /**
     * Return the <i>not before claim</i> ("nbf") in <b>milliseconds</b> since the epoch.
     * @return the time or 0 if not set
     * @since 5
     */
    public long getNotBefore() {
        return payload.isNumber("nbf") ? payload.get("nbf").longValue() : 0;
    }

    /**
     * Return the <i>expiry claim</i> ("exp"), in <b>milliseconds</b> since the epoch.
     * @return the time or 0 if not set
     * @since 5
     */
    public long getExpiry() {
        return payload.isNumber("exp") ? payload.get("exp").longValue() : 0;
    }

    /**
     * Return the <i>issuer claim</i> ("iss")
     * @return the issuer or null if not set
     * @since 5
     */
    public String getIssuer() {
        return payload.isString("iss") ? payload.stringValue("iss") : null;
    }

    /**
     * Return the <i>subject claim</i> ("sub")
     * @return the subject or null if not set
     * @since 5
     */
    public String getSubject() {
        return payload.isString("sub") ? payload.stringValue("sub") : null;
    }

    /**
     * Return the <i>audience claim</i> ("aud")
     * @return the audience claim, or an empty list if not set
     * @since 5
     */
    public List<String> getAudience() {
        Json aud = payload.get("aud");
        if (aud == null) {
            return Collections.<String>emptyList();
        } else if (aud.isString("aud")) {
            return Collections.<String>singletonList(payload.stringValue("aud"));
        } else if (payload.isList("aud")) {
            List<String> l = new ArrayList<String>();
            for (int i=0;i<aud.size();i++) {
                if (aud.get(i).isString()) {
                    l.add(aud.get(i).stringValue());
                }
            }
            return l;
        } else {
            return Collections.<String>emptyList();
        }
    }

    /**
     * Return the <i>unique id claim</i> ("jti")
     * @return the unique id or null if not set
     * @since 5
     */
    public String getUniqueID() {
        return payload.isString("sub") ? payload.stringValue("sub") : null;
    }

    /**
     * Set the <i>issued at claim</i> ("iat") in <b>milliseconds</b> since the epoch.
     * @param ms the time, or 0 to unset it
     * @since 5
     */
    public void setIssuedAt(long ms) {
        if (ms <= 0) {
            payload.remove("iat");
        } else {
            payload.put("iat", ms < 20000000000l ? ms : ms / 1000); // we want ms but we can sniff seconds
        }
    }

    /**
     * Set the <i>not before claim</i> ("nbf") in <b>milliseconds</b> since the epoch.
     * @param ms the time, or 0 to unset it
     * @since 5
     */
    public void setNotBefore(long ms) {
        if (ms <= 0) {
            payload.remove("nbf");
        } else {
            payload.put("nbf", ms < 20000000000l ? ms : ms / 1000); // we want ms but we can sniff seconds
        }
    }

    /**
     * Set the <i>expiry claim</i> ("exp"), in <b>milliseconds</b> since the epoch.
     * @param ms the time, or 0 to unset it
     * @since 5
     */
    public void setExpiry(long ms) {
        if (ms <= 0) {
            payload.remove("exp");
        } else {
            payload.put("exp", ms < 20000000000l ? ms : ms / 1000); // we want ms but we can sniff seconds
        }
    }

    /**
     * Set the <i>issuer claim</i> ("iss")
     * @param val the issuer, or null to unset it
     * @since 5
     */
    public void setIssuer(String val) {
        if (val == null) {
            payload.remove("iss");
        } else {
            payload.put("iss", val);
        }
    }

    /**
     * Set the <i>subject claim</i> ("sub")
     * @param val the issuer, or null to unset it
     * @since 5
     */
    public void setSubject(String val) {
        if (val == null) {
            payload.remove("sub");
        } else {
            payload.put("sub", val);
        }
    }

    /**
     * Set the <i>audience claim</i> ("aud")
     * @param val the audience claim; null or an empty list will unset it
     * @since 5
     */
    public void setAudience(List<String> val) {
        if (val == null) {
            payload.remove("aud");
        } else {
            Json j = Json.read("[]");
            for (String s : val) {
                if (s != null) {
                    j.put(j.size(), s);
                }
            }
            if (j.size() == 0) {
                payload.remove("aud");
            } else {
                payload.put("aud", j);
            }
        }
    }

    /**
     * Set the <i>unique id claim</i> ("jti")
     * @param val the unique id, or null to unset it
     * @since 5
     */
    public void setUniqueID(String val) {
        if (val == null) {
            payload.remove("jti");
        } else {
            payload.put("jti", val);
        }
    }

    /**
     * Check the token was valid at the specified time.
     * If the supplied time is 0, the current time will be used.
     * If the token has an expiry time and/or not-before time, they
     * will be compared to the supplied time and false returned if
     * they are out of range. If they are not specified, true is returned.
     * @param time the token issued-at time, or 0 to use the current time
     * @return if the key can not be determined as invalid at the specified time
     */
    public boolean isValidAt(long time) {
        if (time == 0) {
            time = System.currentTimeMillis();
        }
        if (getExpiry() != 0 && getExpiry() < time) {
            return false;
        }
        if (getNotBefore() != 0 && getNotBefore() > time) {
            return false;
        }
        return true;
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
        return hex(in, 0, in.length);
    }

    static String hex(byte[] in, int off, int len) {
        char[] c = new char[len * 2];
        for (int i=0;i<len;i++) {
            int v = in[off + i] & 0xFF;
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
