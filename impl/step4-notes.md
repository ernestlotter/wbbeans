# WebBeans — Step 4 Implementation Notes (v0.1)

What was built for roadmap step 4 (Nodes API and explorer views), how it maps to `design/nodes-and-explorer.md`, and where it deviates. Source: `webbeans-step4.zip` (77 JUnit tests; `webbeans-demo/e2e/nodes.js` and `windows.js` drive Chromium).

## 1. What exists

| Module / package | Classes | Design § |
|---|---|---|
| `webbeans-ui` — `org.webbeans.actions` | `Action` (displayName, icon, enabled, `actionPerformed(Lookup context)`), `AbstractAction`, `ActionMap` (`copy/cut/paste/delete/rename` keys) — the seed the actions module (step 5) builds on | §1, §8 |
| `webbeans-nodes` — `org.webbeans.nodes` | `Node` (+ `Property<T>`, `PropertySet`, `Handle`, `DropAction`), `AbstractNode`, `FilterNode` (+ `FilterNode.Children`), `Children` (`LEAF`, `Array`, `Keys<T>`, `create(ChildFactory, async)`), `ChildFactory<T>`, `NodeListener`/`NodeAdapter`, `NodeEvent`/`NodeMemberEvent`/`NodeReorderEvent`, `PropertySupport` (`ReadOnly`, `ReadWrite`, `Reflection`, `Name`), `Sheet` (+ `Sheet.Set`), `PropertyEditor<T>` (+ `Kind`, `Choice`), `PropertyEditors` (text, numbers, boolean, enums as choices; `register(type, editor)`), `NodeOp` (`findActions`, `createPath`, `findPath`, `restore`), `Openable`/`Editable`/`Savable`, `Transferable`/`Transferables`/`PasteType`/`Clipboard`, `NodeHandleFactory` | §2–5, 9, 10 |
| `org.webbeans.explorer` | `ExplorerManager` (+ `Provider`, `find(Component)`), `ExplorerUtils` (`createLookup`, `actionCopy/Cut/Paste/Delete`, `activateActions`), `NodeView` (base: node table, payloads, events), `TreeView`, `OutlineView`, `ListView`, `PropertySheet`, `NodeComboBox` | §6, 7 |
| `org/webbeans/nodes/client/` | `treeview.js` (the shared engine `wb.nodeView` + `wb.TreeView`), `outlineview.js`, `listview.js`, `propertysheet.js`, `nodes.css`, `wait.svg` | §7, 8 |
| `webbeans-windows` additions | `TopComponent.Factory` + `@Description(factory = …)`; `WindowManagerImpl` recreates `#n` instances from `tcState` through the factory and keeps them in the saved layout | §10 |
| `webbeans-ui` additions | `Component.resync()` hook (call-delivered state is re-sent on a full `init`); `attached()` hooks now run after the whole subtree's `create` ops | — |
| `webbeans-demo` | `SolarSystem` (per-session model, `Services/Session`), `Planet`, `PlanetNode` (lookup: planet + `Openable`; sheet with gravity/moons/type/explored + rename; shared `OPEN`/`TOGGLE_EXPLORED` actions; handle `planet`), `SolarSystemNode` (async `ChildFactory` with a visible wait node), `PlanetsTopComponent` (`OutlineView` with editable columns, selection → status bar), `PlanetTopComponent` (multi-instance with `Factory`, embeds a `PropertySheet` on its node), `PropertiesTopComponent` (`PropertySheet` over the global context's nodes via `Lookup.Result`) | — |

Tests: `NodesTest` (keys diff keeps nodes and fires add/remove/reorder with indices, `refreshKey`; sync and async factories incl. the wait node; `FilterNode` mirroring; properties, editors, enum choices, `Reflection`, `Name`; `NodeOp.findActions` by identity, preferred action from `Openable`; transferables; `destroy` through `Children.Array`), `ExplorerTest` on a headless UI (initial payload with columns and `can` flags, lazy children, node events → `add/update/remove`, selection ↔ lookup ↔ client with veto revert, rename/editCell/errors/delete, context menu items for a multi-selection and action execution with the context lookup, drop → `PasteType.paste`, `ListView` traversal, `PropertySheet` common properties + write-to-all, resync on a full `init`). The Playwright script covers the same against the real server plus in-place cell editing, F2 rename, keyboard navigation, quick search, client-side sorting, factory-restored windows after reload, and a forced resync.

## 2. Shape as implemented

- Packages `org.webbeans.nodes` / `org.webbeans.explorer` (design: `org.webbeans.api.nodes` / `api.explorer` / `core.explorer`); views are the public classes themselves, there is no separate implementation package.
- Actions: `org.webbeans.actions.Action` has `actionPerformed(Lookup context)` (the context is the selection: the nodes, their lookups, the window's `ActionMap`). Node actions should be **shared instances** — `NodeOp.findActions` intersects multi-selections by identity, as NetBeans does with `SystemAction`.
- `Node.getLookup()` is the lookup given to the constructor (`Lookup.EMPTY` by default); `getPreferredAction()` prefers an `Openable` in it. `Node.Cookie` does not exist.
- Payload format of the views ({@code call("nodes", …)}): `add: [{n, p, i, name, dn, html?, icon?, oicon?, leaf, tip?, can:{rename,destroy,cut,copy}, cols?:{name:{text, edit?, choices?}}}]`, `remove: [n…]`, `update: [{n, …same fields…}]`, `reorder: [{p, order:[n…]}]`, `children: [{p, complete}]`; plus `reset`, `select [n…]`, `expand n`, `collapse n`, `showMenu {x,y,items:[{id,label,enabled,icon?}|null]}`, `startRename n`, `editError {n,prop,message}`, `context {n, dn, hasParent}` (list view). Upstream: `expand/collapse {n}`, `select {nodes}` (lazy 50 ms), `activate {n}`, `contextMenu {nodes,x,y}`, `menuAction {id}`, `rename {n,name}`, `delete {nodes}`, `editCell {n,prop,text}`, `drop {target,index,action,nodes|text}`, `traverse {n | up}`; `PropertySheet`: `sheet {…}`/`editError` down, `edit {prop,text}` up.
- Children are sent when a node is expanded (and the root's immediately); expansion state lives on the client. Unexpanded subtrees cost nothing. Node events reach every view showing the node through one `NodeListener` per view.
- Editing: outline cells with a writable property and the property sheet edit in place with the editor matching `PropertyEditor.Kind` (text, number, boolean checkbox, choice select); the server converts with `fromText`, `setValue` throws → `editError` at the cell/row. Multi-selection in the sheet shows common properties (`mixed` when values differ) and writes to all.
- Context menu: node actions (`NodeOp.findActions`) followed by Copy/Cut/Paste/Delete (from the window's `ActionMap`, else `ExplorerUtils` defaults) and Rename when applicable; the popup is drawn by the client from the item list; `menuAction` runs the action with the selection context. The actions module (step 5) will replace the client-local popup with its menu components.
- Drag & drop: rows with `can.cut || can.copy` are drag sources (`setDragSource(true)`); drop targets (`setDropTarget(true)`) send `drop` with the dragged node ids, `target.getDropType(transferable, action, index)` decides. External text drops arrive as `Transferables.text`.
- Sorting is client-side (numeric-aware `localeCompare` on cell texts); column widths are client-side; quick search is client-side (type-ahead on visible rows). Virtualised rendering is not in v1 (open point).
- `ListView` shows the explored context's children; double-clicking a folder traverses (`setExploredContext`), `..` goes up (`setExploredContextAndSelection(parent, [child])`). `NodeComboBox` lists the explored context's children and selects on change.
- `Clipboard` is per session (`UISession` attribute); copy/cut/paste work across tabs of one user.
- `WindowManager.Registry.getActivatedNodes()` returns the `Node`s of the activated window's lookup (resolved by class name, so the window system stays independent of the nodes module).
- Persistence: `TopComponent.Factory` restores multi-instance windows (`PlanetTC#1`) from `writeState`; `Node.Handle` + `Nodes/Handles/<type>` factories restore nodes (`NodeOp.restore`). The demo's planet windows survive reloads.

## 3. Platform fixes found on the way

- `UI.attach` ran `attached()` while registering each component, so a `call` made from `attached()` preceded the component's own `create` op and was dropped by the client. Hooks now run after the subtree is created (two passes).
- A full `init` (first connect, resync after a lost history) dropped the buffered calls; `Component.resync()` lets call-driven components (views, the property sheet) re-send their state after the creates. `NodeView.rebuild()` and `PropertySheet.push()` implement it.
- The root of a tree is expanded by the server on `rebuild` (`call("expand", root)`), so a fresh view shows its first level.

## 4. Deviations from the design

1. Sorting, column widths and quick search are client-side (design: server-side `sort`, `quickSearch`, `columns` events). Server-side sorting can be added for very large children later.
2. No client-side virtualisation yet: all received rows are DOM rows. Fine for thousands, not for 50 000 (design §13 target).
3. `drop` carries `index: -1` (drop on a row), not an insertion index between rows.
4. Custom editors (`Kind.CUSTOM`) are declared but need the dialog API (step 5); `DATE/DATETIME/COLOR` fall back to text input.
5. External file drops (`/wb/upload`) are not wired; text drops are.
6. `ExplorerUtils.activateActions` is a no-op (actions read the manager live).

## 5. Notes for step 5 (actions, menus, dialogs)

- `org.webbeans.actions.Action/AbstractAction/ActionMap` are the base; `@ActionID`/`@ActionRegistration`/`@ActionReference` and `ContextAwareAction` build on them; the views' `showContextMenu` should switch to the real popup component and `NodeView.standardActions` to the registered system actions.
- `Actions/Window/<id>` entries with `kind = openTopComponent` (step 3) and `Menu/Window` are waiting for the menu bar.
- Delete confirmation currently uses the browser's `confirm()` in `treeview.js`; replace with `DialogDisplayer`.
