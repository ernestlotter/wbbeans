package org.webbeans.scene3d;

import java.util.Map;

/** Light radiating from a point (a bulb, a star). */
public class PointLight extends Light {

    private double distance;
    private double decay = 2;

    public PointLight(int color, double intensity) {
        super(color, intensity);
    }

    @Override
    String type() {
        return "light.point";
    }

    /** Range (0 = infinite) and falloff exponent. */
    public PointLight setRange(double distance, double decay) {
        this.distance = distance;
        this.decay = decay;
        changed("range", java.util.Arrays.asList(distance, decay));
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        super.typeJson(m, into);
        m.put("range", java.util.Arrays.asList(distance, decay));
    }
}
