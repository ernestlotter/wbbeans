package org.webbeans.scene3d;

import java.util.Map;

/** Parallel light (the sun) shining from its position towards a target; can cast shadows. */
public class DirectionalLight extends Light {

    private Vector3 target = Vector3.ZERO;
    private double shadowSize = 20;

    public DirectionalLight(int color, double intensity) {
        super(color, intensity);
    }

    @Override
    String type() {
        return "light.directional";
    }

    public Vector3 getTarget() {
        return target;
    }

    public DirectionalLight setTarget(Vector3 t) {
        target = t == null ? Vector3.ZERO : t;
        changed("target", target.toList());
        return this;
    }

    /** Half-extent of the orthographic shadow frustum around the target. */
    public DirectionalLight setShadowSize(double s) {
        shadowSize = s;
        changed("shadowSize", s);
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        super.typeJson(m, into);
        m.put("target", target.toList());
        m.put("shadowSize", shadowSize);
    }
}
