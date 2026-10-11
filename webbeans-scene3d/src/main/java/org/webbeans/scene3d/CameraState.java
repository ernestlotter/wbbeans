package org.webbeans.scene3d;

import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable camera pose (position, target, up), e.g. to store and restore views. */
public final class CameraState {

    private final Vector3 position;
    private final Vector3 target;
    private final Vector3 up;

    public CameraState(Vector3 position, Vector3 target, Vector3 up) {
        this.position = position == null ? Vector3.ZERO : position;
        this.target = target == null ? Vector3.ZERO : target;
        this.up = up == null ? Vector3.Y : up;
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector3 getTarget() {
        return target;
    }

    public Vector3 getUp() {
        return up;
    }

    public double getDistance() {
        return position.distanceTo(target);
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("pos", position.toList());
        m.put("target", target.toList());
        m.put("up", up.toList());
        return m;
    }

    @Override
    public String toString() {
        return "CameraState[pos=" + position + " target=" + target + "]";
    }
}
