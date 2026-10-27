package org.webbeans.map;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** A pointer event on the map: where (lat/lng and page coordinates) and on which overlay, if any. */
public class MapEvent extends Event {

    public MapEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public MapView getMap() {
        return (MapView) getSource();
    }

    /** The position under the pointer, or null when the event carries none. */
    public LatLng getPosition() {
        return LatLng.fromJson(getData().get("p"));
    }

    /** The overlay under the pointer, or null for the base map. */
    public Overlay getOverlay() {
        return getMap().getOverlay(getString("id"));
    }

    /** For GeoJSON overlays: the index of the feature hit, else −1. */
    public int getFeature() {
        return getInt("f", -1);
    }

    /** For GeoJSON overlays: the feature's {@code id} or {@code properties.id}, or null. */
    public String getFeatureId() {
        return getString("fid");
    }

    /** Page coordinates (for popups). */
    public int getX() {
        return getInt("x", 0);
    }

    public int getY() {
        return getInt("y", 0);
    }
}
