package com.bfo.json;

import java.io.*;

/**
 * An event object which notifies a {@link JsonListener} of changes to a Json object.
 */
public class JsonEvent {

    public static enum Type { ADD, REMOVE, CHANGE };

    /**
     * The Json object that the event was raised on
     */
    public final Json json;

    /**
     * The key within that object that was modified
     */
    public final String key;

    /**
     * The type of event that took place
     */
    public final Type type;

    /**
     * Create a new JsonEvent
     * @param json the Json object this event applies to
     * @param key the key that changed
     * @param type the type of event
     */
    public JsonEvent(Json json, String key, Type type) {
        this.json = json;
        this.key = key;
        this.type = type;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"");
        sb.append(type);
        sb.append("\",\"key\":");
        try {
            IString.write(key, sb);
            sb.append(",\"node\":");
            json.write(sb, null);
        } catch (IOException e) { }
        sb.append("}");
        return sb.toString();
    }

}
