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
 * A class representing a single "JSON Web Key", a JSON representation of an asymmetric key/keypair, or a symmetric key.
 * It may contain one <code>java.security.Key</code> or two (public AND private). Currently supports
 * <ul>
 *  <li>Elliptic Curve - ES256, ES384, ES512. ES256K (prior to its removal in Java 15)</li>
 *  <li>RSASSA-PSS - PS256, PS384, PS512</li>
 *  <li>RSA - RS256, RS384, RS512 (not used in COSE, only JWT)</li>
 *  <li>EdDSA - Ed25519 and Ed448 (requires Java 15 or later)</li>
 *  <li>ML-DSA (requires Java 24 or later, or BouncyCastle 1.79 or greater as a provider). Only public keys are serialized (new in 2.1)</li>
 * </ul>
 * and symmetric ciphers with a <code>javax.crypto.SecretKey</code>
 * <ul>
 *  <li>Hmac - HS256, HS384, HS512</li>
 *  <li>AES Key Wrap - A128KW, A192KW, A256KW</li>
 *  <li>AES GCM Key Wrap - A128GCMKW, A192GCMKW, A256GCMKW</li>
 * </ul>
 * @since 5
 * @see JWT
 * @see COSE
 */
public class JWK extends Json {

    private static final Map<Integer,String> COSE_ALGORITHMS, COSE_EC_REGISTRY, COSE_KEYOPS;
    private static final Map<String,Json> AKP_DATA;

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
     * Create a new JWK from the supplied Key
     * @param key the Key, which should be public, private or secret
     */
    public JWK(Key key) {
        super(Collections.EMPTY_MAP);
        setKeys(Collections.<Key>singleton(key));
    }

    /**
     * Create a new JWK from the supplied KeyPair
     * @param pair the KeyPair
     */
    public JWK(KeyPair pair) {
        super(Collections.EMPTY_MAP);
        setKeys(Arrays.<Key>asList(new Key[] { pair.getPublic(), pair.getPrivate() }));
    }

    /**
     * Create a new JWK from the specified Json, sharing its content
     * @param jwk the JWK
     */
    public JWK(Json jwk) {
        super(jwk);
    }

    /**
     * Create a new JWK from the specified Json, sharing its content, and set the provider
     * @param jwk the JWK
     * @param provider the Provider
     * @since 2.0
     */
    public JWK(Json jwk, Provider provider) {
        this(jwk);
        if (provider != null) {
            setProvider(provider);
        }
    }

