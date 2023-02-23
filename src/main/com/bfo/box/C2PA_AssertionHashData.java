package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A C2PA Assertion for the "c2pa.hash.data" type
 * @since 5
 */
public class C2PA_AssertionHashData extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionHashData() {
        super("cbor", "c2pa.hash.data");
    }

    @Override public void verify() {
        getManifest().verifyExactlyOneHash();
    }

}
