# WebBeans — Graph Scenes (detailed design v0.1)

Step 16. JDK 8. Module `webbeans-graph` (`org.webbeans.graph`), the WebBeans counterpart of the NetBeans
**Visual Library** (`org.netbeans.api.visual`): an object-to-widget scene for nodes, pins, edges and notes with
rendering decided per object on the server and drawn as SVG in the browser. The lighter `org.webbeans.ui.components.Diagram`
(whole node/edge lists as properties, one look) stays for quick pictures; `GraphScene` is for editors and
dashboards that keep a graph alive.

## 1. Goals and non-goals

- The Visual Library's model: **objects** (`N`, `E`) mapped to **widgets** by overridable `attachNodeWidget` /
  `attachEdgeWidget`; `findWidget(object)`, `findObject(widget)`, `setEdgeSource/Target`, `findNodeEdges`;
  pins as connection points of a node (`GraphPinScene` folded into `NodeWidget.addPin`).
- Flexible rendering without client code: shapes, borders, icons, accent bands, pins (rows or sockets),
  anchors, routers, end shapes, line styles, labels along edges, sticky notes with connectors, custom SVG
  outlines, arbitrary attributes surfacing as `data-*` and CSS custom properties for application stylesheets.
- The platform's interaction set: select (click, Ctrl, marquee), move (snap to grid, multi), connect by drag
  (`ConnectProvider`), double-click, hover, right-click (`PopupProvider`), in-place edit, Delete, keyboard
  nudges and zoom, pan and wheel zoom, minimap, export to SVG/PNG, palette drops.
- Layouts on the server (`GraphLayout`: grid, circular, layered tree, force-directed) over measured sizes,
  animated in the browser.
- One `graph` call per flush carrying only what changed; a full description on attach and resync.
- Non-goals (v0.1): nested/compound nodes, edge reconnection by dragging an end, orthogonal routing that
  avoids other nodes, printing/paging, collaborative cursors.

## 2. API

```
GraphScene<N, E> extends Component                       // wb.GraphScene
  addNode(N) → NodeWidget, removeNode, addEdge(E) → EdgeWidget, removeEdge
  setEdgeSource/Target(E, N), setEdgeSourcePin/TargetPin(E, N, pinId), getEdgeSource/Target, findNodeEdges(N, out, in)
  getNodes, getEdges, isNode, isEdge, findWidget(obj), findNodeWidget, findEdgeWidget, findObject(widget), clear
  addNote(id, text) → NoteWidget, getNote, getNotes, removeNote
  getSelectedObjects, getSelectedWidgets, setSelectedObjects/Widgets, getSelectionLookup()     // objects + widgets
  setConnectProvider, setPopupProvider (popupFromRegistry(folder)), setSelectionMode, setGrid, setShowGrid, setRouter,
  setAnimation, setMinimap, setDropTarget
  fitToView, zoomTo, centerOn(obj), layout(GraphLayout, animated), exportPng, exportSvg, download
  events: SELECT, MOVE, CONNECT, ACTIVATE, HOVER (lazy), MENU, RENAME, DELETE, DROP
  hooks:  attachNodeWidget(N), attachEdgeWidget(E), newNodeWidget(), newEdgeWidget(), nextId(prefix)

Widget         id, scene, visible, opacity, styleName, tooltip, foreground, background, border, setAttribute(name, v)
NodeWidget     label, secondaryLabel, icon (+ position), shape (RECTANGLE ROUNDED ELLIPSE DIAMOND HEXAGON PARALLELOGRAM
               CYLINDER DOCUMENT ICON CUSTOM) + customSvg, location (centre), preferredSize | measured size, movable,
               selectable, labelEditable, accent, addPin(id) → PinWidget (label, icon, side LEFT RIGHT TOP BOTTOM AUTO)
EdgeWidget     source/target (node or pin), router (DIRECT ORTHOGONAL CURVED FREE + control points), source/target anchor
               (CENTER BOUNDARY CIRCULAR LEFT RIGHT TOP BOTTOM SIDE), source/target shape (NONE ARROW TRIANGLE[_HOLLOW]
               DIAMOND[_HOLLOW] CIRCLE[_HOLLOW] BAR), lineStyle, strokeWidth, cornerRadius, selectable, labels (text, 0..1, above)
NoteWidget     text, location (top-left), width, attachTo(widget), movable, textEditable, fontSize
GraphLayout    GridGraphLayout(columns), CircularGraphLayout, TreeGraphLayout(orientation), ForceGraphLayout(iterations, springLength, seed)
GraphTopComponent(scene)   toolbar: zoom in/out, fit, arrange (setGraphLayout), save SVG/PNG, Graph/Actions; selection in the lookup
```

Widgets are created by the scene (`newNodeWidget()`), never shared between scenes; ids are `n<k>`, `e<k>`,
`<node>/<pin>` and the caller's note ids.

## 3. Wire format

Properties: `grid`, `showGrid`, `selectionMode`, `router`, `connectable`, `animation`, `minimap`, `dropTarget`.

