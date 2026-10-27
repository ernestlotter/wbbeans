package org.webbeans.graph;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.UI;

/**
 * A graph of nodes and edges drawn as widgets, in the spirit of the NetBeans Visual Library's {@code GraphScene}:
 * your <em>objects</em> ({@code N} nodes, {@code E} edges) are mapped to {@link NodeWidget}s and
 * {@link EdgeWidget}s that decide how they look — override {@link #attachNodeWidget} / {@link #attachEdgeWidget}
 * to choose shapes, pins, anchors, routers, labels and colours per object, or configure the widgets
 * {@link #addNode} and {@link #addEdge} hand back. {@link NoteWidget Notes} are free text attached to anything.
 * Everything lives on the server; the browser draws SVG and sends back selection, moves, connections, hovers
 * and double-clicks. Every change is a small delta merged per flush into one {@code graph} call.
 *
 * <pre>
 * GraphScene&lt;Service, Call&gt; scene = new GraphScene&lt;Service, Call&gt;() {
 *     &#64;Override protected NodeWidget attachNodeWidget(Service s) {
 *         NodeWidget w = new NodeWidget(s.getName()).setSecondaryLabel(s.getKind()).setAccent(s.isUp() ? "#2f8a4a" : "#b43b3b");
 *         w.addPin("in").setSide(Side.LEFT);
 *         return w;
 *     }
 * };
 * scene.addNode(web).setLocation(100, 80);
 * scene.addEdge(call).setRouter(Router.ORTHOGONAL).setLabel("HTTP");
 * scene.setEdgeSource(call, web);
 * scene.setEdgeTargetPin(call, db, "in");
 * scene.addNote("n1", "Fails over to the replica").attachTo(scene.findWidget(db));
 * scene.layout(new TreeGraphLayout(), true);
 * </pre>
 */
@Renderer(id = "wb.GraphScene", resource = "org/webbeans/graph/client/graph.js", css = "org/webbeans/graph/client/graph.css")
public class GraphScene<N, E> extends Component {

    public enum SelectionMode { NONE, SINGLE, MULTI }

