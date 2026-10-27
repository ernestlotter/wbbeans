package org.webbeans.map;

import java.util.Map;

/** Stroke and fill of a vector overlay (lines, polygons, circles, rectangles, GeoJSON). */
public abstract class PathOverlay extends Overlay {

    private String color;
    private double weight = 2;
    private String dash;
    private String fillColor;
    private double fillOpacity = 0.25;
    private boolean fill = true;

    PathOverlay(String id) {
        super(id);
    }

    /** Stroke colour (CSS); null = the theme accent. */
    public PathOverlay setColor(String css) {
        color = css;
        changed();
        return this;
    }

    public String getColor() {
        return color;
    }

    /** Stroke width in pixels. */
    public PathOverlay setWeight(double px) {
        weight = Math.max(0, px);
        changed();
        return this;
    }

    public double getWeight() {
        return weight;
    }

    /** SVG dash pattern, e.g. {@code "6 4"}; null = solid. */
    public PathOverlay setDash(String pattern) {
        dash = pattern;
        changed();
        return this;
    }

    /** Fill colour (CSS); null = the stroke colour. */
    public PathOverlay setFillColor(String css) {
        fillColor = css;
        changed();
        return this;
    }

    public PathOverlay setFillOpacity(double o) {
        fillOpacity = Math.max(0, Math.min(1, o));
        changed();
        return this;
    }

    /** Whether closed shapes are filled at all. */
    public PathOverlay setFill(boolean b) {
        fill = b;
        changed();
        return this;
    }

    void writeStyle(Map<String, Object> m) {
        put(m, "c", color);
        if (weight != 2) {
            m.put("w", weight);
        }
        put(m, "dash", dash);
        put(m, "fc", fillColor);
        if (fillOpacity != 0.25) {
            m.put("fo", fillOpacity);
        }
        if (!fill) {
            m.put("fill", false);
        }
    }
}
