package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A client-side animation of one node: continuous rotation or orbit, or a timed move/scale/path. The server
 * sends the description once ({@code anim} op) and the browser interpolates every frame; when a timed
 * animation ends the browser reports the final transform, the node's server state follows and
 * {@link #onComplete} runs. Attach with {@link Node3D#animate(Animation)}.
 *
 * <pre>
 * planet.animate(Animation.rotate(Axis.Y, 0.3));
 * probe.animate(Animation.moveTo(mars.getPosition(), 4000).easing(Easing.EASE_IN_OUT).onComplete(this::arrived));
 * </pre>
 */
public final class Animation {

    enum Kind { ROTATE, ORBIT, MOVE_TO, SCALE_TO, PATH }

    private final Kind kind;
    private Vector3 axis;
    private Vector3 center;
    private double rate;
    private Vector3 to;
    private List<Vector3> path;
    private long durationMs;
    private boolean loop;
    private boolean yoyo;
    private long delayMs;
    private Easing easing = Easing.LINEAR;
    private Runnable onComplete;

    private String id;
    private Node3D node;
    private boolean paused;

    private Animation(Kind kind) {
        this.kind = kind;
    }

    /** Spins the node around a local axis forever. */
    public static Animation rotate(Axis axis, double radiansPerSecond) {
        return rotate(axis.vector(), radiansPerSecond);
    }

    public static Animation rotate(Vector3 axis, double radiansPerSecond) {
        Animation a = new Animation(Kind.ROTATE);
        a.axis = axis.normalized();
        a.rate = radiansPerSecond;
        a.loop = true;
        return a;
    }

    /** Revolves the node's position around a center point (in the parent's space) forever. */
    public static Animation orbit(Vector3 center, Vector3 axis, double radiansPerSecond) {
        Animation a = new Animation(Kind.ORBIT);
        a.center = center;
        a.axis = axis.normalized();
        a.rate = radiansPerSecond;
        a.loop = true;
        return a;
    }

    public static Animation moveTo(Vector3 to, long durationMs) {
        Animation a = new Animation(Kind.MOVE_TO);
        a.to = to;
        a.durationMs = durationMs;
        return a;
    }

    public static Animation scaleTo(Vector3 to, long durationMs) {
        Animation a = new Animation(Kind.SCALE_TO);
        a.to = to;
        a.durationMs = durationMs;
        return a;
    }

    /** Moves through the points (Catmull-Rom interpolated) in the given time. */
    public static Animation path(List<Vector3> points, long durationMs) {
        Animation a = new Animation(Kind.PATH);
        a.path = new ArrayList<Vector3>(points);
        a.durationMs = durationMs;
        return a;
    }

    public Animation loop(boolean b) {
        loop = b;
        return this;
    }

    /** Play forwards then backwards (timed animations). */
    public Animation yoyo(boolean b) {
        yoyo = b;
        return this;
    }

    public Animation delay(long ms) {
        delayMs = ms;
        return this;
    }

    public Animation easing(Easing e) {
        easing = e == null ? Easing.LINEAR : e;
        return this;
    }

    /** Runs on the session thread when a timed, non-looping animation finishes. */
    public Animation onComplete(Runnable r) {
        onComplete = r;
        return this;
    }

    public boolean isLoop() {
        return loop;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /** The animation id while playing ({@code a3}), null otherwise. */
    public String getId() {
        return id;
    }

    public Node3D getNode() {
        return node;
    }

    public boolean isPlaying() {
        return id != null && !paused;
    }

    public Animation pause() {
        if (id != null && !paused) {
            paused = true;
            op("pause");
        }
        return this;
    }

    public Animation resume() {
        if (id != null && paused) {
            paused = false;
            op("resume");
        }
        return this;
    }

    /** Stops and removes the animation; the node keeps its last client-side transform only if reported. */
    public void stop() {
        if (node != null) {
            node.animationStopped(this);
            op("stop");
            id = null;
        }
    }

    /** Jumps to a time within a timed animation. */
    public Animation seek(long ms) {
        if (id != null) {
            Map<String, Object> m = op("seek");
            if (m != null) {
                m.put("t", ms);
            }
        }
        return this;
    }

    // ---- plumbing ------------------------------------------------------------------------------------------------

    void bind(Node3D n, String animId) {
        node = n;
        id = animId;
        paused = false;
    }

    Runnable completion() {
        return onComplete;
    }

    void unbind() {
        id = null;
    }

    private Map<String, Object> op(String name) {
        Scene3D s = node == null ? null : node.getScene();
        if (s == null || id == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("op", name);
        m.put("id", id);
        s.animOp(m);
        return m;
    }

    /** The {@code play} op. */
    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("op", "play");
        m.put("id", id);
        m.put("node", node.getId());
        m.put("kind", kind.name().toLowerCase());
        if (axis != null) {
            m.put("axis", axis.toList());
        }
        if (center != null) {
            m.put("center", center.toList());
        }
        if (rate != 0) {
            m.put("rate", rate);
        }
        if (to != null) {
            m.put("to", to.toList());
        }
        if (path != null) {
            m.put("path", Line.flat(path));
        }
        if (durationMs > 0) {
            m.put("duration", durationMs);
        }
        if (loop) {
            m.put("loop", true);
        }
        if (yoyo) {
            m.put("yoyo", true);
        }
        if (delayMs > 0) {
            m.put("delay", delayMs);
        }
        if (easing != Easing.LINEAR) {
            m.put("easing", easing.name().toLowerCase().replace("_", "-"));
        }
        if (paused) {
            m.put("paused", true);
        }
        return m;
    }

    @Override
    public String toString() {
        return "Animation[" + kind + (id != null ? " " + id : "") + "]";
    }
}
