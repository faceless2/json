package com.bfo.json;

/**
 * An interface that can be implemented to monitor changes to the Json object tree.
 * See {@link JsonEvent} for an example of use
 */
public interface JsonListener {
    
    /**
     * Called when a JsonEvent was fired on the object the listener was added to
     * or one of its descendants.
     * @param owner the Json object whose listener this is
     * @param event the event
     */
    public void jsonEvent(Json owner, JsonEvent event);

}
