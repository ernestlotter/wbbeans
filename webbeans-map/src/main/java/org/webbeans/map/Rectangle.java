package org.webbeans.map;

import java.util.Map;

/** A latitude/longitude box. */
public class Rectangle extends PathOverlay {

    private LatLngBounds bounds;

    Rectangle(String id, LatLngBounds bounds) {
        super(id);
        this.bounds = bounds;
    }

    public LatLngBounds getBounds() {
        return bounds;
    }

    public Rectangle setBounds(LatLngBounds b) {
        bounds = b;
        changed();
        return this;
    }

    @Override
    public Rectangle setColor(String css) {
        super.setColor(css);
        return this;
    }

    @Override
    public Rectangle setWeight(double px) {
        super.setWeight(px);
        return this;
    }

    @Override
    public Rectangle setDash(String pattern) {
        super.setDash(pattern);
        return this;
    }

    @Override
    public Rectangle setFillColor(String css) {
        super.setFillColor(css);
        return this;
    }

    @Override
    public Rectangle setFillOpacity(double o) {
        super.setFillOpacity(o);
        return this;
    }

    @Override
    public Rectangle setFill(boolean b) {
        super.setFill(b);
        return this;
    }

    @Override
    public Rectangle setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public Rectangle setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public Rectangle setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    String kind() {
        return "rect";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("b", bounds.toJson());
        writeStyle(m);
    }
}
