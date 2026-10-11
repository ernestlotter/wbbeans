package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A polyline (or closed loop) through points, drawn with a {@link LineMaterial}. Not pickable by default. */
public class Line extends Node3D {

    private List<Vector3> points = new ArrayList<Vector3>();
    private LineMaterial material;
    private boolean closed;

    public Line(List<Vector3> points, LineMaterial material) {
        this.points = new ArrayList<Vector3>(points);
        this.material = material;
        setPickable(false);
    }

    /** A circle of {@code segments} points around the origin in the XZ plane (orbit paths and the like). */
    public static List<Vector3> circle(double radius, int segments) {
        List<Vector3> pts = new ArrayList<Vector3>(segments);
        for (int i = 0; i < segments; i++) {
            double a = i * 2 * Math.PI / segments;
            pts.add(new Vector3(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
        return pts;
    }

    @Override
    String type() {
        return "line";
    }

    public List<Vector3> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public Line setPoints(List<Vector3> pts) {
        points = new ArrayList<Vector3>(pts);
        changed("pts", flat(points));
        return this;
    }

    public boolean isClosed() {
        return closed;
    }

    public Line setClosed(boolean b) {
        closed = b;
        changed("closed", b);
        return this;
    }

    public LineMaterial getMaterial() {
        return material;
    }

    public Line setMaterial(LineMaterial m) {
        material = m;
        if (getScene() != null) {
            getScene().register(m);
        }
        changed("m", m == null ? null : m.getId());
        return this;
    }

    /** Flattens points to {@code [x,y,z,...]}, rounded to 1e-5 to keep large point lists compact on the wire. */
    static List<Double> flat(List<Vector3> pts) {
        List<Double> l = new ArrayList<Double>(pts.size() * 3);
        for (Vector3 v : pts) {
            l.add(round(v.x));
            l.add(round(v.y));
            l.add(round(v.z));
        }
        return l;
    }

    private static double round(double v) {
        return Math.abs(v) < 1e6 ? Math.round(v * 1e5) / 1e5 : v;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        into.register(material);
        m.put("m", material == null ? null : material.getId());
        m.put("pts", flat(points));
        if (closed) {
            m.put("closed", true);
        }
    }
}
