package com.bfo.box;

import java.io.*;
import java.util.*;
import com.bfo.json.*;

/**
 * The <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_c2pa_box_details">store box</a>
 * is the top-level box for any C2PA object. It contains one or more {@link C2PAManifest manifest boxes}
 * which must be added to {@link #getManifests}
 * @since 5
 */
public class C2PAStore extends JUMBox {

    /**
     * Create a new C2PAStore
     */
    public C2PAStore() {
    }

    /**
     * Return a live list of the {@link C2PAManifest manifest} objects in this store
     * @return the list of manifests
     */
    public List<C2PAManifest> getManifests() {
        if (first() == null) {
           add(new JumdBox("c2pa", "c2pa"));
        }
        return new BoxList<C2PAManifest>(this, C2PAManifest.class);
    }

    /**
     * Return the active manifest, which is just the last one
     * in the list returned by {@link #getManifests}
     * @return the active manifest
     */
    public C2PAManifest getActiveManifest() {
        List<C2PAManifest> l = getManifests();
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /**
     * Return a representation of this store as a single Json object.
     * The returned value is not live, changes will not affect this object.
     * The object should be largely comparable to the output from <code>c2patool</code> 
     * @return the json
     */
    public Json toJson() {
        Json out = Json.read("{\"manifests\":{}}");
        for (C2PAManifest manifest : getManifests()) {
            Json m = Json.read("{}");
            out.get("manifests").put(manifest.label(), m);
            m.put("claim", manifest.getClaim().getBox().cbor().duplicate().sort());
            Json al = Json.read("{}");
            m.put("assertion_store", al);
            for (C2PA_Assertion assertion : manifest.getAssertions()) {
                if (assertion instanceof JsonContainerBox) {
                    al.put(assertion.asBox().label(), ((JsonContainerBox)assertion.asBox()).getBox().json().duplicate().sort());
                } else if (assertion instanceof CborContainerBox) {
                    al.put(assertion.asBox().label(), ((CborContainerBox)assertion.asBox()).getBox().cbor().duplicate().sort());
                } else if (assertion instanceof EmbeddedFileContainerBox) {
                    al.put(assertion.asBox().label(), ((EmbeddedFileContainerBox)assertion.asBox()).data());
                } else {
                    al.put(assertion.asBox().label(), assertion.asBox().getEncoded());
                }
            }
            COSE signature = manifest.getSignature().cose();
            m.put("signature.alg", signature.getAlgorithm(0));
            if (!signature.getCertificates().isEmpty()) {
                m.put("signature.issuer", signature.getCertificates().get(0).getSubjectX500Principal().getName());
            }
            m.put("signature.length", signature.toCbor().limit());
            m.put("signature.cose", signature);
        }
        return out;
    }

}
