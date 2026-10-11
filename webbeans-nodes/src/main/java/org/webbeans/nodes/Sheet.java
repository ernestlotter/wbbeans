package org.webbeans.nodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The property sets of a node, by set name; {@link #createPropertiesSet()} is the standard "Properties" set. */
public final class Sheet {

    public static final String PROPERTIES = "properties";
    public static final String EXPERT = "expert";

    /** A mutable property set. */
    public static final class Set extends Node.PropertySet {
        private final Map<String, Node.Property<?>> props = new LinkedHashMap<String, Node.Property<?>>();

        public Set() {
        }

        public Set(String name, String displayName) {
            setName(name);
            setDisplayName(displayName);
        }

        public Node.Property<?> put(Node.Property<?> p) {
            return props.put(p.getName(), p);
        }

        public void put(Node.Property<?>... ps) {
            for (Node.Property<?> p : ps) {
                put(p);
            }
        }

        public Node.Property<?> get(String name) {
            return props.get(name);
        }

        public Node.Property<?> remove(String name) {
            return props.remove(name);
        }

        @Override
        public Node.Property<?>[] getProperties() {
            return props.values().toArray(new Node.Property<?>[props.size()]);
        }
    }

    private final Map<String, Set> sets = new LinkedHashMap<String, Set>();

    public static Set createPropertiesSet() {
        return new Set(PROPERTIES, "Properties");
    }

    public static Set createExpertSet() {
        Set s = new Set(EXPERT, "Expert");
        s.setExpert(true);
        return s;
    }

    public void put(Set set) {
        sets.put(set.getName(), set);
    }

    public Set get(String name) {
        return sets.get(name);
    }

    public Set remove(String name) {
        return sets.remove(name);
    }

    public Node.PropertySet[] toArray() {
        List<Node.PropertySet> l = new ArrayList<Node.PropertySet>(sets.values());
        return l.toArray(new Node.PropertySet[l.size()]);
    }
}
