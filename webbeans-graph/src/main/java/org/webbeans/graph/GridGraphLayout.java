package org.webbeans.graph;

import java.util.List;

/** Nodes in rows of equal cells, in insertion order. */
public class GridGraphLayout extends GraphLayout {

    private int columns;

    /** 0 = about square. */
    public GridGraphLayout setColumns(int c) {
        columns = Math.max(0, c);
        return this;
    }

    @Override
    public void layout(GraphScene<?, ?> scene, boolean animated) {
        List<NodeWidget> ns = nodes(scene);
        if (ns.isEmpty()) {
            return;
        }
        double cw = 0;
        double ch = 0;
        for (NodeWidget n : ns) {
            cw = Math.max(cw, n.getSize().width);
            ch = Math.max(ch, n.getSize().height);
        }
        int cols = columns > 0 ? columns : Math.max(1, (int) Math.ceil(Math.sqrt(ns.size())));
        for (int i = 0; i < ns.size(); i++) {
            int r = i / cols;
            int c = i % cols;
            ns.get(i).setLocation(new Point(c * (cw + horizontalGap) + cw / 2, r * (ch + verticalGap) + ch / 2), animated);
        }
    }
}
