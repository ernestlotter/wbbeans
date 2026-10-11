package org.webbeans.scene3d;

import java.util.Map;

/**
 * A text label anchored at a point in the scene, drawn as an HTML overlay that follows the projection (so it
 * is crisp at any zoom and styleable with CSS). Occludable labels hide behind geometry.
 */
public class Label3D extends Node3D {

    private String text;
    private String styleName;
    private boolean occludable;
    private double offsetX;
    private double offsetY;

    public Label3D(String text) {
        this.text = text;
        setPickable(false);
    }

    @Override
    String type() {
        return "label";
    }

    public String getText() {
        return text;
    }

    public Label3D setText(String t) {
        if (t == null ? text == null : t.equals(text)) {
            return this;
        }
        text = t;
        changed("text", t);
        return this;
    }

    /** Extra CSS class(es) on the overlay element. */
    public Label3D setStyleName(String css) {
        styleName = css;
        changed("css", css);
        return this;
    }

    public String getStyleName() {
        return styleName;
    }

    public boolean isOccludable() {
        return occludable;
    }

    public Label3D setOccludable(boolean b) {
        occludable = b;
        changed("occ", b);
        return this;
    }

    /** Screen-space offset in pixels from the projected anchor. */
    public Label3D setOffset(double x, double y) {
        offsetX = x;
        offsetY = y;
        changed("off", java.util.Arrays.asList(x, y));
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        m.put("text", text);
        if (styleName != null) {
            m.put("css", styleName);
        }
        if (occludable) {
            m.put("occ", true);
        }
        if (offsetX != 0 || offsetY != 0) {
            m.put("off", java.util.Arrays.asList(offsetX, offsetY));
        }
    }
}
