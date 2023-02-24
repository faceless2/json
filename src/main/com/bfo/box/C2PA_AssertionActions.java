package com.bfo.box;

import com.bfo.json.*;
import java.util.*;

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

    @Override public void verify() throws C2PAException {
        // For each action in the actions list:
        //
        // If the action field is c2pa.opened, c2pa.placed, c2pa.removed,
        // c2pa.repackaged, or c2pa.transcoded:.
        //
        // * Check the ingredient field that is a member of the parameters object for the
        //   presence of a JUMBF URI. If the JUMBF URI is not present, or cannot be resolved
        //   to the related ingredient assertion, the claim must be rejected with a failure
        //   code of assertion.action.ingredientMismatch..
        //
        // * Follow the JUMBF URI link in the ingredient field to the ingredient
        //   assertion. Check that the URI link resolves to an assertion in the active
        //   manifest. If it does not, the claim must be rejected with a failure code of
        //   assertion.action.ingredientMismatch.
        //
        // * For c2pa.opened, c2pa.repackaged, or c2pa.transcoded: Check that the value
        //   of the relationship field is parentOf. If it is not, the claim must be rejected
        //   with a failure code of assertion.action.ingredientMismatch..
        //
        // * For c2pa.placed or c2pa.removed: Check that the value of the relationship
        //   field is componentOf. If it is not, the claim must be rejected with a failure
        //   code of assertion.action.ingredientMismatch.
        //
        // * Check the c2pa_manifest field in the ingredient assertion for the presence
        //   of a hashed URI. If the hashed URI is not present, or cannot be resolved to a
        //   manifest, the claim must be rejected with a failure code of
        //   assertion.action.ingredientMismatch.
        //
        //  -- https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_assertion_validation

        Json actions = cbor().get("actions");
        for (int i=0;i<actions.size();i++) {
            Json action = actions.get(i);
            String type = action.stringValue("action");
            if (Arrays.asList("c2pa.opened", "c2pa.placed", "c2pa.removed", "c2pa.repackaged", "c2pa.transcoded").contains(type)) {
                String url = action.hasPath("parameters.ingredient.url") ? action.getPath("parameters.ingredient").stringValue("url") : null;
                JUMBox box = getManifest().find(url);
                if (box == null) {
                    throw new C2PAException(C2PAStatus.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" not found");
                }
                if (!(box instanceof C2PA_AssertionIngredient && ((C2PA_Assertion)box).getManifest() == getManifest())) {
                    throw new C2PAException(C2PAStatus.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" in different manifest");
                }
                C2PA_AssertionIngredient ingredient = (C2PA_AssertionIngredient)box;
                String relationship = ingredient.cbor().stringValue("relationship");
                if (Arrays.asList("c2pa.opened", "c2pa.repackaged", "c2pa.transcoded").contains(type) && !"parentOf".equals(relationship)) {
                    throw new C2PAException(C2PAStatus.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" relationship \"" + relationship + "\"");
                }
                if (Arrays.asList("c2pa.placed", "c2pa.removed").contains(type) && !"componentOf".equals(relationship)) {
                    throw new C2PAException(C2PAStatus.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" relationship \"" + relationship + "\"");
                }
                if (ingredient.hasTargetManifest()) {
                    C2PAManifest target = ingredient.getTargetManifest();
                    if (target == null) {
                        throw new C2PAException(C2PAStatus.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" manifest \"" + ingredient.getTargetManifestURL() + "\" not found");
                    }
                }
            }
        }

    }

}
