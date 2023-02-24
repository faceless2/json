package com.bfo.box;

import java.io.*;

/**
 * An interface implemented by JUMBox objects that represent assertions.
 */
public interface C2PA_Assertion {

    /**
     * This default method in the marker interface casts this object to a JUMBox.
     * Every assertion must be a JUMBox
     * @return this
     */
    public default JUMBox asBox() {
        return (JUMBox)this;
    }

    /**
     * Return the manifest box containing this assertion
     * @return the manifest containing this assertion
     */
    public default C2PAManifest getManifest() {
        return (C2PAManifest)asBox().parent().parent();
    }

    /**
     * Verify this assertion. If it fails, throw an IllegalStateException with details of why.
     * The default implementation <b>succeeds</b>.
     * @throws C2PAException if the assertion failed to verify
     * @throws UnsupportedOperationException if the assertion isn't implemented
     * @throws IOException if the assertion verification involved I/O and the IO layer threw an exception
     */
    public default void verify() throws IOException, UnsupportedOperationException, C2PAException {
    }

}
