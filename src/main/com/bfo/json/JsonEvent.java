package com.bfo.json;

/**
 * <p>
 * An event object which notifies a {@link JsonListener} of changes to a Json object.
 * Events are fired on Json object for any changes to it or its descendants, so its
 * possible to use this to audit changes to the object. Here's an example.
 * </p>
 * <pre style="background: #EEE; border: 1px solid #AAA; font-size: 0.8em">
 * json.addListener(new JsonListener() {
 *     public void jsonEvent(Json root, JsonEvent event) {
 *     if (event.after == null) {
 *         // this event is fired just before "event.before" was removed.
 *         System.out.println(root.path(event.before) + " removed");
 *     } else {
 *         // this event is fired just after "event.after" was added.
 *         // if event.before is not null it was the value before removal
 *         System.out.println(root.path(event.after) + " added");
 *     }
 * }
 * </pre>
 */
public class JsonEvent {

    /**
     * The Json object before the event. Will have a parent only if after == null.
     */
    public final Json before;

    /**
     * The Json object after the event. If not null, this value will have a parent.
     */
    public final Json after;

    /**
     * Create a new JsonEvent
     * @param before the Json object before the event
     * @param after the Json object after the event
     */
    public JsonEvent(Json before, Json after) {
        this.before = before;
        this.after = after;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"before\":\"");
        sb.append(before);
        sb.append("\",\"after\":");
        sb.append(after);
        sb.append('}');
        return sb.toString();
    }

}
