package org.webbeans.map;

import java.util.LinkedHashMap;
import java.util.Map;

/** A named group of overlays the user can switch on and off in the layer control. */
public final class MapLayer {

    private final String id;
    private final MapView map;
    private String name;
    private boolean visible = true;

    MapLayer(String id, String name, MapView map) {
        this.id = id;
        this.name = name;
        this.map = map;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MapLayer setName(String n) {
        name = n;
        map.layerChanged(this);
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public MapLayer setVisible(boolean b) {
        visible = b;
        map.layerChanged(this);
        return this;
    }

    void toggledByUser(boolean b) {
        visible = b;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("vis", visible);
        return m;
    }
}
