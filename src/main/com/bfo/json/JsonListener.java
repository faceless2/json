package com.bfo.json;

/**
 * An interface that can be implemented to monitor changes to the Json object tree
 */
public interface JsonListener {
    
    /**
     * Called when a JsonEvent was fired on the object the listener was added to
     * or one of its descendants
     * @param event the event
     */
    public void jsonEvent(JsonEvent event);

}
