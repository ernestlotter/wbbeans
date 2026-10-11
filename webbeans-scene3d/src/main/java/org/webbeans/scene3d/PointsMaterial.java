package org.webbeans.scene3d;

import java.util.Map;

/** Material for {@link Points} nodes: color, point size and whether size shrinks with distance. */
public class PointsMaterial extends Material {

    private double size = 1;
    private boolean sizeAttenuation = true;

    public PointsMaterial() {
        super(Type.BASIC);
    }

    public PointsMaterial(int color, double size) {
        this();
        setColor(color);
        this.size = size;
    }

    @Override
    String kind() {
        return "points";
    }

    public double getSize() {
        return size;
    }

    public PointsMaterial setSize(double s) {
        size = s;
        changed();
        return this;
    }

    public boolean isSizeAttenuation() {
        return sizeAttenuation;
    }

    public PointsMaterial setSizeAttenuation(boolean b) {
        sizeAttenuation = b;
        changed();
        return this;
    }

    @Override
    Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("size", size);
        if (!sizeAttenuation) {
            m.put("sizeAttenuation", false);
        }
        return m;
    }
}
