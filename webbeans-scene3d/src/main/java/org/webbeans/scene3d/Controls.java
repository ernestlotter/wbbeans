package org.webbeans.scene3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How the user moves the camera. Orbit (default) rotates around the camera target with limits, pans and
 * zooms; map keeps the camera upright for top-down layouts; first person walks; none leaves the camera
 * entirely to the server. {@link #setLocked} freezes user input without changing the mode.
 */
public final class Controls {

    public enum Mode { ORBIT, MAP, FIRST_PERSON, NONE }

    private Scene3D scene;
    private final Mode mode;
    private double minDistance = 0.1;
    private double maxDistance = Double.POSITIVE_INFINITY;
    private double minPolar;
    private double maxPolar = Math.PI;
    private boolean pan = true;
    private boolean zoom = true;
    private boolean rotate = true;
    private double autoRotate;
    private boolean locked;
    private boolean damping = true;
    private double speed = 5;

    private Controls(Mode mode) {
        this.mode = mode;
    }

    public static Controls orbit() {
        return new Controls(Mode.ORBIT);
    }

    public static Controls map() {
        Controls c = new Controls(Mode.MAP);
        c.maxPolar = Math.PI / 2 - 0.05;
        return c;
    }

    public static Controls firstPerson(double unitsPerSecond) {
        Controls c = new Controls(Mode.FIRST_PERSON);
        c.speed = unitsPerSecond;
        return c;
    }

    public static Controls none() {
        return new Controls(Mode.NONE);
    }

    public Mode getMode() {
        return mode;
    }

    public Controls setDistanceLimits(double min, double max) {
        minDistance = min;
        maxDistance = max;
        return changed();
    }

    /** Polar angle limits in radians (0 = looking straight down, PI/2 = horizon). */
    public Controls setPolarLimits(double min, double max) {
        minPolar = min;
        maxPolar = max;
        return changed();
    }

    public Controls setPan(boolean b) {
        pan = b;
        return changed();
    }

    public Controls setZoom(boolean b) {
        zoom = b;
        return changed();
    }

    public Controls setRotate(boolean b) {
        rotate = b;
        return changed();
    }

    /** Radians per second of idle rotation around the target (0 = off). */
    public Controls setAutoRotate(double radPerSec) {
        autoRotate = radPerSec;
        return changed();
    }

    public boolean isLocked() {
        return locked;
    }

    public Controls setLocked(boolean b) {
        locked = b;
        return changed();
    }

    public Controls setDamping(boolean b) {
        damping = b;
        return changed();
    }

    void setScene(Scene3D s) {
        scene = s;
    }

    private Controls changed() {
        if (scene != null) {
            scene.controlsChanged();
        }
        return this;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("mode", mode.name().toLowerCase());
        m.put("minDist", minDistance);
        if (maxDistance != Double.POSITIVE_INFINITY) {
            m.put("maxDist", maxDistance);
        }
        m.put("minPolar", minPolar);
        m.put("maxPolar", maxPolar);
        m.put("pan", pan);
        m.put("zoom", zoom);
        m.put("rotate", rotate);
        if (autoRotate != 0) {
            m.put("autoRotate", autoRotate);
        }
        if (locked) {
            m.put("locked", true);
        }
        m.put("damping", damping);
        if (mode == Mode.FIRST_PERSON) {
            m.put("speed", speed);
        }
        return m;
    }
}
