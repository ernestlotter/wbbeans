package org.webbeans.graph;

/** Outline of a {@link NodeWidget}; {@link #CUSTOM} draws the markup given to {@link NodeWidget#setCustomSvg}. */
public enum Shape {
    RECTANGLE, ROUNDED, ELLIPSE, DIAMOND, HEXAGON, PARALLELOGRAM, CYLINDER, DOCUMENT,
    /** Only the icon and the label, no outline (a "glyph" node). */
    ICON,
    CUSTOM
}
