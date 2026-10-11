package org.webbeans.scene3d;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Distance fog: linear between two distances, or exponential by density. */
public final class Fog {

    private final int color;
    private final double near;
    private final double far;
    private final double density;

    private Fog(int color, double near, double far, double density) {
        this.color = color & 0xffffff;
        this.near = near;
        this.far = far;
        this.density = density;
    }

    public static Fog linear(int color, double near, double far) {
        return new Fog(color, near, far, 0);
    }

    public static Fog exponential(int color, double density) {
        return new Fog(color, 0, 0, density);
    }

    public int getColor() {
        return color;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("color", color);
        if (density > 0) {
            m.put("density", density);
        } else {
            m.put("range", Arrays.asList(near, far));
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Fog)) {
            return false;
        }
        Fog f = (Fog) o;
        return f.color == color && f.near == near && f.far == far && f.density == density;
    }

    @Override
    public int hashCode() {
        return color * 31 + Double.valueOf(near + far + density).hashCode();
    }
}
