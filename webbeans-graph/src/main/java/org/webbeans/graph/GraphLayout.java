package org.webbeans.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the nodes of a scene (NetBeans' {@code GraphLayout}). Layouts run on the server over the widgets'
 * {@link NodeWidget#getSize() sizes} (preferred, or as the browser measured them — so run a layout after the
 * scene has been shown once for exact results) and set each node's location; the browser glides the nodes
 * there when {@code animated}. Subclass for your own: iterate {@link #nodes(GraphScene)} and call
 * {@link NodeWidget#setLocation(Point, boolean)}.
 */
public abstract class GraphLayout {

    protected double horizontalGap = 40;
    protected double verticalGap = 40;

    public GraphLayout setGaps(double horizontal, double vertical) {
        horizontalGap = horizontal;
        verticalGap = vertical;
        return this;
    }

    /** Computes and applies the locations. */
    public abstract void layout(GraphScene<?, ?> scene, boolean animated);

    /** The visible node widgets of the scene, in insertion order. */
    protected static List<NodeWidget> nodes(GraphScene<?, ?> scene) {
        List<NodeWidget> out = new ArrayList<NodeWidget>();
        for (Object n : scene.getNodes()) {
            NodeWidget w = scene.nodeWidgetOf(n);
            if (w != null && w.isVisible()) {
                out.add(w);
            }
        }
        return out;
    }

    /** The visible edges as widget pairs (source node, target node); edges without both ends are skipped. */
    protected static List<EdgeWidget> edges(GraphScene<?, ?> scene) {
        List<EdgeWidget> out = new ArrayList<EdgeWidget>();
        for (Object e : scene.getEdges()) {
            EdgeWidget w = scene.edgeWidgetOf(e);
            if (w != null && w.isVisible() && w.getSourceNode() != null && w.getTargetNode() != null) {
                out.add(w);
            }
        }
        return out;
    }
}
