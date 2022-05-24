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
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
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
     * @param alg the algorithm name. If <code>null</code> it will default to {@link #getAlgorithm}.
     * @return true if the JWT is verified, false if it failed to verify.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when verifying or if the key failed to decode.
     */
    public boolean verify(byte[] key, String alg) {
        try {
            return verify(toKey(key, alg != null ? alg : getAlgorithm(), true), alg);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verify the JWT.
     * @param key the key. A {@link SecretKey}, {@link PublicKey}, or null if the algorithm is "none". Missing keys or keys of the wrong type will cause this method to return false; specifically, if the algorithm is "none" the key <i>must</i> be null.
     * @param alg the algorithm name. If <code>null</code> it will default to {@link #getAlgorithm}.
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
                } else if (alg.startsWith("HS")) {
                    // Symmetric
                    alg = toJavaAlgorithm(alg);
                    if (key instanceof SecretKey) {
                        Mac m = Mac.getInstance(alg);
                        m.init((SecretKey)key);
                        return Arrays.equals(signature, m.doFinal(data));
                    }
                } else {
                    // Asymmetric
                    AlgorithmParameterSpec param = toJavaAlgorithmParameters(alg);
                    boolean ecfix = alg.startsWith("ES");
                    alg = toJavaAlgorithm(alg);
                    if (key instanceof PublicKey) {
                        Signature sig = provider == null ? Signature.getInstance(alg) : Signature.getInstance(alg, provider);
                        if (param != null) {
                            sig.setParameter(param);
                        }
                        sig.initVerify((PublicKey)key);
                        sig.update(data);
                        byte[] signature = this.signature;
                        if (ecfix) {
                            signature = cat2der(signature);
                        }
                        return sig.verify(signature);
                    }
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
     */
    public void sign(byte[] key, String alg) {
        try {
            sign(toKey(key, alg, false), alg);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sign the JWT. Sets the "alg" key in the header and updates the signature.
     * @param key the key. A {@link SecretKey} or {@link PrivateKey}, or null if the algorithm is "none". Required.
     * @param alg the algorithm name. Required.
     * @throws RuntimeException wrapping a GeneralSecurityException if there are cryptographic problems when signing.
     */
    public void sign(Key key, String alg) {
        if (alg == null) {
            throw new NullPointerException("Algorithm is null");
        }
        alg = alg.toUpperCase();
        Json newheader = Json.read(header.toString());
        newheader.put("alg", alg.equals("NONE") ? "none" : alg);
        byte[] data = ((base64encode(newheader.toString()) + "." + base64encode(payload.toString())).getBytes(StandardCharsets.UTF_8));
        byte[] signature = null;
        try {
            if (alg.equals("NONE")) {
                signature = new byte[0];
            } else if (alg.startsWith("HS")) {
                // Symmetric
                String jalg = toJavaAlgorithm(alg);
                if (key instanceof SecretKey) {
                    Mac m = Mac.getInstance(jalg);
                    m.init((SecretKey)key);
                    signature = m.doFinal(data);
                }
            } else {
                // Asymmetric
                String jalg = toJavaAlgorithm(alg);
                AlgorithmParameterSpec param = toJavaAlgorithmParameters(alg);
                int keylen = alg.startsWith("ES") ? Integer.parseInt(alg.substring(2)) / 8 : 0;
                if (key instanceof PrivateKey) {
                    Signature sig = provider == null ? Signature.getInstance(jalg) : Signature.getInstance(jalg, provider);
                    if (param != null) {
                        sig.setParameter(param);
                    }
                    sig.initSign((PrivateKey)key);
                    sig.update(data);
                    signature = sig.sign();
                    if (keylen != 0) {
                        signature = der2cat(signature, keylen == 64 ? 66 : keylen);
                    }
                }
            }
            header.put("alg", alg.equals("NONE") ? "none" : alg);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        if (signature == null) {
            throw new IllegalStateException("Missing or incorrect key for alg \"" + alg + "\"");
        }
        this.signature = signature;
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

    private static String toJavaAlgorithm(String alg) {
        if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
            return "HmacSHA" + alg.substring(2);
        } else if ("RS256".equals(alg) || "RS384".equals(alg) || "RS512".equals(alg)) {
            return "SHA" + alg.substring(2) + "withRSA";
        } else if ("PS256".equals(alg) || "PS384".equals(alg) || "PS512".equals(alg)) {
            return "RSASSA-PSS";
        } else if ("ES256".equals(alg) || "ES384".equals(alg) || "ES512".equals(alg)) {
            return "SHA" + alg.substring(2) + "withECDSA";
        } else {
            throw new IllegalStateException("Unsupported alg \"" + alg + "\"");
        }
    }

    private static AlgorithmParameterSpec toJavaAlgorithmParameters(String alg) {
        if ("PS256".equals(alg)) {
            return new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
        } else if ("PS384".equals(alg)) {
            return new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1);
        } else if ("PS512".equals(alg)) {
            return new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1);
        } else {
            return null;
        }
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
    private static byte[] der2cat(byte[] in, int keylen) {
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

    private Key toKey(byte[] key, String alg, boolean pub) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (key == null || key.length == 0) {
            return null;
        }
        int i = 0;
        while (i < key.length && " \t\r\n".indexOf(key[i]) >= 0) {
            i++;
        }
        if (key[i] == '-') {
            String s = new String(key, i, key.length, StandardCharsets.ISO_8859_1);
            if (s.startsWith("-----BEGIN") && (i = s.indexOf("-----END")) >= 0) {
                s = s.substring(s.indexOf('\n') + 1, i);
                key = Base64.getMimeDecoder().decode(s);
            }
        }
        if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
            return new SecretKeySpec(key, "HmacSHA" + alg.substring(2));
        } else if ("RS256".equals(alg) || "RS384".equals(alg) || "RS512".equals(alg)) {
            alg = "RSA";
        } else if ("PS256".equals(alg) || "PS384".equals(alg) || "PS512".equals(alg)) {
            alg = "RSA";
        } else if ("ES256".equals(alg) || "ES384".equals(alg) || "ES512".equals(alg)) {
            alg = "EC";
        } else {
            return null;
        }
        KeyFactory keyfactory = provider == null ? KeyFactory.getInstance(alg) : KeyFactory.getInstance(alg, provider);
        if (pub) {
            return keyfactory.generatePublic(new X509EncodedKeySpec(key));
        } else {
            return keyfactory.generatePrivate(new PKCS8EncodedKeySpec(key));
        }
    }

    private static Json bigint(BigInteger i) {
        byte[] b = i.toByteArray();
        return new Json(base64encode(b));
    }

    private static boolean eq(Object o1, Object o2) {
        if (o1 == null || o2 == null) {
            return o1 == o2;
        } else if (o1 instanceof ECParameterSpec && o2 instanceof ECParameterSpec) {
            ECParameterSpec e1 = (ECParameterSpec)o1;
            ECParameterSpec e2 = (ECParameterSpec)o2;
            return e1.getCofactor() == e2.getCofactor() && eq(e1.getCurve(), e2.getCurve()) && eq(e1.getGenerator(), e2.getGenerator()) && eq(e1.getOrder(), e2.getOrder());
        } else {
            return o1.equals(o2);
        }
    }

    private static BigInteger bigint(Json j, String param, String type, boolean opt) {
        j = j.get(param);
        if (j == null) {
            if (opt) {
                return null;
            } else {
                throw new IllegalArgumentException("Missing " + type + " param " + param);
            }
        } else if (!j.isString()) {
            throw new IllegalArgumentException("Invalid " + type + " param " + param);
        } else {
            try {
                return new BigInteger(1, base64decode(j.stringValue()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid " + type + " param " + param, e);
            }
        }
    }
    
    /**
     * A trivial Java Web Key (JWK) class
     */
    public static class JWK extends Json {

        private Provider provider;
        private List<Key> keys;
        private List<X509Certificate> certs;

        /**
         * Create a new, empty JWK
         */
        public JWK() {
            super(Collections.EMPTY_MAP);
        }

        /**
         * Create a new JWK from the specified Json, sharing its content
         * @param jwk the JWK
         */
        public JWK(Json jwk) {
            super(jwk);
        }

        /**
         * Set the Provider to be used for any cryptographic operations
         * @param provider the crypto Provider to use, or null to use the default
         */
        public void setProvider(Provider provider) {
            this.provider = provider;
        }

        /**
         * Return the Provider set by {@link #setProvider}
         * @return the provider
         */
        public Provider getProvider() {
            return provider;
        }

        /**
         * Return the algorithm name, if set
         * @return the algorithm name
         */
        public String getAlgorithm() {
            return stringValue("alg");
        }

        /**
         * Return the key id, if set.
         * @return the key id
         */
        public String getId() {
            return stringValue("kid");
        }

        /**
         * Return the key use, if set.
         * @return the key use
         */
        public String getUse() {
            return stringValue("use");
        }

        /**
         * Set the key id
         * @param id the key id, or null to remove it
         */
        public void setId(String id) {
            if (id == null) {
                remove("kid");
            } else {
                put("kid", id);
            }
        }

        /**
         * Set the key use
         * @param use the key use, or null to remove it
         */
        public void setUse(String use) {
            if (use == null) {
                remove("use");
            } else {
                put("use", use);
            }
        }

        /**
         * Return the key operations, if set
         * @return the key operations, or an empty collection if they're not set
         */
        public Collection<String> getOps() {
            Collection<String> l = new ArrayList<String>();
            if (isList("key_ops")) {
                for (Json j : listValue("key_ops")) {
                    if (j.isString()) {
                        String s = j.stringValue();
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
            }
            return l;
        }

        /**
         * Set the key operations
         * @param ops the key operations, or null to remove any existing ops. Duplicates are discarded
         */
        public void setOps(Collection<String> ops) {
            if (ops == null) {
                remove("key_ops");
            } else {
                Json j = Json.read("[]");
                Set<String> seen = new HashSet<String>();
                for (String s : ops) {
                     if (s != null && seen.add(s)) {
                         j.put(j.size(), s);
                     }
                }
                if (j.isEmpty()) {
                    remove("key_ops");
                } else {
                    put("key_ops", j);
                }
            }
        }

        /**
         * Return the list of X.509 certificates specified in the JWK,
         * downloading them if required. If none are specified, return
         * an empty collection
         * @return the list of X.509 certificates referenced from this jWK
         */
        public List<X509Certificate> getCertificates() {
            if (certs == null) {
                try {
                    Collection<? extends Certificate> cl = null;
                    if (isList("x5c")) {
                        cl = new ArrayList<Certificate>();
                        Base64.Decoder decoder = Base64.getDecoder();   // certs are different
                        for (Json j : listValue("x5c")) {
                            CertificateFactory factory = CertificateFactory.getInstance("X.509");
                            cl = factory.generateCertificates(new ByteArrayInputStream(decoder.decode(j.stringValue())));
                        }
                    } else if (isString("x5u")) {
                        CertificateFactory factory = CertificateFactory.getInstance("X.509");
                        URL url = new URL(stringValue("x5u"));
                        InputStream in = null;
                        try {
                            in = url.openConnection().getInputStream();
                            cl = factory.generateCertificates(in);
                        } finally {
                            if (in != null) try { in.close(); } catch (IOException e) {}
                        }
                        MessageDigest digest = null;
                        byte[] b1 = null;;
                        if (isString("x5t#256")) {
                            b1 = base64decode(stringValue("x5t#256"));
                            digest = MessageDigest.getInstance("SHA-256");
                        } else if (isString("x5t")) {
                            b1 = base64decode(stringValue("x5t"));
                            digest = MessageDigest.getInstance("SHA-1");
                        }
                        if (digest != null) {
                            byte[] b2 = digest.digest(certs.iterator().next().getEncoded());
                            if (!Arrays.equals(b1, b2)) {
                                throw new IllegalStateException("Certificate thumbprint mismatch");
                            }
                        }
                    } else {
                        cl = Collections.<Certificate>emptyList();
                    }
                    certs = new ArrayList<X509Certificate>();
                    for (Certificate c : cl) {
                        certs.add((X509Certificate)c);
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Algorithm missing", e);
                } catch (CertificateException e) {
                    throw new IllegalStateException("Certificate exception", e);
                } catch (IOException e) {
                    throw new IllegalStateException("IO exception", e);
                }
            }
            return certs;
        }

        /**
         * Set the list of X.509 certificates specified in the JWK,
         * either as a url or inline.
         * <ul>
         * <li>If both the url and certs are specified, it's presumed the URL would
         * retrieve the supplied list. A checksum is calculated and stored.</li>
         * <li>If only the certs are specified, they are stored in the JWK</li>
         * <li>If only the URL is specified, it's stored in the JWK</li>
         * <li>If neither are specified, any existing certificates are removed</li>
         * </ul>
         * @param certs the list of certificates, or null
         * @param url the URL to download the certificates from, or null
         * @throws IllegalArgumentException if they cannot be generated for any reason
         */
        public void setCertificates(List<X509Certificate> certs, String url) {
            try {
                remove("x5u");
                remove("x5c");
                remove("xtu");
                remove("xtu#256");
                if (url != null) {
                    if (certs != null) {
                        MessageDigest d = MessageDigest.getInstance("SHA-256");
                        for (X509Certificate c : certs) {
                            d.update(c.getEncoded());
                        }
                        put("xtu#256", base64encode(d.digest()));
                    }
                    put("x5u", url);
                } else if (certs != null) {
                    Base64.Encoder encoder = Base64.getEncoder();   // certs are different
                    Json l = Json.read("[]");
                    for (X509Certificate c : certs) {
                        l.put(l.size(), encoder.encodeToString(c.getEncoded()));
                    }
                    put("x5c", l);
                }
                if (certs != null) {
                    this.certs = new ArrayList<X509Certificate>(certs);
                } else {
                    this.certs = null;
                }
            } catch (CertificateException e) {
                throw new IllegalStateException("Certificate Exception", e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("No Algorithm", e);
            }
        }

        /**
         * Retrieve the Keys specified in this JWK. If the certificates
         * have been retrieved and no key was otherwise specified,
         * return the key from the first certificate
         * @return key the keys - either a single SecretKey, PublicKey or PrivateKey, or a paired PublicKey and PrivateKey. If no keys are found, return an empty list.
         * @throws IllegalArgumentException if the Keys cannot be generated for any reason
         */
        public List<Key> getKeys() {
            if (keys == null && isString("kty")) {
                List<Key> keys = new ArrayList<Key>();
                try {
                    // https://www.rfc-editor.org/rfc/rfc7518.html - list of algs
                    String kty = stringValue("kty");
                    if ("EC".equals(kty)) {
                        KeyFactory factory = provider == null ? KeyFactory.getInstance("EC") : KeyFactory.getInstance("EC", provider);
                        String crv = stringValue("crv");
                        ECParameterSpec params;
                        if ("P-256".equals(crv)) {
                            params = ECSPEC_P256;
                        } else if ("P-384".equals(crv)) {
                            params = ECSPEC_P384;
                        } else if ("P-521".equals(crv)) {
                            params = ECSPEC_P521;
                        } else {
                            throw new IllegalArgumentException("Unknown EC curve \"" + crv + "\"");
                        }
                        BigInteger d = bigint(this, "d", "EC private", true);
                        BigInteger x = bigint(this, "x", "EC public", true);
                        BigInteger y = bigint(this, "y", "EC public", true);
                        if (d != null) {
                            // EC private
                            ECPrivateKeySpec spec = new ECPrivateKeySpec(d, params);
                            keys.add(factory.generatePrivate(spec));
                        }
                        if (x != null && y != null) {
                            ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(x, y), params);
                            keys.add(factory.generatePublic(spec));
                        }
                    } else if ("RSA".equals(kty)) {
                        KeyFactory factory = provider == null ? KeyFactory.getInstance("RSA") : KeyFactory.getInstance("RSA", provider);
                        BigInteger n = bigint(this, "n", "RSA", false);
                        BigInteger d = bigint(this, "d", "RSA", true);
                        BigInteger e = bigint(this, "e", "RSA", true);
                        if (d != null) {
                            KeySpec spec;
                            BigInteger p = bigint(this, "p", "RSA private", true);
                            if (p != null) {
                                if (e == null) {
                                    throw new IllegalArgumentException("Missing RSA param e");
                                }
                                BigInteger q  = bigint(this, "q",  "RSA private", false);
                                BigInteger dp = bigint(this, "dp", "RSA private", false);
                                BigInteger dq = bigint(this, "dq", "RSA private", false);
                                BigInteger qi = bigint(this, "qi", "RSA private", false);
                                if (isList("oth")) {
                                    RSAOtherPrimeInfo[] oth = new RSAOtherPrimeInfo[get("oth").size()];
                                    for (int i=0;i<oth.length;i++) {
                                        BigInteger or = bigint(this, "oth["+i+"].r", "RSA private", false);
                                        BigInteger od = bigint(this, "oth["+i+"].d", "RSA private", false);
                                        BigInteger ot = bigint(this, "oth["+i+"].t", "RSA private", false);
                                        oth[i] = new RSAOtherPrimeInfo(or, od, ot);
                                    }
                                    spec = new RSAMultiPrimePrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi, oth);
                                } else {
                                    spec = new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi);
                                }
                            } else {
                                spec = new RSAPrivateKeySpec(n, d);
                            }
                            keys.add(factory.generatePrivate(spec));
                        }
                        if (e != null) {
                            RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
                            keys.add(factory.generatePublic(spec));
                        }
                    } else if ("oct".equals(kty)) {
                        byte[] k = null;
                        if (isString("k")) {
                            try {
                                k = base64decode(stringValue("k"));
                            } catch (Exception e) {
                                throw new IllegalArgumentException("Invalid symmetric param k", e);
                            }
                        } else {
                            throw new IllegalArgumentException("Missing symmetric param k");
                        }
                        String alg = stringValue("alg");
                        if (alg == null) {
                            alg = "NONE";
                        } else if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
                            alg = "HmacSHA" + alg.substring(2);
                        } else if ("A128KW".equals(alg) || "A192KW".equals(alg) || "A256KW".equals(alg)) {
                            alg = "AES";
                        } else if ("A128GCMKW".equals(alg) || "A192GCMKW".equals(alg) || "A256GCMKW".equals(alg)) {
                            // We need to create a GCMParameterSpec here as well as a SecretKeySpec
                            // secretkeyspec. Our API doesn't cover that. But we can still create the key.
                            alg = "AES";
                            byte[] iv, tag;
                            if (isString("iv")) {
                                try {
                                    iv = base64decode(stringValue("iv"));
                                } catch (Exception e) {
                                    throw new IllegalArgumentException("Invalid symmetric param iv", e);
                                }
                            } else {
                                throw new IllegalArgumentException("Missing symmetric param iv");
                            }
                            if (isString("tag")) {
                                try {
                                    tag = base64decode(stringValue("tag"));
                                } catch (Exception e) {
                                    throw new IllegalArgumentException("Invalid symmetric param tag", e);
                                }
                            } else {
                                throw new IllegalArgumentException("Missing symmetric param tag");
                            }
                            // https://stackoverflow.com/questions/23864440/aes-gcm-implementation-with-authentication-tag-in-java
                            // rest is TODO, but this is JWE specific
                        } else if ("dir".equals(alg)) {
                            // Meh. JWE specific
                            throw new IllegalArgumentException("Unsupported symmetric alg \"" + alg + "\"");
                        } else {
                            throw new IllegalArgumentException("Unknown symmetric alg \"" + alg + "\"");
                        }
                        keys.add(new SecretKeySpec(k, alg));
                    } else if ("OKP".equals(kty)) {
                        // https://datatracker.ietf.org/doc/html/rfc8037
                        // lots of TODO
                        String crv = stringValue("crv");
                        ECParameterSpec params;
                        if ("Ed25519".equals(crv)) {
                        } else if ("Ed448".equals(crv)) {
                        } else if ("X25519".equals(crv)) {
                        } else if ("X448".equals(crv)) {
                        } else {
                            throw new IllegalArgumentException("Unknown EC curve \"" + crv + "\"");
                        }
                        BigInteger d = bigint(this, "d", "EC ", false);
                        BigInteger x = bigint(this, "x", "EC ", false);
                        throw new IllegalArgumentException("Key type \"" + kty + "\" not yet supported");
                    } else {
                        throw new IllegalArgumentException("Unknown key type \"" + kty + "\"");
                    }
                } catch (InvalidKeySpecException e) {
                    throw new IllegalArgumentException("Invalid key", e);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalArgumentException("Unknown algorithm", e);
                }
                if (keys.isEmpty()) {
                    throw new IllegalStateException("No keys found");
                }
                this.keys = Collections.<Key>unmodifiableList(keys);
            }
            if (keys != null) {
                return keys;
            } else if (keys == null && certs != null && !certs.isEmpty()) {
                return Collections.<Key>unmodifiableList(Collections.<Key>singletonList(certs.get(0).getPublicKey()));
            } else {
                return Collections.<Key>emptyList();
            }
        }

        /**
         * Return the PublicKey from {@link #getKeys}, or null if none exists
         * @return the key
         */
        public PublicKey getPublicKey() {
            for (Key key : getKeys()) {
                if (key instanceof PublicKey) {
                    return (PublicKey)key;
                }
            }
            return null;
        }

        /**
         * Return the PrivateEKey from {@link #getKeys}, or null if none exists
         * @return the key
         */
        public PrivateKey getPrivateKey() {
            for (Key key : getKeys()) {
                if (key instanceof PrivateKey) {
                    return (PrivateKey)key;
                }
            }
            return null;
        }

        /**
         * Return the SecretKey from {@link #getKeys}, or null if none exists
         * @return the key
         */
        public SecretKey getSecretKey() {
            for (Key key : getKeys()) {
                if (key instanceof SecretKey) {
                    return (SecretKey)key;
                }
            }
            return null;
        }
        
        
        
        /**
         * Set the Key on this JWK. This removes any existing key,
         * but does not clear any X509Certificates from the JWK.
         * @param keys the keys to store, or null to remove any existing key
         */
        public void setKeys(Collection<Key> keys) {
            remove("kty");
            remove("x");
            remove("y");
            remove("n");
            remove("e");
            remove("k");
            remove("d");
            remove("oth");
            remove("p");
            remove("q");
            remove("dp");
            remove("dq");
            remove("ri");
            remove("crv");
            if (keys == null) {
                return;
            }
            int seentype = 0;
            boolean seenpub = false, seenpri = false;
            for (Key key : keys) {
                if (key instanceof ECKey) {
                    if (seentype != 0 && seentype != 1) {
                        throw new IllegalArgumentException("Can't mix Key algorithms");
                    }
                    seentype = 1;
                    put("kty", "EC");
                    ECParameterSpec spec = ((ECKey)key).getParams();
                    if (eq(spec, ECSPEC_P256)) {
                        put("crv", "P-256");
                    } else if (eq(spec, ECSPEC_P384)) {
                        put("crv", "P-384");
                    } else if (eq(spec, ECSPEC_P521)) {
                        put("crv", "P-521");
                    } else {
                        throw new IllegalArgumentException("Unknown EC curve");
                    }
                    if (key instanceof ECPublicKey) {
                        if (seenpub) {
                            throw new IllegalArgumentException("Only one ECPublicKey allowed");
                        }
                        seenpub = true;
                        ECPublicKey k = (ECPublicKey)key;
                        put("x", bigint(k.getW().getAffineX()));
                        put("y", bigint(k.getW().getAffineY()));
                    } else if (key instanceof ECPrivateKey) {
                        if (seenpri) {
                            throw new IllegalArgumentException("Only one ECPrivateKey allowed");
                        }
                        seenpri = true;
                        ECPrivateKey k = (ECPrivateKey)key;
                        put("d", bigint(k.getS()));
                    } else {
                        throw new IllegalArgumentException("Unknown key class");
                    }
                } else if (key instanceof SecretKey) {
                    if (seentype != 0) {
                        throw new IllegalArgumentException("Only once SecretKey allowed");
                    }
                    seentype = 2;
                    SecretKey k = (SecretKey)key;
                    put("kty", "oct");
                    put("k", base64encode(k.getEncoded()));
                } else if (key instanceof RSAKey) {
                    if (seentype != 0 && seentype != 3) {
                        throw new IllegalArgumentException("Can't mix Key algorithms");
                    }
                    seentype = 3;

                    put("kty", "RSA");
                    if (key instanceof RSAPublicKey) {
                        if (seenpub) {
                            throw new IllegalArgumentException("Only one RSAPublicKey allowed");
                        }
                        seenpub = true;
                        RSAPublicKey k = (RSAPublicKey)key;
                        put("n", bigint(k.getModulus()));
                        put("e", bigint(k.getPublicExponent()));
                    } else if (key instanceof RSAPrivateKey) {
                        if (seenpri) {
                            throw new IllegalArgumentException("Only one RSA PrivateKey allowed");
                        }
                        seenpri = true;
                        RSAPrivateKey k = (RSAPrivateKey)key;
                        put("n", bigint(k.getModulus()));
                        put("d", bigint(k.getPrivateExponent()));
                        if (key instanceof RSAPrivateCrtKey) {
                            RSAPrivateCrtKey kk = (RSAPrivateCrtKey)key;
                            put("e", bigint(kk.getPublicExponent()));
                            put("p", bigint(kk.getPrimeP()));
                            put("q", bigint(kk.getPrimeQ()));
                            put("dp", bigint(kk.getPrimeExponentP()));
                            put("dq", bigint(kk.getPrimeExponentQ()));
                            put("qi", bigint(kk.getCrtCoefficient()));
                        } else if (key instanceof RSAMultiPrimePrivateCrtKey) {
                            RSAMultiPrimePrivateCrtKey kk = (RSAMultiPrimePrivateCrtKey)key;
                            put("e", bigint(kk.getPublicExponent()));
                            put("p", bigint(kk.getPrimeP()));
                            put("q", bigint(kk.getPrimeQ()));
                            put("dp", bigint(kk.getPrimeExponentP()));
                            put("dq", bigint(kk.getPrimeExponentQ()));
                            put("qi", bigint(kk.getCrtCoefficient()));
                            RSAOtherPrimeInfo[] oth = kk.getOtherPrimeInfo();
                            for (int i=0;i<oth.length;i++) {
                                put("oth[" + i + "].r", bigint(oth[i].getPrime()));
                                put("oth[" + i + "].d", bigint(oth[i].getExponent()));
                                put("oth[" + i + "].t", bigint(oth[i].getCrtCoefficient()));
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Unknown key class");
                    }
                } else {
                    throw new IllegalArgumentException("Unknown key class");
                }
            }
        }
    }

    private static final ECParameterSpec ECSPEC_P256 = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951")), new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853948"), new BigInteger("41058363725152142129326129780047268409114441015993725554835256314039467401291")), new ECPoint(new BigInteger("48439561293906451759052585252797914202762949526041747995844080717082404635286"), new BigInteger("36134250956749795798585127919587881956611106672985015071877198253568414405109")), new BigInteger("115792089210356248762697446949407573529996955224135760342422259061068512044369"), 1);
    private static final ECParameterSpec ECSPEC_P256K = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("115792089237316195423570985008687907853269984665640564039457584007908834671663")), new BigInteger("0"), new BigInteger("7")), new ECPoint(new BigInteger("55066263022277343669578718895168534326250603453777594175500187360389116729240"), new BigInteger("32670510020758816978083085130507043184471273380659243275938904335757337482424")), new BigInteger("115792089237316195423570985008687907852837564279074904382605163141518161494337"), 1);
    private static final ECParameterSpec ECSPEC_P384 = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112319")), new BigInteger("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112316"), new BigInteger("27580193559959705877849011840389048093056905856361568521428707301988689241309860865136260764883745107765439761230575")), new ECPoint(new BigInteger("26247035095799689268623156744566981891852923491109213387815615900925518854738050089022388053975719786650872476732087"), new BigInteger("8325710961489029985546751289520108179287853048861315594709205902480503199884419224438643760392947333078086511627871")), new BigInteger("39402006196394479212279040100143613805079739270465446667946905279627659399113263569398956308152294913554433653942643"), 1);
    private static final ECParameterSpec ECSPEC_P521 = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057151")), new BigInteger("6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057148"), new BigInteger("1093849038073734274511112390766805569936207598951683748994586394495953116150735016013708737573759623248592132296706313309438452531591012912142327488478985984")), new ECPoint(new BigInteger("2661740802050217063228768716723360960729859168756973147706671368418802944996427808491545080627771902352094241225065558662157113545570916814161637315895999846"), new BigInteger("3757180025770020463545507224491183603594455134769762486694567779615544477440556316691234405012945539562144444537289428522585666729196580810124344277578376784")), new BigInteger("6864797660130609714981900799081393217269435300143305409394463459185543183397655394245057746333217197532963996371363321113864768612440380340372808892707005449"), 1);

}
