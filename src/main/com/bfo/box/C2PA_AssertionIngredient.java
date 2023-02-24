package com.bfo.box;

import com.bfo.json.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.ingredient" type
 * @since 5
 */
public class C2PA_AssertionIngredient extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionIngredient() {
        super("cbor", "c2pa.ingredient");
    }

    /**
     * Create a new assertion
     * @param label the label (will default to "c2pa.ingredient" if null)
     * @param json the Json to initialize the assertion with
     */
    public C2PA_AssertionIngredient(String label, Json json) {
        super("cbor", label == null ? "c2pa.ingredient" : label, json);
    }

    String getTargetManifestURL() {
        return cbor().has("c2pa_manifest") ?  cbor().get("c2pa_manifest").stringValue("url") : null;
    }

    /**
     * If this ingredient has a c2pa_manifest value, return the target manifest, or null if
     * it's not specified or can't be found
     */
    public C2PAManifest getTargetManifest() {
        String url = getTargetManifestURL();
        JUMBox box = getManifest().find(url);
        return box instanceof C2PAManifest ? (C2PAManifest)box : null;
    }

    boolean hasTargetManifest() {
        return cbor().has("c2pa_manifest");
    }

    /**
     * Return the specified relationship between this ingredient and the manifest it refers to
     * @return one of "parentOf", "componentOf"
     */
    public String relationship() {
        return cbor().stringValue("relationship");
    }

    @Override public void verify() throws C2PAException {
        //
        // Validate that there are zero or one c2pa.ingredient assertions whose
        // relationship is parentOf. If there is more than one, the manifest must be
        // rejected with a failure code of manifest.multipleParents.
        //
        //     -- https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_validate_the_correct_assertions_for_the_type_of_manifest

        int count = 0;
        for (C2PA_Assertion a : getManifest().getAssertions()) {
            if (a instanceof C2PA_AssertionIngredient && "parentOf".equals(((C2PA_AssertionIngredient)a).cbor().stringValue("relationship")))  {
                count++;
            }
        }
        if (count > 1) {
            throw new C2PAException(C2PAStatus.manifest_multipleParents, "manifest has multiple \"parentOf\" c2pa.ingredient assertions");
        }

        if (cbor().isMap("c2pa_manifest")) {
            if (getTargetManifest() == null) {
                throw new C2PAException(C2PAStatus.claim_missing, "\"" + getTargetManifestURL() + "\" not in manifest");
            }

            // adobe-20220124-E-uri-CIE-sig-CA.jpg
            // 
            //   active manifest ends in 644a63d1f7d0: the "c2pa.ingredient" refers to another manifest,
            //   7af56501ce4b, which has been deliberately altered. So this would be invalid IFF were
            //   are to recursively validate ingredients. However it does have a "validationStatus"
            //   showing it was validated as a failure.
            // 
            // adobe-20220124-CACA.jpg
            // 
            //   active manifest ends in f85380524443: the "c2pa.ingredient" assertion refers to another
            //   manifest 7af56501ce4b with a digest "3epjVN8X1spZW0Z6TYQO/6owR7xADaDDVzeeDBOGV4g=",
            //   but that manifest doesn't digest this way. Moreover, if I change one byte in that
            //   manifest the overall signature fails, but "c2pa.ingredient" does not. So based on this,
            //   we not NOT supposed to recursively validate ingredients.
            //
            // Conclusion: recursively validating manifests is NOT done, but if there is a validationStatus
            // listed, we will report that.

            if (cbor().isList("validationStatus")) {
                Json vals = cbor().get("validationStatus");
                for (int i=0;i<vals.size();i++) {
                    String code = vals.get(i).stringValue("code");
                    if (code != null) {
                        for (C2PAStatus status : C2PAStatus.values()) {
                            if (status.toString().equals(code) && status.isError()) {
                                C2PAException nest = new C2PAException(status, vals.get(i).stringValue("explanation"));
                                C2PAException e = new C2PAException(C2PAStatus.ingredient_hashedURI_mismatch, "referenced ingredient at \"" + getTargetManifestURL() + "\" validationStatus has error");
                                e.initCause(nest);
                                throw e;
                            }
                        }
                    }
                }
            }
            // If we were recursive, we'd do this
            // C2PASignature.digestHashedURL(cbor().get("c2pa_manifest"), getManifest(), true);
        }
    }

}
