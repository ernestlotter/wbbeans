package org.webbeans.scene3d;

/** What the browser does locally when the pointer is over a pickable node (no round trip). */
public enum HoverEffect {
    /** Nothing. */
    NONE,
    /** Pointer cursor only. */
    CURSOR,
    /** Pointer cursor and a faint emissive tint. */
    HIGHLIGHT,
    /** Pointer cursor and an edge outline. */
    OUTLINE
}
