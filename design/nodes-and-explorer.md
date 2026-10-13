# WebBeans — Nodes and Explorer API (detailed design v0.3)

Roadmap step 4. JDK 8. **v0.3 reflects the implementation** (`webbeans-nodes`; notes in `impl/step4-notes.md`). Packages: `org.webbeans.nodes` (Node, Children, properties, editors, clipboard), `org.webbeans.explorer` (ExplorerManager, views), `org.webbeans.actions` (in `webbeans-ui`: the minimal `Action` contract). Client renderers `treeview.js` (shared engine `wb.nodeView`), `outlineview.js`, `listview.js`, `propertysheet.js`, `nodes.css`. Depends on steps 1–3.

## 1. Scope and NetBeans mapping

The API mirrors `org.openide.nodes` / `org.openide.explorer` so NetBeans code ports mostly by changing imports, with WebBeans types where NetBeans uses Swing/AWT: `Resource` instead of `Image`, `org.webbeans.actions.Action` instead of `javax.swing.Action`, `PropertyEditor` is a server-side object with a client editor kind.

| NetBeans | WebBeans | Notes |
|---|---|---|
| `Node`, `AbstractNode`, `FilterNode` | same | `Node.Cookie` replaced by `getLookup()` capabilities |
| `Children`, `Children.Keys`, `ChildFactory` | same | async `ChildFactory` with a "Please wait…" node |
| `Node.Property`, `Node.PropertySet`, `PropertySupport`, `Sheet` | same | typed generics, server-side editors |
| `ExplorerManager`, `ExplorerUtils` | same | |
| `BeanTreeView`, `OutlineView`, `ListView`, `PropertySheet`, `ChoiceView` | `TreeView`, `OutlineView`, `ListView`, `PropertySheet`, `NodeComboBox` | components |
| `ExClipboard`, `PasteType`, `Transferable` | `Clipboard` (per session), `PasteType`, `Transferable` | flavor names instead of `DataFlavor` |
| `OpenCookie`, `EditCookie`, `SaveCookie` | `Openable`, `Editable`, `Savable` in the node's lookup | |
| `SystemAction`, `ActionMap` | `org.webbeans.actions.Action` (shared instances), `ActionMap` | step 5 adds registration and context-aware actions |

## 2. Node

```java
public abstract class Node implements Lookup.Provider {
    protected Node(Children children);  protected Node(Children children, Lookup lookup);

    // identity & display
    public String getName();  public void setName(String n);
    public String getDisplayName();  public void setDisplayName(String n);
    public String getHtmlDisplayName();    // null = plain; b, i, em, strong, u, s, span, font, small (sanitised on the client)
    public String getShortDescription();   // tooltip
    public Resource getIcon();  public Resource getOpenedIcon();

    // structure
    public final Children getChildren();  protected final void setChildren(Children c);
    public final Node getParentNode();    public final boolean isLeaf();

    // behaviour
    public Lookup getLookup();             // the constructor's lookup, else Lookup.EMPTY
    public Action[] getActions(boolean context);   // null = separator; use shared instances
    public Action getPreferredAction();    // first Openable in the lookup, else the first action
    public boolean canRename(); canDestroy(); canCut(); canCopy();
    public void destroy() throws IOException;      // removes from Children.Array parents and fires nodeDestroyed
    public Transferable clipboardCopy(); clipboardCut(); drag();
    public PasteType[] getPasteTypes(Transferable t);
    public PasteType getDropType(Transferable t, DropAction action, int index);   // default: first paste type

    // properties
    public PropertySet[] getPropertySets();  public final List<Property<?>> getProperties();  public final Property<?> getProperty(String name);
    public Handle getHandle();             // persistable pointer (§10); null = not persistable

    // events
    public void addNodeListener(NodeListener l);  removeNodeListener(l);  addPropertyChangeListener(l);  removePropertyChangeListener(l);
    protected final void fireNameChange / fireDisplayNameChange / fireIconChange / fireOpenedIconChange / firePropertyChange / firePropertySetsChange / fireNodeDestroyed(...)
}
public class AbstractNode extends Node { setIconBase(path); setIconBaseWithExtension(path); setActions(Action...); setPreferredAction(a); getSheet()/setSheet()/createSheet(); setCanRename/setCanDestroy/setCanCut(boolean) }
public class FilterNode extends Node { getOriginal(); delegates everything; FilterNode.Children wraps the original's children and follows its changes (override createNodes to filter) }
public final class Sheet { static Set createPropertiesSet(); createExpertSet(); put(Set); get(name); PropertySet[] toArray(); class Set extends Node.PropertySet { put(Property...); get(name); remove(name) } }
```

