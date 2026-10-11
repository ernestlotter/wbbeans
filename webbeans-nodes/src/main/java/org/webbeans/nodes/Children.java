package org.webbeans.nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The children of a {@link Node}: {@link #LEAF}, an explicit {@link Array}, or a keyed set ({@link Keys},
 * usually through {@link #create(ChildFactory, boolean)}). Children are materialised lazily: {@link #addNotify}
 * runs the first time a view asks for the nodes (the first expansion).
 */
public abstract class Children {

    /** No children; the node is a leaf. */
    public static final Children LEAF = new Children() {
        @Override
        protected List<Node> nodes() {
            return Collections.emptyList();
        }

        @Override
        void attach(Node owner) {
        }

        @Override
        void detach() {
        }
    };

    private Node owner;
    private boolean initialized;

    /** The owning node. */
    public final Node getNode() {
        return owner;
    }

    void attach(Node node) {
        if (owner != null && owner != node) {
            throw new IllegalStateException("Children already belong to " + owner);
        }
        owner = node;
    }

    void detach() {
        owner = null;
    }

    /** The current nodes (materialising on first call). */
    public final Node[] getNodes() {
        ensureInitialized();
        List<Node> l = nodes();
        return l.toArray(new Node[l.size()]);
    }

    /** As {@link #getNodes()}; {@code optimalResult} waits for asynchronous factories to finish. */
    public Node[] getNodes(boolean optimalResult) {
        return getNodes();
    }

    public final int getNodesCount() {
        ensureInitialized();
        return nodes().size();
    }

    public final Node getNodeAt(int i) {
        ensureInitialized();
        List<Node> l = nodes();
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    public final Node findChild(String name) {
        for (Node n : getNodes()) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        return null;
    }

    /** Whether {@link #addNotify} has run. */
    public final boolean isInitialized() {
        return initialized;
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialized = true;
            addNotify();
        }
    }

    /** Called once, the first time the nodes are requested: compute keys, start listening to the model. */
    protected void addNotify() {
    }

    /** Called when the children are released (owner node destroyed); stop listening. */
    protected void removeNotify() {
    }

    /** The live list, in order. */
    protected abstract List<Node> nodes();

    // ---- change notification used by subclasses --------------------------------------------------------------

    final void nodesAdded(List<Node> added, int[] indices) {
        for (Node n : added) {
            n.setParentNode(owner);
        }
        if (owner != null && !added.isEmpty()) {
            owner.fireChildrenAdded(added, indices);
        }
    }

    final void nodesRemoved(List<Node> removed, int[] indices) {
        for (Node n : removed) {
            if (n.getParentNode() == owner) {
                n.setParentNode(null);
            }
        }
        if (owner != null && !removed.isEmpty()) {
            owner.fireChildrenRemoved(removed, indices);
        }
    }

    final void nodesReordered(int[] perm) {
        if (owner != null) {
            owner.fireChildrenReordered(perm);
        }
    }

    // ---- Array ----------------------------------------------------------------------------------------------------

    /** Explicit, mutable children. */
    public static class Array extends Children {
        private final List<Node> list = new ArrayList<Node>();

        public Array() {
        }

        public Array(Collection<? extends Node> initial) {
            list.addAll(initial);
            for (Node n : initial) {
                n.setParentNode(getNode());
            }
        }

        @Override
        void attach(Node node) {
            super.attach(node);
            for (Node n : list) {
                n.setParentNode(node);
            }
        }

        @Override
        protected List<Node> nodes() {
            return list;
        }

        public boolean add(Node... nodes) {
            Node.checkThread();
            List<Node> added = new ArrayList<Node>();
            int[] idx = new int[nodes.length];
            int k = 0;
            for (Node n : nodes) {
                if (!list.contains(n)) {
                    idx[k++] = list.size();
                    list.add(n);
                    added.add(n);
                }
            }
            nodesAdded(added, Arrays.copyOf(idx, k));
            return !added.isEmpty();
        }

        public boolean remove(Node... nodes) {
            Node.checkThread();
            List<Node> removed = new ArrayList<Node>();
            List<Integer> idx = new ArrayList<Integer>();
            for (Node n : nodes) {
                int i = list.indexOf(n);
                if (i >= 0) {
                    idx.add(i);
                    removed.add(n);
                }
            }
            // remove from the back so indices stay valid for the listener
            List<Integer> sorted = new ArrayList<Integer>(idx);
            Collections.sort(sorted, Collections.reverseOrder());
            for (int i : sorted) {
                list.remove(i);
            }
            int[] ia = new int[idx.size()];
            for (int i = 0; i < ia.length; i++) {
                ia[i] = idx.get(i);
            }
            nodesRemoved(removed, ia);
            return !removed.isEmpty();
        }
    }

    // ---- Keys --------------------------------------------------------------------------------------------------------

    /**
     * Children computed from keys: {@link #setKeys} diffs the new keys against the current ones, keeps the nodes
     * of unchanged keys (identity, expansion and selection survive), creates nodes for new keys and removes the
     * nodes of vanished keys. Keys must have value equality (ids, immutable objects).
     */
    public abstract static class Keys<T> extends Children {
        private final Map<T, List<Node>> byKey = new LinkedHashMap<T, List<Node>>();
        private final List<Node> flat = new ArrayList<Node>();

        /** Nodes for one key; an empty array hides the key. */
        protected abstract Node[] createNodes(T key);

        @Override
        protected List<Node> nodes() {
            return flat;
        }

        /** Replaces the keys, reusing nodes of keys that are still present. */
        protected final void setKeys(Collection<? extends T> keys) {
            Node.checkThread();
            Map<T, List<Node>> next = new LinkedHashMap<T, List<Node>>();
            List<Node> created = new ArrayList<Node>();
            for (T key : keys) {
                if (next.containsKey(key)) {
                    continue;
                }
                List<Node> existing = byKey.get(key);
                if (existing != null) {
                    next.put(key, existing);
                } else {
                    Node[] ns = createNodes(key);
                    List<Node> l = ns == null ? Collections.<Node>emptyList() : Arrays.asList(ns);
                    next.put(key, new ArrayList<Node>(l));
                    created.addAll(l);
                }
            }
            List<Node> removed = new ArrayList<Node>();
            for (Map.Entry<T, List<Node>> e : byKey.entrySet()) {
                if (!next.containsKey(e.getKey())) {
                    removed.addAll(e.getValue());
                }
            }
            apply(next, created, removed);
        }

        /** Recreates the nodes of one key (e.g. after the underlying object changed shape). */
        protected final void refreshKey(T key) {
            Node.checkThread();
            List<Node> old = byKey.get(key);
            if (old == null) {
                return;
            }
            Node[] ns = createNodes(key);
            List<Node> l = new ArrayList<Node>(ns == null ? Collections.<Node>emptyList() : Arrays.asList(ns));
            Map<T, List<Node>> next = new LinkedHashMap<T, List<Node>>(byKey);
            next.put(key, l);
            apply(next, l, old);
        }

        private void apply(Map<T, List<Node>> next, List<Node> created, List<Node> removed) {
            List<Node> oldFlat = new ArrayList<Node>(flat);
            List<Node> newFlat = new ArrayList<Node>();
            for (List<Node> l : next.values()) {
                newFlat.addAll(l);
            }
            // removals (indices in the old list)
            if (!removed.isEmpty()) {
                int[] idx = new int[removed.size()];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = oldFlat.indexOf(removed.get(i));
                }
                flat.clear();
                for (Node n : oldFlat) {
                    if (!removed.contains(n)) {
                        flat.add(n);
                    }
                }
                nodesRemoved(removed, idx);
                for (Node n : removed) {
                    n.getChildren().removeNotify();
                }
            }
            byKey.clear();
            byKey.putAll(next);
            // additions (indices in the new list)
            flat.clear();
            flat.addAll(newFlat);
            if (!created.isEmpty()) {
                int[] idx = new int[created.size()];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = newFlat.indexOf(created.get(i));
                }
                nodesAdded(created, idx);
            }
            // reorder of survivors
            List<Node> survivors = new ArrayList<Node>(oldFlat);
            survivors.removeAll(removed);
            List<Node> survivorsNow = new ArrayList<Node>(newFlat);
            survivorsNow.removeAll(created);
            if (!survivors.equals(survivorsNow)) {
                int[] perm = new int[newFlat.size()];
                for (int i = 0; i < perm.length; i++) {
                    perm[i] = i;
                }
                nodesReordered(perm);
            }
        }

        @Override
        protected void removeNotify() {
            for (Node n : flat) {
                n.getChildren().removeNotify();
            }
        }
    }

    /** Children driven by a {@link ChildFactory}; asynchronous factories compute keys off the session thread. */
    public static <T> Children create(ChildFactory<T> factory, boolean asynchronous) {
        return new FactoryChildren<T>(factory, asynchronous);
    }
}
