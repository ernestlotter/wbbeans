package org.webbeans.scene3d;

import java.util.Map;

import org.openide.util.Lookup;
import org.webbeans.ui.Component;

/**
 * A pickable node was clicked (or double-clicked / right-clicked, see {@link #getButton()}): the node, the hit
 * point and normal in world space, the instance index for instanced meshes, and modifier keys. Clicking empty
 * space fires with a null node. The event's lookup is the node's (the node and its user data), so actions
 * registered over the domain type work on it.
 */
public class PickEvent extends SceneEvent {

    public PickEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    /** The picked node, or null for a click on the background. */
    public Node3D getNode() {
        return node("node");
    }

    /** The node's user data, or null. */
    public Object getUserData() {
        Node3D n = getNode();
        return n == null ? null : n.getUserData();
    }

    /** World-space hit point (null for background clicks). */
    public Vector3 getPoint() {
        return vector("point");
    }

    public Vector3 getNormal() {
        return vector("normal");
    }

    /** The instance index for an {@link InstancedMesh}, else -1. */
    public int getInstance() {
        return getInt("inst", -1);
    }

    /** 0 = left, 1 = middle, 2 = right. */
    public int getButton() {
        return getInt("button", 0);
    }

    public boolean isDoubleClick() {
        return getBoolean("dbl");
    }

    public boolean isCtrl() {
        return getBoolean("ctrl");
    }

    public boolean isShift() {
        return getBoolean("shift");
    }

    /** Pointer position within the canvas (CSS pixels). */
    public int getX() {
        return getInt("x", 0);
    }

    public int getY() {
        return getInt("y", 0);
    }

    @Override
    public Lookup getLookup() {
        Node3D n = getNode();
        return n == null ? super.getLookup() : n.getLookup();
    }
}
