package org.webbeans.workbench.viewer3d;

import org.webbeans.scene3d.Vector3;

/** A machine part: id, name, kind, colour and a position/rotation in the assembly. Plain data owned by {@link PartsModel}. */
public final class Part {

    public enum Kind { HUB, BLADE, SHAFT, HOUSING, SENSOR }

    private final String id;
    private String name;
    private final Kind kind;
    private int color;
    private Vector3 position;
    private double angle;         // rotation around Y in radians (blades)
    private double temperature;   // °C, live
    private boolean ok = true;

    public Part(String id, String name, Kind kind, int color, Vector3 position, double angle) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.color = color;
        this.position = position;
        this.angle = angle;
        this.temperature = 20;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        name = n;
    }

    public Kind getKind() {
        return kind;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int rgb) {
        color = rgb;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 p) {
        position = p;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double a) {
        angle = a;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double t) {
        temperature = t;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean b) {
        ok = b;
    }

    @Override
    public String toString() {
        return name;
    }
}
