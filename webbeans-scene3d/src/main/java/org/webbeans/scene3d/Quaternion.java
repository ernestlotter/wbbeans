package org.webbeans.scene3d;

import java.util.Arrays;
import java.util.List;

/** An immutable rotation (x, y, z, w). */
public final class Quaternion {

    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

    public final double x;
    public final double y;
    public final double z;
    public final double w;

    public Quaternion(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /** From Euler angles in radians (XYZ order, as three.js' default). */
    public static Quaternion fromEuler(double ex, double ey, double ez) {
        double c1 = Math.cos(ex / 2), c2 = Math.cos(ey / 2), c3 = Math.cos(ez / 2);
        double s1 = Math.sin(ex / 2), s2 = Math.sin(ey / 2), s3 = Math.sin(ez / 2);
        return new Quaternion(s1 * c2 * c3 + c1 * s2 * s3, c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 + s1 * s2 * c3, c1 * c2 * c3 - s1 * s2 * s3);
    }

    /** Rotation of {@code radians} around a (unit) axis. */
    public static Quaternion fromAxisAngle(Vector3 axis, double radians) {
        Vector3 a = axis.normalized();
        double s = Math.sin(radians / 2);
        return new Quaternion(a.x * s, a.y * s, a.z * s, Math.cos(radians / 2));
    }

    public Quaternion times(Quaternion q) {
        return new Quaternion(
                w * q.x + x * q.w + y * q.z - z * q.y,
                w * q.y - x * q.z + y * q.w + z * q.x,
                w * q.z + x * q.y - y * q.x + z * q.w,
                w * q.w - x * q.x - y * q.y - z * q.z);
    }

    public Quaternion normalized() {
        double l = Math.sqrt(x * x + y * y + z * z + w * w);
        return l == 0 ? IDENTITY : new Quaternion(x / l, y / l, z / l, w / l);
    }

    public List<Double> toList() {
        return Arrays.asList(x, y, z, w);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Quaternion && ((Quaternion) o).x == x && ((Quaternion) o).y == y && ((Quaternion) o).z == z && ((Quaternion) o).w == w;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) + 31 * Double.hashCode(y) + 961 * Double.hashCode(z) + 29791 * Double.hashCode(w);
    }

    @Override
    public String toString() {
        return "Quaternion(" + x + ", " + y + ", " + z + ", " + w + ")";
    }
}
