package org.webbeans.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.core.json.JsonValue;

/**
 * A static resource inside a module jar (an icon, an image): {@code Resource.of("org/example/icons/save.svg")}.
 * Served at {@code /wb/res/<path>} and sent on the wire as {@code {"$res": path}}.
 */
public final class Resource implements JsonValue {

    private final String path;

    private Resource(String path) {
        this.path = path;
    }

    public static Resource of(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("..")) {
            throw new IllegalArgumentException("Invalid resource path " + path);
        }
        return new Resource(path);
    }

    public String getPath() {
        return path;
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("$res", path);
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Resource && ((Resource) o).path.equals(path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "Resource[" + path + "]";
    }
}
