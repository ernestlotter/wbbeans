package org.webbeans.scene3d;

import java.util.List;
import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** Base of the scene events: resolves node ids and vectors from the browser's data. */
public class SceneEvent extends Event {

    public SceneEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public Scene3D getScene() {
        return (Scene3D) getSource();
    }

    /** The node named by {@code key}, or null if it is gone meanwhile or the key is absent. */
    protected Node3D node(String key) {
        String id = getString(key);
        return id == null ? null : getScene().findNode(id);
    }

    /** A {@code [x,y,z]} list under {@code key}, or null. */
    protected Vector3 vector(String key) {
        Object v = getData().get(key);
        if (!(v instanceof List) || ((List<?>) v).size() < 3) {
            return null;
        }
        List<?> l = (List<?>) v;
        return new Vector3(num(l.get(0)), num(l.get(1)), num(l.get(2)));
    }

    protected Quaternion quaternion(String key) {
        Object v = getData().get(key);
        if (!(v instanceof List) || ((List<?>) v).size() < 4) {
            return null;
        }
        List<?> l = (List<?>) v;
        return new Quaternion(num(l.get(0)), num(l.get(1)), num(l.get(2)), num(l.get(3)));
    }

    static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }
}
