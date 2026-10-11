package org.webbeans.scene3d;

import java.util.Map;

import org.webbeans.ui.Component;

/** A timed animation finished; the node's transform has been updated from the browser. */
public class AnimationEvent extends SceneEvent {

    public AnimationEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public Node3D getNode() {
        return node("node");
    }

    public String getAnimationId() {
        return getString("id");
    }
}
