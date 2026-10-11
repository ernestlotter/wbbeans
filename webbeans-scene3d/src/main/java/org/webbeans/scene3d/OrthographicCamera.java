package org.webbeans.scene3d;

import java.util.Map;

/** Parallel projection: {@code size} world units fit the viewport height. */
public class OrthographicCamera extends Camera {

    private double size;

    public OrthographicCamera(double size) {
        this.size = size;
    }

    @Override
    String projection() {
        return "ortho";
    }

    public double getSize() {
        return size;
    }

    public OrthographicCamera setSize(double s) {
        size = s;
        changed();
        return this;
    }

    @Override
    void projectionJson(Map<String, Object> m) {
        m.put("size", size);
    }
}
