package org.webbeans.scene3d;

/** Observes the server-side scene graph (node views, inspectors); all calls on the session thread. */
public interface SceneListener {

    void nodeAdded(Node3D node);

    void nodeRemoved(Node3D node);

    /** A field of a node changed ({@code name}, {@code pos}, {@code vis}, ...). */
    void nodeChanged(Node3D node, String field);
}
