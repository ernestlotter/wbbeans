package org.webbeans.map;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Something drawn over the base map: a marker, a shape, an image, a GeoJSON set or a popup. Every setter marks
 * the overlay dirty and the {@link MapView} sends its full description once per flush. Overlays can be grouped
 * into {@link MapView#addLayer layers} the user toggles.
 */
public abstract class Overlay {

    private final String id;
    private MapView map;
    private boolean visible = true;
    private double opacity = 1;
    private String tooltip;
    private String styleName;
    private String layer;
    private int zIndex;
    private boolean interactive = true;
    private Map<String, Object> attributes;

    Overlay(String id) {
        this.id = id;
    }

    public final String getId() {
        return id;
    }

    public final MapView getMap() {
        return map;
    }

    final void setMap(MapView m) {
        map = m;
    }

    protected final void changed() {
        if (map != null) {
            map.overlayChanged(this);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public Overlay setVisible(boolean b) {
        visible = b;
        changed();
        return this;
    }

    public Overlay setOpacity(double o) {
        opacity = Math.max(0, Math.min(1, o));
        changed();
        return this;
    }

    public double getOpacity() {
        return opacity;
    }

    public Overlay setTooltip(String t) {
        tooltip = t;
        changed();
        return this;
    }

    public String getTooltip() {
        return tooltip;
    }

    /** Extra CSS class(es) on the element. */
    public Overlay setStyleName(String css) {
        styleName = css;
        changed();
        return this;
    }

    /** The layer group ({@link MapView#addLayer}); null = always shown. */
    public Overlay setLayer(String layerId) {
        layer = layerId;
        changed();
        return this;
    }

    public String getLayer() {
        return layer;
    }

    /** Drawing order among overlays of the same kind (higher on top). */
    public Overlay setZIndex(int z) {
        zIndex = z;
        changed();
        return this;
    }

    /** Whether the overlay reacts to the pointer (click, hover, drag); false lets clicks fall through to the map. */
    public Overlay setInteractive(boolean b) {
        interactive = b;
        changed();
        return this;
    }

    public boolean isInteractive() {
        return interactive;
    }

    /** A renderer attribute: {@code data-<name>} on the element and {@code --wb-m-<name>} for CSS; null removes it. */
    public Overlay setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<String, Object>();
        }
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
        changed();
        return this;
    }

    public Object getAttribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }

    final Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("k", kind());
        if (!visible) {
            m.put("vis", false);
        }
        if (opacity != 1) {
            m.put("op", opacity);
        }
        put(m, "tt", tooltip);
        put(m, "st", styleName);
        put(m, "ly", layer);
        if (zIndex != 0) {
            m.put("z", zIndex);
        }
        if (!interactive) {
            m.put("ia", false);
        }
        if (attributes != null && !attributes.isEmpty()) {
            m.put("a", new LinkedHashMap<String, Object>(attributes));
        }
        writeJson(m);
        return m;
    }

    abstract String kind();

    abstract void writeJson(Map<String, Object> m);

    static void put(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    static String lower(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
