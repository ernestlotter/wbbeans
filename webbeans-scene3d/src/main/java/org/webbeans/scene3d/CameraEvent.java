package org.webbeans.scene3d;

import java.util.Map;

import org.webbeans.ui.Component;

/** The user moved the camera; the scene's {@link Camera} already reflects the new pose. */
public class CameraEvent extends SceneEvent {

    public CameraEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public CameraState getState() {
        return new CameraState(vector("pos"), vector("target"), vector("up"));
    }
}