    /**
     * Create a new JWK key from a DER encoded secret, public or private key, or
     * PEM encoded versions of public, private or both keys
     * @param data the DER or PEM encoded key
     * @param alg the algorithm - required for secret keys, optional for public/private
     * @throws IllegalArgumentException if the key cannot be parsed
     * @since 5
     */
    public JWK(byte[] data, String alg) {
        super(Collections.EMPTY_MAP);
        if (data == null || data.length == 0) {
            throw new NullPointerException("data is null");
        }
        List<Key> keys = new ArrayList<Key>();
        try {
            String datastring = new String(data, StandardCharsets.ISO_8859_1);
            do {
                boolean privkey = false;
                int ix = datastring.indexOf("-----BEGIN");
                if (ix >= 0) {
                    while (datastring.length() > 0 && " \t\r\n".indexOf(datastring.charAt(0)) >= 0) {
                        datastring = datastring.substring(1);
                    }
                    int ix2 = datastring.indexOf("-----END", ix);
                    if (ix2 > 0) {
                        privkey = datastring.startsWith("-----BEGIN PRIVATE");
                        data = Base64.getMimeDecoder().decode(datastring.substring(datastring.indexOf('\n', ix) + 1, ix2));
                        ix2 = datastring.indexOf('\n', ix2);
                        if (ix2 > 0) {
                            datastring = datastring.substring(ix2 + 1);
                            while (datastring.length() > 0 && " \t\r\n".indexOf(datastring.charAt(0)) >= 0) {
                                datastring = datastring.substring(1);
                            }
                        } else {
                            datastring = "";
                        }
                    }
                } else {
                    datastring = "";
                }
                if (alg == null) {
                    // Generated 1000 keys of each, checked what's in common. No need for ASN.1 here
                    String s = JWT.hex(data, 0, Math.min(data.length, 32));
                    if (s.contains("300d06092a864886f70d010101050003")) {            // RSA public 10224/2048/4096
                        alg = "RSA"; privkey = false;
                    } else if (s.contains("0100300d06092a864886f70d010101050004")) { // RSA private 10224/2048/4096
                        alg = "RSA"; privkey = true;
                    } else if (s.contains("20100301006072a8648ce3d020106")) {       // EC private sec256/384/521r1
                        alg = "EC"; privkey = true;
                    } else if (s.contains("06072a8648ce3d020106")) {                // EC public sec256/384/521r1
                        alg = "EC"; privkey = false;
                    } else if (s.startsWith("302a300506032b6570032100")) {         // Ed25519 public
                        alg = "EdDSA"; privkey = false;
                    } else if (s.startsWith("3043300506032b6571033a00")) {         // Ed558 public
                        alg = "EdDSA"; privkey = false;
                    } else if (s.startsWith("302e020100300506032b657004220420")) { // Ed25519 private
                        alg = "EdDSA"; privkey = false;
                    } else if (s.startsWith("3047020100300506032b6571043b0439")) { // Ed448 private
                        alg = "EdDSA"; privkey = false;
                    }
                }
                Key key = null;
                if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
                    key = new SecretKeySpec(data, "HmacSHA" + alg.substring(2));
                } else if ("RS256".equals(alg) || "RS384".equals(alg) || "RS512".equals(alg)) {
                    alg = "RSA";
                } else if ("PS256".equals(alg) || "PS384".equals(alg) || "PS512".equals(alg)) {
                    alg = "RSA";
                } else if ("ES256".equals(alg) || "ES384".equals(alg) || "ES512".equals(alg)) {
                    alg = "EC";
                } else if ("Ed25519".equals(alg) || "Ed448".equals(alg) || "EdDSA".equals(alg)) {
                    alg = "EdDSA";
                }
                if (key == null) {
                    KeyFactory keyfactory = KeyFactory.getInstance(alg);
                    if (privkey) {
                        key = keyfactory.generatePrivate(new PKCS8EncodedKeySpec(data));
                    } else {
                        key = keyfactory.generatePublic(new X509EncodedKeySpec(data));
                    }
                }
                if (key != null) {
                    keys.add(key);
                } else {
                    throw new IllegalArgumentException("invalid key or invalid alg \"" + alg + "\"");
                }
            } while (datastring.length() > 0);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid key", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unknown algorithm", e);
        }
        if (!keys.isEmpty()) {
            setKeys(keys);
        } else {
            throw new IllegalStateException("no keys");
        }
    }

    /**
     * Convert a COSE Key
     * (<a href="https://datatracker.ietf.org/doc/html/rfc9052#section-7">https://datatracker.ietf.org/doc/html/rfc9052#section-7</a>)
     * to a JWT version
     * @param in the COSE Key, with numeric values like 1 for "kty"
     * @return the equivalent key as a JWK key
     */
    public static JWK fromCOSEKey(Json in) {
        // Convert stupid and unnecessary numeric key/values into JWT value. 
        // https://datatracker.ietf.org/doc/html/rfc9052#section-7
        in = in.duplicate();
        String kty;
        if (in.isNumber(1)) {
            int v = in.remove(1).intValue();
            switch (v) {
                case 1: kty = "OKP"; break;
                case 2: kty = "EC"; break;          // "EC" is JWT, EC2 is name of COSE tag
                case 3: kty = "RSA"; break;         // https://www.rfc-editor.org/rfc/rfc8230.html
                case 4: kty = "oct"; break;         // "oct" is JWT, sym is name of COSE tag
                // 5 and 6 are unsupported
                case 7: kty = "AKP"; break;         // "AKP" algorithm key-pair, for ML-DSA, SLH-DSA and probably more
                default: kty = Integer.toString(v);
            }
        } else if (in.isString(1)) {
            kty = in.remove(1).stringValue();
        } else if (in.isString("kty")) {
            kty = in.remove("kty").stringValue();
        } else {
            throw new IllegalArgumentException("kty not specified");
        }


        Json out = Json.read("{}");
        out.put("kty", kty);
        if (kty.equals("EC") || kty.equals("EC2") || kty.equals("OKP")) {
            // https://datatracker.ietf.org/doc/html/rfc9053#table-19
            // https://datatracker.ietf.org/doc/html/rfc9053#table-20
            Json crv = in.has(-1) ? in.remove(-1) : in.remove("crv");
            if (crv != null && crv.isNumber()) {
                String s = COSE_EC_REGISTRY.get(crv.intValue());
                if (s != null) {
                    crv = new Json(s);
                }
            }
            out.put("crv", crv);
            Json x = in.has(-2) ? in.remove(-2) : in.remove("x");
            if (x != null && x.isBuffer()) {
                out.put("x", x.stringValue());
            }
            if (!kty.equals("OKP")) {
                Json y = in.has(-3) ? in.remove(-3) : in.remove("y");
                if (y != null && (y.isBoolean() || y.isBuffer())) {
                    out.put("y", y.isBoolean() ? y : new Json(y.stringValue()));
                }
            }
            Json d = in.has(-4) ? in.remove(-4) : in.remove("d");
            if (d != null && d.isBuffer()) {
                out.put("d", d.stringValue());
            }
        } else if (kty.equals("RSA")) {
            // https://datatracker.ietf.org/doc/html/rfc8230.html#page-6
            String[] keys = new String[] { null, "n", "e", "d", "p", "q", "dP", "dQ", "qInv", "other", "r_i", "d_i", "t_i" };
            for (int i=0;i<keys.length;i++) {
                if (keys[i] != null) {
                    Json v = in.has(-i) ? in.remove(-i) : in.remove(keys[i]);
                    if (v != null) {
                        Object val = keys[i].equals("other") ? v.value() : v.stringValue();
                        out.put(keys[i], new Json(val));
                    }
                }
            }
        } else if (kty.equals("oct")) {
            // https://datatracker.ietf.org/doc/html/rfc9053#table-21
            Json k = in.has(-1) ? in.remove(-1) : in.remove("k");
            if (k != null && k.isBuffer()) {
                out.put("k", k.stringValue());
            }
        } else if (kty.equals("AKP")) {         // Algorithm Key Pair, for ML-DSA, SLH-DSA and probably more
            // https://www.ietf.org/archive/id/draft-ietf-cose-dilithium-05.html
            if (in.isBuffer(-1)) {
                out.put("pub", in.remove(-1).stringValue());
            } else if (in.isBuffer(-2)) {
                out.put("priv", in.remove(-2).stringValue());
            }
        }
        for (Map.Entry<Object,Json> e : in.mapValue().entrySet()) {
            Object key = e.getKey();
            Json val = e.getValue();
            if (key instanceof Number) {
                int v = ((Number)key).intValue();
                switch (v) {
                    // https://datatracker.ietf.org/doc/html/rfc9052#table-4    except 1=kty handled above
                    case  2: key = "kid"; break;                                    
                    case  3: key = "alg"; break;                                    // https://datatracker.ietf.org/doc/html/rfc9052#table-4
                    case  4: key = "key_ops"; break;                                // https://datatracker.ietf.org/doc/html/rfc9052#table-4
                    case  5: key = "iv"; break;                                     // https://datatracker.ietf.org/doc/html/rfc9052#table-4
                }
            }
            if ("alg".equals(key)) {
                val = fromCOSEAlgorithm(val);
            } else if ("key_ops".equals(key)) {
                if (val.isList()) {
                    Json j2 = Json.read("[]");
                    for (int i=0;i<val.size();i++) {
                        Json val2 = val.get(i);
                        if (val2.isNumber()) {
                            String s = COSE_KEYOPS.get(val2.intValue());
                            if (s != null) {
                                val2 = new Json(s);
                            }
                        }
                        j2.put(i, val2);
                    }
                    val = j2;
                }
            }
            out.put(key, val);
        }
        return new JWK(out);
    }

    /**
     * Convert this JWK key to a COSE Key
     * (<a href="https://datatracker.ietf.org/doc/html/rfc9052#section-7">https://datatracker.ietf.org/doc/html/rfc9052#section-7</a>)
     * @return the equivalent key as a COSE key
     */
    public Json toCOSEKey() {
        // Convert stupid and unnecessary numeric key/values into JWT value. 
        // https://datatracker.ietf.org/doc/html/rfc9052#section-7
        Json in = duplicate();
        Json out = Json.read("{}");
        String kty = in.has("kty") ? in.remove("kty").stringValue() : null;
        if ("EC".equals(kty)) {             // "EC" is JWT, EC2 is COSE tag name
            out.put(1, 2);
            String crv = in.remove("crv").stringValue();
            out.put(-1, crv);
            for (Map.Entry<Integer,String> e : COSE_EC_REGISTRY.entrySet()) {
                if (e.getValue().equals(crv)) {
                    out.put(-1, e.getKey());
                    break;
                }
            }
            if (in.bufferValue("x") != null) {
                out.put(-2, in.remove("x").bufferValue());
            }
            if (in.isBoolean("y")) {
                out.put(-3, in.remove("y"));
            } else if (in.bufferValue("y") != null) {
                out.put(-3, in.remove("y").bufferValue());
            }
            if (in.bufferValue("d") != null) {
                out.put(-4, in.remove("d").bufferValue());
            }
        } else if ("OKP".equals(kty)) {
            out.put(1, 1);
            String crv = in.remove("crv").stringValue();
            out.put(-1, crv);
            for (Map.Entry<Integer,String> e : COSE_EC_REGISTRY.entrySet()) {
                if (e.getValue().equals(crv)) {
                    out.put(-1, e.getKey());
                    break;
                }
            }
            if (in.bufferValue("x") != null) {
                out.put(-2, in.remove("x").bufferValue());
            }
            if (in.bufferValue("d") != null) {
                out.put(-4, in.remove("d").bufferValue());
            }
        } else if ("RSA".equals(kty)) {
            out.put(1, 3);
            // https://datatracker.ietf.org/doc/html/rfc8230.html#page-6
            String[] keys = new String[] { null, "n", "e", "d", "p", "q", "dP", "dQ", "qInv", "other", "r_i", "d_i", "t_i" };
            for (int i=0;i<keys.length;i++) {
                if (keys[i] != null) {
                    Json v = in.remove(keys[i]);
                    if (v != null) {
                        Object val = keys[i].equals("other") ? v.value() : v.bufferValue();
                        out.put(-i, new Json(val));
                    }
                }
            }
        } else if ("oct".equals(kty)) {             // "oct" is JWT kty, sym is name of COSE tag
            out.put(1, 4);
            if (in.bufferValue("k") != null) {
                out.put(-1, in.remove("k").bufferValue());
            }
        } else if ("AKP".equals(kty)) {
            out.put(1, 7);
            if (in.bufferValue("pub") != null) {
                out.put(-1, in.remove("pub").bufferValue());
            } else if (in.bufferValue("priv") != null) {
                out.put(-2, in.remove("priv").bufferValue());
            }
        } else {
            throw new IllegalArgumentException("Unkown kty \"" + kty + "\"");
        }

        for (Map.Entry<Object,Json> e : in.mapValue().entrySet()) {
            Object key = e.getKey();
            Json val = e.getValue();
            if (key.equals("alg")) {
                key = 3;
                val = toCOSEAlgorithm(e.getValue());
            } else if (key.equals("kid")) {
                key = 2;
            } else if (key.equals("key_ops")) {
                key = 4;
                if (val.isList()) {
                    Json j2 = Json.read("[]");
                    for (int i=0;i<val.size();i++) {
                        Json val2 = val.get(i);
                        if (val2.isString()) {
                            for (Map.Entry<Integer,String> e2 : COSE_KEYOPS.entrySet()) {
                                if (e2.getValue().equals(val2.stringValue())) {
                                    val2 = new Json(e2.getKey());
                                    break;
                                }
                            }
                        }
                        j2.put(i, val2);
                    }
                    val = j2;
                }
            } else if (key.equals("iv")) {
                key = 5;
            }
            out.put(key, val);
        }
        return out;
    }

    /**
     * Convert a COSE algorithm (eg -7) to the JWT equivalent ("ES256"), or return as is if not found
     */
    static Json fromCOSEAlgorithm(Json val) {
        if (val.isNumber() && COSE_ALGORITHMS.containsKey(val.intValue())) {
            val = new Json(COSE_ALGORITHMS.get(val.intValue()));
        }
        return val;
    }

    /**
     * Convert a JWT algorithm (eg "ES256") to the COSE equivalent (-7), or return as is if not found
     */
    static Json toCOSEAlgorithm(Json val) {
        if (val.isString()) {
            for (Map.Entry<Integer,String> e : COSE_ALGORITHMS.entrySet()) {
                if (e.getValue().equals(val.stringValue())) {
                    return new Json(e.getKey());
                }
            }
        }
        return val;
    }

    static {
        // Full list, see https://www.iana.org/assignments/cose/cose.xhtml
        Map<Integer,String> m = new HashMap<Integer,String>();
        m.put(-7, "ES256");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-35, "ES384");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-36, "ES512");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-8, "EdDSA");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(4, "HS256/64"); // https://datatracker.ietf.org/doc/html/rfc9053        - no JWT equiv
        m.put(5, "HS256");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(6, "HS384");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(7, "HS512");    // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(14, "AES-CBC-MAC-128/64");       // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv or is it A128CBC-HS256
        m.put(15, "AES-CBC-MAC-256/64");       // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(25, "AES-CBC-MAC-128/128");      // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(26, "AES-CBC-MAC-256/128");      // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(1, "A128GCM");  // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(2, "A192GCM");  // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(3, "A256GCM");  // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(10, "AEC-CCM-16-64-128");        // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(11, "AEC-CCM-16-64-256");        // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(12, "AEC-CCM-64-64-128");        // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(13, "AEC-CCM-64-64-256");        // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(30, "AEC-CCM-16-128-128");       // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(31, "AEC-CCM-16-128-256");       // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(32, "AEC-CCM-64-128-128");       // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(33, "AEC-CCM-64-128-256");       // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
        m.put(24, "ChaCha20/Poly1305");        // https://datatracker.ietf.org/doc/html/rfc9053        no JWT equiv
                // Few more here from https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-6, "dir");        // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-3, "A128KW");        // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-4, "A192KW");        // https://datatracker.ietf.org/doc/html/rfc9053
        m.put(-5, "A256KW");        // https://datatracker.ietf.org/doc/html/rfc9053
                // Few more here from https://datatracker.ietf.org/doc/html/rfc9053

        m.put(-37, "PS256");    // https://datatracker.ietf.org/doc/rfc8230/
        m.put(-38, "PS384");    // https://datatracker.ietf.org/doc/rfc8230/
        m.put(-39, "PS512");    // https://datatracker.ietf.org/doc/rfc8230/

        m.put(-48, "ML-DSA-44");        // https://www.ietf.org/archive/id/draft-ietf-cose-dilithium-05.html
        m.put(-49, "ML-DSA-65");        // https://www.ietf.org/archive/id/draft-ietf-cose-dilithium-05.html
        m.put(-50, "ML-DSA-87");        // https://www.ietf.org/archive/id/draft-ietf-cose-dilithium-05.html
        //m.put(-51, "SLH-DSA-SHA2-128s");   // https://datatracker.ietf.org/doc/draft-ietf-cose-sphincs-plus/
        //m.put(-52, "SLH-DSA-SHAKE-128s");  // https://datatracker.ietf.org/doc/draft-ietf-cose-sphincs-plus/
        //m.put(-53, "SLH-DSA-SHA2-128f ");  // https://datatracker.ietf.org/doc/draft-ietf-cose-sphincs-plus/
        COSE_ALGORITHMS = Collections.<Integer,String>unmodifiableMap(m);

        // https://datatracker.ietf.org/doc/html/rfc9053#table-18
        m = new HashMap<Integer,String>();
        m.put(1, "P-256");
        m.put(2, "P-384");
        m.put(3, "P-521");
        m.put(4, "X25519");
        m.put(5, "X448");
        m.put(6, "Ed25519");
        m.put(7, "Ed448");
        COSE_EC_REGISTRY = Collections.<Integer,String>unmodifiableMap(m);

        // https://datatracker.ietf.org/doc/html/rfc9052#table-5
        m = new HashMap<Integer,String>();
        m.put(1, "sign");
        m.put(2, "verify");
        m.put(3, "encrypt");
        m.put(4, "decrypt");
        m.put(5, "wrap key");
        m.put(6, "unwrap key");
        m.put(7, "derive key");
        m.put(8, "derive bits");
        m.put(9, "MAC create");
        m.put(10, "MAC verify");
        COSE_KEYOPS = Collections.<Integer,String>unmodifiableMap(m);
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
        // Note: as a reminder to myself, there is no useful correlation between keyusage bits in
        // X.509 and key_ops in JWK - the purposes for the latter are derived from
        // https://www.w3.org/TR/WebCryptoAPI/#subtlecrypto-interface-methods, and don't include things
        // like certificate signing. So don't bother going down that rabbit-hole
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
            if (isList("x5c")) {
                certs = extractCertificates(get("x5c"));
            } else if (isString("x5u")) {
                try {
                    certs = downloadCertificates(get("x5u"), get("x5t#256"), get("x5t"));
                } catch (IOException e) {
                    throw new IllegalStateException("Failed downloading certificate from \"" + get("x5u").stringValue() + "\"", e);
                }
            } else {
                certs = Collections.<X509Certificate>emptyList();
            }
        }
        return certs;
    }

    static List<X509Certificate> extractCertificates(Json list) {
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        try {
            if (list.isList()) {
                Base64.Decoder decoder = Base64.getDecoder();   // certs are different, they don't use URL encoding
                for (Json j : list.listValue()) {
                    CertificateFactory factory = CertificateFactory.getInstance("X.509");
                    byte[] input;
                    if (j.isBuffer()) { // Which will be true if we're coming from COSE
                        input = j.bufferValue().array();
                    } else {
                        input = decoder.decode(j.stringValue().replace("-", "+").replace("_", "/"));    // just in case
                    }
                    for (Certificate cert : factory.generateCertificates(new ByteArrayInputStream(input))) {
                        certs.add((X509Certificate)cert);
                    }
                }
            }
        } catch (CertificateException e) {
            throw new IllegalStateException("Certificate exception", e);
        }
        return certs;
    }

    static List<X509Certificate> downloadCertificates(Json jsonurl, Json sha256, Json sha1) throws IOException {
        try {
            List<X509Certificate> certs = new ArrayList<X509Certificate>();
            if (jsonurl.isString()) {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                URL url = new URI(jsonurl.stringValue()).toURL();
                InputStream in = null;
                Collection<? extends Certificate> cl = null;
                try {
                    in = url.openConnection().getInputStream();
                    cl = factory.generateCertificates(in);
                } finally {
                    in.close();
                }
                if (cl != null) {
                    for (Certificate c : cl) {
                        certs.add((X509Certificate)c);
                    }
                    MessageDigest digest = null;
                    byte[] b1 = null;
                    if (sha256 != null && sha256.isString()) {
                        b1 = JWT.base64decode(sha256.stringValue());
                        digest = MessageDigest.getInstance("SHA-256");
                    } else if (sha1 != null && sha1.isString()) {
                        b1 = JWT.base64decode(sha1.stringValue());
                        digest = MessageDigest.getInstance("SHA-1");
                    }
                    if (digest != null) {
                        byte[] b2 = digest.digest(certs.iterator().next().getEncoded());
                        if (!Arrays.equals(b1, b2)) {
                            throw new IllegalStateException("Certificate thumbprint mismatch");
                        }
                    }
                }
            }
            return certs;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid URL to \"" + jsonurl.stringValue() + "\"", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm missing", e);
        } catch (CertificateException e) {
            throw new IllegalStateException("Certificate exception", e);
        }
    }

    /**
     * Set the list of X.509 certificates specified in the JWK,
     * either as a url or inline.
     * <ul>
     * <li>If only the certificates are specified, they are stored in the JWK</li>
     * <li>If only the URL is specified, it's stored in the JWK</li>
     * <li>If both the url and certificates are specified, it's presumed the URL would
     * retrieve the supplied list. The URL is stored in the JWK, and a checksum is calculated from
     * the certificates and that is stored as well, but the certificates themselves are not stored.</li>
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
                    put("xtu#256", JWT.base64encode(d.digest()));
                }
                put("x5u", url);
            } else if (certs != null) {
                Base64.Encoder encoder = Base64.getEncoder();   // certs are different, they don't use URL encoding
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
            List<Key> keys = getKeys(this, provider);
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
     * Convenience method that retrieves the Keys specified in the supplied object,
     * which may be a JWK or some other type - so long as it has a "kty" key.
     * @return key the keys - either a single SecretKey, PublicKey or PrivateKey, or a paired PublicKey and PrivateKey. If no keys are found, return an empty list.
     * @throws IllegalArgumentException if the Keys cannot be generated for any reason
     */
    private static List<Key> getKeys(final Json j, Provider provider) {
        List<Key> keys = new ArrayList<Key>();
        String alg = null;
        try {
            // https://www.rfc-editor.org/rfc/rfc7518.html - list of algs
            String kty = j.stringValue("kty");
            if ("EC".equals(kty)) {
                KeyFactory factory = provider == null ? KeyFactory.getInstance("EC") : KeyFactory.getInstance("EC", provider);
                String crv = j.stringValue("crv");
                ECParameterSpec params;
                if ("P-256".equals(crv)) {
                    params = ECSPEC_P256;
                } else if ("secp256k1".equals(crv)) {
                    params = ECSPEC_P256K;
                } else if ("P-384".equals(crv)) {
                    params = ECSPEC_P384;
                } else if ("P-521".equals(crv)) {
                    params = ECSPEC_P521;
                } else {
                    throw new IllegalArgumentException("Unknown EC curve \"" + crv + "\"");
                }
                BigInteger d = bigint(j, "d", "EC private", true);
                BigInteger x = bigint(j, "x", "EC public", true);
                BigInteger y = bigint(j, "y", "EC public", true);
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
                BigInteger n = bigint(j, "n", "RSA", false);
                BigInteger d = bigint(j, "d", "RSA", true);
                BigInteger e = bigint(j, "e", "RSA", true);
                if (d != null) {
                    KeySpec spec;
                    BigInteger p = bigint(j, "p", "RSA private", true);
                    if (p != null) {
                        if (e == null) {
                            throw new IllegalArgumentException("Missing RSA param e");
                        }
                        BigInteger q  = bigint(j, "q",  "RSA private", false);
                        BigInteger dp = bigint(j, "dp", "RSA private", false);
                        BigInteger dq = bigint(j, "dq", "RSA private", false);
                        BigInteger qi = bigint(j, "qi", "RSA private", false);
                        if (j.isList("oth")) {
                            RSAOtherPrimeInfo[] oth = new RSAOtherPrimeInfo[j.get("oth").size()];
                            for (int i=0;i<oth.length;i++) {
                                BigInteger or = bigint(j, "oth["+i+"].r", "RSA private", false);
                                BigInteger od = bigint(j, "oth["+i+"].d", "RSA private", false);
                                BigInteger ot = bigint(j, "oth["+i+"].t", "RSA private", false);
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
                if (j.isString("k")) {
                    try {
                        k = JWT.base64decode(j.stringValue("k"));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid symmetric param k", e);
                    }
                } else {
                    throw new IllegalArgumentException("Missing symmetric param k");
                }
                alg = j.stringValue("alg");
                if (alg == null) {
                    alg = "none";
                } else if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
                    alg = "HmacSHA" + alg.substring(2);
                } else if ("A128KW".equals(alg) || "A192KW".equals(alg) || "A256KW".equals(alg)) {
                    alg = "AES";
                } else if ("A128GCMKW".equals(alg) || "A192GCMKW".equals(alg) || "A256GCMKW".equals(alg)) {
                    // We need to create a GCMParameterSpec here as well as a SecretKeySpec
                    // secretkeyspec. Our API doesn't cover that. But we can still create the key.
                    alg = "AES";
                    byte[] iv, tag;
                    if (j.isString("iv")) {
                        try {
                            iv = JWT.base64decode(j.stringValue("iv"));
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Invalid symmetric param iv", e);
                        }
                    } else {
                        throw new IllegalArgumentException("Missing symmetric param iv");
                    }
                    if (j.isString("tag")) {
                        try {
                            tag = JWT.base64decode(j.stringValue("tag"));
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
                    throw new IllegalArgumentException("Unsupported symmetric algorithm \"" + alg + "\"");
                } else {
                    throw new IllegalArgumentException("Unknown symmetric algorithm \"" + alg + "\"");
                }
                keys.add(new SecretKeySpec(k, alg));
            } else if ("OKP".equals(kty)) {
                // https://datatracker.ietf.org/doc/html/rfc8037
                // This is Java 15 or later
                alg = "EdDSA";
                KeyFactory factory = provider == null ? KeyFactory.getInstance("EdDSA") : KeyFactory.getInstance("EdDSA", provider);
                String crv = j.stringValue("crv");
                if (j.has("x")) {
                    byte[] x = j.bufferValue("x").array().clone();
                    // https://github.com/openjdk/jdk/blob/fd28aad72d3a13fbc2eb13293d40564e471414f3/test/lib/jdk/test/lib/Convert.java
                    boolean xIsOdd = (x[x.length - 1] & 0x80) != 0;
                    x[x.length - 1] &= 0x7f;
                    reverse(x);
                    BigInteger y = new BigInteger(1, x);
                    KeySpec spec = generateEdECKeySpec(crv, xIsOdd, y, null);
                    keys.add(factory.generatePublic(spec));
                }
                if (j.has("d")) {
                    byte[] d =  JWT.base64decode(j.stringValue("d"));
                    KeySpec spec = generateEdECKeySpec(crv, false, null, d);
                    keys.add(factory.generatePrivate(spec));
                }
            } else if ("AKP".equals(kty)) {
                alg = j.stringValue("alg");
                Json v = AKP_DATA.get(alg);
                if (v != null) {
                    String factoryName = v.stringValue("factory");
                    KeyFactory factory = provider == null ? KeyFactory.getInstance(factoryName) : KeyFactory.getInstance(factoryName, provider);
                    if (j.has("pub")) {
                        byte[] pub = j.bufferValue("pub").array().clone();
                        KeySpec spec = generateAKPKeySpec(alg, v, pub, true, provider);
                        if (spec != null) {
                            keys.add(factory.generatePublic(spec));
                        }
                    }
                    if (j.has("priv")) {
                        byte[] priv = j.bufferValue("priv").array().clone();
                        KeySpec spec = generateAKPKeySpec(alg, v, priv, false, provider);
                        if (spec != null) {
                            keys.add(factory.generatePrivate(spec));
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown key type \"" + kty + "\"");
            }
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid key", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unknown algorithm" + (alg == null ? "" : " " + alg), e);
        }
        return keys;
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
     * Return the PrivateKey from {@link #getKeys}, or null if none exists
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
            String alg = null;
            if (key instanceof ECKey) {
                if (seentype != 0 && seentype != 1) {
                    throw new IllegalArgumentException("Can't mix Key algorithms");
                }
                seentype = 1;
                put("kty", "EC");
                ECParameterSpec spec = ((ECKey)key).getParams();
                if (eq(spec, ECSPEC_P256)) {
                    put("crv", "P-256");
                    alg = "ES256";
                } else if (eq(spec, ECSPEC_P256K)) {
                    put("crv", "secp256k1");    // Note this one is deprecated in Java 15+
                    alg = "ES256K";
                } else if (eq(spec, ECSPEC_P384)) {
                    put("crv", "P-384");
                    alg = "ES384";
                } else if (eq(spec, ECSPEC_P521)) {
                    put("crv", "P-521");
                    alg = "ES512";
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
                }
            } else if (key instanceof SecretKey) {
                if (seentype != 0) {
                    throw new IllegalArgumentException("Only once SecretKey allowed");
                }
                seentype = 2;
                SecretKey k = (SecretKey)key;
                put("kty", "oct");
                put("k", JWT.base64encode(k.getEncoded()));
                if (k.getAlgorithm().equals("HmacSHA256")) {
                    alg = "HS256";
                } else if (k.getAlgorithm().equals("HmacSHA384")) {
                    alg = "HS384";
                } else if (k.getAlgorithm().equals("HmacSHA512")) {
                    alg = "HS512";
                }
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
                    int bitlength = k.getPrivateExponent().bitLength();
                    if (bitlength <= 2048) {
                        alg = "PS256";
                    } else if (bitlength <= 3072) {
                        alg = "PS384";
                    } else if (bitlength <= 4096) {
                        alg = "PS512";
                    }
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
                }
            } else if (EdECKey != null && EdECKey.isInstance(key)) {
                // This is Java 15 or later, so use reflection here so this can compile under Java11
                if (seentype != 0 && seentype != 4) {
                    throw new IllegalArgumentException("Can't mix Key algorithms");
                }
                seentype = 4;
                put("kty", "OKP");
                alg = "EdDSA";
                Json j = getEdECKeyDetails(key);
                put("crv", j.get("crv"));
                if (j.has("y")) {       // public key
                    // https://www.rfc-editor.org/rfc/rfc8032.html
                    if (seenpub) {
                        throw new IllegalArgumentException("Only one EdECPublicKey allowed");
                    }
                    seenpub = true;
                    boolean xodd = j.get("xodd").booleanValue();
                    BigInteger y = (BigInteger)j.get("y").value();
                    byte[] x = y.toByteArray();
                    reverse(x);
                    if ((x[x.length-1] & 0x80) != 0) {
                        byte[] x2 = new byte[x.length + 1];
                        System.arraycopy(x, 0, x2, 0, x.length);
                    }
                    if (xodd) {
                        x[x.length - 1] |= 0x80;
                    }
                    put("x", x);
                } else if (j.has("d")) {        // private key
                    if (seenpri) {
                        throw new IllegalArgumentException("Only one EdECPrivateKey allowed");
                    }
                    seenpri = true;
                    put("d", j.get("d"));
                }
            } else {
                Json j = getJavaAKPKeyDetails(key);
                if (j != null) {
                    int type = j.stringValue("factory").hashCode();
                    if (seentype != 0 && seentype != type) {
                        throw new IllegalArgumentException("Can't mix Key algorithms");
                    }
                    put("kty", "AKP");
                    put("alg", j.get("alg"));
                    if (j.has("pub")) {
                        put("pub", j.get("pub"));
                    } else if (j.has("priv")) {
                        put("priv", j.get("priv"));
                    }
                } else {
                    throw new IllegalArgumentException("Unknown key class " + (key == null ? null : key.getClass().getName()));
                }
            }
            if (alg != null) {
                put("alg", alg);
            }
        }
    }

    static void reverse(byte[] b) {
        for (int i=b.length/2-1;i>=0;i--) {
            byte v = b[i];
            b[i] = b[b.length - i - 1];
            b[b.length - i - 1] = v;
        }
    }

    static Json bigint(BigInteger i) {
        byte[] b = i.toByteArray();
        return new Json(JWT.base64encode(b));
    }

    static boolean eq(Object o1, Object o2) {
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

    static BigInteger bigint(Json j, String param, String type, boolean opt) {
        j = j.get(param);
        if (j == null) {
            if (opt) {
                return null;
            } else {
                throw new IllegalArgumentException("Missing " + type.trim() + " param " + param);
            }
        } else if (!j.isString()) {
            throw new IllegalArgumentException("Invalid " + type.trim() + " param " + param);
        } else {
            try {
                return new BigInteger(1, JWT.base64decode(j.stringValue()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid " + type.trim() + " param " + param, e);
            }
        }
    }

    /**
     * Given an algorithm name and provider, return a new uninitialized Signature
     * object if the algorithm is a signature algorithm, or throw an exception otherwise.
     * @param alg the algorithm, eg "ES256"
     * @param provider the Provider, or null
     */
    static Signature createSignature(String alg, Key key, Provider provider) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec params = null;
        if ("RS256".equals(alg) || "RS384".equals(alg) || "RS512".equals(alg)) {
            alg = "SHA" + alg.substring(2) + "withRSA";
        } else if ("PS256".equals(alg)) {
            alg = "RSASSA-PSS";
            params = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
        } else if ("PS384".equals(alg)) {
            alg = "RSASSA-PSS";
            params = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1);
        } else if ("PS512".equals(alg)) {
            alg = "RSASSA-PSS";
            params = new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1);
        } else if ("ES256".equals(alg) || "ES384".equals(alg) || "ES512".equals(alg)) {
            alg = "SHA" + alg.substring(2) + "withECDSA";
        } else if ("ES256K".equals(alg)) {
            alg = "SHA256withECDSA";
        } else if ("EdDSA".equals(alg)) {
            alg = "EdDSA";
        } else {
            throw new NoSuchAlgorithmException("Unrecognised Signature algorithm \"" + alg + "\"");
        }
        Signature sig = provider == null ? Signature.getInstance(alg) : Signature.getInstance(alg, provider);
        if (params != null) {
            sig.setParameter(params);
        }
        return sig;
    }

    static Mac createMac(String alg, Key key, Provider provider) throws NoSuchAlgorithmException {
        if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
            alg = "HmacSHA" + alg.substring(2);
        } else {
            throw new NoSuchAlgorithmException("Unrecognised MAC algorithm \"" + alg + "\"");
        }
        return provider == null ? Mac.getInstance(alg) : Mac.getInstance(alg, provider);
    }


    //----------------------------------------------------------------------------------
    // EdDSA keys are new in Java 15, but I want library to compile and run under Java 11
    // So all handling of EdDSA is done with reflection. ML-DSA same, but new in Java 24
    //
    private static final Class<?> EdECKey, EdECPublicKey, EdECPrivateKey, EdECPoint, EdECPublicKeySpec, EdECPrivateKeySpec; // Java 15
    private static final Class<?> AsymmetricKey; // Java 22
    static {
        // EdDSA
        Class<?> tEdECKey = null, tEdECPublicKey = null, tEdECPrivateKey = null, tEdECPoint = null, tEdECPublicKeySpec = null, tEdECPrivateKeySpec = null;
        try {
            tEdECKey = Class.forName("java.security.interfaces.EdECKey");
            tEdECPublicKey = Class.forName("java.security.interfaces.EdECPublicKey");
            tEdECPrivateKey = Class.forName("java.security.interfaces.EdECPrivateKey");
            tEdECPoint = Class.forName("java.security.spec.EdECPoint");
            tEdECPublicKeySpec = Class.forName("java.security.spec.EdECPublicKeySpec");
            tEdECPrivateKeySpec = Class.forName("java.security.spec.EdECPrivateKeySpec");
        } catch (Exception e) {
            tEdECKey = tEdECPublicKey = tEdECPrivateKey = tEdECPoint = tEdECPublicKeySpec = tEdECPrivateKeySpec = null;
        }
        EdECKey = tEdECKey;
        EdECPublicKey = tEdECPublicKey;
        EdECPrivateKey = tEdECPrivateKey;
        EdECPoint = tEdECPoint;
        EdECPublicKeySpec = tEdECPublicKeySpec;
        EdECPrivateKeySpec = tEdECPrivateKeySpec;
        // ML-DSA
        Class<?> tAsymmetricKey = null;
        try {
            tAsymmetricKey = Class.forName("java.security.AsymmetricKey");
        } catch (Exception e) {
            tAsymmetricKey = null;
        }
        AsymmetricKey = tAsymmetricKey;
    }

    /**
     * Extract "crv", "xIsOdd", "y", "d" from an EdECPublicKey or EdECPrivateKey and return in a Json.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Json getEdECKeyDetails(Key key) {
        // NOTE NOTE - this method compiles under Java 11 but require Java 15 to do anything
        // useful. If compiling with Java older than 11, just make it return null
        try {
            Json j = Json.read("{}");
            if (EdECPublicKey != null && EdECPublicKey.isInstance(key)) {
                Object point = EdECPublicKey.getMethod("getPoint").invoke(key);
                // Presumption that curve names will be Ed25519 and Ed448 - not listed, but true at least in OpenJDK
                j.put("crv", ((NamedParameterSpec)EdECKey.getMethod("getParams").invoke(key)).getName());
                j.put("xodd", EdECPoint.getMethod("isXOdd").invoke(point));
                j.put("y", EdECPoint.getMethod("getY").invoke(point));
                return j;
            } else if (EdECPrivateKey != null && EdECPrivateKey.isInstance(key)) {
                j.put("crv", ((NamedParameterSpec)EdECKey.getMethod("getParams").invoke(key)).getName());
                j.put("d", ((Optional)EdECPrivateKey.getMethod("getBytes").invoke(key)).get());
                return j;
            }
        } catch (Throwable e) {}
        return null;
    }
    /**
     * Create a new KeySpec for public (if y!=null) or private (if d!=null) EdDSA keys
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static KeySpec generateEdECKeySpec(String crv, boolean xIsOdd, BigInteger y, byte[] d) {
        // NOTE NOTE - this method compiles under Java 11 but require Java 15 to do anything
        // useful. If compiling with Java older than 11, just make it throw an Exception
        try {
            NamedParameterSpec np;
            if ("Ed25519".equals(crv)) {
                np = (NamedParameterSpec)NamedParameterSpec.class.getField("ED25519").get(null);
            } else if ("Ed448".equals(crv)) {
                np = (NamedParameterSpec)NamedParameterSpec.class.getField("ED448").get(null);
            } else if ("X25519".equals(crv)) {
                np = NamedParameterSpec.X25519;
            } else if ("X448".equals(crv)) {
                np = NamedParameterSpec.X448;
            } else {
                throw new IllegalArgumentException("Unknown EdDSA curve \"" + crv + "\"");
            }
            if (y != null) {        // public key
                Object point = EdECPoint.getDeclaredConstructor(Boolean.TYPE, BigInteger.class).newInstance(Boolean.valueOf(xIsOdd), y);
                return (KeySpec)EdECPublicKeySpec.getDeclaredConstructor(NamedParameterSpec.class, EdECPoint).newInstance(np, point);
            } else {
                return (KeySpec)EdECPrivateKeySpec.getDeclaredConstructor(NamedParameterSpec.class, byte[].class).newInstance(np, d);
            }
        } catch (Throwable e) {
            // We can't get here - only called after we've created an EdDSA KeyFactory so we know they exist
            throw new RuntimeException(e);
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Json getJavaAKPKeyDetails(Key key) {
        // NOTE NOTE - this method compiles under Java 11 but require Java 22 to do anything
        // useful. If compiling with Java older than 11, just make it return null
        try {
            String name;
            try {
                // Java 22 way
                NamedParameterSpec spec = (NamedParameterSpec)AsymmetricKey.getMethod("getParams").invoke(key);
                name = spec.getName();
            } catch (Throwable e) {
                // BC way
                AlgorithmParameterSpec spec = (AlgorithmParameterSpec)key.getClass().getMethod("getParameterSpec").invoke(key);
                name = (String)spec.getClass().getMethod("getName").invoke(spec);
            }
            for (Map.Entry<String,Json> e : AKP_DATA.entrySet()) {
                if (name.equalsIgnoreCase(e.getKey())) {
                    name = e.getKey();
                    Json json = e.getValue();
                    Json j = Json.read("{}");
                    j.put("alg", name);
                    j.put("factory", json.stringValue("factory"));
                    if (key instanceof PublicKey && json.isString("pubprefix")) {
                        byte[] data = ((PublicKey)key).getEncoded();
                        byte[] prefix = JWT.hex(json.stringValue("pubprefix"));
                        data = Arrays.copyOfRange(data, prefix.length, data.length);
                        j.put("pub", JWT.base64encode(data));
                        return j;
                    } else {
                        // Unsupported. See generateAKPKeySpec for details.
                        return null;
                    }
                }
            }
        } catch (Throwable e) {}
        return null;
    }
    /**
     * Create a new KeySpec for public (if y!=null) or private (if d!=null) EdDSA keys
     */
    private static KeySpec generateAKPKeySpec(String alg, Json json, byte[] key, boolean pub, Provider provider) {
        // AKP keys are extracted from their X.509 encoding, which is a sequence of the key algorithm
        // and the key itself, always encoded (so far) as a bitstring or octet string. Examples
        //
        //  0:d=0  hl=4 l=1330 cons: SEQUENCE
        //  4:d=1  hl=2 l=  11 cons:  SEQUENCE
        //  6:d=2  hl=2 l=   9 prim:   OBJECT            :ML-DSA-44
        // 17:d=1  hl=4 l=1313 prim:  BIT STRING
        //
        //  0:d=0  hl=2 l=  48 cons: SEQUENCE
        //  2:d=1  hl=2 l=  11 cons:  SEQUENCE
        //  4:d=2  hl=2 l=   9 prim:   OBJECT            :SLH-DSA-SHA2-128f
        // 15:d=1  hl=2 l=  33 prim:  BIT STRING
        //
        // In all these cases the String value is stored in the JWK, so we add or remove the prefix
        // as appropriate. ML-DSA keys are 1312/1952/2592 bytes, SLH-DSA are 32/48/64 bytes, although
        // they are all slightly longer when serialized to ASN.1
        //
        // Private keys are not supported yet because:
        //
        // ML-DSA:
        //   priv is always a 32-byte seed value, encoded raw. This needs to be converted
        //   to a 2560, 4032 or 4896-byte ML-DSA private key by calling KeyGen_internal,
        //   defined in FIPS204 (https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.204.pdf).
        //   We may be able to do this with BC, certainly not with Java 24.
        // SLH-DSA:
        //   too early in spec process to say how it works.
        //
        // ML-DSA:  https://cose-wg.github.io/draft-ietf-cose-dilithium/draft-ietf-cose-dilithium.html
        // SLH-DSA: https://cose-wg.github.io/draft-ietf-cose-sphincs-plus/draft-ietf-cose-sphincs-plus.html
        //
        // ***NOTE***: as of 20251028 the SLH-DSA spec is really not fit for implementation; for example, it
        // refers to COSE keys which are reserved for other algorithms, and only attempts to define three of
        // the twelve algorithms from FIPS205. There are no test vectors. So this is very much a work-in-progress.
        // See https://github.com/cose-wg/draft-ietf-cose-sphincs-plus
        // 
        if (pub && json.isString("pubprefix")) {
            byte[] prefix = JWT.hex(json.stringValue("pubprefix"));
            byte[] data = new byte[prefix.length + key.length];
            System.arraycopy(prefix, 0, data, 0, prefix.length);
            System.arraycopy(key, 0, data, prefix.length, key.length);
            EncodedKeySpec spec = new X509EncodedKeySpec(data, alg);
            return spec;
        }
        return null;
    }

    //
    // End of reflection nonsense for EdDSA and ML-DSA keys
    //----------------------------------------------------------------------------------

    private static final ECParameterSpec ECSPEC_P256 = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951")), new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853948"), new BigInteger("41058363725152142129326129780047268409114441015993725554835256314039467401291")), new ECPoint(new BigInteger("48439561293906451759052585252797914202762949526041747995844080717082404635286"), new BigInteger("36134250956749795798585127919587881956611106672985015071877198253568414405109")), new BigInteger("115792089210356248762697446949407573529996955224135760342422259061068512044369"), 1);
    private static final ECParameterSpec ECSPEC_P256K = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("115792089237316195423570985008687907853269984665640564039457584007908834671663")), new BigInteger("0"), new BigInteger("7")), new ECPoint(new BigInteger("55066263022277343669578718895168534326250603453777594175500187360389116729240"), new BigInteger("32670510020758816978083085130507043184471273380659243275938904335757337482424")), new BigInteger("115792089237316195423570985008687907852837564279074904382605163141518161494337"), 1);
    private static final ECParameterSpec ECSPEC_P384 = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112319")), new BigInteger("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112316"), new BigInteger("27580193559959705877849011840389048093056905856361568521428707301988689241309860865136260764883745107765439761230575")), new ECPoint(new BigInteger("26247035095799689268623156744566981891852923491109213387815615900925518854738050089022388053975719786650872476732087"), new BigInteger("8325710961489029985546751289520108179287853048861315594709205902480503199884419224438643760392947333078086511627871")), new BigInteger("39402006196394479212279040100143613805079739270465446667946905279627659399113263569398956308152294913554433653942643"), 1);
    private static final ECParameterSpec ECSPEC_P521 = new ECParameterSpec(new EllipticCurve(new ECFieldFp(new BigInteger("6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057151")), new BigInteger("6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057148"), new BigInteger("1093849038073734274511112390766805569936207598951683748994586394495953116150735016013708737573759623248592132296706313309438452531591012912142327488478985984")), new ECPoint(new BigInteger("2661740802050217063228768716723360960729859168756973147706671368418802944996427808491545080627771902352094241225065558662157113545570916814161637315895999846"), new BigInteger("3757180025770020463545507224491183603594455134769762486694567779615544477440556316691234405012945539562144444537289428522585666729196580810124344277578376784")), new BigInteger("6864797660130609714981900799081393217269435300143305409394463459185543183397655394245057746333217197532963996371363321113864768612440380340372808892707005449"), 1);

    static {
        Map<String,Json> m = new HashMap<String,Json>();
        // FIPS204 approved three parameter sets.
        m.put("ML-DSA-44", Json.read("{\"factory\":\"ML-DSA\", \"pubprefix\":\"30820532300B06096086480165030403110382052100\"}"));
        m.put("ML-DSA-65", Json.read("{\"factory\":\"ML-DSA\", \"pubprefix\":\"308207B2300B0609608648016503040312038207A100\"}"));
        m.put("ML-DSA-87", Json.read("{\"factory\":\"ML-DSA\", \"pubprefix\":\"30820A32300B060960864801650304031303820A2100\"}"));
        // FIPS205 approved twelve parameter sets.
        m.put("SLH-DSA-SHA2-128s",  Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3030300B0609608648016503040314032100\"}"));
        m.put("SLH-DSA-SHA2-128f",  Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3030300B0609608648016503040315032100\"}"));
        m.put("SLH-DSA-SHA2-192s",  Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3040300B0609608648016503040316033100\"}"));
        m.put("SLH-DSA-SHA2-192f",  Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3040300B0609608648016503040317033100\"}"));
        m.put("SLH-DSA-SHA2-256s",  Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3050300B0609608648016503040318034100\"}"));
        m.put("SLH-DSA-SHA2-256f",  Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3050300B0609608648016503040319034100\"}"));
        m.put("SLH-DSA-SHAKE-128s", Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3030300B060960864801650304031A032100\"}"));
        m.put("SLH-DSA-SHAKE-128f", Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3030300B060960864801650304031B032100\"}"));
        m.put("SLH-DSA-SHAKE-192s", Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3040300B060960864801650304031C033100\"}"));
        m.put("SLH-DSA-SHAKE-192f", Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3040300B060960864801650304031D033100\"}"));
        m.put("SLH-DSA-SHAKE-256s", Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3050300B060960864801650304031E034100\"}"));
        m.put("SLH-DSA-SHAKE-256f", Json.read("{\"factory\":\"SLH-DSA\", \"pubprefix\":\"3050300B060960864801650304031F034100\"}"));

        AKP_DATA = Collections.<String,Json>unmodifiableMap(m);
    }

}

