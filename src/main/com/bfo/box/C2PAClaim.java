package com.bfo.box;

import java.io.*;
import java.util.*;
import com.bfo.json.*;

/**
 * The <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_claims">claim box</a>
 * used to stored the claim in a {@link C2PAManifest manifest box}. Every manifest has exactly one claim.
 * @see C2PAManifest#getClaim
 * @since 5
 */
public class C2PAClaim extends CborContainerBox {

    private List<JUMBox> assertionList;
    private byte[] data;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    protected C2PAClaim() {
    }

    C2PAClaim(Json cbor) {
        super("c2cl", "c2pa.claim", cbor);
    }

    /**
     * Set the format - this must be set before signing
     * @param format the format, eg "application/pdf"
     */
    public void setFormat(String format) {
        cbor().put("dc:format", format);
    }

    /**
     * Return the format, as loaded or set by {@link #setFormat}
     * @return the format
     */
    public String getFormat() {
        return cbor().stringValue("dc:format");
    }

    /**
     * Set the instance ID - this should be a UUID and must be set before signing
     * @param id the id
     */
    public void setInstanceID(String id) {
        cbor().put("instanceID", id);
    }

    /**
     * Return the instance ID, as loaded or set by {@link #setInstanceID}
     * @return the instanace ID
     */
    public String getInstanceID() {
        return cbor().stringValue("instanceID");
    }

    /**
     * Set the hash algorithm used to hash any assertions.
     * If this is not set before signing, it will be set to SHA256
     * @param alg the hash algorithm, which should be a valid Java Hash algorithm name.
     */
    public void setHashAlgorithm(String alg) {
        cbor().put("alg", alg);
    }

    /**
     * Return the hash algorithm used to hash any assertions,
     * as loaded or set by {@link #setHashAlgorithm}.
     * @return the hash algorithm
     */
    public String getHashAlgorithm() {
        return cbor().stringValue("alg");
    }

    /**
     * Return the list of assertions that will be part of this claim.
     * Although this list is live can be edited, if it is blank whan the manifest
     * is signed, it will be set to the same value as {@link C2PAManifest#getAssertions}.
     * All assertions added must be present in that list; all assertions that are returned
     * should be too, and if not the value <code>null</code> will be returned for that entry
     * and the entry cleared when the manifest is signed.
     * @return a live list of all the assertions that will be part of this claim.
     */
    public List<JUMBox> getAssertions() {
        if (assertionList == null) {
            final Json assertions;
            final C2PAManifest manifest = (C2PAManifest)parent();
            final List<JUMBox> manifestAssertions = manifest.getAssertions();
            if (cbor().isList("assertions")) {
                assertions = cbor().get("assertions");
            } else {
                cbor().put("assertions", assertions = Json.read("[]"));
            }
            assertionList = new AbstractList<JUMBox>() {
                @Override public int size() {
                    return assertions.size();
                }
                @Override public JUMBox get(int i) {
                    if (i < 0 || i >= size()) {
                        throw new IndexOutOfBoundsException(Integer.toString(i));
                    }
                    String url = assertions.get(i).stringValue("url");
                    Box box = manifest.find(url);
                    return box instanceof JUMBox ? (JUMBox)box : null;
                }
                @Override public JUMBox set(int i, JUMBox box) {
                    if (i < 0 || i >= size()) {
                        throw new IndexOutOfBoundsException(Integer.toString(i));
                    }
                    JUMBox old = get(i);
                    if (old != box) {
                        String url = manifest.find(box);
                        if (!manifestAssertions.contains(box) || url == null) {
                            throw new IllegalArgumentException("not in manifest assertions");
                        }
                        Json j = Json.read("{}");
                        j.put("url", "self#jumbf=" + url);
                        assertions.put(i, j);
                    }
                    return old;
                }
                @Override public JUMBox remove(int i) {
                    if (i < 0 || i >= size()) {
                        throw new IndexOutOfBoundsException(Integer.toString(i));
                    }
                    JUMBox old = get(i);
                    assertions.remove(i);
                    return old;
                }
                @Override public boolean add(JUMBox box) {
                    String url = manifest.find(box);
                    if (!manifestAssertions.contains(box) || url == null) {
                        throw new IllegalArgumentException("not in manifest assertions");
                    }
                    Json j = Json.read("{}");
                    j.put("url", "self#jumbf=" + url);
                    assertions.put(assertions.size(), j);
                    return true;
                }
            };
        }
        return assertionList;
    }

    /**
     * Set the "claim generator", a user-agent string identifying the tooling
     * that created this C2PA claim. If not set before signing, a default value
     * will be used as this is a mandatory field.
     * @param generator the claim_generator id
     * @param properties an optional Json that will be stored as
     * <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_claim_generator_info">claim_generator_info</a>
     * in the C2PA object. This is supposed to be mandatory, but is missing from several official examples - we accept null.
     */
    public void setGenerator(String generator, Json properties) {
        if (generator == null) {
            throw new IllegalArgumentException("generator is null");
        }
        cbor().put("claim_generator", generator);
        if (properties == null || !properties.isList()) {
            // Apparently it's required for "compat with earlier versions", but not in examples.
            cbor().remove("claim_generator_info");
        } else {
            cbor().put("claim_generator_info", properties);
        }
    }

    /**
     * Return the "claim_generator" field, as loaded or set by {@link #setGenerator}
     * @return the claim_generator
     */
    public String getGenerator() {
        return cbor().stringValue("claim_generator");
    }

}
