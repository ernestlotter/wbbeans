# WebBeans — Step 16 Implementation Notes (v0.1): graph scenes (`webbeans-graph`)

A component in the spirit of the NetBeans **Visual Library**, asked for as "rendering connected edges and notes
with flexible rendering options". Design: `design/graph.md`. Source: `webbeans-graph`; demo window
`DeploymentTopComponent` (*Window ▸ Deployment graph*); browser suite `e2e/graph.js`.

## 1. What was built

| Piece | Content |
|---|---|
| `GraphScene<N, E>` | object↔widget mapping (`addNode/addEdge` with `attachNodeWidget/attachEdgeWidget` hooks, `findWidget/findObject`, `setEdgeSource/Target[Pin]`, `findNodeEdges`), notes, selection (+ `getSelectionLookup`), `ConnectProvider`, `PopupProvider` (+ `popupFromRegistry`), view calls, layouts, export; one `graph` call per flush with merged descriptions, add+remove in one task cancels, full description on attach and resync |
| Widgets | `Widget` (visibility, opacity, style class, tooltip, colours, border, free attributes → `data-*` + `--wb-g-*`), `NodeWidget` (labels, icon, 10 shapes incl. custom SVG, accent band, location/size, movable/selectable/editable, pins), `PinWidget` (label, icon, side), `EdgeWidget` (node or pin ends, routers, anchors, end shapes, line style, width, corner radius, control points, labels along the path), `NoteWidget` (wrapped text, width, attachment, editable) |
| Layouts | `GridGraphLayout`, `CircularGraphLayout`, `TreeGraphLayout` (layered, cycle-safe, top-down or left-right, barycentre ordering), `ForceGraphLayout` (Fruchterman–Reingold, seeded) — all over measured sizes, optionally animated |
| Renderer | `graph.js` (~600 lines, no dependencies) + `graph.css`: text measuring, shape paths, pins, anchors with facing direction, orthogonal/curved/free routing, markers per shape+colour, edge labels, notes with fold and connector, pan/zoom, drag with snap, marquee, connect-by-drag with target highlight, in-place editor, keyboard, minimap, animation, SVG/PNG export |
| `GraphTopComponent` | toolbar (zoom in/out, fit, arrange with a configurable layout, save SVG/PNG, `Graph/Actions`), scene + selection in the lookup, activated-nodes owner |
| Build | `build.sh`, root/launcher/demo poms, dist and fat-jar lists |

## 2. Decisions

- **Server owns the model, browser owns the pixels.** Sizes are the one thing the browser knows better (fonts),
  so nodes without a preferred size report their measured size once per change; layouts use it. This keeps
  layouts deterministic and testable on the server while labels still fit.
- **Descriptions, not ops.** Unlike `Scene3D` (a stream of ops), a widget is re-described whole when it changes:
  descriptions are small, merging is trivial (last write wins per widget), and a renderer rebuilds a node only
  when its content signature changed (position changes are a transform update).
- **Anchors carry a direction.** `anchorPoint` returns the attachment point *and* the side it faces; the
  orthogonal and curved routers use it, which is what makes pin edges leave sideways and node edges leave
  through the facing side without any per-edge configuration.
- **Pins are part of the node.** They travel inside the node description and have ids `<node>/<pin>`, so a pin
  change marks its node dirty and edges reference `{n, p}`.
- **Popups through the actions module.** `PopupProvider` returns `Action`s; the scene builds the popup with
  `Actions.actionsToPopup` over a lookup of scene + widget + object, so registered context actions over the
  application's object types work unchanged.
- **Kept `Diagram`.** The lightweight `org.webbeans.ui.components.Diagram` stays for quick pictures; the guide
  says when to use which.

## 3. Verified

- `GraphSceneTest` (7 tests): wire format, merging/cancellation, object model and removal cascade, events and
  lookup, connect provider, four layouts, resync/export.
- Browser (`e2e/graph.js`, demo): 6 nodes of five looks, 6 edges of three routers/styles with markers, two
  wrapped notes and a connector, minimap; select, activate on an edge, drag (edges re-route), connect by
  handle (provider adds the edge), F2 rename, popup action (accent toggles), marquee + Delete (edges and
  note attachment cascade), arrange animated + fit + wheel zoom, SVG export; no page errors.

## 4. Open

See `design/graph.md` §7: edge reconnection, control-point editing, node-avoiding routing, compound nodes,
keyboard traversal.
