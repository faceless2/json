package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A C2PA Assertion for the "c2pa.hash.bmff" or "c2pa.hash.bmff.v2" types
 * @since 5
 */
public class C2PA_AssertionHashBMFF extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    protected C2PA_AssertionHashBMFF() {
    }

    /**
     * Create a new BMFF assertion
     * @param v2 if true, create a version 2 BMFF
     */
    public C2PA_AssertionHashBMFF(boolean v2) {
        super("cbor", v2 ? "c2pa.hash.bmff.v2" : "c2pa.hash.bmff");
    }

    /**
     * Return whether this BMFF is version 2
     * @return true if version 2
     */
    public boolean isV2() {
        return "c2pa.hash.bmff.v2".equals(label());
    }

    @Override public void verify() throws C2PAException {
        getManifest().verifyExactlyOneHash();
        // TODO https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_validating_a_bmff_hash
        throw new UnsupportedOperationException(label() + " not yet implemented");
    }

    /**
     * Calculate the digest during signing. Not yet implemented
     */
    public void sign() throws IOException, C2PAException {
        throw new UnsupportedOperationException(label() + " not yet implemented");
    }

}
