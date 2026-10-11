package org.webbeans.scene3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a draggable node may move: freely in the camera plane, on a world plane, or along an axis, optionally
 * snapped to a grid. The client solves the constraint while dragging; the server re-applies it to the reported
 * position before the {@link DragEvent} is delivered, so a client cannot move a node where it is not allowed.
 */
public final class DragConstraint {

    private final Axis planeNormal;
    private final double planeOffset;
    private final Axis axis;
    private final double snap;

    private DragConstraint(Axis planeNormal, double planeOffset, Axis axis, double snap) {
        this.planeNormal = planeNormal;
        this.planeOffset = planeOffset;
        this.axis = axis;
        this.snap = snap;
    }

    /** Free movement in the plane facing the camera. */
    public static DragConstraint free() {
        return new DragConstraint(null, 0, null, 0);
    }

    /** Movement on the world plane {@code normal · p = offset}, e.g. {@code plane(Axis.Y, 0)} = the floor. */
    public static DragConstraint plane(Axis normal, double offset) {
        return new DragConstraint(normal, offset, null, 0);
    }

    /** Movement along one axis only. */
    public static DragConstraint axis(Axis a) {
        return new DragConstraint(null, 0, a, 0);
    }

    /** Snaps the position to multiples of {@code gridSize} on the free coordinates. */
    public DragConstraint snap(double gridSize) {
        return new DragConstraint(planeNormal, planeOffset, axis, gridSize);
    }

    public double getSnap() {
        return snap;
    }

    /** Applies the constraint to a position reported by the client, relative to where the drag started. */
    public Vector3 apply(Vector3 start, Vector3 reported) {
        double x = reported.x, y = reported.y, z = reported.z;
        if (axis != null) {
            x = axis == Axis.X ? x : start.x;
            y = axis == Axis.Y ? y : start.y;
            z = axis == Axis.Z ? z : start.z;
        } else if (planeNormal != null) {
            if (planeNormal == Axis.X) {
                x = planeOffset;
            } else if (planeNormal == Axis.Y) {
                y = planeOffset;
            } else {
                z = planeOffset;
            }
        }
        if (snap > 0) {
            if (axis == null || axis == Axis.X) {
                x = round(x);
            }
            if (axis == null || axis == Axis.Y) {
                y = round(y);
            }
            if (axis == null || axis == Axis.Z) {
                z = round(z);
            }
            if (planeNormal == Axis.X) {
                x = planeOffset;
            } else if (planeNormal == Axis.Y) {
                y = planeOffset;
            } else if (planeNormal == Axis.Z) {
                z = planeOffset;
            }
        }
        return new Vector3(x, y, z);
    }

    private double round(double v) {
        return Math.round(v / snap) * snap;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (planeNormal != null) {
            Vector3 n = planeNormal.vector();
            m.put("plane", java.util.Arrays.asList(n.x, n.y, n.z, planeOffset));
        } else if (axis != null) {
            m.put("axis", axis.vector().toList());
        } else {
            m.put("free", true);
        }
        if (snap > 0) {
            m.put("snap", snap);
        }
        return m;
    }
}
