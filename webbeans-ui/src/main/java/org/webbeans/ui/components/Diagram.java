package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/**
 * A node-and-edge diagram on an SVG canvas (a flow chart, a dependency graph, a simple modeller): pan and zoom,
 * drag nodes, select, connect nodes by dragging from a node's border when {@link #setConnectable connectable},
 * drop {@link Palette} items onto it. Like {@link Kanban}, a drag is applied to the model on the server (nodes
 * keep their new position) and reported; connections and drops are only reported — the listener decides what
 * to add. Positions are in diagram units; the view (pan, zoom) is the client's.
 */
@Renderer(id = "wb.Diagram", resource = "org/webbeans/ui/client/renderers/diagram.js", css = "org/webbeans/ui/client/renderers/diagram.css")
public class Diagram extends Component {

    public enum Shape { RECT, ROUNDED, ELLIPSE, DIAMOND, CIRCLE }

    public enum LineStyle { SOLID, DASHED, DOTTED }

    /** A node. Mutable position (the diagram moves it); the rest is set through the fluent methods. */
    public static final class Node implements JsonValue {
        private final String key;
        private String label;
        private double x;
        private double y;
        private double width = 120;
        private double height = 44;
        private Shape shape = Shape.ROUNDED;
        private String color;
        private Resource icon;
        private String tooltip;
        private String style;
        private boolean locked;

