package org.webbeans.ui;

import java.util.Collections;
import java.util.Map;

import org.openide.util.Lookup;

/** Base class of client events. Subclasses add typed accessors over the data map the client sent. */
public class Event {

    private final Component source;
    private final Map<String, Object> data;

    public Event(Component source, Map<String, Object> data) {
        this.source = source;
        this.data = data == null ? Collections.<String, Object>emptyMap() : data;
    }

    public Component getSource() {
        return source;
    }

    public UI getUI() {
        return source.getUI();
    }

    /** The invocation context: the source component's lookup chain (component → UI → session). */
    public Lookup getLookup() {
        return source.getLookup();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getString(String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    public int getInt(String key, int def) {
        Object v = data.get(key);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public double getDouble(String key, double def) {
        Object v = data.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public boolean getBoolean(String key) {
        return Boolean.TRUE.equals(data.get(key));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + data;
    }
}
