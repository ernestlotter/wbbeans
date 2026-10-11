package org.webbeans.scene3d;

import java.util.Map;

/** Base of the light nodes: color and intensity. */
public abstract class Light extends Node3D {

    private int color = 0xffffff;
    private double intensity = 1;

    protected Light(int color, double intensity) {
        this.color = color & 0xffffff;
        this.intensity = intensity;
        setPickable(false);
    }

    public int getColor() {
        return color;
    }

    public Light setColor(int rgb) {
        color = rgb & 0xffffff;
        changed("color", color);
        return this;
    }

    public double getIntensity() {
        return intensity;
    }

    public Light setIntensity(double i) {
        intensity = i;
        changed("intensity", i);
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        m.put("color", color);
        m.put("intensity", intensity);
    }
}
