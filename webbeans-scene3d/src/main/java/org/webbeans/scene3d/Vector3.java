package org.webbeans.scene3d;

import java.util.Arrays;
import java.util.List;

/** An immutable 3D vector. */
public final class Vector3 {

    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 ONE = new Vector3(1, 1, 1);
    public static final Vector3 X = new Vector3(1, 0, 0);
    public static final Vector3 Y = new Vector3(0, 1, 0);
    public static final Vector3 Z = new Vector3(0, 0, 1);

    public final double x;
    public final double y;
    public final double z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vector3 of(double x, double y, double z) {
        return new Vector3(x, y, z);
    }

    public Vector3 plus(Vector3 o) {
        return new Vector3(x + o.x, y + o.y, z + o.z);
    }

    public Vector3 minus(Vector3 o) {
        return new Vector3(x - o.x, y - o.y, z - o.z);
    }

    public Vector3 times(double s) {
        return new Vector3(x * s, y * s, z * s);
    }

    public double dot(Vector3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vector3 cross(Vector3 o) {
        return new Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3 normalized() {
        double l = length();
        return l == 0 ? this : times(1 / l);
    }

    public double distanceTo(Vector3 o) {
        return minus(o).length();
    }

    /** Wire form. */
    public List<Double> toList() {
        return Arrays.asList(x, y, z);
    }

    static Vector3 fromList(List<?> l, Vector3 def) {
        if (l == null || l.size() < 3) {
            return def;
        }
        return new Vector3(((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Vector3 && ((Vector3) o).x == x && ((Vector3) o).y == y && ((Vector3) o).z == z;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 * 31 + Double.hashCode(y) * 31 + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
