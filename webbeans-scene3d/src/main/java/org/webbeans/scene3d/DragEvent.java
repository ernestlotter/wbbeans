package org.webbeans.scene3d;

import java.util.Map;

import org.webbeans.ui.Component;

/**
 * A draggable node is being moved. {@code START} arrives once, {@code MOVE} throttled while dragging and
 * {@code END} when the pointer is released. The reported position has already been validated against the
 * node's {@link DragConstraint} and applied to the node; a listener may {@link #reject()} to snap it back to
 * where the drag started.
 */
public class DragEvent extends SceneEvent {

    public enum Phase { START, MOVE, END }

    private boolean rejected;
    private Vector3 reported;

    public DragEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public Node3D getNode() {
        return node("node");
    }

    public Phase getPhase() {
        String p = getString("phase");
        return "start".equals(p) ? Phase.START : "end".equals(p) ? Phase.END : Phase.MOVE;
    }

    /** The validated position (the node's current position). */
    public Vector3 getPosition() {
        return vector("pos");
    }

    /** Where the node was when the drag started. */
    public Vector3 getStart() {
        return vector("start");
    }

    /** The position as the browser reported it, before validation. */
    public Vector3 getReportedPosition() {
        return reported;
    }

    void setReported(Vector3 v) {
        reported = v;
    }

    /** Snaps the node back to the start position (any phase). */
    public void reject() {
        rejected = true;
    }

    public boolean isRejected() {
        return rejected;
    }
}
