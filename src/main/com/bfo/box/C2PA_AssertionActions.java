package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A C2PA Assertion for the "c2pa.actions" type
 * @since 5
 */
public class C2PA_AssertionActions extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionActions() {
        super("cbor", "c2pa.actions");
    }

}
