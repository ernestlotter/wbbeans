package org.webbeans.scene3d;

/** Uniform light from everywhere. */
public class AmbientLight extends Light {

    public AmbientLight(int color, double intensity) {
        super(color, intensity);
    }

    @Override
    String type() {
        return "light.ambient";
    }
}
