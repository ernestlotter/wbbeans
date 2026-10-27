package org.webbeans.map;

import java.util.Map;

/** A circle of a radius in metres (drawn to scale). */
public class Circle extends PathOverlay {

    private LatLng center;
    private double radius;

    Circle(String id, LatLng center, double radiusMetres) {
        super(id);
        this.center = center;
        this.radius = Math.max(0, radiusMetres);
    }

    public LatLng getCenter() {
        return center;
    }

    public Circle setCenter(LatLng c) {
        center = c;
        changed();
        return this;
    }

    public double getRadius() {
        return radius;
    }

    public Circle setRadius(double metres) {
        radius = Math.max(0, metres);
        changed();
        return this;
    }

    public LatLngBounds getBounds() {
        return new LatLngBounds(Geo.destination(center, radius, 225), Geo.destination(center, radius, 45));
    }

    @Override
    public Circle setColor(String css) {
        super.setColor(css);
        return this;
    }

    @Override
    public Circle setWeight(double px) {
        super.setWeight(px);
        return this;
    }

    @Override
    public Circle setDash(String pattern) {
        super.setDash(pattern);
        return this;
    }

    @Override
    public Circle setFillColor(String css) {
        super.setFillColor(css);
        return this;
    }

    @Override
    public Circle setFillOpacity(double o) {
        super.setFillOpacity(o);
        return this;
    }

    @Override
    public Circle setFill(boolean b) {
        super.setFill(b);
        return this;
    }

    @Override
    public Circle setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public Circle setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public Circle setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public Circle setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    String kind() {
        return "circle";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("p", center.toJson());
        m.put("r", radius);
        writeStyle(m);
    }
}