        public Node(String key, String label, double x, double y) {
            this.key = key;
            this.label = label;
            this.x = x;
            this.y = y;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public Node label(String l) {
            label = l;
            return this;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public Node at(double nx, double ny) {
            x = nx;
            y = ny;
            return this;
        }

        public Node size(double w, double h) {
            width = w;
            height = h;
            return this;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        public Node shape(Shape s) {
            shape = s;
            return this;
        }

        public Node color(String css) {
            color = css;
            return this;
        }

        public Node icon(Resource r) {
            icon = r;
            return this;
        }

        public Node tooltip(String t) {
            tooltip = t;
            return this;
        }

        /** Extra CSS class names on the node group. */
        public Node style(String css) {
            style = css;
            return this;
        }

        /** Cannot be moved. */
        public Node locked(boolean b) {
            locked = b;
            return this;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("k", key);
            m.put("t", label);
            m.put("x", x);
            m.put("y", y);
            m.put("w", width);
            m.put("h", height);
            m.put("shape", shape.name().toLowerCase());
            if (color != null) {
                m.put("c", color);
            }
            if (icon != null) {
                m.put("i", icon);
            }
            if (tooltip != null) {
                m.put("tt", tooltip);
            }
            if (style != null) {
                m.put("s", style);
            }
            if (locked) {
                m.put("locked", Boolean.TRUE);
            }
            return m;
        }
    }

    /** A directed edge between two nodes. */
    public static final class Edge implements JsonValue {
        private final String key;
        private final String from;
        private final String to;
        private String label;
        private LineStyle lineStyle = LineStyle.SOLID;
        private boolean arrow = true;
        private String color;
        private String style;

        public Edge(String key, String from, String to) {
            this.key = key;
            this.from = from;
            this.to = to;
        }

        public String getKey() {
            return key;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public Edge label(String l) {
            label = l;
            return this;
        }

        public Edge lineStyle(LineStyle s) {
            lineStyle = s;
            return this;
        }

        public Edge arrow(boolean b) {
            arrow = b;
            return this;
        }

        public Edge color(String css) {
            color = css;
            return this;
        }

        public Edge style(String css) {
            style = css;
            return this;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("k", key);
            m.put("from", from);
            m.put("to", to);
            if (label != null) {
                m.put("t", label);
            }
            if (lineStyle != LineStyle.SOLID) {
                m.put("ls", lineStyle.name().toLowerCase());
            }
            if (!arrow) {
                m.put("arrow", Boolean.FALSE);
            }
            if (color != null) {
                m.put("c", color);
            }
            if (style != null) {
                m.put("s", style);
            }
            return m;
        }
    }

    /** Selection changed: {@link #getNodeKeys()}, {@link #getEdgeKeys()}. */
    public static class SelectionEvent extends Event {
        public SelectionEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        @SuppressWarnings("unchecked")
        public List<String> getNodeKeys() {
            Object v = getData().get("nodes");
            return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
        }

        @SuppressWarnings("unchecked")
        public List<String> getEdgeKeys() {
            Object v = getData().get("edges");
            return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
        }

        public Node getNode() {
            List<String> k = getNodeKeys();
            return k.isEmpty() ? null : ((Diagram) getSource()).getNode(k.get(0));
        }
    }

    /** Something about one node: {@link #getNode()}. */
    public static class NodeEvent extends Event {
        public NodeEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        public Node getNode() {
            return ((Diagram) getSource()).getNode(getKey());
        }

        public double getX() {
            return getDouble("x", 0);
        }

        public double getY() {
            return getDouble("y", 0);
        }
    }

    /** An edge was clicked or activated: {@link #getEdge()}. */
    public static class EdgeEvent extends Event {
        public EdgeEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        public Edge getEdge() {
            return ((Diagram) getSource()).getEdge(getKey());
        }
    }

    /** The user dragged a connection from one node to another: {@link #getFrom()} → {@link #getTo()}. */
    public static class ConnectEvent extends Event {
        public ConnectEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getFrom() {
            return getString("from");
        }

        public String getTo() {
            return getString("to");
        }
    }

    /** A {@link Palette} item was dropped: {@link #getItemKey()} at {@link #getX()}, {@link #getY()} (diagram units). */
    public static class DropEvent extends Event {
        public DropEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getItemKey() {
            return getString("item");
        }

        public double getX() {
            return getDouble("x", 0);
        }

        public double getY() {
            return getDouble("y", 0);
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Node>> NODES = (Property<List<Node>>) (Property<?>) Property.of("nodes", List.class, new ArrayList<Node>());
    @SuppressWarnings("unchecked")
    public static final Property<List<Edge>> EDGES = (Property<List<Edge>>) (Property<?>) Property.of("edges", List.class, new ArrayList<Edge>());
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> SELECTED = (Property<List<String>>) (Property<?>) Property.of("selected", List.class, new ArrayList<String>()).clientOwned();
    public static final Property<Integer> GRID = Property.of("grid", Integer.class, Integer.valueOf(10));
    public static final Property<Boolean> SHOW_GRID = Property.of("showGrid", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> CONNECTABLE = Property.of("connectable", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> DROP_TARGET = Property.of("dropTarget", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> MINIMAP = Property.of("minimap", Boolean.class, Boolean.FALSE);

    public static final EventType<SelectionEvent> SELECTION = EventType.of("select", SelectionEvent::new);
    public static final EventType<NodeEvent> NODE_MOVE = EventType.of("nodeMove", NodeEvent::new);
    public static final EventType<NodeEvent> NODE_ACTIVATE = EventType.of("nodeActivate", NodeEvent::new);
    public static final EventType<NodeEvent> NODE_MENU = EventType.of("nodeMenu", NodeEvent::new);
    public static final EventType<EdgeEvent> EDGE_ACTIVATE = EventType.of("edgeActivate", EdgeEvent::new);
    public static final EventType<ConnectEvent> CONNECT = EventType.of("connect", ConnectEvent::new);
    public static final EventType<DropEvent> DROP = EventType.of("drop", DropEvent::new);
    /** Delete pressed with a selection: the {@link SelectionEvent} lists what to delete (nothing is removed by the diagram). */
    public static final EventType<SelectionEvent> DELETE = EventType.of("delete", SelectionEvent::new);
    static final EventType<Event> MOVE = EventType.of("move", Event::new);

    private final Map<String, Node> nodes = new LinkedHashMap<String, Node>();
    private final Map<String, Edge> edges = new LinkedHashMap<String, Edge>();
    private boolean publishScheduled;

    public Diagram() {
        super("wb.Diagram");
        addListener(SELECTION, new Listener<SelectionEvent>() {
            @Override
            public void onEvent(SelectionEvent e) {
                setFromClient(SELECTED, new ArrayList<String>(e.getNodeKeys()));
            }
        });
        addListener(MOVE, new Listener<Event>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onEvent(Event e) {
                Object list = e.getData().get("nodes");
                if (list instanceof List) {
                    for (Map<String, Object> m : (List<Map<String, Object>>) list) {
                        Node n = nodes.get(String.valueOf(m.get("key")));
                        if (n != null && !n.locked) {
                            n.at(((Number) m.get("x")).doubleValue(), ((Number) m.get("y")).doubleValue());
                            if (isListened(NODE_MOVE.getName())) {
                                Map<String, Object> d = new LinkedHashMap<String, Object>();
                                d.put("key", n.key);
                                d.put("x", n.x);
                                d.put("y", n.y);
                                fireEvent(NODE_MOVE, d);
                            }
                        }
                    }
                }
            }
        });
    }

    // ---- model --------------------------------------------------------------------------------------------------

    public Node addNode(String key, String label, double x, double y) {
        Node n = new Node(key, label, x, y);
        addNode(n);
        return n;
    }

    public Diagram addNode(Node n) {
        nodes.put(n.key, n);
        publish();
        return this;
    }

    public Diagram removeNode(String key) {
        if (nodes.remove(key) != null) {
            List<String> gone = new ArrayList<String>();
            for (Edge e : edges.values()) {
                if (e.from.equals(key) || e.to.equals(key)) {
                    gone.add(e.key);
                }
            }
            edges.keySet().removeAll(gone);
            publish();
        }
        return this;
    }

    public Edge addEdge(String key, String from, String to) {
        Edge e = new Edge(key, from, to);
        addEdge(e);
        return e;
    }

    public Diagram addEdge(Edge e) {
        edges.put(e.key, e);
        publish();
        return this;
    }

    public Diagram removeEdge(String key) {
        if (edges.remove(key) != null) {
            publish();
        }
        return this;
    }

    public Diagram clear() {
        nodes.clear();
        edges.clear();
        publish();
        return this;
    }

    public Diagram setGraph(Collection<Node> ns, Collection<Edge> es) {
        nodes.clear();
        edges.clear();
        for (Node n : ns) {
            nodes.put(n.key, n);
        }
        for (Edge e : es) {
            edges.put(e.key, e);
        }
        publish();
        return this;
    }

    public Node getNode(String key) {
        return key == null ? null : nodes.get(key);
    }

    public Edge getEdge(String key) {
        return key == null ? null : edges.get(key);
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(new ArrayList<Node>(nodes.values()));
    }

    public List<Edge> getEdges() {
        return Collections.unmodifiableList(new ArrayList<Edge>(edges.values()));
    }

    /** Re-sends the model after nodes or edges were changed in place (label, colour, position). */
    public Diagram refresh() {
        publish();
        return this;
    }

    private void publish() {
        if (publishScheduled) {
            return;
        }
        if (getUI() == null) {
            doPublish();
            return;
        }
        publishScheduled = true;
        getUI().beforeFlush(new Runnable() {
            @Override
            public void run() {
                publishScheduled = false;
                doPublish();
            }
        });
    }

    private void doPublish() {
        set(NODES, new ArrayList<Node>(nodes.values()));
        set(EDGES, new ArrayList<Edge>(edges.values()));
    }

    @Override
    protected void attached() {
        if (!publishScheduled) {
            doPublish();
        }
    }

    // ---- view ---------------------------------------------------------------------------------------------------

    public Diagram setSelected(Collection<String> keys) {
        set(SELECTED, new ArrayList<String>(keys));
        return this;
    }

    public List<String> getSelectedKeys() {
        return get(SELECTED);
    }

    public List<Node> getSelectedNodes() {
        List<Node> out = new ArrayList<Node>();
        for (String k : get(SELECTED)) {
            Node n = nodes.get(k);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    /** Snap positions to a grid of this many units (0: off). */
    public Diagram setGrid(int units) {
        set(GRID, units);
        return this;
    }

    public Diagram setShowGrid(boolean b) {
        set(SHOW_GRID, b);
        return this;
    }

    public Diagram setConnectable(boolean b) {
        set(CONNECTABLE, b);
        return this;
    }

    public Diagram setDropTarget(boolean b) {
        set(DROP_TARGET, b);
        return this;
    }

    public Diagram setMinimap(boolean b) {
        set(MINIMAP, b);
        return this;
    }

    /** Zooms and pans so every node is visible. */
    public void fitToView() {
        call("fit");
    }

    public void zoomTo(double factor) {
        call("zoom", factor);
    }

    public void scrollTo(String nodeKey) {
        call("scrollTo", nodeKey);
    }

    /** Arranges the nodes in layers by edge direction (a simple layered layout) and re-sends them. */
    public Diagram autoLayout() {
        Map<String, Integer> layer = new LinkedHashMap<String, Integer>();
        for (Node n : nodes.values()) {
            layer.put(n.key, 0);
        }
        for (int pass = 0; pass < nodes.size(); pass++) {
            boolean changed = false;
            for (Edge e : edges.values()) {
                Integer a = layer.get(e.from), b = layer.get(e.to);
                if (a != null && b != null && b <= a && !e.from.equals(e.to) && a < nodes.size()) {
                    layer.put(e.to, a + 1);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        Map<Integer, Integer> count = new LinkedHashMap<Integer, Integer>();
        for (Node n : nodes.values()) {
            int l = layer.get(n.key);
            int idx = count.containsKey(l) ? count.get(l) : 0;
            count.put(l, idx + 1);
            n.at(40 + l * 200, 40 + idx * 90);
        }
        publish();
        return this;
    }

    public Registration addSelectionListener(Listener<SelectionEvent> l) {
        return addListener(SELECTION, l);
    }

    public Registration addNodeMoveListener(Listener<NodeEvent> l) {
        return addListener(NODE_MOVE, l);
    }

    public Registration addNodeActivateListener(Listener<NodeEvent> l) {
        return addListener(NODE_ACTIVATE, l);
    }

    public Registration addNodeMenuListener(Listener<NodeEvent> l) {
        return addListener(NODE_MENU, l);
    }

    public Registration addEdgeActivateListener(Listener<EdgeEvent> l) {
        return addListener(EDGE_ACTIVATE, l);
    }

    public Registration addConnectListener(Listener<ConnectEvent> l) {
        setConnectable(true);
        return addListener(CONNECT, l);
    }

    public Registration addDropListener(Listener<DropEvent> l) {
        return addListener(DROP, l);
    }

    public Registration addDeleteListener(Listener<SelectionEvent> l) {
        return addListener(DELETE, l);
    }
}
