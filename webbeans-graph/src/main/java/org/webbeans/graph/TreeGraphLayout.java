package org.webbeans.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A layered (Sugiyama-style, simplified) layout along edge direction: roots (nodes without incoming edges) in
 * the first layer, every other node one layer below its deepest predecessor; cycles are broken at the edge
 * that closes them. Within a layer nodes are ordered by the mean position of their predecessors, then spread
 * with the gaps. {@link Orientation#TOP_DOWN} puts layers in rows, {@link Orientation#LEFT_RIGHT} in columns.
 */
public class TreeGraphLayout extends GraphLayout {

    public enum Orientation { TOP_DOWN, LEFT_RIGHT }

    private Orientation orientation = Orientation.TOP_DOWN;

    public TreeGraphLayout() {
        horizontalGap = 36;
        verticalGap = 60;
    }

    public TreeGraphLayout setOrientation(Orientation o) {
        orientation = o == null ? Orientation.TOP_DOWN : o;
        return this;
    }

    @Override
    public void layout(GraphScene<?, ?> scene, boolean animated) {
        List<NodeWidget> ns = nodes(scene);
        if (ns.isEmpty()) {
            return;
        }
        Map<NodeWidget, List<NodeWidget>> succ = new LinkedHashMap<NodeWidget, List<NodeWidget>>();
        Map<NodeWidget, List<NodeWidget>> pred = new LinkedHashMap<NodeWidget, List<NodeWidget>>();
        for (NodeWidget n : ns) {
            succ.put(n, new ArrayList<NodeWidget>());
            pred.put(n, new ArrayList<NodeWidget>());
        }
        for (EdgeWidget e : edges(scene)) {
            NodeWidget s = e.getSourceNode();
            NodeWidget t = e.getTargetNode();
            if (s != t && succ.containsKey(s) && succ.containsKey(t) && !succ.get(s).contains(t)) {
                succ.get(s).add(t);
                pred.get(t).add(s);
            }
        }
        // layers by longest path from a root, with a DFS that ignores back edges
        Map<NodeWidget, Integer> layer = new HashMap<NodeWidget, Integer>();
        Set<NodeWidget> onStack = new HashSet<NodeWidget>();
        for (NodeWidget n : ns) {
            if (pred.get(n).isEmpty()) {
                assign(n, 0, succ, layer, onStack);
            }
        }
        for (NodeWidget n : ns) {
            if (!layer.containsKey(n)) {           // only reachable through a cycle
                assign(n, 0, succ, layer, onStack);
            }
        }
        int depth = 0;
        for (int l : layer.values()) {
            depth = Math.max(depth, l);
        }
        List<List<NodeWidget>> layers = new ArrayList<List<NodeWidget>>();
        for (int i = 0; i <= depth; i++) {
            layers.add(new ArrayList<NodeWidget>());
        }
        for (NodeWidget n : ns) {
            layers.get(layer.get(n)).add(n);
        }
        // order within a layer by the mean index of the predecessors in the layer above
        final Map<NodeWidget, Double> order = new HashMap<NodeWidget, Double>();
        for (int i = 0; i < layers.size(); i++) {
            List<NodeWidget> row = layers.get(i);
            if (i > 0) {
                for (NodeWidget n : row) {
                    double sum = 0;
                    int cnt = 0;
                    for (NodeWidget p : pred.get(n)) {
                        if (order.containsKey(p)) {
                            sum += order.get(p);
                            cnt++;
                        }
                    }
                    order.put(n, cnt == 0 ? Double.MAX_VALUE : sum / cnt);
                }
                java.util.Collections.sort(row, new java.util.Comparator<NodeWidget>() {
                    @Override
                    public int compare(NodeWidget a, NodeWidget b) {
                        return Double.compare(order.get(a), order.get(b));
                    }
                });
            }
            for (int k = 0; k < row.size(); k++) {
                order.put(row.get(k), (double) k);
            }
        }
        // sizes
        boolean topDown = orientation == Orientation.TOP_DOWN;
        double[] layerExtent = new double[layers.size()];
        double[] layerSpan = new double[layers.size()];
        double maxSpan = 0;
        for (int i = 0; i < layers.size(); i++) {
            double span = 0;
            for (NodeWidget n : layers.get(i)) {
                Size s = n.getSize();
                layerExtent[i] = Math.max(layerExtent[i], topDown ? s.height : s.width);
                span += (topDown ? s.width : s.height);
            }
            span += Math.max(0, layers.get(i).size() - 1) * (topDown ? horizontalGap : verticalGap);
            layerSpan[i] = span;
            maxSpan = Math.max(maxSpan, span);
        }
        double along = 0;
        for (int i = 0; i < layers.size(); i++) {
            double across = (maxSpan - layerSpan[i]) / 2;
            for (NodeWidget n : layers.get(i)) {
                Size s = n.getSize();
                double a = across + (topDown ? s.width : s.height) / 2;
                double b = along + layerExtent[i] / 2;
                n.setLocation(topDown ? new Point(a, b) : new Point(b, a), animated);
                across += (topDown ? s.width + horizontalGap : s.height + verticalGap);
            }
            along += layerExtent[i] + (topDown ? verticalGap : horizontalGap);
        }
    }

    private static void assign(NodeWidget n, int l, Map<NodeWidget, List<NodeWidget>> succ, Map<NodeWidget, Integer> layer, Set<NodeWidget> onStack) {
        Integer cur = layer.get(n);
        if (cur != null && cur >= l) {
            return;
        }
        if (onStack.contains(n)) {
            return;                                   // back edge: the cycle is broken here
        }
        layer.put(n, l);
        onStack.add(n);
        for (NodeWidget s : succ.get(n)) {
            assign(s, l + 1, succ, layer, onStack);
        }
        onStack.remove(n);
    }
}
