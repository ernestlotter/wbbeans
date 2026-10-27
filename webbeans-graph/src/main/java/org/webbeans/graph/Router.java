package org.webbeans.graph;

/** How an edge finds its way between its anchors (NetBeans' {@code Router}). */
public enum Router {
    /** A straight line. */
    DIRECT,
    /** Horizontal and vertical segments with rounded corners ({@link EdgeWidget#setCornerRadius}). */
    ORTHOGONAL,
    /** A cubic curve leaving and entering along the anchor sides. */
    CURVED,
    /** Through the edge's own {@link EdgeWidget#setControlPoints control points}. */
    FREE
}
