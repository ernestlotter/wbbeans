package org.webbeans.graph;

import java.util.List;

/** Nodes on a circle, in insertion order; the radius grows with the node count and sizes. */
public class CircularGraphLayout extends GraphLayout {

    @Override
    public void layout(GraphScene<?, ?> scene, boolean animated) {
        List<NodeWidget> ns = nodes(scene);
        if (ns.isEmpty()) {
            return;
        }
        double perimeter = 0;
        double maxH = 0;
        for (NodeWidget n : ns) {
            perimeter += n.getSize().width + horizontalGap;
            maxH = Math.max(maxH, n.getSize().height);
        }
        double r = Math.max(maxH, perimeter / (2 * Math.PI));
        for (int i = 0; i < ns.size(); i++) {
            double a = 2 * Math.PI * i / ns.size() - Math.PI / 2;
            ns.get(i).setLocation(new Point(r + Math.cos(a) * r, r + Math.sin(a) * r), animated);
        }
    }
}
