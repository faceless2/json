package com.bfo.json;

import java.util.*;
import java.io.*;
import java.math.*;
import java.nio.charset.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.Mac;
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
 * assert jwt.verify(key, null);    // Verify using the same symmetric key
 *
 * byte[] pubkey = ...
 * byte[] prikey = ...
 * jwt.getHeader().put("x5u", ...);       // Add custom content to header
 * jwt.sign(prikey, "ES256");             // Sign using a asymmetric key
 * assert jwt.verify(pubkey, "ES256");   // Verify using corresponding key
 *
 * jwt.getPayload().clear();              // Modify the payload
 * assert !jwt.verify(pubkey, "ES256");  // Signature is no longer valid
 *
 * System.out.println(jwt.getPayload());
 * System.out.println(jwt.getAlgorithm());
 * </pre>
 */
public class JWT {

    private final Json header, payload;
    private byte[] signature;

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
     * @param key the key. Either an HMAC key or a DER or PEM encoded public key for signature types. Required unless the algorithm is "none".
     * @param alg the algorithm name. If <code>null</code> it will default to {@link #getAlgorithm}.
     * @return true if the JWT is verified, false otherwise.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying.
     */
    public boolean verify(byte[] key, String alg) {
        return verify(null, key, alg, null);
    }

    /**
     * Verify the JWT.
     * @param key the key. Required unless the algorithm is "none".
     * @param alg the algorithm name. Optional, if it's null it will use {@link #getAlgorithm}.
     * @param provider the Provider to use for Signature verification, or <code>null</code> to use the default.
     * @return true if the JWT is verified, false otherwise.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying.
     */
    public boolean verify(PublicKey key, String alg, Provider provider) {
        return verify(key, null, alg, provider);
    }

    private boolean verify(PublicKey key, byte[] keybytes, String alg, Provider provider) {
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
                    return signature.length == 0;
                }
                if (key == null && keybytes == null) {
                    throw new NullPointerException("Key is null");
                }
                switch (alg) {
                    case "HS256":
                        return Arrays.equals(signature, hmac(data, keybytes, "HmacSHA256"));
                    case "HS384":
                        return Arrays.equals(signature, hmac(data, keybytes, "HmacSHA384"));
                    case "HS512":
                        return Arrays.equals(signature, hmac(data, keybytes, "HmacSHA512"));
                    case "RS256":
                        return verifySignature(data, signature, (PublicKey)toKey(key, keybytes, "RSA", true, provider), "SHA256withRSA", null, provider);
                    case "RS384":
                        return verifySignature(data, signature, (PublicKey)toKey(key, keybytes, "RSA", true, provider), "SHA384withRSA", null, provider);
                    case "RS512":
                        return verifySignature(data, signature, (PublicKey)toKey(key, keybytes, "RSA", true, provider), "SHA512withRSA", null, provider);
                    case "PS256":
                        return verifySignature(data, signature, (PublicKey)toKey(key, keybytes, "RSA", true, provider), "RSASSA-PSS", new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1), provider);
                    case "PS384":
                        return verifySignature(data, signature, (PublicKey)toKey(key, keybytes, "RSA", true, provider), "RSASSA-PSS", new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1), provider);
                    case "PS512":
                        return verifySignature(data, signature, (PublicKey)toKey(key, keybytes, "RSA", true, provider), "RSASSA-PSS", new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1), provider);
                    case "ES256":
                        return verifySignature(data, cat2der(signature), (PublicKey)toKey(key, keybytes, "EC", true, provider), "SHA256withECDSA", null, provider);
                    case "ES384":
                        return verifySignature(data, cat2der(signature), (PublicKey)toKey(key, keybytes, "EC", true, provider), "SHA384withECDSA", null, provider);
                    case "ES512":
                        return verifySignature(data, cat2der(signature), (PublicKey)toKey(key, keybytes, "EC", true, provider), "SHA512withECDSA", null, provider);
                    default:
                        throw new IllegalStateException("Unsupported alg \"" + alg + "\"");
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Unsupported typ \"" + typ + "\"");
        }
    }

    /**
     * Sign the JWT. Sets the "alg" key in the header and updates the signature.
     * @param key the key. Either an HMAC key or a DER or PEM encoded private key for signature types. Required unless the algorithm is "none".
     * @param alg the algorithm name. Required.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     */
    public void sign(byte[] key, String alg) {
        sign(null, key, alg, null);
    }

    /**
     * Sign the JWT. Sets the "alg" key in the header and updates the signature.
     * @param key the key. Required unless the algorithm is "none".
     * @param alg the algorithm name. Required.
     * @param provider the Provider to use for Signature creation, or <code>null</code> to use the default.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     */
    public void sign(PrivateKey key, String alg, Provider provider) {
        sign(key, null, alg, provider);
    }

    private void sign(PrivateKey key, byte[] keybytes, String alg, Provider provider) {
        if (alg == null) {
            throw new NullPointerException("Algorithm is null");
        }
        alg = alg.toUpperCase();
        header.put("alg", alg.equals("NONE") ? "none" : alg);
        byte[] data = ((base64encode(header.toString()) + "." + base64encode(payload.toString())).getBytes(StandardCharsets.UTF_8));
        try {
            if (alg.equals("NONE")) {
                signature = new byte[0];
            } else {
                if (key == null && keybytes == null) {
                    throw new NullPointerException("Key is null");
                }
                switch (alg) {
                    case "NONE":
                        break;
                    case "HS256":
                        signature = hmac(data, keybytes, "HmacSHA256");
                        break;
                    case "HS384":
                        signature = hmac(data, keybytes, "HmacSHA384");
                        break;
                    case "HS512":
                        signature = hmac(data, keybytes, "HmacSHA512");
                        break;
                    case "RS256":
                        signature = signSignature(data, (PrivateKey)toKey(key, keybytes, "RSA", false, provider), "SHA256withRSA", null, provider);
                        break;
                    case "RS384":
                        signature = signSignature(data, (PrivateKey)toKey(key, keybytes, "RSA", false, provider), "SHA384withRSA", null, provider);
                        break;
                    case "RS512":
                        signature = signSignature(data, (PrivateKey)toKey(key, keybytes, "RSA", false, provider), "SHA512withRSA", null, provider);
                        break;
                    case "PS256":
                        signature = signSignature(data, (PrivateKey)toKey(key, keybytes, "RSA", false, provider), "RSASSA-PSS", new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1), provider);
                        break;
                    case "PS384":
                        signature = signSignature(data, (PrivateKey)toKey(key, keybytes, "RSA", false, provider), "RSASSA-PSS", new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1), provider);
                        break;
                    case "PS512":
                        signature = signSignature(data, (PrivateKey)toKey(key, keybytes, "RSA", false, provider), "RSASSA-PSS", new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1), provider);
                        break;
                    case "ES256":
                        signature = der2cat(signSignature(data, (PrivateKey)toKey(key, keybytes, "EC", false, provider), "SHA256withECDSA", null, provider), 32);
                        break;
                    case "ES384":
                        signature = der2cat(signSignature(data, (PrivateKey)toKey(key, keybytes, "EC", false, provider), "SHA384withECDSA", null, provider), 48);
                        break;
                    case "ES512":
                        signature = der2cat(signSignature(data, (PrivateKey)toKey(key, keybytes, "EC", false, provider), "SHA512withECDSA", null, provider), 64);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported alg \"" + alg + "\"");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
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

    private static byte[] base64decode(String in) {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        return decoder.decode(in);
    }

    private static String base64encode(String in) {
        return base64encode(in.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64encode(byte[] in) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(in);
    }

    private static String hex(byte[] in) {
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
    private static byte[] cat2der(byte[] in) {
        byte[] b = new byte[in.length / 2];
        System.arraycopy(in, 0, b, 0, b.length);
        byte[] r = new BigInteger(1, b).toByteArray();
        System.arraycopy(in, b.length, b, 0, b.length);
        byte[] s = new BigInteger(1, b).toByteArray();
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

    // Convert DER sequence of two integers to raw P1363 encoding
    private static byte[] der2cat(byte[] in, int keylen) {
        byte[] out = new byte[keylen * 2];
        int i = in[2] == (byte)0x81 ? 5 : 4;
        int l = in[i - 1] & 0xff;
        int j = i + l + 2;
        System.arraycopy(in, l > keylen ? i + 1 : i, out, keylen - Math.min(keylen, l), Math.min(keylen, l));
        i += l + 2;
        l = in[i - 1] & 0xff;
        System.arraycopy(in, l > keylen ? i + 1 : i, out, keylen + keylen - Math.min(keylen, l), Math.min(keylen, l));
        return out;
    }

    private byte[] hmac(byte[] data, byte[] key, String alg) throws NoSuchAlgorithmException, InvalidKeyException {
        if (key == null) {
            throw new IllegalArgumentException("Asymmetric key cannot be used with HMAC");
        }
        Mac m = Mac.getInstance(alg);
        m.init(new SecretKeySpec(key, alg));
        return m.doFinal(data);
    }

    private static Key toKey(Key realkey, byte[] key, String alg, boolean pub, Provider provider) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (realkey != null) {
            return realkey;
        }
        KeyFactory keyfactory = provider == null ? KeyFactory.getInstance(alg) : KeyFactory.getInstance(alg, provider);
        String s;
        if (key[0] == '-' && (s=new String(key, StandardCharsets.ISO_8859_1)).startsWith("-----BEGIN")) {
            s = s.substring(s.indexOf('\n') + 1, s.indexOf("-----END"));
            key = Base64.getMimeDecoder().decode(s);
        }
        if (pub) {
            return keyfactory.generatePublic(new X509EncodedKeySpec(key));
        } else {
            return keyfactory.generatePrivate(new PKCS8EncodedKeySpec(key));
        }
    }

    private static boolean verifySignature(byte[] data, byte[] signature, PublicKey key, String alg, AlgorithmParameterSpec param, Provider provider) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {
        Signature sig = provider == null ? Signature.getInstance(alg) : Signature.getInstance(alg, provider);
        if (param != null) {
            sig.setParameter(param);
        }
        sig.initVerify(key);
        sig.update(data);
        return sig.verify(signature);
    }

    private static byte[] signSignature(byte[] data, PrivateKey key, String alg, AlgorithmParameterSpec param, Provider provider) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {
        Signature sig = provider == null ? Signature.getInstance(alg) : Signature.getInstance(alg, provider);
        if (param != null) {
            sig.setParameter(param);
        }
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    /*
    public static void main(String[] args) throws Exception {
        JWT jwt = new JWT(Json.read("{\"foo\":true}"));
        byte[] pubkey = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0]));
        byte[] prikey = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1]));
        jwt.sign(prikey, "ES256");
        System.out.println(jwt.verify(pubkey, null));
        System.out.println(jwt);
        BufferedReader r = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(args[0]), StandardCharsets.UTF_8);
        byte[] pubkey = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1]));
        String s;
        while ((s=r.readLine())!= null) {
            JWT jwt = JWT.decode(s);
            System.out.println(jwt.verify(pubkey));
        }
    }
    */

}
