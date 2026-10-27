package org.webbeans.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The user finished drawing in a {@link MapView.DrawMode}; nothing is added to the map unless a listener does it. */
public class DrawEvent extends Event {

    public DrawEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public MapView getMap() {
        return (MapView) getSource();
    }

    public MapView.DrawMode getMode() {
        try {
            return MapView.DrawMode.valueOf(String.valueOf(getString("mode")).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MapView.DrawMode.NONE;
        }
    }

    /** The drawn points: one for MARKER, the vertices for LINE / POLYGON, two corners for RECTANGLE, centre + rim for CIRCLE. */
    public List<LatLng> getPoints() {
        List<LatLng> out = new ArrayList<LatLng>();
        Object pts = getData().get("pts");
        if (pts instanceof List) {
            for (Object o : (List<?>) pts) {
                LatLng p = LatLng.fromJson(o);
                if (p != null) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** For RECTANGLE: the box; for others the box around the points. */
    public LatLngBounds getBounds() {
        return LatLngBounds.of(getPoints());
    }

    /** For CIRCLE: the radius in metres. */
    public double getRadius() {
        List<LatLng> p = getPoints();
        return p.size() >= 2 ? Geo.distance(p.get(0), p.get(1)) : 0;
    }
}
