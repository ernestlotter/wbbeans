package org.webbeans.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named actions of a component (NetBeans/Swing {@code ActionMap}): callback actions such as {@code "copy"},
 * {@code "cut"}, {@code "paste"}, {@code "delete"}, {@code "rename"} that global menu entries delegate to.
 */
public final class ActionMap {

    public static final String COPY = "copy";
    public static final String CUT = "cut";
    public static final String PASTE = "paste";
    public static final String DELETE = "delete";
    public static final String RENAME = "rename";

    private final Map<String, Action> actions = new LinkedHashMap<String, Action>();

    public void put(String key, Action action) {
        if (action == null) {
            actions.remove(key);
        } else {
            actions.put(key, action);
        }
    }

    public Action get(String key) {
        return actions.get(key);
    }

    public Map<String, Action> asMap() {
        return Collections.unmodifiableMap(actions);
    }
}
