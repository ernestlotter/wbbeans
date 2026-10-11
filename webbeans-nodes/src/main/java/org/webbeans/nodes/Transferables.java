package org.webbeans.nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Factory of {@link Transferable}s. */
public final class Transferables {

    /** Flavor whose data is a {@code List<Node>}. */
    public static final String FLAVOR_NODES = "nodes";
    /** Flavor whose data is a {@code String}. */
    public static final String FLAVOR_TEXT = "text";

    private Transferables() {
    }

    private static final class Simple implements Transferable {
        private final Map<String, Object> data = new LinkedHashMap<String, Object>();

        @Override
        public Set<String> getFlavors() {
            return Collections.unmodifiableSet(data.keySet());
        }

        @Override
        public boolean isDataFlavorSupported(String flavor) {
            return data.containsKey(flavor);
        }

        @Override
        public <T> T getData(String flavor, Class<T> type) {
            Object o = data.get(flavor);
            return type.isInstance(o) ? type.cast(o) : null;
        }
    }

    public static Transferable nodes(Node... nodes) {
        Simple t = new Simple();
        t.data.put(FLAVOR_NODES, Collections.unmodifiableList(new ArrayList<Node>(Arrays.asList(nodes))));
        return t;
    }

    public static Transferable text(String text) {
        Simple t = new Simple();
        t.data.put(FLAVOR_TEXT, text);
        return t;
    }

    public static Transferable of(String flavor, Object data) {
        Simple t = new Simple();
        t.data.put(flavor, data);
        return t;
    }

    /** Merges several transferables; node lists are concatenated. */
    public static Transferable multi(Transferable... parts) {
        Simple t = new Simple();
        List<Node> nodes = new ArrayList<Node>();
        Set<String> flavors = new LinkedHashSet<String>();
        for (Transferable p : parts) {
            for (String f : p.getFlavors()) {
                if (f.equals(FLAVOR_NODES)) {
                    @SuppressWarnings("unchecked")
                    List<Node> l = p.getData(f, List.class);
                    if (l != null) {
                        nodes.addAll(l);
                    }
                } else if (flavors.add(f)) {
                    t.data.put(f, p.getData(f, Object.class));
                }
            }
        }
        if (!nodes.isEmpty()) {
            t.data.put(FLAVOR_NODES, Collections.unmodifiableList(nodes));
        }
        return t;
    }

    /** The nodes carried by a transferable, or an empty list. */
    @SuppressWarnings("unchecked")
    public static List<Node> nodesOf(Transferable t) {
        List<Node> l = t == null ? null : t.getData(FLAVOR_NODES, List.class);
        return l == null ? Collections.<Node>emptyList() : l;
    }
}
