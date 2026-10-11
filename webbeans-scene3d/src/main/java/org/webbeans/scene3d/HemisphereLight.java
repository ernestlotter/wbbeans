package org.webbeans.scene3d;

import java.util.Map;

/** Sky color from above fading to ground color from below: a cheap, pleasant fill. */
public class HemisphereLight extends Light {

    private int groundColor;

    public HemisphereLight(int skyColor, int groundColor, double intensity) {
        super(skyColor, intensity);
        this.groundColor = groundColor & 0xffffff;
    }

    @Override
    String type() {
        return "light.hemisphere";
    }

    public int getGroundColor() {
        return groundColor;
    }

    public HemisphereLight setGroundColor(int rgb) {
        groundColor = rgb & 0xffffff;
        changed("ground", groundColor);
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        super.typeJson(m, into);
        m.put("ground", groundColor);
    }
}
