package org.webbeans.scene3d;

import java.util.Map;

/** A cone of light from its position towards a target. */
public class SpotLight extends Light {

    private Vector3 target = Vector3.ZERO;
    private double angle = Math.PI / 6;
    private double penumbra = 0.2;
    private double distance;

    public SpotLight(int color, double intensity) {
        super(color, intensity);
    }

    @Override
    String type() {
        return "light.spot";
    }

    public SpotLight setTarget(Vector3 t) {
        target = t == null ? Vector3.ZERO : t;
        changed("target", target.toList());
        return this;
    }

    public Vector3 getTarget() {
        return target;
    }

    /** Half-angle of the cone in radians and the soft edge fraction (0..1). */
    public SpotLight setCone(double angleRad, double penumbraFraction) {
        angle = angleRad;
        penumbra = penumbraFraction;
        changed("cone", java.util.Arrays.asList(angle, penumbra));
        return this;
    }

    public SpotLight setDistance(double d) {
        distance = d;
        changed("distance", d);
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        super.typeJson(m, into);
        m.put("target", target.toList());
        m.put("cone", java.util.Arrays.asList(angle, penumbra));
        m.put("distance", distance);
    }
}
