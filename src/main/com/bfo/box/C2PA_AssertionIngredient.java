package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

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

    public void verify() {
        int count = 0;
        for (C2PA_Assertion a : getManifest().getAssertions()) {
            if (a instanceof C2PA_AssertionIngredient && "parentOf".equals(((C2PA_AssertionIngredient)a).cbor().stringValue("relationship")))  {
                count++;
            }
        }
        if (count > 1) {
            throw new IllegalStateException("manifest has multiple \"parentOf\" c2pa.ingredient assertions [manifest.multipleParents]");
        }

        if (cbor().isMap("c2pa_manifest")) {
            // this is TODO, still having issues
            // specifically:
            //   adobe-20220124-CACA.jpg
            //   * the manifest ending in f85380524443 has assertion "c2pa.ingredient"
            //   * that assertion has a c2pa_manifest referencing a second manifest
            //     ending in 7af56501ce4b, digest "3epjVN8X1spZW0Z6TYQO/6owR7xADaDDVzeeDBOGV4g="
            //   * however that manifest just doesn't digest that way. Verified by digesting
            //     the original bytes as read in; can't make it work.
            //   * c2patool says the file is fine. However if I modify one byte in the
            //     manifest ending in 7af56501ce4b, it fails the file - but it doesn't fail
            //     the "c2pa.ingredient" assertion. Conclusion, it's not checking that
            //     digest.
            //
            // HOWEVER
            //  adobe-20220124-E-clm-CAICAI.jpg
            //  * manifest 762c0362b236 is invalid; "c2pa.ingredient__1" appears to have been
            //    stored with the wrong hash, as again the data is unchanged since loading
            //  * same manifest also has "c2pa.ingredient__1", and this refers to the
            //    missing entry in the c2pa_manifest, "contentbeef:urn:uuid:8bb8ad50-ef2f-4f75-b709-a0e302d58019"
            //    the item is missing; it should invalidate. However if we don't check it then
            //    a0e302d58019 passes. So we check and it fails.
            //  * other manifest (a0e302d58019) is OK. So I think this is now correct
            //  -- in fact this one is correct if we only check the final manifest
            //
            // adobe-20220124-CIE-sig-CA.jpg
            // * no idea, sig validates for both manifests but apparently shouldn't? enduser cert
            //   is OK, test chain. Seems to be todo with the "E-sig-CA.jpg" inside the manifest,
            //   c2patool says there's an issue with timestamp?
            //
            // adobe-20220124-E-dat-CA.jpg
            // * need to verify hash
            //
            // adobe-20220124-E-uri-CIE-sig-CA.jpg
            // * manifest 7af56501ce4b is hashmismatch - looks intentional (deadbeef in data)
            // * manifest 644a63d1f7d0 is fine, but again includes 7af56501ce4b as ingredient
            //   but because we're not validating ingredients, we pass when it should fail
            //

            String url = cbor().get("c2pa_manifest").stringValue("url");
            if (getManifest().find(url) == null) {
                throw new IllegalStateException("URL \"" + url + "\" not in manifest [claim.missing]");
            }
            // C2PASignature.digestHashedURL(cbor().get("c2pa_manifest"), getManifest(), true);
        }
    }

}
