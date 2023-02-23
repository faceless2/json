package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A C2PA Assertion for the "c2pa.soft-binding" type
 * @since 5
 */
public class C2PA_AssertionSoftBinding extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionSoftBinding() {
        super("cbor", "c2pa.soft-binding");
    }

}
