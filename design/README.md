# WebBeans — Document Map

## Design (normative)

| Document | Scope | Status |
|---|---|---|
| `design/webbeans-architecture.md` | Overall architecture, decisions, roadmap, change log | v0.10 |
| `design/module-system-and-lookup.md` | Step 1 (revised in steps 14 and 15): the class path as the application, Registry, NetBeans Lookup integration, i18n (`Bundles`, `@Messages`, `Bundle-generated.properties`), annotation processor, `Installer` hooks | v0.5 |
| `design/sessions-and-delta-protocol.md` | Steps 2, 10: Jetty HTTP surface, auth, sessions/UIs, threading, component model, wire protocol over WebSocket and long polling, `wb-core.js` | v0.3 |
| `design/standard-components.md` | Steps 2, 11, 12: the standard component library as built — inputs, Form, layouts, SplitPane/ScrollPane, Table, ListBox, Tree, Upload, downloads; tier-2/3: small widgets, Card/Accordion/Popover, TreeTable, PropertyGrid, code/Markdown/diff/output views, Calendar, Kanban, Diagram/Palette, viewers, Quick Search | v0.3 |
| `design/window-system.md` | Step 3 (revised in step 15): TopComponent, Mode, WindowManager, activated nodes, layout model, dock renderer | v0.3 |
| `design/nodes-and-explorer.md` | Step 4: Node, Children, properties/editors, ExplorerManager, views, DnD, persistence | v0.3 |
| `design/actions-menus-dialogs.md` | Steps 5, 8, 9: actions, menus, toolbars, shortcuts, popups, dialogs, notifications, progress, options, help (as built) | v0.3 |
| `design/charts.md` | Step 6: Chart component, model, wire format, SVG renderer, export | v0.2 |
| `design/graph.md` | Step 16: GraphScene (Visual Library model), widgets, anchors/routers, notes, layouts, renderer, wire format | v0.1 |
| `design/scene3d.md` | Step 7: Scene3D, scene graph, blobs, glTF, interaction, binders, explorer bridge (as built) | v0.3 |
| `design/sample-app.md` | Step 8: the "Workbench" sample application — five modules, options, roles/layouts, application jar, headless test API (as built) | v0.2 |

## Implementation notes (per step, as built)

| Document | Scope |
|---|---|
| `impl/step1-core-notes.md` | Core runtime: module system, registry, processor |
| `impl/step2-notes.md` | UI runtime, delta protocol, authentication, the first (JDK `HttpServer`, long-poll only) front end |
| `impl/step3-notes.md` | Window system |
| `impl/step4-notes.md` | Nodes API and explorer views |
| `impl/step5-notes.md` | Actions, menus, toolbars, shortcuts, popups, dialogs, notifications, progress |
| `impl/step6-notes.md` | Charts: model, batched deltas, SVG renderer, export, dashboard demo |
| `impl/step7-notes.md` | Scene3D: scene graph ops, blob store, three.js renderer, drags/picking, glTF, explorer bridge, solar-system demo |
| `impl/step8-notes.md` | Workbench sample application: Options dialog, role layouts, `localeChanged`, `SessionDirectory`, `webbeans-test`/`HeadlessUI`, embedded-module application jar, branding, the tutorial module |
| `impl/step9-notes.md` | Help system: `HelpCtx`, `@HelpPage`, Markdown pages, the Help window, F1, dialog Help buttons, `/wb/help/<id>` via `PageProvider`, tree expansion memory |
| `impl/step10-notes.md` | Jetty 9.4 front end and the WebSocket transport: servlets, `WebSocketHandler`/`WebSocketTransport`, client transport facade with long-poll fallback, keep-alive, tests, building Jetty offline |
| `impl/step11-notes.md` | Tier-1 components: NumberField, Slider, RadioGroup, DatePicker/DateTimePicker, Form, SplitPane, ScrollPane, ListBox, Tree, the virtualised Table with its models, Upload + `/wb/upload`, DownloadService + `/wb/download` |
| `impl/step12-notes.md` | Tier-2/3 components: toggle/segmented/split buttons, colour/time pickers, password strength, tags, badges/chips/avatars, empty state, breadcrumb, stepper, Card, Accordion, Popover, busy panels; TreeTable, PropertyGrid; CodeEditor, MarkdownEditor, DiffView + LineDiff, OutputView; Calendar, Kanban, Diagram + Palette, ImageViewer, PdfViewer; `EnumLabels`/`Labeled` and enum-aware property editors; Quick Search |

## Developer documentation (user-facing)

| Document | Scope |
|---|---|
| `docs/user-guide.md` | **Start here.** A step-by-step tutorial: first window and components, numbers/dates/choices and forms, pretty enums, layouts, files in and out, dialogs, windows and lookup, tables and tree tables of plain objects, property grids, text/code/boards/viewers, nodes and explorer views, actions/menus/toolbars/shortcuts, options and preferences, charts, 3D, services/sessions/push, i18n, headless tests, help pages, packaging — every example taken from the tutorial module, the Workbench or the demo |
| `docs/developer-guide.md` | Concepts and workflows: getting started, threading, modules, sessions, i18n, windows, nodes, actions, dialogs/options/help, charts, 3D scenes, custom components, testing, packaging and deployment (transport, proxies), porting from NetBeans |
| `docs/component-guide.md` | Per-component reference (as implemented): conventions, inputs and Form, small widgets, layouts, cards/accordions/popovers, Table/TreeTable/PropertyGrid/ListBox/Tree and downloads, text and code editors, boards and viewers, explorer views and enum rendering, platform widgets and Quick Search, dialogs, charts, Scene3D, options, help, platform services |

## Conventions

- All Java is JDK 8 compatible.
- NetBeans libraries reused unchanged: `org-openide-util-lookup`, `org-openide-util` (RELEASE126). HTTP front end: Jetty 9.4 (the last Java 8 line) with its WebSocket modules and the Servlet 3.1 API — used by `webbeans-server` only.
- Registration is annotation-only; there is no `layer.xml`.
- Each design doc carries a version and a short change note at the top when revised; the architecture doc's change log is the master history.
