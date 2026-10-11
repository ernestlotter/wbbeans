package org.webbeans.nodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.webbeans.actions.Action;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;

/** Utilities over nodes, as NetBeans' {@code NodeOp}. */
public final class NodeOp {

    private NodeOp() {
    }

    /**
     * The actions common to all nodes (by identity), in the order of the first node; separators kept where all
     * agree. One node: its actions.
     */
    public static Action[] findActions(Node... nodes) {
        if (nodes.length == 0) {
            return new Action[0];
        }
        if (nodes.length == 1) {
            return nodes[0].getActions(true);
        }
        List<Action> first = Arrays.asList(nodes[0].getActions(true));
        Set<Action> common = new LinkedHashSet<Action>(first);
        for (int i = 1; i < nodes.length; i++) {
            Set<Action> theirs = new LinkedHashSet<Action>(Arrays.asList(nodes[i].getActions(true)));
            common.retainAll(theirs);
        }
        List<Action> out = new ArrayList<Action>();
        for (Action a : first) {
            if (a == null) {
                if (!out.isEmpty() && out.get(out.size() - 1) != null) {
                    out.add(null);
                }
            } else if (common.contains(a)) {
                out.add(a);
            }
        }
        while (!out.isEmpty() && out.get(out.size() - 1) == null) {
            out.remove(out.size() - 1);
        }
        return out.toArray(new Action[out.size()]);
    }

    /** The path of names from the root to the node. */
    public static String[] createPath(Node node, Node root) {
        List<String> path = new ArrayList<String>();
        for (Node n = node; n != null && n != root; n = n.getParentNode()) {
            path.add(0, n.getName());
        }
        return path.toArray(new String[path.size()]);
    }

    /** Follows a path of names from the root; null when a segment is missing. */
    public static Node findPath(Node root, String... path) {
        Node n = root;
        for (String name : path) {
            n = n.getChildren().findChild(name);
            if (n == null) {
                return null;
            }
        }
        return n;
    }

    /** Restores a node from handle data through the registered {@link NodeHandleFactory}; null if impossible. */
    public static Node restore(String type, Map<String, Object> data) throws IOException {
        Entry e = Registry.getDefault().entry("Nodes/Handles/" + type);
        NodeHandleFactory f = e == null ? null : e.getInstance(NodeHandleFactory.class);
        return f == null ? null : f.restore(data);
    }
}