`call("graph", {reset?, nodes: {id: spec|null}, edges: {...}, notes: {...}})` — a spec is the widget's full
description (short keys: `l l2 i ip sh svg x y anim w h mv sel ed ac pins[] cs vis op st tt fg bg bd a{}` for
nodes; `s{n,p} t{n,p} r sa ta ss ts ls sw cr sel cp[] lb[]` for edges; `tx x y w to mv ed fs` for notes).
`reset` clears the browser first (attach, resync). A widget touched several times in one task appears once;
added-then-removed in one task does not appear at all. Pins travel inside their node. `cs` (connect source)
is computed per emit from the `ConnectProvider`.

`call("select", ids)`, `call("view", "fit" | "zoom" f | "zoomBy" f | "center" id)`, `call("export", id, fmt, w, h)`,
`call("download", fmt, name)`.

Events: `select {ids}`, `move {moves: {id: [x, y]}}` (already applied to the widgets when listeners run),
`connect {source, target}` (the provider ran first), `activate`/`menu`/`hover` `{id?, x, y, sx, sy}`,
`rename {id, text}`, `delete {ids}`, `drop {item, sx, sy}`, internal `measure {sizes: {id: [w, h]}}` (lazy, only
nodes without a preferred size, only when the size changed) and `export {id, format, data|error}`.

## 4. Renderer (`graph.js`, `graph.css`)

- Layers in one `<svg>`: grid (a `pattern` of dots), note connectors, edges, nodes, notes, overlay (marquee,
  connect line); the viewport is one `<g transform>`; a hidden group measures text (`getComputedTextLength`,
  cached per class+text).
- A node's size is computed from its content (labels, icon, pins) unless preferred; the result is reported once
  per change. Shapes are paths built for the size; `ICON` nodes are an image with the label below; `CUSTOM`
  injects the given markup with `{w}`/`{h}` substituted. The accent band is the shape clipped to its top
  slice, so it follows hexagons and cylinders. AUTO pins are rows under a separator with a dot at each side;
  sided pins are sockets spread along that side.
- Anchors return a point and a facing direction; routers use the direction: orthogonal paths start with a
  16px stub and meet at the midpoint (Z or U shaped), rounded with quadratic corners; curved edges are cubic
  Béziers whose handles follow the anchor directions; free edges pass through the control points.
- End shapes are `<marker>`s created per shape+colour on demand (`auto-start-reverse` for the source end);
  hollow shapes fill with the panel colour; selection recolours markers through CSS.
- Labels sit at `getPointAtLength(len·at)` offset along the normal, with a small background.
- Notes wrap their text to the width by measuring; the fold is a separate path; the connector runs between the
  note's and the target's boundaries and follows drags of either.
- Interaction is pointer-based without external libraries; drags re-render each frame (cheap: only transforms
  and edge paths change); the move is sent once at the end. Keyboard: Delete/Backspace, Ctrl+A, Escape, F2,
  arrows (Shift = 10px), +/−, Ctrl+0.
- Layout moves with `anim` tween over 320ms with ease-out; edges follow every frame.
- Export clones the SVG, inlines computed styles, drops the measuring and overlay groups and sets a
  `viewBox` to the content bounds; PNG rasterises at 2× through a canvas (as `chart.js`).

## 5. Platform integration

- `GraphTopComponent` puts `scene.getSelectionLookup()` (selected objects and widgets) and the scene into the
  window's lookup and declares itself an activated-nodes owner, so context actions over your node/edge types
  enable on the selection and the global context sees it. Toolbar actions come from `Graph/Actions`
  (context: the scene).
- `PopupProvider` menus are built with `Actions.actionsToPopup` over a lookup of scene + widget + object, so
  `@ActionRegistration` context actions taking your object type appear enabled on the right-clicked widget;
  `GraphScene.popupFromRegistry("MyType/Actions")` is the no-code provider.
- Roles, locale and theme: labels are plain strings the application localises (`@Messages`); theme tokens
  drive every colour (`--wb-*`, plus `--wb-note*` for notes); dark mode supported.
- `Palette` drops land as `DROP` with scene coordinates when `setDropTarget(true)`.

## 6. Tests

`GraphSceneTest` (headless UI): initial description, per-flush merging and add+remove cancellation, object
model (sources/targets/pins/edges of a node, removal cascade, note detachment), selection/move/measure/rename/
delete events and the selection lookup, connect provider (flags and refusal), four layouts (ordering,
animation flag, no overlaps), resync and export. Browser: `e2e/graph.js` on the demo's Deployment window —
rendering (shapes, pins, line styles, markers, notes, minimap), select, activate, drag, connect by handle,
F2 rename, popup action, marquee + Delete, arrange + fit + wheel zoom, SVG export.

## 7. Open points

- Reconnecting an edge end by dragging it; edge control-point editing for `FREE` routers.
- Node-avoiding orthogonal routing (a simple grid router) and edge bundling for dense graphs.
- Compound nodes (a node containing a scene) and collapsible groups.
- Keyboard focus traversal between widgets (accessibility).
