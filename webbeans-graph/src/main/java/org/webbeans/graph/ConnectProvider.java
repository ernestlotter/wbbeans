package org.webbeans.graph;

/**
 * Decides and performs user-drawn connections (NetBeans' {@code ConnectProvider}). With one installed through
 * {@link GraphScene#setConnectProvider}, the user drags from a node or pin to another; the scene asks whether
 * the pair is allowed and, on drop, calls {@link #connect}.
 */
public interface ConnectProvider {

    /** Whether a drag may start at this widget (a node or a pin). */
    boolean isSource(Widget source);

    /** Whether the drag may end on this widget (a node or a pin). Called when the drop happens. */
    boolean canConnect(Widget source, Widget target);

    /** Creates the connection: typically {@code scene.addEdge(newEdgeObject)} plus source/target. */
    void connect(Widget source, Widget target);
}
