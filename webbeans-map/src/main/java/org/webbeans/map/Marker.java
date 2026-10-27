package org.webbeans.map;

import java.util.Map;

import org.webbeans.ui.Resource;

/** A point with an icon (the default pin, tinted with {@link #setColor}, or your own) and an optional label. */
public class Marker extends Overlay {

    private LatLng position;
    private Resource icon;
    private int iconWidth = 24;
    private int iconHeight = 32;
    private Integer anchorX;
    private Integer anchorY;
    private String label;
    private String color;
    private boolean draggable;
    private double rotation;

    Marker(String id, LatLng position) {
        super(id);
        this.position = position;
    }

    public LatLng getPosition() {
        return position;
    }

    public Marker setPosition(LatLng p) {
        position = p;
        changed();
        return this;
    }

    void moved(LatLng p) {
        position = p;
    }

    /** An image resource; null = the default pin. */
    public Marker setIcon(Resource r) {
        icon = r;
        changed();
        return this;
    }

    public Resource getIcon() {
        return icon;
    }

    public Marker setIconSize(int w, int h) {
        iconWidth = w;
        iconHeight = h;
        changed();
        return this;
    }

    /** The point of the icon placed on the position; default bottom-centre. */
    public Marker setIconAnchor(int x, int y) {
        anchorX = x;
        anchorY = y;
        changed();
        return this;
    }

    /** Text shown next to the marker. */
    public Marker setLabel(String l) {
        label = l;
        changed();
        return this;
    }

    public String getLabel() {
        return label;
    }

    /** The default pin's colour (CSS). */
    public Marker setColor(String css) {
        color = css;
        changed();
        return this;
    }

    public String getColor() {
        return color;
    }

    /** The user may drag it; the new position arrives as {@link MapView#DRAG} (already applied). */
    public Marker setDraggable(boolean b) {
        draggable = b;
        changed();
        return this;
    }

    public boolean isDraggable() {
        return draggable;
    }

    /** Rotation of the icon in degrees clockwise (a heading). */
    public Marker setRotation(double deg) {
        rotation = deg;
        changed();
        return this;
    }

    @Override
    public Marker setVisible(boolean b) {
        super.setVisible(b);
        return this;
    }

    @Override
    public Marker setOpacity(double o) {
        super.setOpacity(o);
        return this;
    }

    @Override
    public Marker setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public Marker setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public Marker setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public Marker setZIndex(int z) {
        super.setZIndex(z);
        return this;
    }

    @Override
    public Marker setInteractive(boolean b) {
        super.setInteractive(b);
        return this;
    }

    @Override
    public Marker setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    String kind() {
        return "marker";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("p", position.toJson());
        put(m, "i", icon);
        if (iconWidth != 24 || iconHeight != 32) {
            m.put("iw", iconWidth);
            m.put("ih", iconHeight);
        }
        if (anchorX != null) {
            m.put("ax", anchorX);
            m.put("ay", anchorY);
        }
        put(m, "l", label);
        put(m, "c", color);
        if (draggable) {
            m.put("drag", true);
        }
        if (rotation != 0) {
            m.put("rot", rotation);
        }
    }
}
