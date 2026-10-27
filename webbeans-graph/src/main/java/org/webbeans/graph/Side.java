package org.webbeans.graph;

/** The side of a node a {@link PinWidget} sits on. */
public enum Side {
    LEFT, RIGHT, TOP, BOTTOM,
    /** Listed inside the node (VMD style): anchored left or right, whichever faces the other end. */
    AUTO
}
