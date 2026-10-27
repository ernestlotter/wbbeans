package org.webbeans.graph;

/** Where on a widget an edge attaches (NetBeans' {@code Anchor}). */
public enum Anchor {
    /** The widget's centre (the line is drawn under the shape). */
    CENTER,
    /** The point where the line crosses the widget's outline (rectangle, or the ellipse for ELLIPSE nodes). */
    BOUNDARY,
    /** The circle inscribed in the widget's bounds. */
    CIRCULAR,
    LEFT, RIGHT, TOP, BOTTOM,
    /** Left or right side, whichever faces the other end (pins use this by default). */
    SIDE
}
