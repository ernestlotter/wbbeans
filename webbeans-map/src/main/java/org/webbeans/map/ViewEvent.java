package org.webbeans.map;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The view moved or zoomed (already applied to the map's centre/zoom/bounds). */
public class ViewEvent extends Event {

    public ViewEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public MapView getMap() {
        return (MapView) getSource();
    }

    public LatLng getCenter() {
        return getMap().getCenter();
    }

    public double getZoom() {
        return getMap().getZoom();
    }

    public LatLngBounds getBounds() {
        return getMap().getBounds();
    }
}
