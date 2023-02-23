package com.bfo.box;

import java.io.*;
import java.util.*;
import java.security.*;
import com.bfo.json.*;

/**
 * <p>
 * The  <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_manifests">manifest box</a>
 * represents a signed sequence of assertions. There is at least one manifest for any {@link C2PAStore store box}.
 * A valid manifest has one or more assertions, a {@link C2PAClaim claim box} which lists some or all of those assertions
 * along with some additional metadata, and a {@link C2PASignature signature box} which signs the claim.
 * </p>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * C2PAStore store = new C2PAStore();
 * <i>// Add a manifest to the store</i>
 * C2PAManifest manifest = new C2PAManifest("urn:foo");
 * store.getManifests().add(manifest);
 * <i>// Set some assertions on the manifest</i>
 * Json j = Json.read("{\"@context\":\"https://schema.org/\",\"@type\":\"VideoObject\",\"name\":\"LearnJSON-LD\",\"author\":{\"@type\":\"Person\",\"name\":\"Foo Bar\"}}");
 * manifest.getAssertions().add(new JsonContainerBox("stds.schema-org.CreativeWork", j));
 * <i>// Initialize the claim</i>
 * manifest.getClaim().setFormat("video/mp4");          // required
 * manifest.getClaim().setInstanceID("urn:foo");        // required
 * <i>// Sign the manifest's claim</i>
 * KeyStore keystore = KeyStore.getInstance("PKCS12");
 * keystore.load(new FileInputStream(keystorefile), keystorepassword);
 * PrivateKey key = (PrivateKey)keystore.getKey(keystorealias, keystorepassword);
 * List&lt;gX509Certificate&gt; certs = new ArrayList&lt;X509Certificate&gt;();
 * for (Certificate c : keystore.getCertificateChain(keystorealias)) {
 *     if (c instanceof X509Certificate) {
 *         certs.add((X509Certificate)c);
 *     }
 * }
 * manifest.getSignature().sign(key, certs);
 * <i>All done!</i>
 * byte[] c2pa = store.getEncoded();
 * </pre>
 * @since 5
 */
public class C2PAManifest extends JUMBox {

    private InputStream inputStream;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public C2PAManifest() {
    }

    /**
     * Create a new C2PAManifest
     * @param uuid the UUID, which must not be null
     */
    public C2PAManifest(String uuid) {
        super("c2ma", uuid);
        if (label() == null) {
            throw new IllegalArgumentException("uuid is null");
        }
    }

    /**
     * Verifying C2PA requires verifying the data - this method need to be
     * called before verifying the assertions returned by {@link #getAssertions}
     * to supply that data.
     * @param in the InputStream to read from
     */
    public void setInputStream(InputStream in) {
        this.inputStream = in;
    }

    /**
     * Retrieves the InputStream set by {@link #setInputStream} <i>and clears it</i>.
     * Calling the method a second time will return null.
     * @return the InputStream to read from
     */
    public InputStream getInputStream() {
        InputStream in = inputStream;
        inputStream = null;
        return in;
    }

    /**
     * Return a live list of <i>assertions</i>, which can be edited.
     * @return the assertion list
     */
    public List<C2PA_Assertion> getAssertions() {
        Box c2as = null;
        for (Box b=first();b!=null;b=b.next()) {
            if (b instanceof JUMBox && ((JUMBox)b).subtype().equals("c2as")) {
                c2as = b;
                break;
            }
        }
        if (c2as == null) {
            add(c2as = new JUMBox("c2as", "c2pa.assertions"));
        }
        // hack! here is where we upcast any assertions we didn't find
        // into instanceof of C2PA_AssertionUnknown, move the children
        // across and replace the original
        for (Box b=c2as.first();b!=null;b=b.next()) {
            if (!(b instanceof C2PA_Assertion || b instanceof JumdBox)) {
                Box b2 = new C2PA_AssertionUnknown();
                while (b.first() != null) {
                    Box f = b.first();
                    f.replace(null);
                    b2.add(f);
                }
                b.replace(b2);
                b = b2;
            }
        }
        return new BoxList<C2PA_Assertion>(c2as, C2PA_Assertion.class);
    }

    /**
     * Return the {@link C2PAClaim claim} object, creating it if required
     * @return the claim
     */
    public C2PAClaim getClaim() {
        C2PAClaim claim = null;
        for (Box b=first();b!=null;b=b.next()) {
            if (b instanceof C2PAClaim) {
                claim = (C2PAClaim)b;
                break;
            }
        }
        if (claim == null) {
            add(claim = new C2PAClaim(null));
        }
        return claim;
    }

    /**
     * Return the {@link C2PASignature signature} object, creating it if required
     * @return the signature
     */
    public C2PASignature getSignature() {
        C2PASignature claim = null;
        for (Box b=first();b!=null;b=b.next()) {
            if (b instanceof C2PASignature) {
                claim = (C2PASignature)b;
                break;
            }
        }
        if (claim == null) {
            add(claim = new C2PASignature(null));
        }
        return claim;
    }

    void verifyExactlyOneHash() {
        int count = 0;
        for (C2PA_Assertion a : getAssertions()) {
            if (a instanceof C2PA_AssertionHashData || a instanceof C2PA_AssertionHashBMFF) {
                count++;
            }
        }
        if (count > 1) {
            throw new IllegalStateException("manifest has multiple hard-binding [assertion.multipleHardBindings]");
        } else if (count == 0) {
            throw new IllegalStateException("manifest has no hard-bindings [claim.hardBindings.missing]");
        }
    }

    MessageDigest getMessageDigest(Json j) {
        String alg = null;
        while (alg == null && j != null) {
            alg = j.stringValue("alg");
            j = j.parent();
        }
        if (alg == null) {
            alg = getClaim().getHashAlgorithm();
        }
        MessageDigest dig;
        try {
            dig = MessageDigest.getInstance(alg);
            return dig;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("hash alg \"" + alg + "\" not found [algorithm.unsupported]", e);
        }
    }

}
