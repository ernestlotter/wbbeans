package org.webbeans.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A filled ring, optionally with holes. */
public class Polygon extends PathOverlay {

    private List<LatLng> ring;
    private final List<List<LatLng>> holes = new ArrayList<List<LatLng>>();

    Polygon(String id, List<LatLng> ring) {
        super(id);
        this.ring = new ArrayList<LatLng>(ring);
    }

    public List<LatLng> getRing() {
        return Collections.unmodifiableList(ring);
    }

    public Polygon setRing(List<LatLng> pts) {
        ring = new ArrayList<LatLng>(pts);
        changed();
        return this;
    }

    public Polygon addHole(List<LatLng> hole) {
        holes.add(new ArrayList<LatLng>(hole));
        changed();
        return this;
    }

    public LatLngBounds getBounds() {
        return LatLngBounds.of(ring);
    }

    /** Area in square metres (holes subtracted). */
    public double getArea() {
        double a = Geo.area(ring);
        for (List<LatLng> h : holes) {
            a -= Geo.area(h);
        }
        return Math.max(0, a);
    }

    @Override
    public Polygon setColor(String css) {
        super.setColor(css);
        return this;
    }

    @Override
    public Polygon setWeight(double px) {
        super.setWeight(px);
        return this;
    }

    @Override
    public Polygon setDash(String pattern) {
        super.setDash(pattern);
        return this;
    }

    @Override
    public Polygon setFillColor(String css) {
        super.setFillColor(css);
        return this;
    }

    @Override
    public Polygon setFillOpacity(double o) {
        super.setFillOpacity(o);
        return this;
    }

    @Override
    public Polygon setFill(boolean b) {
        super.setFill(b);
        return this;
    }

    @Override
    public Polygon setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public Polygon setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public Polygon setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public Polygon setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    String kind() {
        return "polygon";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        List<Object> rings = new ArrayList<Object>();
        rings.add(MapView.pointsJson(ring));
        for (List<LatLng> h : holes) {
            rings.add(MapView.pointsJson(h));
        }
        m.put("rings", rings);
        writeStyle(m);
    }
}
