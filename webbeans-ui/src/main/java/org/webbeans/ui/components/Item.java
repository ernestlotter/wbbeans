package org.webbeans.ui.components;

import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.ui.Resource;

/** A selectable item of a {@link ComboBox} or list: a key the client sees, a label, an optional icon and description. */
public final class Item implements JsonValue {

    private final String key;
    private final String label;
    private final Resource icon;
    private final String description;
    private final boolean enabled;

    public Item(String key, String label) {
        this(key, label, null, null, true);
    }

    public Item(String key, String label, Resource icon, String description, boolean enabled) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        this.key = key;
        this.label = label == null ? key : label;
        this.icon = icon;
        this.description = description;
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public Resource getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("v", key);
        m.put("l", label);
        if (icon != null) {
            m.put("i", icon.toJson());
        }
        if (description != null) {
            m.put("d", description);
        }
        if (!enabled) {
            m.put("e", Boolean.FALSE);
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Item && ((Item) o).key.equals(key) && ((Item) o).label.equals(label);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
