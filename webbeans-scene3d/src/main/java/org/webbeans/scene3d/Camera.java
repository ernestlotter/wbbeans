package org.webbeans.scene3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The scene's camera: position, target and up vector plus projection parameters. Changes from the server are
 * applied immediately (or animated through {@link Scene3D#flyTo}); when the user orbits, the browser reports
 * back and the server copy follows (see {@link Scene3D#CAMERA_CHANGE}).
 */
public abstract class Camera {

    private Scene3D scene;
    private Vector3 position = new Vector3(0, 5, 10);
    private Vector3 target = Vector3.ZERO;
    private Vector3 up = Vector3.Y;
    private double near = 0.1;
    private double far = 2000;

    /** The wire projection: {@code persp} or {@code ortho}. */
    abstract String projection();

    abstract void projectionJson(Map<String, Object> m);

    public Vector3 getPosition() {
        return position;
    }

    public Camera setPosition(Vector3 p) {
        position = p == null ? Vector3.ZERO : p;
        return changed();
    }

    public Camera setPosition(double x, double y, double z) {
        return setPosition(new Vector3(x, y, z));
    }

    public Vector3 getTarget() {
        return target;
    }

    /** Where the camera looks (also the orbit center). */
    public Camera lookAt(Vector3 t) {
        target = t == null ? Vector3.ZERO : t;
        return changed();
    }

    public Camera lookAt(double x, double y, double z) {
        return lookAt(new Vector3(x, y, z));
    }

    public Vector3 getUp() {
        return up;
    }

    public Camera setUp(Vector3 u) {
        up = u == null ? Vector3.Y : u;
        return changed();
    }

    public double getNear() {
        return near;
    }

    public double getFar() {
        return far;
    }

    public Camera setClipping(double nearPlane, double farPlane) {
        near = nearPlane;
        far = farPlane;
        return changed();
    }

    /** The current state as a value. */
    public CameraState getState() {
        return new CameraState(position, target, up);
    }

    void setScene(Scene3D s) {
        scene = s;
    }

    /** Updates position/target from the browser without echoing them back. */
    void fromClient(Vector3 pos, Vector3 tgt, Vector3 upv) {
        if (pos != null) {
            position = pos;
        }
        if (tgt != null) {
            target = tgt;
        }
        if (upv != null) {
            up = upv;
        }
    }

    protected final Camera changed() {
        if (scene != null) {
            scene.cameraChanged();
        }
        return this;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("proj", projection());
        m.put("pos", position.toList());
        m.put("target", target.toList());
        m.put("up", up.toList());
        m.put("near", near);
        m.put("far", far);
        projectionJson(m);
        return m;
    }
}
