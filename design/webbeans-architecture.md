# WebBeans — Architecture Design v0.9

A rich web application platform modelled on the NetBeans Platform. All application code — UI definition, business logic — is server-side Java (JDK 8) on the class path. The browser runs a thin, generated JavaScript runtime that mirrors the server-side component tree.

Document map: `design/README.md` lists every design document and the developer docs.

## 1. Decisions taken

| Topic | Decision |
|---|---|
| Client code | Hand-written JS renderers, one per component type, bundled by the server at startup |
| Hosting | Embedded Jetty 9.4 (`java -jar webbeans-app.jar`); WAR packaging possible later |
| Language level | Java 8 (no JPMS, no `var`, no records); annotation processing for registration |
| Transport | WebSocket (Jetty's native WebSocket API, one socket per tab) with automatic long-poll fallback; JSON control channel; blobs over HTTP (`/wb/blob/<sha256>`), a binary side-channel later |
| v1 UI scope | Window system, Explorer/Nodes API (tree view, outline view), Actions, menus/toolbars, dialogs, a full standard component library (forms, layouts, tables), simple charts, Scene3D |
| Persistence | Plain per-user files under `<datadir>/users/<username>/` (layouts, preferences); no database dependency |
| i18n | NetBeans bundle conventions (`Bundle.properties`, `Bundle_<locale>`, `Bundle_<brand>`) with WebBeans' own lookup (`Bundles`): locale per session, `@Messages` defaults in a generated `Bundle-generated.properties` merged at run time |
| Browser tabs | Multiple `UI` instances per `UISession`, each with its own window layout |
| Lookup | NetBeans `org-openide-util-lookup` (RELEASE126) used as-is, not re-implemented |
| Registration | Annotations only; no `layer.xml`. The processor generates a Java `RegistrationProvider` per jar feeding a path-keyed `Registry` |

## 2. Layer overview

```
┌──────────────────────────────────────────────────────────┐
│ Application jars (user code)                             │
├──────────────────────────────────────────────────────────┤
│ Platform APIs: Window System · Explorer/Nodes · Actions  │
│                Options · Dialogs · Charts · Scene3D      │
├──────────────────────────────────────────────────────────┤
│ UI Core: Component tree · Properties · Events · Deltas   │
│          Standard component library                      │
├──────────────────────────────────────────────────────────┤
│ Runtime: Class path · Registry · Sessions · Auth         │
│          NetBeans util-lookup + util (Lookup, NbBundle)  │
├──────────────────────────────────────────────────────────┤
│ Transport: WebSocket · long-poll fallback · Bundler ·    │
│            Static resources · module pages               │
├──────────────────────────────────────────────────────────┤
│ Embedded Jetty 9.4                                        │
└──────────────────────────────────────────────────────────┘
         ▲ JSON deltas / events ▼        ▲ blobs (HTTP GET)
┌──────────────────────────────────────────────────────────┐
│ Browser: wb-core.js (tree mirror, events, reconnect)     │
│          + per-component renderers (generated bundle)    │
└──────────────────────────────────────────────────────────┘
```

Packages: `org.webbeans.core.*` (runtime), `org.webbeans.ui.*` (component model and standard components), `org.webbeans.api.*` (platform APIs), `org.webbeans.modules.*` (shipped modules). NetBeans packages reused: `org.openide.util.*`, `org.openide.util.lookup.*`.

## 3. Class path and registry

The application is the JVM class path (as of step 14; the module system of steps 1–13 — manifests, dependency resolution, per-module class loaders, enable/disable, run-time installation — was removed). An application jar is an ordinary jar; what it contributes is in its `META-INF/services`, written at compile time by the annotation processors:

- **Discovery**: `Platform.start` reads every `RegistrationProvider` the class loader can see into the registry (class-path order; later jars win conflicts, `@Hide` masks deliberately) and every `@ServiceProvider` into `Lookup.getDefault()`.
- **Lifecycle**: `Installer { restored(); closing(); close(); }`, registered with `@ServiceProvider`, called in Lookup order at start-up and in reverse at shutdown.
- **No isolation, no versions**: jars see each other; dependencies and versions are Maven's concern; a change of application code is a restart.
- **Registry**: a shared, immutable, path-keyed registry (`Actions/File`, `Menu/File`, `Toolbars/Main`, `Windows/Modes`, `Windows/Components`, `Services/Session`) with `position` ordering, cross-module masking and a per-user hide/reorder overlay — the useful part of NetBeans layers without XML or a virtual filesystem.
- **Annotations → registry**: the `webbeans-processor` (JDK 8 `javax.annotation.processing`) turns `@ActionID`, `@ActionRegistration`, `@ActionReference`, `@TopComponent.Registration`, `@Mode.Registration`, `@NodeFactory.Registration`, `@Renderer`, `@Registration` into a generated `WbRegistrations` class per jar (type-safe factories, no reflection). `@ServiceProvider` and `@Messages` are NetBeans' own.

Detailed design: `design/module-system-and-lookup.md`.

## 4. Lookup

`Lookup` is the universal service and selection mechanism — the NetBeans `org.openide.util.Lookup` API, unchanged: `Lookup.getDefault()`, `Lookups.*`, `AbstractLookup`/`InstanceContent`, `ProxyLookup`, `Lookup.Result` listeners.

- `Lookup.getDefault()` (installed by `webbeans-core` through NetBeans' pluggable default) = every jar's `@ServiceProvider`s + platform singletons + the registry's `Services` folder; immutable after startup.
- `UISession.getLookup()` = per-user contents (`Principal`, `Locale`, `UserFiles`, session services) over the default lookup.
- `ProxyLookup` composes session, UI, window and node lookups into the "global selection context" (`Utilities.actionsGlobalContext()` analogue): `session → active UI → active TopComponent → selected Nodes`.

## 5. Sessions, users, tabs, threading

- Login (form, pluggable `AuthProvider` found in global Lookup: properties file shipped, LDAP/OAuth2 later) creates a `UISession` bound to an HTTP session and a `Principal` with roles.
- Each `UISession` owns: its `Lookup`, the user's `Locale`, a serial executor, and a set of `UI` instances — one per browser tab. All component access happens on the session executor: client events are dispatched on it; background threads use `session.access(Runnable)` or `ui.access(Runnable)` (server push). Swing-EDT-like simplicity without a global lock and without a thread per session.
- Each `UI` owns its own component tree, `WindowManager` and layout; session-scoped services (data, selection broadcast, event bus) are shared through the session Lookup. A `UI` is identified by a `uiId` minted with the page and carried in the WebSocket upgrade (or in every poll); a closed tab says `bye`, a lost one is detected by the socket closing or the polls stopping, and its `UI` is disposed after a grace period.
- Role checks: `roles` on `TopComponent`/`Action` registrations; filtered when the registry is viewed for a session, so unauthorised items never reach the client.
- Session timeout, heartbeat, and reconnection with replay of missed deltas (bounded buffer per `UI`; full resync if exceeded).

Detailed design: `design/sessions-and-delta-protocol.md`.

## 6. Persistence (per-user files)

```
<datadir>/
  users/<username>/
    layout.json            # last window layout (last active UI wins; see §11)
    layouts/<name>.json    # named layouts (optional, "Window > Save layout as…")
    config/registry.json   # user's menu/toolbar customisations (hide / reorder)
    preferences/<node>.properties   # java.util.prefs-style tree, one file per node
    <package-id>/…         # per-user files of one piece of code via UserFiles.dir(id)
  system/
    users.properties       # PropertiesAuthProvider accounts
    preferences/…          # system-level preferences (admin-managed)
```

- `UserFiles` API gives application code a per-user directory; writes are atomic (write temp + rename) and debounced.
- `Preferences` implementation (`WbPreferences`) backs `java.util.prefs.Preferences` on these files; `PreferencesFactory` set via system property so standard `Preferences.userNodeForPackage(...)` works unchanged.
- No database dependency in core. A later `PersistenceProvider` SPI can redirect the same paths to a store.

## 7. i18n (NetBeans style, NetBeans library)

- `org.webbeans.core.i18n.Bundles` provides the lookup (NetBeans' file conventions and suffix chain, `Bundle_<branding>_<locale>.properties` branding, session locale); `@Messages` → generated `Bundle.java` + `Bundle-generated.properties`, merged at run time with the hand-written `Bundle*.properties` so the processor never overwrites a developer's file.
- Locale per session: `UISession.getLocale()` (user preference → `Accept-Language` → server default) is exposed to `NbBundle` through a thread-local set by the session executor for the duration of each task.
- Registry attributes written as `#KEY` resolve against the registering jar's bundle with the session locale, so display names of actions, modes and components are localised per session.
- Localisation happens entirely on the server: renderers receive already-localised strings, so client JS never needs resource bundles. Number/date formats are compiled server-side into locale-specific specs for the few places the client formats (chart tooltips, number/date fields).
- Language switch at runtime: the session invalidates its registry views and re-sends display-name deltas; application-computed text is refreshed via a `LocaleChangeEvent` on the session event bus.

## 8. UI component model and standard library

```java
public abstract class Component {
    public <T> T get(Property<T> p);
    public <T> void set(Property<T> p, T value);      // records delta
    public <E extends Event> Registration addListener(EventType<E> type, Listener<E> l);
    public void call(String method, Object... args);
    protected abstract String rendererId();           // e.g. "wb.Button"
}
public abstract class Container extends Component { children(); add(); add(c, constraint); remove(); }
```

- Every component has a UI-unique `id`; properties are declared statically (`Property.of("text", String.class, "")`) so renderers and protocol stay type-safe. Properties may be `clientOwned()` (text field value, scroll position) and events `lazy()` (debounced on the client).
- Mutations append to the UI's `DeltaBuffer`; a flush after each executor task sends one batched message.
- **Standard library** (`webbeans-ui`): basic — `Label`, `Button`, `ToggleButton`, `SplitButton`, `TextField`, `TextArea`, `PasswordField`, `NumberField`, `CheckBox`, `RadioGroup`, `ButtonGroup`, `ComboBox` (lazy items), `DatePicker`/`DateTimePicker`/`TimePicker`, `ColorPicker`, `TagInput`, `Slider`, `Link`, `Image`, `ProgressBar`, `Html`, `Upload`, `Spacer`, `Badge`, `Chip`, `Avatar`, `EmptyState`, `Breadcrumb`, `Stepper`; layout — `Panel` (with a busy overlay) with `BorderLayout`, `FlowLayout`, `GridLayout`, `FormLayout`, `StackLayout`, `FillLayout`, `SplitPane`, `TabbedPane`, `ScrollPane`, `Card`, `Accordion`, `Popover`, `Toolbar`; data — `Table` (virtualised, keyed row deltas, `ListTableModel`/`LazyTableModel`, inline editing), `TreeTable`, `PropertyGrid`, `ListBox`, `Tree`; text — `CodeEditor`, `MarkdownEditor`, `DiffView`, `OutputView`; boards and viewers — `Calendar`, `Kanban`, `Diagram` + `Palette`, `ImageViewer`, `PdfViewer`; plus `DownloadService` for file downloads, a `Form` helper for validation and `EnumLabels`/`Labeled` for how enum values are shown everywhere. Platform modules add `TreeView`, `OutlineView`, `ListView`, `PropertySheet`, `NodeComboBox`, menus/toolbars/popups, Quick Search, `Dialog`, `Chart`, `Scene3D`.
- Client-side-only interaction (hover, drag-preview, typing) does not round-trip; only semantic events (`click`, `change`, `select`, `expand`, `drop`) are sent, and only for types the server has registered a listener for.

Detailed design: `design/standard-components.md`; usage: `docs/component-guide.md`.

## 9. Protocol

Downstream (server → client), one message per flush:

```json
{ "t": "delta", "seq": 412, "ops": [
  { "op": "create", "id": 18, "type": "wb.Button", "parent": 7, "index": 2, "c": "south", "props": { "text": "Save" } },
  { "op": "set",    "id": 18, "props": { "enabled": false } },
  { "op": "listen", "id": 18, "events": [ { "type": "click" } ] },
  { "op": "remove", "id": 11 },
  { "op": "call",   "id": 30, "m": "scrollTo", "args": [120] }
] }
```

Upstream (client → server):

```json
{ "t": "ev", "ack": 412, "events": [ { "s": 57, "id": 18, "type": "click", "data": { "modifiers": 0 } } ] }
```

- `seq`/`ack` and client `s` give ordering, replay and deduplication on reconnect; `init` carries a full tree when replay is impossible.
- Bulk data (table rows, node trees, chart series, scene graphs) travels as batched `call`s on one component, never as thousands of components.
- Blobs (geometry, textures): content-addressed by SHA-256, referenced from JSON props and fetched with `GET /wb/blob/<sha256>` (immutable cache headers); binary WebSocket frames with an IndexedDB cache remain an option. Uploads and downloads use dedicated HTTP endpoints, not the control channel.
- Transport: a WebSocket per tab (`/wb/ws`, authenticated by the session cookie plus the session token; `hello`/`ev`/`ping`/`bye` frames, `ack`/`pong`/`gone` back) with long polling (`/wb/poll`) as the fallback the client picks by itself when the socket cannot be opened. Reconnects replay from a bounded history or re-init.

## 10. Client runtime and bundler

- `wb-core.js` (~20 KB): WebSocket client with long-poll fallback (keep-alive, backoff reconnect, replay), delta applier, renderer registry, event sender, keyboard shortcut router, theme.
- Each component class declares `@Renderer("wb/button.js")`; the file lives in the module jar. A renderer is an ES module exporting `{ create(ctx), update(el, props, changed), insertChild, removeChild, call(el, name, args), destroy(el) }`.
- At startup the `Bundler` walks the registry's `Renderers/` entries, concatenates `wb-core.js` + all renderers + CSS, minifies (Closure Compiler, Java), and serves `/wb/app-<hash>.js` with long cache headers. Dev mode serves unbundled modules for debugging.
- Theming via CSS custom properties (`--wb-*`); dark/light and a density setting shipped; applications register themes under `Themes/<name>`.

## 11. Window system

Mirrors NetBeans:

- `TopComponent extends Container`: `open()`, `close()`, `requestActive()`, `getLookup()`, `getPersistenceType()`, `componentOpened/Closed/Activated` callbacks, `Factory` for multi-instance persistence.
- `Mode`: named docking slot (`explorer`, `navigator`, `editor`, `properties`, `output`); modes registered with `@Mode.Registration`, each with constraints (side, default size, closeable, floatable).
- `WindowManager` (one per `UI`): registry of modes/components, active component tracking, roles (`WindowManager.setRole("admin")` switches layouts).
- Layout persistence: on layout change the owning `UI` debounces a write of `users/<user>/layout.json`; a new tab opens with the last saved layout, so tabs start identical and then diverge independently. `TopComponent.PERSISTENCE_ALWAYS` components are reopened; `PERSISTENCE_NEVER` are not.
- Client: a dock renderer with drag-to-dock, tab groups, split resizing, maximize, floating windows (browser-side only; state reported back to server as layout events).

Detailed design: `design/window-system.md`.

## 12. Nodes & Explorer API

The data-view abstraction, independent of any widget (own API, mirroring NetBeans' but without Swing types):

```java
public abstract class Node {
    public abstract String getName();  getDisplayName(); getIcon(); getLookup();
    public abstract Children getChildren();      // Children.LEAF, Children.Keys<T>, ChildFactory
    public abstract Action[] getActions(boolean context);
    public abstract PropertySet[] getPropertySets();   // for OutlineView / property sheet
    public abstract boolean canRename(); canDestroy(); canCut(); canCopy();
    public abstract PasteType[] getPasteTypes(Transferable t);
    // NodeListener: childrenAdded/Removed/Reordered, propertyChange, nodeDestroyed
}
```

- `Children.Keys<T>`: `createNodes(T key)`, `setKeys(Collection<T>)` — lazy, keyed, diff-friendly. Children are materialised only when expanded; the client requests expansion and the server sends the children delta.
- `ExplorerManager`: holds `rootContext`, `exploredContext`, `selectedNodes`; fires property changes; drives the lookup of its owning `TopComponent` via `ExplorerUtils.createLookup(manager, actionMap)`.
- Views (components bound to an `ExplorerManager`): `TreeView`, `OutlineView` (tree + property columns from `PropertySet`s, sortable, editable cells), `ListView`, `PropertySheet`, `NodeComboBox`. Each is a thin component whose renderer is driven by node deltas (`nodeAdded/Removed/Changed`, keyed by node id).
- Context menus: `getActions()` → materialised to a popup on right-click; drag & drop via `PasteType`/`Transferable` with the actual transfer executed server-side.

Detailed design: `design/nodes-and-explorer.md`.

## 13. Actions, menus, toolbars

- `Action` interface (`actionPerformed(ActionEvent)`, properties: name, icon, accelerator, enabled). `ContextAwareAction` creates instances bound to a Lookup.
- Registration via annotations → registry `Actions/<category>/`, `Menu/<path>/`, `Toolbars/<name>/`, `Shortcuts/`. `position` orders items; separators via `separatorBefore/After`.
- `Presenter.Menu` / `Presenter.Toolbar` for custom presenters.
- Enablement: context actions observe `Lookup.Result` on the global selection; enabled state changes are deltas on the menu item components.
- Keyboard shortcuts registered globally; the client router matches and sends `action` events.
- Main menu bar and toolbars are generated from the registry view for the session: filtered by role, localised, with the user's hide/reorder overlay applied.

Detailed design: `design/actions-menus-dialogs.md`.

## 14. Dialogs, notifications, options

- `DialogDisplayer.notify(NotifyDescriptor)` / `DialogDescriptor` returning `CompletableFuture<Object>` (non-blocking; the server thread must not block waiting on the browser). Minimal `WizardDescriptor`.
- `NotificationDisplayer` for toasts (fan out to all tabs); `StatusDisplayer` for the status bar; `ProgressHandle` for progress indicators.
- Options: `OptionsPanelController` registration → settings dialog; `Preferences` backed by per-user files (§6).

## 15. Charts

`Chart` component with a server-side data model and a client renderer on a small embedded library (own SVG renderer, no external dependency).

- Types v1: line, bar (grouped/stacked), pie/donut, scatter, area, step; mixed types on shared axes.
- `ChartModel`: series with keyed points; updates are deltas (`append`, `update`, `remove`, sliding windows) so live dashboards are cheap; very large series travel as blobs.
- Events: `pointClick`, `legendToggle`, `rangeSelect`. Export to PNG/SVG via futures and `DownloadService`.

Detailed design: `design/charts.md`.

## 16. Scene3D

Server-side scene graph mirrored by a three.js-based renderer.

```java
Scene3D scene = new Scene3D();
Group root = scene.getRoot();
Mesh box = new Mesh(Geometry.box(1,1,1), Material.standard(Color.RED));
box.setPosition(0, 0.5, 0);
root.add(box);
scene.setCamera(new PerspectiveCamera().lookAt(0,0,0).setPosition(3,3,3));
scene.addPickListener(e -> select(e.getNode()));
```

- Node types: `Group`, `Mesh`, `InstancedMesh`, `Light` (ambient, directional, point, spot, hemisphere), `Camera` (perspective/ortho), `Sprite`, `Line`, `Points`, `Label3D`.
- Geometry/textures: built-ins (box, sphere, plane, cylinder, extrude, lathe, text) and `BufferGeometry` with typed arrays sent over the binary channel; glTF decoded server-side; content-addressed and cached.
- Animations declared once (`Animation.rotate(box, Axis.Y, 360°, 4 s).loop(true)`) and run entirely client-side; drags constrained (`DragConstraint`) and accepted server-side; `SceneBinder` keeps a scene in sync with keyed data; `Scene3DChildren` exposes the scene graph as nodes.
- Events: pick, hover, drag, camera change (throttled). Orbit/first-person/map controls are client-side; the server can set or lock them.
- `Scene3D` is a normal component, so it can be a `TopComponent` content or embedded in a form.

Detailed design: `design/scene3d.md`.

## 17. Build and packaging

- Maven multi-module: `webbeans-core`, `webbeans-ui`, `webbeans-processor`, `webbeans-api-*`, `webbeans-modules-*`, `webbeans-launcher`, `webbeans-test`, `webbeans-maven-plugin`, `webbeans-app-archetype`.
- `webbeans-launcher` runs the platform on embedded Jetty 9.4 over the class path it was started with, and exposes `--port`, `--host`, `--context`, `--title`, `--dev`, `--datadir`, `--branding`, `--locale`, `--session-timeout`, `--secure-cookies`, `--trusted-proxy`, `--no-websocket`, `--http-threads`; the application jar is built with `maven-shade-plugin` (or `tools/fatjar.py` offline).
- Application projects use `webbeans-maven-plugin` (`wb:run`, `wb:package`) — the NetBeans "suite" equivalent.
- Dependencies kept minimal (as built): Jetty 9.4 with its WebSocket modules and the Servlet 3.1 API (`webbeans-server` only), NetBeans `org-openide-util-lookup` + `org-openide-util` (RELEASE126), vendored three.js (Scene3D module only). JSON is WebBeans' own small codec; logging is `java.util.logging` (Jetty routed into it); the bundle is concatenated, not minified.

## 18. Roadmap

1. **Core**: registry, NetBeans Lookup/i18n integration, annotation processor, per-user files (originally with a module system, removed in step 14 — the class path is the application). — *implemented* (`impl/step1-core-notes.md`, `impl/step14-notes.md`)
2. **Transport + component model**: sessions/UIs, delta protocol, `wb-core.js`, bundler, standard component library, login. — *implemented* (`impl/step2-notes.md`; first on the JDK `HttpServer` with long polling)
3. **Window system**: TopComponent, Modes, docking renderer, layout persistence. — *implemented* (`impl/step3-notes.md`)
4. **Explorer**: Nodes, Children, ExplorerManager, TreeView, OutlineView, PropertySheet, context menus, DnD. — *implemented* (`impl/step4-notes.md`)
5. **Actions/menus/toolbars/shortcuts**, dialogs, notifications, progress, options. — *implemented* (`impl/step5-notes.md`)
6. **Charts**. — *implemented* (`impl/step6-notes.md`)
7. **Scene3D**. — *implemented* (`impl/step7-notes.md`)
8. **Sample application ("Workbench"), tutorial and developer docs**. — *implemented* (`impl/step8-notes.md`, `docs/`)
9. **Help system**: `HelpCtx`, `@HelpPage`, Markdown pages, the Help window, F1, dialog Help buttons, standalone help pages. — *implemented* (`impl/step9-notes.md`)
10. **Jetty 9.4 + WebSocket transport** with long-poll fallback, replacing the JDK HTTP server. — *implemented* (`impl/step10-notes.md`)
11. **Standard component library completed**: NumberField, Slider, RadioGroup, date pickers, Form; SplitPane, ScrollPane; ListBox, Tree; the virtualised Table with in-memory and lazy models; Upload and DownloadService. — *implemented* (`impl/step11-notes.md`)
12. **Tier-2/3 components**: small widgets, Card/Accordion/Popover, TreeTable, PropertyGrid, code/Markdown/diff/output views, Calendar, Kanban, Diagram + Palette, image and PDF viewers; pretty enums everywhere; Quick Search. — *implemented* (`impl/step12-notes.md`)
13. Next: `webbeans-maven-plugin` and archetype; `--https`/HTTP/2 inside Jetty; uploads and binary blob frames over the socket; LDAP/OAuth2 providers shipped.

## 19. Change log

- v0.9 — step 15: i18n lookup in `Bundles` (NetBeans file conventions, no `NbBundle` at run time); `@Messages` defaults in `Bundle-generated.properties` merged with hand-written `Bundle*.properties` at run time, so the processor never overwrites a developer's file whatever the build's resource-copy order.
- v0.8 — step 14: the module system is removed; the application is the class path (§3 rewritten: discovery through `META-INF/services`, `Installer` hooks, no isolation or versions); step 13's HTTPS recorded, its run-time module loading withdrawn with the module system.
- v0.7 — step 12: the tier-2/3 components of `design/standard-components.md` v0.3 (§8 list extended), `EnumLabels`/`Labeled` as the one place enum display is decided, Quick Search (`QuickSearch/Providers`) in the actions module, `Markdown` moved to `org.webbeans.ui.text`; roadmap updated (no map component: needs a vendored tile library).
- v0.6 — step 11: the standard component library of §8 is complete as specified in `design/standard-components.md` v0.2 (inputs, Form, containers, lists, Table, uploads, downloads); roadmap updated.
- v0.5 — step 10: embedded Jetty 9.4 replaces the JDK `HttpServer`; WebSocket transport (Jetty native API, not JSR-356) with automatic long-poll fallback; roadmap marks steps 1–10 as implemented and lists what is next; dependency list as built (no Jackson, Closure or SLF4J); step 9 (help system) recorded.
- v0.4 — standard component library specified (`design/standard-components.md`); `NodeComboBox`, `DragConstraint`/`SceneBinder`/`Scene3DChildren`, `DownloadService`, `Upload` and `Table` folded into the designs; step 8 (sample app + docs) delivered.
- v0.3 — adopt NetBeans `org-openide-util-lookup` and `org-openide-util`; drop `layer.xml` in favour of an annotation-generated `Registry`; step 2 (sessions, transport, protocol) designed.
- v0.2 — resolved open questions: per-user file persistence, NetBeans-style i18n, multiple UIs per session with independent layouts.
- v0.1 — initial architecture.
