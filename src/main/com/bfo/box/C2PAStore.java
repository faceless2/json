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

}
