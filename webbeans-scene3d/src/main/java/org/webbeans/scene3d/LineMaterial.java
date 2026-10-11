package org.webbeans.scene3d;

import java.util.Map;

/** Material for {@link Line} nodes: color, (unscaled) width and dashes. */
public class LineMaterial extends Material {

    private double width = 1;
    private double dashSize;
    private double gapSize;

    public LineMaterial() {
        super(Type.BASIC);
    }

    public LineMaterial(int color) {
        this();
        setColor(color);
    }

    @Override
    String kind() {
        return "line";
    }

    public double getWidth() {
        return width;
    }

    /** Line width in pixels (most WebGL implementations only honour 1). */
    public LineMaterial setWidth(double w) {
        width = w;
        changed();
        return this;
    }

    public LineMaterial setDashed(double dash, double gap) {
        dashSize = dash;
        gapSize = gap;
        changed();
        return this;
    }

    public boolean isDashed() {
        return dashSize > 0;
    }

    @Override
    Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("width", width);
        if (dashSize > 0) {
            m.put("dash", java.util.Arrays.asList(dashSize, gapSize));
        }
        return m;
    }
}