- Nodes are UI-agnostic: many views may show one node, each keeping its own expansion and selection state. Several nodes may represent one object (a tree node and a window's node); route model changes through the model so every node fires (`firePropertyChange`), as the demo's `SolarSystem` does.
- Mutations happen on the session executor (strict-threading check when a session is current), so views update synchronously in the same delta.

## 3. Children

```java
public abstract class Children {
    public static final Children LEAF;
    public final Node[] getNodes();  public Node[] getNodes(boolean optimalResult);   // optimalResult forces async factories to compute now
    public final int getNodesCount();  public final Node getNodeAt(int i);  public final Node findChild(String name);
    public final Node getNode();           // owner
    public final boolean isInitialized();
    protected void addNotify() {}          // first time the nodes are requested (first expansion in some view)
    protected void removeNotify() {}       // owner destroyed / keys dropped
    public static class Array extends Children { add(Node...); remove(Node...); }
    public abstract static class Keys<T> extends Children {
        protected abstract Node[] createNodes(T key);
        protected final void setKeys(Collection<? extends T> keys);   // diff: unchanged keys keep their nodes
        protected final void refreshKey(T key);
    }
    public static <T> Children create(ChildFactory<T> f, boolean asynchronous);
}
public abstract class ChildFactory<T> {
    protected abstract boolean createKeys(List<T> toPopulate);   // false = call again (batching)
    protected Node createNodeForKey(T key);  protected Node[] createNodesForKey(T key);
    protected Node createWaitNode();                              // "Please wait…" with a spinner icon
    protected final void refresh(boolean immediate);
}
```

- `Children.Keys.setKeys` is the diff engine: unchanged keys (by `equals`) keep their `Node` instances; new keys get `createNodes`; vanished keys fire `childrenRemoved` with their old indices and `removeNotify` on their children; survivors in a new order fire `childrenReordered`.
- Asynchronous `ChildFactory`: `createKeys` runs on a shared `RequestProcessor` ("Node Children", 4 threads); results are applied with `session.access`. The wait node is shown as a temporary child until the first result; a stale result (a newer refresh started) is discarded.
- Lazy materialisation: `addNotify` runs on the first `getNodes()`, which views call when a node is expanded.

## 4. Events

```java
public interface NodeListener extends PropertyChangeListener {
    void childrenAdded(NodeMemberEvent ev);  void childrenRemoved(NodeMemberEvent ev);
    void childrenReordered(NodeReorderEvent ev);  void nodeDestroyed(NodeEvent ev);
}
```

`NodeMemberEvent.getDelta()` / `getDeltaIndices()` give the added or removed nodes and their indices, so views patch incrementally. Property changes carry `PROP_NAME`, `PROP_DISPLAY_NAME`, `PROP_SHORT_DESCRIPTION`, `PROP_ICON`, `PROP_OPENED_ICON`, `PROP_LEAF`, `PROP_PROPERTY_SETS`, `PROP_PARENT_NODE`, or a `Node.Property` name. `NodeAdapter` is the empty implementation.

## 5. Properties and editors

```java
public abstract static class Node.Property<T> extends FeatureDescriptor {
    public Class<T> getValueType();  public abstract boolean canRead(); canWrite();
    public abstract T getValue() throws …;  public abstract void setValue(T v) throws …;   // throw IllegalArgumentException with a user message
    public PropertyEditor<T> getPropertyEditor();  public void setPropertyEditor(PropertyEditor<T> e);   // default: PropertyEditors.forType
    public String getValueAsText();        // what the views show
}
public final class PropertySupport { ReadOnly<T>, ReadWrite<T>, Reflection<T>(bean, type, getterName, setterName), Name(node, label, hint) }

public interface PropertyEditor<T> {
    enum Kind { TEXT, NUMBER, BOOLEAN, CHOICE, DATE, DATETIME, COLOR, MULTILINE, CUSTOM }
    Kind getKind();  String getAsText(T value);  T fromText(String text) throws IllegalArgumentException;
    default List<Choice<T>> getChoices();  default Map<String,Object> getClientConfig();  default Component createCustomEditor(T current);
}
public final class PropertyEditors { static <T> PropertyEditor<T> forType(Class<T>); static <T> void register(Class<T>, PropertyEditor<T>); TEXT, OBJECT, BOOLEAN, INTEGER, LONG, DOUBLE; enums → CHOICE }
```

- Editing is text-based: the client edits with the primitive editor of the kind (text input, number input, checkbox, select) and sends the text; the server converts with `fromText`, calls `setValue`, and the node's `propertyChange` re-renders the cell/row. Exceptions travel back as `editError {prop, message}` and are shown at the cell (outline) or under the row (sheet).
- Number editors format and parse with the session locale. `DATE/DATETIME/COLOR` fall back to text input in v1; `CUSTOM` needs the dialog API (step 5).

## 6. ExplorerManager and ExplorerUtils

```java
public final class ExplorerManager {
    public static final String PROP_ROOT_CONTEXT, PROP_EXPLORED_CONTEXT, PROP_SELECTED_NODES;
    public Node getRootContext();  public void setRootContext(Node root);           // resets explored context and selection
    public Node getExploredContext();  public void setExploredContext(Node n);  setExploredContextAndSelection(Node n, Node[] sel)
    public Node[] getSelectedNodes();  public void setSelectedNodes(Node[] nodes) throws PropertyVetoException;
    public void addPropertyChangeListener / addVetoableChangeListener(…);
    public interface Provider { ExplorerManager getExplorerManager(); }
    public static ExplorerManager find(Component c);   // nearest Provider up the component tree
}
public final class ExplorerUtils {
    public static Lookup createLookup(ExplorerManager em, ActionMap map);   // selected nodes + their lookups + the action map, live
    public static Action actionCopy(em), actionCut(em), actionPaste(em), actionDelete(em, confirm);
    public static void activateActions(ExplorerManager em, boolean activate);   // no-op (kept for ports)
}
```

Typical window (the demo's `PlanetsTopComponent`):

```java
@TopComponent.Registration(mode = "explorer", openAtStartup = true)
@TopComponent.Description(preferredID = "PlanetsTC", iconBase = "org/webbeans/demo/icons/planet.svg")
public final class PlanetsTopComponent extends TopComponent implements ExplorerManager.Provider {
    private final ExplorerManager em = new ExplorerManager();
    public PlanetsTopComponent() {
        ActionMap map = new ActionMap();
        map.put(ActionMap.DELETE, ExplorerUtils.actionDelete(em, true));
        associateLookup(ExplorerUtils.createLookup(em, map));
        OutlineView view = new OutlineView("Planet");
        view.setPropertyColumns("gravity", "Gravity", "moons", "Moons", "type", "Type");
        setLayout(new Layout.Border());
        add(view, Layout.Border.CENTER);
    }
    @Override protected void componentOpened() { em.setRootContext(new SolarSystemNode(SolarSystem.get())); }
    @Override public ExplorerManager getExplorerManager() { return em; }
}
```

Because `createLookup` puts the selected nodes and the contents of their lookups into the window's lookup, activating the window puts them into the global context (`WindowManager.getGlobalContext()`, what context-aware actions observe) and makes the window the owner of the *activated nodes* (`WindowManager.getActivatedNodesContext()`, what a Properties window observes — it does not move when a window without nodes is activated, so clicking into the sheet keeps the selection) — the NetBeans selection model.

## 7. Views

All views extend `NodeView extends Component`, which binds to the nearest `ExplorerManager` on attach (or `setExplorerManager(em)`) and keeps a **per-view node table** (node ↔ view-local integer id). Children are sent only when a node is expanded (the root's with the first payload).

| View | Renderer | Properties | Notes |
|---|---|---|---|
| `TreeView` | `wb.TreeView` | `rootVisible`, `selectionMode` (SINGLE/MULTI), `dragSource`, `dropTarget`, `quickSearch`, `popupAllowed`, `defaultActionAllowed`; `expandNode/collapseNode` | the `BeanTreeView` |
| `OutlineView` | `wb.OutlineView` | tree props + `columns` (`setPropertyColumns(name, label, …)`, `setColumnWidth`), `treeColumnLabel`, `sortable`, `showHeader` | property columns from `getValueAsText`; writable cells edited in place (double-click) |
| `ListView` | `wb.ListView` | `showParentNode`, `traversalAllowed`, `iconSize` | children of the explored context; double-click traverses, `..` goes up |
| `PropertySheet` | `wb.PropertySheet` | `showDescription`, `expertVisible`; `setNodes(Node...)` for explicit use | sets as collapsible groups; common properties of a multi-selection, writes to all |
| `NodeComboBox` | `wb.ComboBox` | items = children of the explored context | the `ChoiceView`; selection drives `em.setSelectedNodes` |

Views push structure with `call`s on the view component — one component, bounded payloads:

```json
{ "op":"call", "id":42, "m":"nodes", "args":[{
   "add":    [ {"n":17, "p":3, "i":0, "name":"Main.java", "dn":"Main.java", "html":"<b>Main.java</b>", "icon":{"$res":"org/example/java.svg"},
                "leaf":true, "tip":"…", "can":{"rename":true,"destroy":true,"cut":true,"copy":true},
                "cols":{"size":{"text":"12 KB"}, "moons":{"text":"2","edit":"NUMBER"}, "type":{"text":"ROCKY","edit":"CHOICE","choices":[…]}}} ],
   "remove": [ 12 ],
   "update": [ {"n":9, "dn":"Util.java", "cols":{"size":{"text":"3 KB"}}} ],
   "reorder":[ {"p":3, "order":[17, 9, 14]} ],
   "children":[ {"p":3, "complete":true} ]
}] }
```

Other downstream calls: `reset`, `select [n…]`, `expand n`, `collapse n`, `showMenu {x, y, items:[{id, label, enabled, icon?} | null]}`, `startRename n`, `editError {n, prop, message}`, `context {n, dn, hasParent}` (list view); the sheet gets `sheet {count, title, icon, sets:[{name, label, expert, props:[{name, label, desc, text, mixed, canWrite, kind, choices?, config?}]}]}` and `editError {prop, message}`.

Client → server events (validated against the node table):

| Event | Data | Server |
|---|---|---|
| `expand` / `collapse` | `{n}` | `expand` sends the children once (`Children.getNodes()` → `addNotify`); expansion state stays on the client |
| `select` (lazy 50 ms) | `{nodes:[…]}` | `em.setSelectedNodes`; a veto reverts the client with `select` |
| `activate` | `{n}` | `getPreferredAction()` performed with the selection context |
| `contextMenu` | `{nodes, x, y}` | §8 |
| `menuAction` | `{id}` | runs the action computed for the last menu |
| `rename` | `{n, name}` | `canRename` → `setName` |
| `delete` | `{nodes}` | `canDestroy` → `destroy()` (the client asked for confirmation) |
| `editCell` | `{n, prop, text}` | §5 |
| `drop` | `{target, index, action, nodes | text}` | §9 |
| `traverse` | `{n}` / `{up:true}` | list view: explored context |
| `edit` (sheet) | `{prop, text}` | §5, applied to every shown node |

- Client-side: sorting (numeric-aware, by column text), column widths, quick search (type-ahead on visible rows), keyboard navigation (arrows, Home/End, PageUp/Down, Enter = activate, Space = toggle, F2 = rename, Delete), Shift/Ctrl selection, HTML5 drag of selected rows, the context-menu popup, in-place editors. Rows are plain DOM rows (no virtualisation in v1).
- A full `init` (first connect, resync) rebuilds views through `Component.resync()`: root + first level again; deeper expansion is lost, as the design intended for resync.

## 8. Context menus

On `contextMenu` the server computes `NodeOp.findActions(selection)` (one node: its actions; several: the actions common to all by identity, in the first node's order, with separators kept), appends the standard edit actions (Copy, Cut, Paste, Delete from the window's `ActionMap`, else `ExplorerUtils` defaults) and Rename for a single renamable node, and sends `showMenu`. The client draws the popup; `menuAction {id}` runs the action with a context lookup of the selected nodes, their lookups and the view's lookup. The actions module (step 5) replaces the client-local popup with its menu components and the standard actions with the registered system actions.

## 9. Drag & drop and clipboard

```java
public interface Transferable { Set<String> getFlavors(); boolean isDataFlavorSupported(String f); <T> T getData(String flavor, Class<T> type); }
public final class Transferables { FLAVOR_NODES, FLAVOR_TEXT; static nodes(Node...); text(String); of(flavor, data); multi(Transferable...); static List<Node> nodesOf(Transferable) }
public abstract class PasteType { public abstract Transferable paste() throws IOException; public String getName(); }
public final class Clipboard { static getDefault() /* per UISession */; getContents(); isCut(); setContents(Transferable, boolean cut); add/removeListener }
```

- Within one tab a drag carries node ids; the server resolves them (`node.drag()` per node, merged with `Transferables.multi`) and asks `target.getDropType(t, action, index)`; `action` is MOVE, or COPY with Ctrl. Cross-tab drag is not supported (browser limitation); copy/cut/paste works across tabs through the session `Clipboard`.
- External text drops arrive as `Transferables.text(...)`; file drops (`/wb/upload`) are not wired yet.
- `canCut/canCopy` decide which rows are drag sources; `canDestroy` enables Delete; `canRename` enables F2 / Rename.

## 10. Persistence: `Node.Handle` and `TopComponent.Factory`

```java
public interface Node.Handle { String getType(); Map<String,Object> toMap(); }
public interface NodeHandleFactory { Node restore(Map<String,Object> data) throws IOException; }   // @Registration(path = "Nodes/Handles", name = "<type>", type = NodeHandleFactory.class)
public interface TopComponent.Factory { TopComponent create(Map<String,Object> state); }        // @TopComponent.Description(factory = MyTC.Factory.class)
```

`NodeOp.restore(type, data)` finds the factory under `Nodes/Handles/<type>`. Multi-instance windows opened from nodes store what they need in `writeState` (the demo stores the planet name); at the next login the window manager calls the `Factory` for every `#n` id in the saved layout and skips the ones it cannot recreate.

## 11. Contributed node hierarchies (registry)

Not yet implemented: `@NodeFactory.Registration(path = "Nodes/Services")` contributing children to a registry-driven root (NetBeans' Services tab). `Children.Keys` over `Registry.getDefault().folder(path).getEntries()` is all that is needed; step 5 or the sample app adds it.

## 12. Threading

- Node mutations, `setKeys`, `fire*` → session executor (checked when a session is current).
- `ChildFactory.createKeys` of asynchronous factories → `RequestProcessor`; results posted with `session.access`. Property getters run on the session thread when rows are built; keep them cheap or cache.
- A node shown in three views fires one `propertyChange`; each view's `update` lands in the same flush.

## 13. Testing

- `NodesTest`: keys diffing and events, sync/async factories with the wait node, `FilterNode`, properties/editors/sheet, `NodeOp`, transferables, destroy.
- `ExplorerTest` (headless UI with a recording transport): payloads, lazy children, node events, selection ↔ lookup ↔ client with veto, rename/editCell/delete and errors, context menu, drop, list view, property sheet, resync.
- `webbeans-demo/e2e/nodes.js` (Playwright): the same against the real server plus cell editing, F2, keyboard, quick search, sorting, factory-restored windows after reload, forced resync.

## 14. Open points for step 5

- Menu/popup components and `ContextAwareAction` replace the client-local popup and the ad-hoc standard actions; `DialogDisplayer` replaces the browser `confirm()` for Delete and hosts `CUSTOM` property editors.
- Virtualised rendering for very large children; server-side sorting; insertion-index drops; file drops via `/wb/upload`.