    /** Snap-to-grid step in scene units (0 = off). */
    public static final Property<Integer> GRID = Property.of("grid", Integer.class, Integer.valueOf(10));
    public static final Property<Boolean> SHOW_GRID = Property.of("showGrid", Boolean.class, Boolean.TRUE);
    public static final Property<SelectionMode> SELECTION_MODE = Property.of("selectionMode", SelectionMode.class, SelectionMode.MULTI);
    /** Default router of edges that have none of their own. */
    public static final Property<Router> ROUTER = Property.of("router", Router.class, Router.DIRECT);
    /** Whether the user may drag connections (set when a {@link ConnectProvider} is installed). */
    public static final Property<Boolean> CONNECTABLE = Property.of("connectable", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> ANIMATION = Property.of("animation", Boolean.class, Boolean.TRUE);
    /** A small overview in the corner. */
    public static final Property<Boolean> MINIMAP = Property.of("minimap", Boolean.class, Boolean.FALSE);
    /** Accept drops from a {@code Palette} ({@link #DROP}). */
    public static final Property<Boolean> DROP_TARGET = Property.of("dropTarget", Boolean.class, Boolean.FALSE);

    public static final EventType<SelectionEvent> SELECT = EventType.of("select", SelectionEvent::new);
    public static final EventType<MoveEvent> MOVE = EventType.of("move", MoveEvent::new);
    public static final EventType<ConnectEvent> CONNECT = EventType.of("connect", ConnectEvent::new);
    /** Double-click on a node, edge or note. */
    public static final EventType<WidgetEvent> ACTIVATE = EventType.of("activate", WidgetEvent::new);
    /** The pointer entered a widget (or left all: widget null). */
    public static final EventType<WidgetEvent> HOVER = EventType.of("hover", WidgetEvent::new).lazy(60);
    /** Right-click; a {@link PopupProvider} shows a menu before listeners run. */
    public static final EventType<MenuEvent> MENU = EventType.of("menu", MenuEvent::new);
    public static final EventType<RenameEvent> RENAME = EventType.of("rename", RenameEvent::new);
    /** The Delete key with a selection; nothing is removed unless a listener does it. */
    public static final EventType<DeleteEvent> DELETE = EventType.of("delete", DeleteEvent::new);
    /** A palette item dropped on the scene: {@code item}, {@code sx}, {@code sy}. */
    public static final EventType<DropEvent> DROP = EventType.of("drop", DropEvent::new);
    static final EventType<Event> MEASURE = EventType.of("measure", Event::new).lazy(100);
    static final EventType<Event> EXPORT = EventType.of("export", Event::new);

    private final Map<N, NodeWidget> nodes = new LinkedHashMap<N, NodeWidget>();
    private final Map<E, EdgeWidget> edges = new LinkedHashMap<E, EdgeWidget>();
    private final Map<String, NoteWidget> notes = new LinkedHashMap<String, NoteWidget>();
    private final Map<String, Widget> byId = new LinkedHashMap<String, Widget>();
    private final Map<Widget, Object> objects = new LinkedHashMap<Widget, Object>();
    private final Set<String> selected = new LinkedHashSet<String>();
    private final InstanceContent selectionContent = new InstanceContent();
    private final Lookup selectionLookup = new AbstractLookup(selectionContent);

    /** Dirty widgets per kind; null value = removed. */
    private final Map<String, Widget> pendingNodes = new LinkedHashMap<String, Widget>();
    private final Map<String, Widget> pendingEdges = new LinkedHashMap<String, Widget>();
    private final Map<String, Widget> pendingNotes = new LinkedHashMap<String, Widget>();
    /** Ids added since the last flush: removing one of them again sends nothing at all. */
    private final Set<String> fresh = new LinkedHashSet<String>();
    private boolean scheduled;
    private int counter;
    private ConnectProvider connectProvider;
    private PopupProvider popupProvider;
    private final Map<Integer, CompletableFuture<?>> exports = new LinkedHashMap<Integer, CompletableFuture<?>>();
    private int nextExport = 1;

    public GraphScene() {
        super("wb.GraphScene");
        addListener(MEASURE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
            }
        });
        addListener(EXPORT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                completeExport(e);
            }
        });
    }

    // ---- widget creation hooks ------------------------------------------------------------------------------------

    /** Creates the widget for a node object; the default is a rounded node labelled {@code String.valueOf(node)}. */
    protected NodeWidget attachNodeWidget(N node) {
        return new NodeWidget(nextId("n")).setLabel(String.valueOf(node));
    }

    /** Creates the widget for an edge object; the default is a plain arrow with the scene's router. */
    protected EdgeWidget attachEdgeWidget(E edge) {
        return new EdgeWidget(nextId("e"));
    }

    /** A fresh widget id for subclasses creating widgets themselves. */
    protected final String nextId(String prefix) {
        String id;
        do {
            id = prefix + (++counter);
        } while (byId.containsKey(id));
        return id;
    }

    /** Creates a node widget with a fresh id, for {@link #attachNodeWidget} overrides. */
    protected final NodeWidget newNodeWidget() {
        return new NodeWidget(nextId("n"));
    }

    protected final EdgeWidget newEdgeWidget() {
        return new EdgeWidget(nextId("e"));
    }

    // ---- model ----------------------------------------------------------------------------------------------------

    /** Adds a node object and returns its widget (the existing one if already added). */
    public NodeWidget addNode(N node) {
        NodeWidget w = nodes.get(node);
        if (w != null) {
            return w;
        }
        w = attachNodeWidget(node);
        if (byId.containsKey(w.getId())) {
            throw new IllegalStateException("Widget id " + w.getId() + " already in use");
        }
        nodes.put(node, w);
        register(w, node);
        w.attachPins(this);
        for (PinWidget p : w.getPins()) {
            byId.put(p.getId(), p);
        }
        pendingNodes.put(w.getId(), w);
        fresh.add(w.getId());
        schedule();
        return w;
    }

    private void markRemoved(Map<String, Widget> pending, String id) {
        if (fresh.remove(id)) {
            pending.remove(id);
        } else {
            pending.put(id, null);
        }
        schedule();
    }

    /** Removes a node object, its pins, its edges and the attachments of notes to it. */
    public void removeNode(N node) {
        NodeWidget w = nodes.remove(node);
        if (w == null) {
            return;
        }
        for (E e : new ArrayList<E>(edges.keySet())) {
            EdgeWidget ew = edges.get(e);
            if (ew.getSourceNode() == w || ew.getTargetNode() == w) {
                removeEdge(e);
            }
        }
        for (PinWidget p : w.getPins()) {
            pinRemoved(p);
        }
        detachNotes(w);
        unregister(w);
        markRemoved(pendingNodes, w.getId());
    }

    public EdgeWidget addEdge(E edge) {
        EdgeWidget w = edges.get(edge);
        if (w != null) {
            return w;
        }
        w = attachEdgeWidget(edge);
        if (byId.containsKey(w.getId())) {
            throw new IllegalStateException("Widget id " + w.getId() + " already in use");
        }
        edges.put(edge, w);
        register(w, edge);
        pendingEdges.put(w.getId(), w);
        fresh.add(w.getId());
        schedule();
        return w;
    }

    public void removeEdge(E edge) {
        EdgeWidget w = edges.remove(edge);
        if (w == null) {
            return;
        }
        detachNotes(w);
        unregister(w);
        markRemoved(pendingEdges, w.getId());
    }

    public void setEdgeSource(E edge, N node) {
        edgeWidget(edge).setSource(node == null ? null : nodeWidget(node));
    }

    public void setEdgeTarget(E edge, N node) {
        edgeWidget(edge).setTarget(node == null ? null : nodeWidget(node));
    }

    /** Attaches the source end to a pin of the node (created if missing). */
    public void setEdgeSourcePin(E edge, N node, String pinId) {
        edgeWidget(edge).setSource(nodeWidget(node).addPin(pinId));
    }

    public void setEdgeTargetPin(E edge, N node, String pinId) {
        edgeWidget(edge).setTarget(nodeWidget(node).addPin(pinId));
    }

    /** The node object at the edge's source end, or null. */
    public N getEdgeSource(E edge) {
        return nodeObject(edgeWidget(edge).getSourceNode());
    }

    public N getEdgeTarget(E edge) {
        return nodeObject(edgeWidget(edge).getTargetNode());
    }

    /** The edges leaving and/or entering a node. */
    public List<E> findNodeEdges(N node, boolean outgoing, boolean incoming) {
        NodeWidget w = nodes.get(node);
        List<E> out = new ArrayList<E>();
        if (w == null) {
            return out;
        }
        for (Map.Entry<E, EdgeWidget> e : edges.entrySet()) {
            if ((outgoing && e.getValue().getSourceNode() == w) || (incoming && e.getValue().getTargetNode() == w)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public Set<N> getNodes() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    public Set<E> getEdges() {
        return Collections.unmodifiableSet(edges.keySet());
    }

    public boolean isNode(Object o) {
        return nodes.containsKey(o);
    }

    public boolean isEdge(Object o) {
        return edges.containsKey(o);
    }

    /** The widget of a node or edge object, or null. */
    public Widget findWidget(Object object) {
        Widget w = nodes.get(object);
        return w != null ? w : edges.get(object);
    }

    public NodeWidget findNodeWidget(N node) {
        return nodes.get(node);
    }

    public EdgeWidget findEdgeWidget(E edge) {
        return edges.get(edge);
    }

    /** The object a widget stands for (a pin's node object), or null for notes. */
    public Object findObject(Widget w) {
        if (w instanceof PinWidget) {
            return objects.get(((PinWidget) w).getNode());
        }
        return objects.get(w);
    }

    /** Removes everything. */
    public void clear() {
        for (N n : new ArrayList<N>(nodes.keySet())) {
            removeNode(n);
        }
        for (String id : new ArrayList<String>(notes.keySet())) {
            removeNote(id);
        }
    }

    // ---- notes ----------------------------------------------------------------------------------------------------

    /** Adds a note (ids must not clash with other widgets; choose e.g. {@code "note:1"}). */
    public NoteWidget addNote(String id, String text) {
        NoteWidget w = notes.get(id);
        if (w == null) {
            if (byId.containsKey(id)) {
                throw new IllegalStateException("Widget id " + id + " already in use");
            }
            w = new NoteWidget(id);
            notes.put(id, w);
            register(w, null);
            fresh.add(id);
        }
        w.setText(text);
        pendingNotes.put(id, w);
        schedule();
        return w;
    }

    public NoteWidget getNote(String id) {
        return notes.get(id);
    }

    public Collection<NoteWidget> getNotes() {
        return Collections.unmodifiableCollection(notes.values());
    }

    public void removeNote(String id) {
        NoteWidget w = notes.remove(id);
        if (w != null) {
            unregister(w);
            markRemoved(pendingNotes, id);
        }
    }

    private void detachNotes(Widget gone) {
        for (NoteWidget n : notes.values()) {
            if (n.getAttachedTo() == gone) {
                n.attachTo(null);
            }
        }
    }

    // ---- selection -----------------------------------------------------------------------------------------------

    /** A lookup holding the selected objects and widgets (what a window puts in its own lookup). */
    public Lookup getSelectionLookup() {
        return selectionLookup;
    }

    public Set<Object> getSelectedObjects() {
        Set<Object> out = new LinkedHashSet<Object>();
        for (String id : selected) {
            Widget w = byId.get(id);
            Object o = w == null ? null : findObject(w);
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }

    public List<Widget> getSelectedWidgets() {
        List<Widget> out = new ArrayList<Widget>();
        for (String id : selected) {
            Widget w = byId.get(id);
            if (w != null) {
                out.add(w);
            }
        }
        return out;
    }

    /** Selects the widgets of these objects (nodes, edges) and fires {@link #SELECT}. */
    public void setSelectedObjects(Collection<?> objs) {
        List<Widget> ws = new ArrayList<Widget>();
        for (Object o : objs) {
            Widget w = findWidget(o);
            if (w != null) {
                ws.add(w);
            }
        }
        setSelectedWidgets(ws);
    }

    public void setSelectedWidgets(Collection<? extends Widget> ws) {
        List<String> ids = new ArrayList<String>();
        for (Widget w : ws) {
            if (byId.containsKey(w.getId())) {
                ids.add(w.getId());
            }
        }
        call("select", ids);
        applySelection(ids);
        fireEvent(SELECT, Collections.<String, Object>singletonMap("ids", ids));
    }

    private void applySelection(List<String> ids) {
        selected.clear();
        selected.addAll(ids);
        List<Object> content = new ArrayList<Object>();
        for (Widget w : getSelectedWidgets()) {
            content.add(w);
            Object o = findObject(w);
            if (o != null) {
                content.add(o);
            }
        }
        selectionContent.set(content, null);
    }

    public SelectionMode getSelectionMode() {
        return get(SELECTION_MODE);
    }

    public GraphScene<N, E> setSelectionMode(SelectionMode m) {
        set(SELECTION_MODE, m == null ? SelectionMode.NONE : m);
        return this;
    }

    // ---- providers and appearance ----------------------------------------------------------------------------------

    public GraphScene<N, E> setConnectProvider(ConnectProvider p) {
        connectProvider = p;
        set(CONNECTABLE, p != null);
        for (NodeWidget w : nodes.values()) {
            widgetChanged(w);   // the connect-source flags are part of the node description
        }
        return this;
    }

    public ConnectProvider getConnectProvider() {
        return connectProvider;
    }

    /** Right-click menus; {@link #popupFromRegistry} is a ready-made provider over a registry folder. */
    public GraphScene<N, E> setPopupProvider(PopupProvider p) {
        popupProvider = p;
        return this;
    }

    /**
     * A provider showing the actions of a registry folder (e.g. {@code "Deployments/Actions"}) for every
     * widget: context actions over your node/edge types enable themselves on the right-clicked object.
     */
    public static PopupProvider popupFromRegistry(final String folderPath) {
        return new PopupProvider() {
            @Override
            public List<Action> getPopupActions(GraphScene<?, ?> scene, Widget widget) {
                return Actions.forPath(folderPath);
            }
        };
    }

    public GraphScene<N, E> setGrid(int step) {
        set(GRID, step);
        return this;
    }

    public GraphScene<N, E> setShowGrid(boolean b) {
        set(SHOW_GRID, b);
        return this;
    }

    public GraphScene<N, E> setRouter(Router r) {
        set(ROUTER, r == null ? Router.DIRECT : r);
        return this;
    }

    public GraphScene<N, E> setAnimation(boolean b) {
        set(ANIMATION, b);
        return this;
    }

    public GraphScene<N, E> setMinimap(boolean b) {
        set(MINIMAP, b);
        return this;
    }

    public GraphScene<N, E> setDropTarget(boolean b) {
        set(DROP_TARGET, b);
        return this;
    }

    @Override
    public GraphScene<N, E> setTestId(String id) {
        super.setTestId(id);
        return this;
    }

    // ---- events ----------------------------------------------------------------------------------------------------

    public Registration addSelectListener(Listener<SelectionEvent> l) {
        return addListener(SELECT, l);
    }

    public Registration addMoveListener(Listener<MoveEvent> l) {
        return addListener(MOVE, l);
    }

    public Registration addConnectListener(Listener<ConnectEvent> l) {
        return addListener(CONNECT, l);
    }

    public Registration addActivateListener(Listener<WidgetEvent> l) {
        return addListener(ACTIVATE, l);
    }

    public Registration addHoverListener(Listener<WidgetEvent> l) {
        return addListener(HOVER, l);
    }

    public Registration addMenuListener(Listener<MenuEvent> l) {
        return addListener(MENU, l);
    }

    public Registration addRenameListener(Listener<RenameEvent> l) {
        return addListener(RENAME, l);
    }

    public Registration addDeleteListener(Listener<DeleteEvent> l) {
        return addListener(DELETE, l);
    }

    public Registration addDropListener(Listener<DropEvent> l) {
        return addListener(DROP, l);
    }

    @Override
    protected void attached() {
        super.attached();
        // the client needs the menu/connect events even when the application listens to neither
        ensureListened(MENU);
        ensureListened(CONNECT);
        ensureListened(SELECT);
        ensureListened(MOVE);
        resync();       // everything built before the scene was attached goes out as one description
    }

    private <X extends Event> void ensureListened(EventType<X> type) {
        if (!isListened(type.getName())) {
            addListener(type, new Listener<X>() {
                @Override
                public void onEvent(X e) {
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onEvent(Event event) {
        Map<String, Object> d = event.getData();
        if (event instanceof SelectionEvent && !(event instanceof DeleteEvent)) {
            List<String> ids = new ArrayList<String>();
            Object raw = d.get("ids");
            if (raw instanceof List) {
                for (Object o : (List<Object>) raw) {
                    ids.add(String.valueOf(o));
                }
            }
            applySelection(ids);
        } else if (event instanceof MoveEvent) {
            Object moves = d.get("moves");
            if (moves instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) moves).entrySet()) {
                    Widget w = byId.get(e.getKey());
                    Point p = point(e.getValue());
                    if (p == null) {
                        continue;
                    }
                    if (w instanceof NodeWidget) {
                        ((NodeWidget) w).moved(p);
                    } else if (w instanceof NoteWidget) {
                        ((NoteWidget) w).moved(p);
                    }
                }
            }
        } else if (event instanceof ConnectEvent) {
            ConnectEvent ce = (ConnectEvent) event;
            Widget s = ce.getSourceWidget();
            Widget t = ce.getTargetWidget();
            if (connectProvider != null && s != null && t != null && connectProvider.isSource(s) && connectProvider.canConnect(s, t)) {
                connectProvider.connect(s, t);
            }
        } else if (event instanceof MenuEvent) {
            WidgetEvent we = (WidgetEvent) event;
            if (popupProvider != null) {
                List<Action> actions = popupProvider.getPopupActions(this, we.getWidget());
                if (actions != null && !actions.isEmpty()) {
                    Actions.actionsToPopup(actions, lookupFor(we.getWidget()), we.getX(), we.getY());
                }
            }
        } else if (d.get("sizes") instanceof Map) {
            Object sizes = d.get("sizes");
            if (sizes instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) sizes).entrySet()) {
                    Widget w = byId.get(e.getKey());
                    Point p = point(e.getValue());
                    if (w instanceof NodeWidget && p != null) {
                        ((NodeWidget) w).measured(new Size(p.x, p.y));
                    }
                }
            }
        }
    }

    private Lookup lookupFor(Widget w) {
        List<Object> content = new ArrayList<Object>();
        content.add(this);
        if (w != null) {
            content.add(w);
            Object o = findObject(w);
            if (o != null) {
                content.add(o);
            }
        }
        return org.openide.util.lookup.Lookups.fixed(content.toArray());
    }

    @SuppressWarnings("unchecked")
    private static Point point(Object v) {
        if (v instanceof List && ((List<Object>) v).size() >= 2) {
            List<Object> l = (List<Object>) v;
            if (l.get(0) instanceof Number && l.get(1) instanceof Number) {
                return new Point(((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue());
            }
        }
        return null;
    }

    // ---- view ------------------------------------------------------------------------------------------------------

    /** Zooms and pans so everything is visible. */
    public void fitToView() {
        call("view", "fit");
    }

    /** Sets the zoom factor (1 = 100%), keeping the centre. */
    public void zoomTo(double factor) {
        call("view", "zoom", factor);
    }

    /** Scrolls the view so the object's widget is in the middle. */
    public void centerOn(Object object) {
        Widget w = findWidget(object);
        if (w != null) {
            call("view", "center", w.getId());
        }
    }

    /** Runs a layout over the nodes; {@code animated} glides them to their new places. */
    public void layout(GraphLayout layout, boolean animated) {
        layout.layout(this, animated && Boolean.TRUE.equals(get(ANIMATION)));
    }

    /** Renders the scene to PNG in the browser; completes on the session executor. */
    public CompletableFuture<byte[]> exportPng(int width, int height) {
        CompletableFuture<byte[]> f = new CompletableFuture<byte[]>();
        int id = nextExport++;
        exports.put(id, f);
        call("export", id, "png", width, height);
        return f;
    }

    public CompletableFuture<String> exportSvg() {
        CompletableFuture<String> f = new CompletableFuture<String>();
        int id = nextExport++;
        exports.put(id, f);
        call("export", id, "svg", 0, 0);
        return f;
    }

    /** Lets the browser save the scene as a file ({@code "png"} or {@code "svg"}). */
    public void download(String format, String fileName) {
        call("download", format, fileName);
    }

    @SuppressWarnings("unchecked")
    private void completeExport(Event e) {
        int id = e.getInt("id", -1);
        CompletableFuture<?> f = exports.remove(id);
        if (f == null) {
            return;
        }
        String error = e.getString("error");
        if (error != null) {
            f.completeExceptionally(new IllegalStateException(error));
            return;
        }
        String data = e.getString("data");
        if ("png".equals(e.getString("format"))) {
            String b64 = data == null ? "" : data.substring(data.indexOf(',') + 1);
            ((CompletableFuture<byte[]>) f).complete(Base64.getDecoder().decode(b64));
        } else {
            ((CompletableFuture<String>) f).complete(data == null ? "" : data);
        }
    }

    // ---- deltas ----------------------------------------------------------------------------------------------------

    Widget widget(String id) {
        return id == null ? null : byId.get(id);
    }

    /** Untyped lookups for layouts. */
    NodeWidget nodeWidgetOf(Object node) {
        return nodes.get(node);
    }

    EdgeWidget edgeWidgetOf(Object edge) {
        return edges.get(edge);
    }

    private void register(Widget w, Object object) {
        w.setScene(this);
        byId.put(w.getId(), w);
        if (object != null) {
            objects.put(w, object);
        }
    }

    private void unregister(Widget w) {
        w.setScene(null);
        byId.remove(w.getId());
        objects.remove(w);
        if (selected.remove(w.getId())) {
            applySelection(new ArrayList<String>(selected));
        }
    }

    void pinRemoved(PinWidget p) {
        byId.remove(p.getId());
        for (EdgeWidget e : edges.values()) {
            if (e.getSource() == p) {
                e.setSource(p.getNode());
            }
            if (e.getTarget() == p) {
                e.setTarget(p.getNode());
            }
        }
        detachNotes(p);
    }

    private N nodeObject(NodeWidget w) {
        @SuppressWarnings("unchecked")
        N n = (N) (w == null ? null : objects.get(w));
        return n;
    }

    private NodeWidget nodeWidget(N node) {
        NodeWidget w = nodes.get(node);
        if (w == null) {
            throw new IllegalArgumentException("Not a node of this scene: " + node);
        }
        return w;
    }

    private EdgeWidget edgeWidget(E edge) {
        EdgeWidget w = edges.get(edge);
        if (w == null) {
            throw new IllegalArgumentException("Not an edge of this scene: " + edge);
        }
        return w;
    }

    void widgetChanged(Widget w) {
        if (w instanceof PinWidget) {
            w = ((PinWidget) w).getNode();
        }
        if (byId.get(w.getId()) != w) {
            return;     // not (or no longer) on this scene
        }
        if (w instanceof NodeWidget) {
            for (PinWidget p : ((NodeWidget) w).getPins()) {   // pins added since the node was registered
                p.setScene(this);
                byId.put(p.getId(), p);
            }
        }
        Map<String, Widget> pending = w instanceof NodeWidget ? pendingNodes : w instanceof EdgeWidget ? pendingEdges : pendingNotes;
        pending.put(w.getId(), w);
        schedule();
    }

    private void schedule() {
        UI ui = getUI();
        if (ui != null && !scheduled) {
            scheduled = true;
            ui.beforeFlush(new Runnable() {
                @Override
                public void run() {
                    scheduled = false;
                    emitPending();
                }
            });
        }
    }

    private Map<String, Object> describe(Widget w) {
        Map<String, Object> m = w.toJson();
        if (w instanceof NodeWidget && connectProvider != null) {
            NodeWidget n = (NodeWidget) w;
            if (connectProvider.isSource(n)) {
                m.put("cs", true);
            }
            if (!n.getPins().isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pins = (List<Map<String, Object>>) m.get("pins");
                int i = 0;
                for (PinWidget p : n.getPins()) {
                    if (connectProvider.isSource(p)) {
                        pins.get(i).put("cs", true);
                    }
                    i++;
                }
            }
        }
        return m;
    }

    private Map<String, Object> describeAll(Map<String, Widget> pending) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Widget> e : pending.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : describe(e.getValue()));
        }
        return out;
    }

    /** Sends the merged pending changes as one {@code graph} call. */
    private void emitPending() {
        if (!isAttached()) {
            pendingNodes.clear();
            pendingEdges.clear();
            pendingNotes.clear();
            fresh.clear();
            return;
        }
        if (pendingNodes.isEmpty() && pendingEdges.isEmpty() && pendingNotes.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (!pendingNodes.isEmpty()) {
            payload.put("nodes", describeAll(pendingNodes));
        }
        if (!pendingEdges.isEmpty()) {
            payload.put("edges", describeAll(pendingEdges));
        }
        if (!pendingNotes.isEmpty()) {
            payload.put("notes", describeAll(pendingNotes));
        }
        pendingNodes.clear();
        pendingEdges.clear();
        pendingNotes.clear();
        fresh.clear();
        call("graph", payload);
    }

    @Override
    protected void resync() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("reset", true);
        Map<String, Object> ns = new LinkedHashMap<String, Object>();
        for (NodeWidget w : nodes.values()) {
            ns.put(w.getId(), describe(w));
        }
        Map<String, Object> es = new LinkedHashMap<String, Object>();
        for (EdgeWidget w : edges.values()) {
            es.put(w.getId(), describe(w));
        }
        Map<String, Object> nts = new LinkedHashMap<String, Object>();
        for (NoteWidget w : notes.values()) {
            nts.put(w.getId(), describe(w));
        }
        payload.put("nodes", ns);
        payload.put("edges", es);
        payload.put("notes", nts);
        pendingNodes.clear();
        pendingEdges.clear();
        pendingNotes.clear();
        fresh.clear();
        call("graph", payload);
        if (!selected.isEmpty()) {
            call("select", new ArrayList<String>(selected));
        }
    }
}
