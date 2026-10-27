package org.webbeans.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A line through points. */
public class Polyline extends PathOverlay {

    private List<LatLng> points;
    private boolean arrow;

    Polyline(String id, List<LatLng> points) {
        super(id);
        this.points = new ArrayList<LatLng>(points);
    }

    public List<LatLng> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public Polyline setPoints(List<LatLng> pts) {
        points = new ArrayList<LatLng>(pts);
        changed();
        return this;
    }

    public Polyline addPoint(LatLng p) {
        points.add(p);
        changed();
        return this;
    }

    /** An arrowhead at the end. */
    public Polyline setArrow(boolean b) {
        arrow = b;
        changed();
        return this;
    }

    public LatLngBounds getBounds() {
        return LatLngBounds.of(points);
    }

    /** Length in metres. */
    public double getLength() {
        return Geo.length(points);
    }

    @Override
    public Polyline setColor(String css) {
        super.setColor(css);
        return this;
    }

    @Override
    public Polyline setWeight(double px) {
        super.setWeight(px);
        return this;
    }

    @Override
    public Polyline setDash(String pattern) {
        super.setDash(pattern);
        return this;
    }

    @Override
    public Polyline setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public Polyline setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public Polyline setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public Polyline setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    String kind() {
        return "line";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("pts", MapView.pointsJson(points));
        writeStyle(m);
        if (arrow) {
            m.put("arrow", true);
        }
    }
}
