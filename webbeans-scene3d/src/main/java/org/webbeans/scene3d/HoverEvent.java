package org.webbeans.scene3d;

import java.util.Map;

import org.webbeans.ui.Component;

/** The pointer entered a pickable node (or left it: null node). Throttled in the browser. */
public class HoverEvent extends SceneEvent {

    public HoverEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    /** The hovered node, or null when the pointer left. */
    public Node3D getNode() {
        return node("node");
    }

    public Vector3 getPoint() {
        return vector("point");
    }

    public int getInstance() {
        return getInt("inst", -1);
    }

    public int getX() {
        return getInt("x", 0);
    }

    public int getY() {
        return getInt("y", 0);
    }
}
