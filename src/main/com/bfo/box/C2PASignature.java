package com.bfo.box;

import java.io.*;
import java.util.*;
import java.nio.*;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.*;
import java.security.spec.*;
import com.bfo.json.*;

/**
 * The <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_digital_signatures">C2PA signature</a>
 * is applied to each {@link C2PAManifest manifest} to sign it. There is one signature per manifest.
 * @since 5
 */
public class C2PASignature extends CborContainerBox {

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

    private int getMinSize() {
        return 0;       // TODO test 
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
     * @throws IOException if signing fails due to an IOException
     * @throws C2PAException if the C2PA preconditions for signing are not met
     */
    public void sign(PrivateKey key, List<X509Certificate> certs) throws IOException, C2PAException /*throws GeneralSecurityException*/ {
        final COSE cose = cose();
        final C2PAManifest manifest = (C2PAManifest)parent();
        final C2PAClaim claim = manifest.getClaim();
        final List<C2PA_Assertion> assertions = claim.getAssertions();
        if (claim.getFormat() == null) {
            throw new IllegalStateException("claim has no format");
        }
        if (claim.getInstanceID() == null) {
            throw new IllegalStateException("claim has no instanceID");
        }
        boolean foundHash = false;
        if (assertions.isEmpty()) {
            assertions.addAll(manifest.getAssertions());
        }
        C2PA_AssertionHashData hashdata = null;
        C2PA_AssertionHashBMFF hashbmff = null;
        for (C2PA_Assertion a : assertions) {
            if (a == null) {
                throw new NullPointerException("assertion in claim is null");    // shouldn't happen
            } else if (a instanceof C2PA_AssertionUnknown) {
                throw new C2PAException(C2PAStatus.assertion_missing, "assertion \"" + ((C2PA_AssertionUnknown)a).url() + "\" not found");
            } else if (a instanceof C2PA_AssertionHashData) {
                if (hashdata != null || hashbmff != null) {
                    throw new C2PAException(C2PAStatus.assertion_multipleHardBindings, "manifest has multiple hard-binding");
                }
                hashdata = (C2PA_AssertionHashData)a;
            } else if (a instanceof C2PA_AssertionHashBMFF) {
                if (hashdata != null || hashbmff != null) {
                    throw new C2PAException(C2PAStatus.assertion_multipleHardBindings, "manifest has multiple hard-binding");
                }
                hashbmff = (C2PA_AssertionHashBMFF)a;
            }
        }
        for (int i=0;i<claim.cbor().get("assertions").size();i++) {
            claim.cbor().get("assertions").get(i).remove("hash");
        }
        if (hashdata != null) {
            hashdata.sign();
        } else if (hashbmff != null) {
            hashbmff.sign();
        } else {
            throw new C2PAException(C2PAStatus.claim_hardBindings_missing, "manifest has no hard-binding");
        }
        if (getMinSize() > 0) {
            Json j = cose.getUnprotectedAttributes();
            if (j == null) {
                j = Json.read("{}");
            }
            j.put("pad", new byte[getMinSize()]);
        }
        if (claim.getGenerator() == null) {
            claim.setGenerator("BFO Json library", null);
        }
        claim.cbor().put("signature", "self#jumbf=" + manifest.find(this));
        cose.setPayload(generatePayload(getMinSize(), true), true);
        cose.setCertificates(certs);
        cose.sign(key, null);
    }

    private ByteBuffer generatePayload(int padlength, boolean signing) throws C2PAException {
        final C2PAManifest manifest = (C2PAManifest)parent();
        final C2PAClaim claim = manifest.getClaim();
        MessageDigest digest;
        Json l = claim.cbor().get("assertions");
        for (int i=0;i<l.size();i++) {
            digestHashedURL(l.get(i), manifest, false, signing);
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
     * asseration in the claim's list must also be verified, see {@link C2PA_Assertion#verify}
     * @param key the public key; if null, it will be extracted (if possible) from any certificates in the signature
     * @throws IllegalArgumentException if no key is available to verify
     * @throws IllegalStateException if the signature is not signed or has been incorrectly set up
     * @return true if the supplied key verifies the signature, false otherwise
     */
    public C2PAStatus verify(PublicKey key) throws C2PAException {
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
                throw new C2PAException(C2PAStatus.claim_multiple, "too many claim boxes");
            }
        }
        if (!claim.cbor().isString("signature") || manifest.find(claim.cbor().stringValue("signature")) != this) {
            throw new C2PAException(C2PAStatus.claimSignature_missing, "signature not in claim");
        }
        if (key == null && cose.getCertificates() != null && !cose.getCertificates().isEmpty()) {
            key = cose.getCertificates().get(0).getPublicKey();
        } else if (key == null) {
            throw new IllegalArgumentException("no key supplied and no certificates included in the signature");
        }
        ByteBuffer payload = generatePayload(getMinSize(), false);
        cose.setPayload(payload, true);
        boolean b = cose.verify(key) >= 0;
        return b ? C2PAStatus.claimSignature_validated : C2PAStatus.claimSignature_mismatch;
    }

    /**
     * Given a Json object with {"uri":x}, calculate the digest
     * and update it. If it already has a digest and it differs,
     * or the URL cannot be found, throw an Exception
     */
    static void digestHashedURL(Json hasheduri, C2PAManifest manifest, boolean ingredient, boolean signing) throws C2PAException {
        String url = hasheduri.stringValue("url");
        JUMBox box = manifest.find(url);
        if (box == null) {
            throw new C2PAException(C2PAStatus.assertion_missing, "\"" + url + "\" not in manifest");
        }
        MessageDigest digest = manifest.getMessageDigest(hasheduri, signing);
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
        byte[] digestbytes = digest.digest();
        if (hasheduri.isBuffer("hash")) {
            byte[] olddigestbytes = hasheduri.bufferValue("hash").array();
            if (!Arrays.equals(digestbytes, olddigestbytes)) {
                debugMismatch(box);
                C2PAStatus status = ingredient ? C2PAStatus.ingredient_hashedURI_mismatch : C2PAStatus.assertion_hashedURI_mismatch;
                throw new C2PAException(status, "hash mismatch for \"" + box.label() + "\"");
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

    /**
     * Verify that the supplied certificate chain is allowed for use with C2PA.
     * @param certs the list of certificates, in order, with the signing certificate first
     * @param purpose the purpose of this chain - either "ocsp", "timestamp" or anything else for "signing"
     * @throws a C2PAException describing why if the certificates are not valid
     */
    public static void verifyCertificates(List<X509Certificate> certs, String purpose) throws C2PAException {
        if (!"timestamp".equals(purpose) && !"ocsp".equals(purpose)) {
            purpose = "signing";
        }

        StringBuilder sb = new StringBuilder();
        Exception e2 = null;

        for (int ix=0;ix<certs.size();ix++) {
            List<String> list = new ArrayList<String>();
            X509Certificate cert = certs.get(ix);
            try {

                //
                // The algorithm field of the signatureAlgorithm field shall be one of the following values:
                //   ecdsa-with-SHA256 (RFC 5758 section 3.2)
                //   ecdsa-with-SHA384 (RFC 5758 section 3.2)
                //   ecdsa-with-SHA512 (RFC 5758 section 3.2)
                //   sha256WithRSAEncryption (RFC 8017 appendix A.2.4)
                //   sha384WithRSAEncryption (RFC 8017 appendix A.2.4)
                //   sha512WithRSAEncryption (RFC 8017 appendix A.2.4)
                //   id-RSASSA-PSS (RFC 8017 appendix A.2.3)
                //   id-Ed25519 (RFC 8410 section 3u).
                // 
                if (!Arrays.asList("1.2.840.10045.4.3.2", "1.2.840.10045.4.3.3", "1.2.840.10045.4.3.4", "1.2.840.113549.1.1.11", "1.2.840.113549.1.1.12", "1.2.840.113549.1.1.13", "1.2.840.113549.1.1.10", "1.3.101.112").contains(cert.getSigAlgOID())) {
                    list.add("algorithm " + cert.getSigAlgOID());
                } else if (cert.getSigAlgOID().equals("1.2.840.113549.1.1.10")) {
                    // If the algorithm field of the signatureAlgorithm
                    // field is id-RSASSA-PSS, the parameters field is
                    // of type RSASSA-PSS-params. Its fields shall have
                    // the following requirements: RFC 8017 section A.2.3.
                    // 
                    //   The hashAlgorithm field shall be present.
                    // 
                    //   The algorithm field of the hashAlgorithm field
                    //   shall be one of the following values: (RFC 8017
                    //   appendix B.1): id-sha256 id-sha384 id-sha512
                    // 
                    //   The maskGenAlgorithm field shall be present.
                    // 
                    //   The algorithm field of the parameters field of
                    //   the maskGenAlgorithm field shall be equal to
                    //   the algorithm field of the hashAlgorithm
                    //   field.

                    AlgorithmParameters p = AlgorithmParameters.getInstance(cert.getSigAlgName());
                    p.init(cert.getSigAlgParams());
                    PSSParameterSpec spec = p.getParameterSpec(PSSParameterSpec.class);
                    if (!Arrays.asList("SHA256", "SHA384", "SHA512").contains(spec.getDigestAlgorithm())) {
                        list.add("RSASSA-PSS-params algorithm " + spec.getDigestAlgorithm());
                    } else if (!(spec.getMGFParameters() instanceof MGF1ParameterSpec && spec.getDigestAlgorithm().equals(((MGF1ParameterSpec)spec.getMGFParameters()).getDigestAlgorithm()))) {
                        list.add("RSASSA-PSS-params algorithm != mfg algorithm");
                    }
                }

                if (cert.getPublicKey() instanceof ECPublicKey) {
                    // If the algorithm field of the algorithm field of
                    // the certificate’s subjectPublicKeyInfo is
                    // id-ecPublicKey, the parameters field shall be one
                    // of the following named curves: (RFC 5480 section
                    // 2.1.1.1.): prime256v1 secp384r1 secp521r1
                    // 
                    String crv = new JWK(cert.getPublicKey()).stringValue("crv");
                    if (!Arrays.asList("P-256", "P-384", "P-521").contains(crv)) {
                        list.add("public-key EC curve");
                    }
                } else if (cert.getPublicKey() instanceof RSAPublicKey) {
                    // If the algorithm field of the algorithm field of
                    // the certificate’s subjectPublicKeyInfo is
                    // rsaEncryption or rsaPSS, the modulus field of the
                    // parameters field shall have a length of at least
                    // 2048 bits.
                    int bitlength = ((RSAKey)cert.getPublicKey()).getModulus().bitLength();
                    if (bitlength < 2048) {
                        list.add("public-key RSA bits=" + bitlength);
                    }
                }

                // Version must be v3. RFC 5280 section 4.1.2.1
                if (cert.getVersion() != 3) {
                    list.add("version " + cert.getVersion());
                }

                // The issuerUniqueID and subjectUniqueID optional fields of
                // the TBSCertificate sequence must not be present. RFC 5280
                // section 4.1.2.8
                if (cert.getSubjectUniqueID() != null || cert.getIssuerUniqueID() != null) {
                    list.add("has issuerUniqueID or subjectUniqueID");
                }

                // The Basic Constraints extension must follow RFC 5280
                // section 4.2.1.9. In particular, it must be present with the
                // CA boolean asserted if the certificate issues certificates,
                // and not asserted if it does not.
                if ("ca".equals(purpose) && cert.getBasicConstraints() < 0) {
                    list.add("no basic constraints");
                } else if (!"ca".equals(purpose) && cert.getBasicConstraints() >= 0) {
                    list.add("basic constraints set");
                }

                // The Authority Key Identifier extension must be present in
                // any certificate that is not self-signed. RFC 5280 section
                // 4.2.1.1
                if (cert.getSubjectX500Principal() != null && !cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                    if (cert.getExtensionValue("1.3.6.1.5.5.7.1.1") == null) {
                        list.add("Authority Key Identifier (1.3.6.1.5.5.7.1.1) not set and not self-signed");
                    }
                }

                // The Subject Key Identifier extension must be present in
                // any certificate that acts as a CA. It should be present in
                // end entity certificates. RFC 5280 section 4.2.1.2
                // TODO surely this is every cert?
                if (cert.getSubjectX500Principal() == null) {
                    list.add("no subject");
                }

                // The Key Usage extension must be present and should be
                // marked as critical. Certificates used to sign C2PA manifests
                // must assert the digitalSignature bit. The keyCertSign bit
                // must only be asserted if the cA boolean is asserted in the
                // Basic Constraints extension. RFC 5280 section 4.2.1.3
                boolean[] keyusage = cert.getKeyUsage();
                if ("signing".equals(purpose) && !keyusage[0]) {
                    list.add("keyUsage missing digitalSignature");
                }
                if (keyusage[5] && cert.getBasicConstraints() < 0) {
                    list.add("keyUsage contains keyCertSign");
                }

                // The Extended Key Usage (EKU) extension must be present and
                // non-empty in any certificate where the Basic Constraints
                // extension is absent or the cA boolean is not asserted. These
                // are commonly called "end entity" or "leaf" certificates. RFC
                // 5280 section 4.2.1.12
                if (cert.getBasicConstraints() == 0) {
                    if (!cert.getCriticalExtensionOIDs().contains("2.5.29.37")) {
                        // list.add("extendedKeyUsage not critical"); not listed, should be
                    }
                    List<String> eku = cert.getExtendedKeyUsage();
                    if (eku == null) {
                        list.add("extendedKeyUsage not present");
                    } else if (eku.contains("2.5.29.37.0")) {
                        // The anyExtendedKeyUsage EKU (2.5.29.37.0) must not be present.
                        list.add("extendedKeyUsage contains 2.5.29.37.0");
                    } else if ("signing".equals(purpose) && !eku.contains("1.3.6.1.5.5.7.3.4")) {
                        // If the configuration store contains a list of EKUs, a
                        // certificate that signs C2PA manifests must be valid for at
                        // least one of the listed purposes.
                        //
                        // If the configuration store does not contain a list of
                        // EKUs, a certificate that signs C2PA manifests must be valid
                        // for the id-kp-emailProtection (1.3.6.1.5.5.7.3.4) purpose.
                        //
                        //    The id-kp-emailProtection purpose is not implicitly
                        //    included by default if a list of EKUs has been configured. If
                        //    desired, it must explicitly be added to the list in the
                        //    configuration store.
                        list.add("extendedKeyUsage missing 1.3.6.1.5.5.7.3.4");
                    } else if ("timestamp".equals(purpose) && !eku.contains("1.3.6.1.5.5.7.3.8")) {
                        // A certificate that signs time-stamping countersignatures
                        // must be valid for the id-kp-timeStamping (1.3.6.1.5.5.7.3.8)
                        // purpose.
                        list.add("extendedKeyUsage missing 1.3.6.1.5.5.7.3.8");
                    } else if ("timestamp".equals(purpose) && eku.contains("1.3.6.1.5.5.7.3.8")) {
                        // If a certificate is valid for either id-kp-timeStamping or
                        // id-kp-OCSPSigning, it must be valid for exactly one of those
                        // two purposes, and not valid for any other purpose.
                        if (eku.size() > 1) {
                            list.add("extendedKeyUsage contains not only 1.3.6.1.5.5.7.3.8");
                        }
                    } else if ("ocsp".equals(purpose) && !eku.contains("1.3.6.1.5.5.7.3.9")) {
                        // A certificate that signs OCSP responses for certificates
                        // must be valid for the id-kp-OCSPSigning (1.3.6.1.5.5.7.3.9)
                        // purpose.
                        list.add("extendedKeyUsage missing 1.3.6.1.5.5.7.3.9");
                    } else if ("ocsp".equals(purpose) && !eku.contains("1.3.6.1.5.5.7.3.9")) {
                        // If a certificate is valid for either id-kp-timeStamping or
                        // id-kp-OCSPSigning, it must be valid for exactly one of those
                        // two purposes, and not valid for any other purpose.
                        if (eku.size() > 1) {
                            list.add("extendedKeyUsage contains not only 1.3.6.1.5.5.7.3.9");
                        }
                    }
                }

                // A certificate should not be valid for any other purposes
                // outside of the purposes listed above, but the presence of any
                // EKUs not mentioned in this profile and not in the list of
                // EKUs in the configuration store shall not cause the
                // certificate to be rejected.
                // NOOP
            } catch (Exception e) {
                list.add("parsing exception");
                e2 = e;
            }

            for (String s : list) {
                // System.out.println("## ix="+ix+" p="+purpose+": "+s);
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(purpose);
                sb.append("cert");
                if ("ca".equals(purpose)) {
                    sb.append(" ");
                    sb.append(ix);
                }
                sb.append(": ");
                sb.append(s);
            }
            list.clear();

            purpose = "ca";
        }

        if (sb.length() > 0) {
            C2PAException e = new C2PAException(C2PAStatus.signingCredential_invalid, sb.toString());
            if (e2 != null) {
                e.initCause(e2);
            }
            // System.out.println(certs);
            throw e;
        }
    }
}

// TODO
// * add hard bindings (datahash)
// * require certs in key
// * look at timestamps; sigTst?
// * look at update manifests
// * add credential store: https://c2pa.org/specifications/specifications/1.0/specs/C2PA_Specification.html#_credential_storage
