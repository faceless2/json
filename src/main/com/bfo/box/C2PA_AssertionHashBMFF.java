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

    public C2PA_AssertionHashBMFF(boolean v2) {
        super("cbor", v2 ? "c2pa.hash.bmff.v2" : "c2pa.hash.bmff");
    }

    public boolean isV2() {
        return "c2pa.hash.bmff.v2".equals(label());
    }

    @Override public void verify() {
        getManifest().verifyExactlyOneHash();
    }

}
