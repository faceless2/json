package com.bfo.box;

public interface C2PA_Assertion {

    /**
     * This default method in the marker interface casts this object to a JUMBox.
     * Every assertion must be a JUMBox
     */
    public default JUMBox asBox() {
        return (JUMBox)this;
    }

    /**
     * Return the manifest box containing this assertion
     */
    public default C2PAManifest getManifest() {
        return (C2PAManifest)asBox().parent().parent();
    }

    /**
     * Verify this assertion. If it fails, throw an IllegalStateException with details of why
     */
    public default void verify() {
    }

}
