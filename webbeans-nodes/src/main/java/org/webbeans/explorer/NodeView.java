package org.webbeans.explorer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionMap;
import org.webbeans.actions.Actions;
import org.webbeans.actions.ContextAwareAction;
import org.webbeans.actions.Popup;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.nodes.Clipboard;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.NodeEvent;
import org.webbeans.nodes.NodeListener;
import org.webbeans.nodes.NodeMemberEvent;
import org.webbeans.nodes.NodeOp;
import org.webbeans.nodes.NodeReorderEvent;
import org.webbeans.nodes.PasteType;
import org.webbeans.nodes.Transferable;
import org.webbeans.nodes.Transferables;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/**
 * Base of the explorer views ({@link TreeView}, {@link OutlineView}, {@link ListView}): binds to the nearest
 * {@link ExplorerManager}, keeps a view-local node table (node ↔ integer id), mirrors node events to the client as
 * incremental {@code nodes} payloads, and turns client events (expand, select, activate, rename, context menu,
 * drop) into node operations. Children are sent only when a node is expanded.
 */
public abstract class NodeView extends Component {

    private static final Logger LOG = Logger.getLogger(NodeView.class.getName());

    public enum SelectionMode { SINGLE, MULTI }

    public static final Property<SelectionMode> SELECTION_MODE = Property.of("selectionMode", SelectionMode.class, SelectionMode.MULTI);
    public static final Property<Boolean> POPUP_ALLOWED = Property.of("popupAllowed", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> DEFAULT_ACTION_ALLOWED = Property.of("defaultActionAllowed", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> DRAG_SOURCE = Property.of("dragSource", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> DROP_TARGET = Property.of("dropTarget", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> QUICK_SEARCH = Property.of("quickSearch", Boolean.class, Boolean.TRUE);
    /** Localised strings for the renderer. */
    public static final Property<Map<String, Object>> LABELS = Property.of("labels", mapClass(), null);

    public static final EventType<Event> EXPAND = EventType.of("expand", Event::new);
    public static final EventType<Event> COLLAPSE = EventType.of("collapse", Event::new);
    public static final EventType<Event> SELECT = EventType.of("select", Event::new).lazy(50);
    public static final EventType<Event> ACTIVATE = EventType.of("activate", Event::new);
    public static final EventType<Event> CONTEXT_MENU = EventType.of("contextMenu", Event::new);
    public static final EventType<Event> RENAME = EventType.of("rename", Event::new);
    public static final EventType<Event> DELETE = EventType.of("delete", Event::new);
    public static final EventType<Event> EDIT_CELL = EventType.of("editCell", Event::new);
    public static final EventType<Event> DROP = EventType.of("drop", Event::new);

    private ExplorerManager explicitManager;
    private ExplorerManager em;
    private final Map<Node, Integer> ids = new IdentityHashMap<Node, Integer>();
    private final Map<Integer, Node> nodes = new LinkedHashMap<Integer, Node>();
    private final Set<Node> childrenSent = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private Popup popup;
    private int nextId = 1;
    private boolean updatingSelection;

    private final PropertyChangeListener emListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String p = evt.getPropertyName();
            if (ExplorerManager.PROP_ROOT_CONTEXT.equals(p)) {
                rebuild();
            } else if (ExplorerManager.PROP_SELECTED_NODES.equals(p)) {
                pushSelection();
            } else if (ExplorerManager.PROP_EXPLORED_CONTEXT.equals(p)) {
                exploredContextChanged();
            }
        }
    };

    private final NodeListener nodeListener = new NodeListener() {
        @Override
        public void childrenAdded(NodeMemberEvent ev) {
            Node parent = ev.getNode();
            if (!childrenSent.contains(parent)) {
                return;
            }
            Map<String, Object> payload = payload();
            List<Object> adds = new ArrayList<Object>();
            Node[] all = parent.getChildren().getNodes();
            for (Node n : ev.getDelta()) {
                int index = Arrays.asList(all).indexOf(n);
                adds.add(row(n, parent, index));
            }
            payload.put("add", adds);
            emit(payload);
        }

        @Override
        public void childrenRemoved(NodeMemberEvent ev) {
            List<Object> removed = new ArrayList<Object>();
            for (Node n : ev.getDelta()) {
                Integer id = ids.get(n);
                if (id != null) {
                    removed.add(id);
                    unregister(n);
                }
            }
            if (!removed.isEmpty()) {
                Map<String, Object> payload = payload();
                payload.put("remove", removed);
                emit(payload);
                selectionPruned();
            }
        }

        @Override
        public void childrenReordered(NodeReorderEvent ev) {
            Node parent = ev.getNode();
            Integer pid = ids.get(parent);
            if (pid == null || !childrenSent.contains(parent)) {
                return;
            }
            List<Object> order = new ArrayList<Object>();
            for (Node n : parent.getChildren().getNodes()) {
                Integer id = ids.get(n);
                if (id != null) {
                    order.add(id);
                }
            }
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("p", pid);
            r.put("order", order);
            Map<String, Object> payload = payload();
            payload.put("reorder", Collections.singletonList(r));
            emit(payload);
        }

        @Override
        public void nodeDestroyed(NodeEvent ev) {
            Node n = ev.getNode();
            Integer id = ids.get(n);
            if (id != null) {
                unregister(n);
                Map<String, Object> payload = payload();
                payload.put("remove", Collections.singletonList(id));
                emit(payload);
                selectionPruned();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            Node n = (Node) evt.getSource();
            Integer id = ids.get(n);
            if (id == null) {
                return;
            }
            if (Node.PROP_PARENT_NODE.equals(evt.getPropertyName())) {
                return;
            }
            Map<String, Object> u = row(n, n.getParentNode(), -1);
            u.remove("p");
            u.remove("i");
            Map<String, Object> payload = payload();
            payload.put("update", Collections.singletonList(u));
            emit(payload);
        }
    };

    /** The nodes expanded by the user or by code; re-expanded after a rebuild (root change, reconnect, re-attach). */
    private final Set<Node> expanded = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());

    protected NodeView(String rendererId) {
        super(rendererId);
        addListener(EXPAND, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Node n = node(e.getInt("n", -1));
                if (n != null) {
                    expanded.add(n);
                    sendChildren(n);
                }
            }
        });
        addListener(COLLAPSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Node n = node(e.getInt("n", -1));
                if (n != null) {
                    expanded.remove(n);
                }
            }
        });
        addListener(SELECT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                selectFromClient(e);
            }
        });
        addListener(ACTIVATE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Node n = node(e.getInt("n", -1));
                if (n != null && Boolean.TRUE.equals(get(DEFAULT_ACTION_ALLOWED))) {
                    performPreferred(n);
                }
            }
        });
        addListener(CONTEXT_MENU, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                if (Boolean.TRUE.equals(get(POPUP_ALLOWED))) {
                    showContextMenu(nodesOf(e.getData().get("nodes")), e.getInt("x", 0), e.getInt("y", 0));
                }
            }
        });
        addListener(RENAME, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Node n = node(e.getInt("n", -1));
                String name = e.getString("name");
                if (n != null && n.canRename() && name != null && !name.trim().isEmpty()) {
                    n.setName(name.trim());
                }
            }
        });
        addListener(DELETE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                ExplorerUtils.deleteNodes(nodesOf(e.getData().get("nodes")), true);
            }
        });
        addListener(EDIT_CELL, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Node n = node(e.getInt("n", -1));
                if (n != null) {
                    editCell(n, e.getString("prop"), e.getString("text"));
                }
            }
        });
        addListener(DROP, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                drop(e);
            }
        });
        set(LABELS, labels());
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    private static Map<String, Object> labels() {
        Map<String, Object> l = new LinkedHashMap<String, Object>();
        for (String k : new String[] {"CTL_Copy", "CTL_Cut", "CTL_Paste", "CTL_Delete", "CTL_Rename", "CTL_Open", "MSG_ConfirmDelete", "LBL_PleaseWait"}) {
            l.put(k, Bundles.getMessage(Node.class, k));
        }
        return l;
    }

    // ---- manager binding -----------------------------------------------------------------------------------------------

    /** Uses this manager instead of the nearest {@link ExplorerManager.Provider}. */
    public NodeView setExplorerManager(ExplorerManager m) {
        explicitManager = m;
        if (isAttached()) {
            bind();
        }
        return this;
    }

    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void attached() {
        bind();
    }

    @Override
    protected void detached() {
        unbind();
    }

    @Override
    protected void resync() {
        if (em != null) {
            rebuild();
        }
    }

    private void bind() {
        unbind();
        em = explicitManager != null ? explicitManager : ExplorerManager.find(this);
        if (em != null) {
            em.addPropertyChangeListener(emListener);
        }
        rebuild();
    }

    private void unbind() {
        if (em != null) {
            em.removePropertyChangeListener(emListener);
            em = null;
        }
        clearTable();
    }

    private void clearTable() {
        for (Node n : new ArrayList<Node>(ids.keySet())) {
            n.removeNodeListener(nodeListener);
        }
        ids.clear();
        nodes.clear();
        childrenSent.clear();
    }

    /** Sends the whole visible structure again (root changed). */
    protected void rebuild() {
        clearTable();
        call("reset");
        Node root = rootNode();
        if (root == null) {
            return;
        }
        register(root);
        Map<String, Object> payload = payload();
        payload.put("add", Collections.singletonList(row(root, null, 0)));
        emit(payload);
        if (expandRootInitially()) {
            sendChildren(root);
            call("expand", idOf(root));
        }
        reexpand();
        pushSelection();
    }

    /** Expands a node (sends its children) and remembers it. */
    protected final void expand(Node n) {
        expanded.add(n);
        if (ids.containsKey(n)) {
            sendChildren(n);
            call("expand", idOf(n));
        }
    }

    /** Collapses a node and forgets it. */
    protected final void collapse(Node n) {
        expanded.remove(n);
        if (ids.containsKey(n)) {
            call("collapse", idOf(n));
        }
    }

    /**
     * After a rebuild: expands the remembered nodes again. A node can only be expanded once its parent's children
     * were sent, so passes repeat until nothing changes; what is still unknown then is not under the root and
     * is forgotten.
     */
    private void reexpand() {
        if (expanded.isEmpty()) {
            return;
        }
        List<Node> pending = new ArrayList<Node>(expanded);
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (java.util.Iterator<Node> it = pending.iterator(); it.hasNext();) {
                Node n = it.next();
                if (ids.containsKey(n)) {
                    sendChildren(n);
                    call("expand", idOf(n));
                    it.remove();
                    progress = true;
                }
            }
        }
        for (Node n : pending) {
            expanded.remove(n);
        }
    }

    /** The node shown at the top: the root context (tree) or the explored context (list). */
    protected Node rootNode() {
        return em == null ? null : em.getRootContext();
    }

    /** Whether the root's children are sent with the initial payload. */
    protected boolean expandRootInitially() {
        return true;
    }

    protected void exploredContextChanged() {
    }

    // ---- node table ----------------------------------------------------------------------------------------------------

    protected final int register(Node n) {
        Integer id = ids.get(n);
        if (id == null) {
            id = nextId++;
            ids.put(n, id);
            nodes.put(id, n);
            n.addNodeListener(nodeListener);
        }
        return id;
    }

    private void unregister(Node n) {
        Integer id = ids.remove(n);
        if (id != null) {
            nodes.remove(id);
            n.removeNodeListener(nodeListener);
        }
        if (childrenSent.remove(n)) {
            for (Node c : n.getChildren().getNodes()) {
                unregister(c);
            }
        }
    }

    protected final Node node(int id) {
        return nodes.get(id);
    }

    protected final Integer idOf(Node n) {
        return ids.get(n);
    }

    protected final List<Node> nodesOf(Object idList) {
        List<Node> out = new ArrayList<Node>();
        if (idList instanceof List) {
            for (Object o : (List<?>) idList) {
                if (o instanceof Number) {
                    Node n = nodes.get(((Number) o).intValue());
                    if (n != null) {
                        out.add(n);
                    }
                }
            }
        }
        return out;
    }

    /** Sends the children of a node (once); later changes arrive through node events. */
    protected final void sendChildren(Node parent) {
        if (childrenSent.contains(parent) || !ids.containsKey(parent)) {
            return;
        }
        childrenSent.add(parent);
        Node[] children = parent.getChildren().getNodes();
        List<Object> adds = new ArrayList<Object>();
        for (int i = 0; i < children.length; i++) {
            adds.add(row(children[i], parent, i));
        }
        Map<String, Object> payload = payload();
        payload.put("add", adds);
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("p", ids.get(parent));
        c.put("complete", Boolean.TRUE);
        payload.put("children", Collections.singletonList(c));
        emit(payload);
    }

    /** The wire representation of one node. Subclasses add {@code cols}. */
    protected Map<String, Object> row(Node n, Node parent, int index) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("n", register(n));
        if (parent != null) {
            r.put("p", ids.get(parent));
        }
        if (index >= 0) {
            r.put("i", index);
        }
        r.put("name", n.getName());
        r.put("dn", n.getDisplayName());
        if (n.getHtmlDisplayName() != null) {
            r.put("html", n.getHtmlDisplayName());
        }
        Resource icon = n.getIcon();
        if (icon != null) {
            r.put("icon", icon);
            Resource opened = n.getOpenedIcon();
            if (opened != null && !opened.equals(icon)) {
                r.put("oicon", opened);
            }
        }
        r.put("leaf", n.isLeaf());
        String tip = n.getShortDescription();
        if (tip != null && !tip.equals(n.getDisplayName())) {
            r.put("tip", tip);
        }
        Map<String, Object> can = new LinkedHashMap<String, Object>();
        can.put("rename", n.canRename());
        can.put("destroy", n.canDestroy());
        can.put("cut", n.canCut());
        can.put("copy", n.canCopy());
        r.put("can", can);
        Map<String, Object> cols = columns(n);
        if (cols != null) {
            r.put("cols", cols);
        }
        return r;
    }

    /** Extra per-row data (outline columns); null by default. */
    protected Map<String, Object> columns(Node n) {
        return null;
    }

    protected static Map<String, Object> payload() {
        return new LinkedHashMap<String, Object>();
    }

    protected final void emit(Map<String, Object> payload) {
        call("nodes", payload);
    }

    // ---- selection -----------------------------------------------------------------------------------------------------------

    private void pushSelection() {
        if (updatingSelection || em == null) {
            return;
        }
        List<Object> sel = new ArrayList<Object>();
        for (Node n : em.getSelectedNodes()) {
            reveal(n);
            Integer id = ids.get(n);
            if (id != null) {
                sel.add(id);
            }
        }
        call("select", sel);
    }

    /** Expands the ancestors of a node selected programmatically, so the selection is visible (as Swing's TreeView does). */
    private void reveal(Node n) {
        if (ids.containsKey(n) || n == null) {
            return;
        }
        List<Node> chain = new ArrayList<Node>();
        for (Node p = n.getParentNode(); p != null && !ids.containsKey(p); p = p.getParentNode()) {
            chain.add(0, p);
        }
        Node top = chain.isEmpty() ? n.getParentNode() : chain.get(0).getParentNode();
        if (top == null || !ids.containsKey(top)) {
            return; // not under this view's root
        }
        chain.add(0, top);
        for (Node p : chain) {
            expanded.add(p);
            sendChildren(p);
            call("expand", idOf(p));
        }
    }

    private void selectFromClient(Event e) {
        if (em == null) {
            return;
        }
        List<Node> sel = nodesOf(e.getData().get("nodes"));
        if (get(SELECTION_MODE) == SelectionMode.SINGLE && sel.size() > 1) {
            sel = sel.subList(0, 1);
        }
        updatingSelection = true;
        try {
            em.setSelectedNodes(sel.toArray(new Node[sel.size()]));
        } catch (PropertyVetoException ex) {
            updatingSelection = false;
            pushSelection(); // revert the client
            return;
        } finally {
            updatingSelection = false;
        }
    }

    private void selectionPruned() {
        if (em == null) {
            return;
        }
        List<Node> keep = new ArrayList<Node>();
        for (Node n : em.getSelectedNodes()) {
            if (ids.containsKey(n)) {
                keep.add(n);
            }
        }
        if (keep.size() != em.getSelectedNodes().length) {
            try {
                em.setSelectedNodes(keep.toArray(new Node[keep.size()]));
            } catch (PropertyVetoException ex) {
                // keep the stale selection
            }
        }
    }

    // ---- actions ----------------------------------------------------------------------------------------------------------

    /**
     * The invocation context for actions on these nodes: the nodes, their lookups, the window's {@link ActionMap}
     * and the UI's services — but not the window's current selection, which may lag behind a right-click.
     */
    private Lookup contextFor(List<Node> sel) {
        List<Lookup> l = new ArrayList<Lookup>();
        l.add(Lookups.fixed(sel.toArray()));
        for (Node n : sel) {
            l.add(n.getLookup());
        }
        ActionMap map = getLookup().lookup(ActionMap.class);
        if (map != null) {
            l.add(Lookups.singleton(map));
        }
        if (getUI() != null) {
            l.add(getUI().getLookup());
        }
        return new ProxyLookup(l.toArray(new Lookup[l.size()]));
    }

    private void performPreferred(Node n) {
        Action a = n.getPreferredAction();
        if (a != null) {
            Lookup ctx = contextFor(Collections.singletonList(n));
            Action bound = a instanceof ContextAwareAction ? ((ContextAwareAction) a).createContextAwareInstance(ctx) : a;
            if (bound.isEnabled()) {
                Actions.invoke(bound, this, ctx);
            }
        }
    }

    /**
     * Computes the popup actions for the selection (node actions, then the standard edit actions) and shows
     * them as a {@link Popup} over the selection context ({@link Actions#actionsToPopup}).
     */
    protected void showContextMenu(List<Node> sel, int x, int y) {
        if (sel.isEmpty()) {
            return;
        }
        List<Action> actions = new ArrayList<Action>(Arrays.asList(NodeOp.findActions(sel.toArray(new Node[sel.size()]))));
        List<Action> standard = standardActions(sel);
        if (!standard.isEmpty()) {
            if (!actions.isEmpty()) {
                actions.add(null);
            }
            actions.addAll(standard);
        }
        if (popup != null) {
            popup.close();
        }
        popup = Actions.actionsToPopup(actions, contextFor(sel), x, y);
        popup.setOnClose(new Runnable() {
            @Override
            public void run() {
                popup = null;
            }
        });
    }

    /** The popup currently shown for this view, or null. */
    public Popup getPopup() {
        return popup;
    }

    /** Copy / Cut / Paste / Delete / Rename for the selection, from the window's {@link ActionMap} when it has them. */
    protected List<Action> standardActions(final List<Node> sel) {
        List<Action> out = new ArrayList<Action>();
        ActionMap map = getLookup().lookup(ActionMap.class);
        if (em != null) {
            out.add(fromMap(map, ActionMap.COPY, ExplorerUtils.actionCopy(em)));
            out.add(fromMap(map, ActionMap.CUT, ExplorerUtils.actionCut(em)));
            out.add(fromMap(map, ActionMap.PASTE, ExplorerUtils.actionPaste(em)));
            out.add(null);
            out.add(fromMap(map, ActionMap.DELETE, ExplorerUtils.actionDelete(em, true)));
        }
        if (sel.size() == 1 && sel.get(0).canRename()) {
            final Node n = sel.get(0);
            final Integer id = ids.get(n);
            out.add(new AbstractAction(Bundles.getMessage(Node.class, "CTL_Rename")) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    call("startRename", id);
                }
            });
        }
        return out;
    }

    private static Action fromMap(ActionMap map, String key, Action fallback) {
        Action a = map == null ? null : map.get(key);
        return a != null ? a : fallback;
    }

    /** Applies an edited cell text to the property; subclasses with columns call this. */
    protected void editCell(Node n, String propName, String text) {
        Node.Property<?> p = propName == null ? null : n.getProperty(propName);
        if (p == null || !p.canWrite()) {
            return;
        }
        try {
            setFromText(p, text);
        } catch (Exception ex) {
            Map<String, Object> err = new LinkedHashMap<String, Object>();
            err.put("n", ids.get(n));
            err.put("prop", propName);
            err.put("message", ex.getMessage() == null ? ex.toString() : ex.getMessage());
            call("editError", err);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> void setFromText(Node.Property<T> p, String text) throws Exception {
        org.webbeans.nodes.PropertyEditor<T> editor = p.getPropertyEditor();
        T value = editor == null ? (T) text : editor.fromText(text == null ? "" : text);
        p.setValue(value);
    }

    private void drop(Event e) {
        if (!Boolean.TRUE.equals(get(DROP_TARGET))) {
            return;
        }
        Node target = node(e.getInt("target", -1));
        if (target == null) {
            return;
        }
        List<Node> dragged = nodesOf(e.getData().get("nodes"));
        Transferable t;
        if (!dragged.isEmpty()) {
            List<Transferable> parts = new ArrayList<Transferable>();
            for (Node n : dragged) {
                try {
                    parts.add(n.drag());
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, "drag failed for " + n, ex);
                }
            }
            t = parts.size() == 1 ? parts.get(0) : Transferables.multi(parts.toArray(new Transferable[parts.size()]));
        } else if (e.getString("text") != null) {
            t = Transferables.text(e.getString("text"));
        } else {
            return;
        }
        Node.DropAction action = Node.DropAction.MOVE;
        try {
            action = Node.DropAction.valueOf(String.valueOf(e.getString("action")).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // default
        }
        PasteType pt = target.getDropType(t, action, e.getInt("index", -1));
        if (pt != null) {
            try {
                Transferable after = pt.paste();
                if (after != null) {
                    Clipboard.getDefault().setContents(after, false);
                }
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Drop failed on " + target, ex);
            }
        }
    }

    // ---- convenience setters ------------------------------------------------------------------------------------------------

    public NodeView setSelectionMode(SelectionMode m) {
        set(SELECTION_MODE, m);
        return this;
    }

    public NodeView setPopupAllowed(boolean b) {
        set(POPUP_ALLOWED, b);
        return this;
    }

    public NodeView setDefaultActionAllowed(boolean b) {
        set(DEFAULT_ACTION_ALLOWED, b);
        return this;
    }

    public NodeView setDragSource(boolean b) {
        set(DRAG_SOURCE, b);
        return this;
    }

    public NodeView setDropTarget(boolean b) {
        set(DROP_TARGET, b);
        return this;
    }

    public NodeView setQuickSearchAllowed(boolean b) {
        set(QUICK_SEARCH, b);
        return this;
    }

    /** Known nodes, for tests. */
    protected final Set<Node> knownNodes() {
        return new HashSet<Node>(ids.keySet());
    }
}
