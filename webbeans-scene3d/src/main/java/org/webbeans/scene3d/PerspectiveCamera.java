package org.webbeans.scene3d;

import java.util.Map;

/** The usual camera: vertical field of view in degrees. */
public class PerspectiveCamera extends Camera {

    private double fov;

    public PerspectiveCamera() {
        this(50);
    }

    public PerspectiveCamera(double fovDegrees) {
        fov = fovDegrees;
    }

    public PerspectiveCamera(double fovDegrees, double near, double far) {
        fov = fovDegrees;
        setClipping(near, far);
    }

    @Override
    String projection() {
        return "persp";
    }

    public double getFov() {
        return fov;
    }

    public PerspectiveCamera setFov(double degrees) {
        fov = degrees;
        changed();
        return this;
    }

    @Override
    void projectionJson(Map<String, Object> m) {
        m.put("fov", fov);
    }
}
