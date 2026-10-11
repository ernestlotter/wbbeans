package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A point cloud: positions (and optional per-point colors) drawn with a {@link PointsMaterial}. */
public class Points extends Node3D {

    private List<Vector3> points;
    private int[] colors;
    private PointsMaterial material;

    public Points(List<Vector3> points, PointsMaterial material) {
        this.points = new ArrayList<Vector3>(points);
        this.material = material;
    }

    @Override
    String type() {
        return "points";
    }

    public List<Vector3> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public Points setPoints(List<Vector3> pts) {
        points = new ArrayList<Vector3>(pts);
        changed("pts", Line.flat(points));
        return this;
    }

    /** One {@code 0xRRGGBB} per point, or null for the material color. */
    public Points setColors(int[] rgb) {
        colors = rgb == null ? null : rgb.clone();
        changed("col", colorList());
        return this;
    }

    public PointsMaterial getMaterial() {
        return material;
    }

    public Points setMaterial(PointsMaterial m) {
        material = m;
        if (getScene() != null) {
            getScene().register(m);
        }
        changed("m", m == null ? null : m.getId());
        return this;
    }

    private List<Integer> colorList() {
        if (colors == null) {
            return null;
        }
        List<Integer> l = new ArrayList<Integer>(colors.length);
        for (int c : colors) {
            l.add(c);
        }
        return l;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        into.register(material);
        m.put("m", material == null ? null : material.getId());
        m.put("pts", Line.flat(points));
        if (colors != null) {
            m.put("col", colorList());
        }
    }
}
