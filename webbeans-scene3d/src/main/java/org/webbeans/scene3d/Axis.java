package org.webbeans.scene3d;

/** A world axis. */
public enum Axis {
    X(Vector3.X), Y(Vector3.Y), Z(Vector3.Z);

    private final Vector3 vector;

    Axis(Vector3 v) {
        this.vector = v;
    }

    public Vector3 vector() {
        return vector;
    }
}
