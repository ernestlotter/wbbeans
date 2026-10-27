# WebBeans Platform

* An attempt to make a web UI framework that allows the user to write applications, in the spirit and style of Swing applications on the NetBeans Platform, but in only server-side Java and no JavaScript (unless they choose to). The end result is a similar development pattern as for NBP applications, rendered in the browser with HTML5/JavaScript.
* Inspiration comes heavily from the excellent work in the NetBeans Platform.

A rich web application platform in the spirit of the NetBeans Platform: the application is server-side Java 8
on the class path (every jar's registrations, services and installers take part — no manifests, no module
system), the browser runs a generated client. See the design documents in the project (`design/`) and the
developer guide (`docs/developer-guide.md`); new developers start with the step-by-step user's guide (`docs/user-guide.md`).

## Status

Roadmap steps 1 (**core runtime**), 2 (**UI runtime, delta protocol, authentication, server**), 3
(**window system**), 4 (**nodes and explorer**), 5 (**actions, menus, toolbars, shortcuts, dialogs,
notifications, progress**), 6 (**charts**), 7 (**3D scenes**), 8 (**sample application, options dialog,
test harness, application packaging**), 9 (**help system**), 10 (**Jetty + WebSocket**), 11 (**tier-1 components**)
12 (**tier-2/3 components, enum rendering, Quick Search**) and 13 (**HTTPS**) are implemented and tested; the module system of step 1 was removed afterwards in favour of
the plain class path (step 14):

| Project | Contents |
|---|---|
| `webbeans-core` | Registry (`org.webbeans.core.registry`), NetBeans Lookup integration (`org.webbeans.core.lookup`), session-locale i18n (`org.webbeans.core.i18n`), per-user files (`org.webbeans.core.files`), JSON (`org.webbeans.core.json`), bootstrap (`org.webbeans.core.Platform`: registry and `Lookup.getDefault()` from the class path, `Installer` start-up/shutdown hooks) |
| `webbeans-processor` | Annotation processor: `@Registration`, `@Hide`, `@Renderer`, `@Messages` → generated `WbRegistrations` providers and `Bundle` classes; extension SPI for later annotations |
| `webbeans-auth` | Pluggable authentication (`org.webbeans.auth`): `Authenticator`, `AuthenticationProvider` SPI (form / redirect / script / token / header), challenges, role mappers, authorizers, session policy, throttle, login page SPI; `PropertiesAuthenticationProvider` (PBKDF2 hashes in `system/users.properties`) and `HeaderAuthenticationProvider` |
| `webbeans-ui` | Component model (`Component`, `Container`, typed `Property`, `EventType`), `UISession` (one per login, serial executor, event bus, locale) and `UI` (one per browser tab, timers, delta history, resync), the standard components (`org.webbeans.ui.components`: inputs incl. NumberField/Slider/RadioGroup/date and time pickers/ColorPicker/PasswordField/TagInput/ToggleButton/ButtonGroup/SplitButton, Panel layouts with a busy overlay, SplitPane, ScrollPane, TabbedPane, Card, Accordion, Popover, Badge/Chip/Avatar/EmptyState/Breadcrumb/Stepper, ListBox, Tree, Upload, CodeEditor, MarkdownEditor, DiffView, OutputView, Calendar, Kanban, Diagram + Palette, ImageViewer, PdfViewer, PropertyGrid; `org.webbeans.ui.table`: the virtualised Table and TreeTable with in-memory and lazy models; `org.webbeans.ui.text`: Markdown, LineDiff; `Form`, `DownloadService`, `EnumLabels`/`Labeled`) and the client runtime (`wb-core.js`, one renderer file per component, `wb.css`) |
| `webbeans-windows` | The window system (`org.webbeans.windows`): `TopComponent` (a panel with tab properties, lookup, lifecycle callbacks, persistence), `Mode` (docking slots; `explorer`, `navigator`, `editor`, `properties`, `output` shipped), `WindowManager` (one per browser tab: open/close/activate, global context lookup, groups, named and per-role layouts), `LayoutModel` (splits/modes/floating/minimised, JSON on the wire and in `layout.json`), the `Dock` component and its renderer (tabs, drag & drop into modes or edge splits, gutters, maximize, float, minimise to side bars, tab menu), `MainWindow` + `StatusBar` and the default `MainWindowProvider` |
| `webbeans-nodes` | The Nodes API (`org.webbeans.nodes`: `Node`, `AbstractNode`, `FilterNode`, `Children` (`Array`, `Keys`, factories), `ChildFactory` (sync/async with a wait node), properties + `PropertySupport` + `Sheet`, `PropertyEditor`/`PropertyEditors`, `NodeOp`, `Openable`/`Editable`/`Savable`, `Transferable`/`PasteType`/`Clipboard`, `Node.Handle` + `NodeHandleFactory`) and the explorer (`org.webbeans.explorer`: `ExplorerManager`, `ExplorerUtils`, `TreeView`, `OutlineView`, `ListView`, `PropertySheet`, `NodeComboBox`) with their renderers |
| `webbeans-actions` | Actions (`org.webbeans.actions`: `Action`/`AbstractAction`/`ActionEvent`/`ActionMap`, `ContextAwareAction`, `Presenter`, the `Actions` factories — context, always-enabled, callback, checkbox — and the registry bridge `Actions.forID/forPath/actionFor`; `@ActionID`/`@ActionRegistration`/`@ActionReference(s)`; `MenuBar`/`Menu`/`MenuItem`/`Separator`/`Popup`, `ToolbarPanel`/`Toolbar`/`ToolbarButton` with `MenuBarBuilder`/`ToolbarBuilder` over the registry folders `Menu/` and `Toolbars/`, `Shortcuts` from `Shortcuts/<key>`, View ▸ Toolbars with the user overlay), dialogs (`org.webbeans.dialogs`: `DialogDisplayer`, `NotifyDescriptor` + `Message`/`Confirmation`/`InputLine`/`Exception`, `DialogDescriptor`, `Dialog`, `NotificationLineSupport`, a minimal `WizardDescriptor`, the `wb.Dialog` component), notifications (`org.webbeans.notifications`: session-wide `NotificationDisplayer`, toasts + drawer + status bar bell), progress (`org.webbeans.progress`: `ProgressHandle`, `ProgressHandleFactory`, `Cancellable`, status bar cell) and per-user `WbPreferences` |
| `webbeans-help` | The help system (`org.webbeans.help`): `@HelpPage` registrations of Markdown/HTML pages in application jars (tree of topics with `parent`, positions, keywords, roles; `page_<lang>.md` variants), the `Markdown` converter and `HelpContent` renderer (relative links between pages resolved to topics, images served from the jar, everything sanitised), the Help window (`HelpTC` in its own `help` mode: table of contents, search over titles/keywords/text, breadcrumbs, Back, *Open in new tab*), `HelpDisplayer` (the `HelpCtx.Displayer` service), Help ▸ Help Contents and `F1` context help, the stand-alone page `GET /wb/help/<id>` (a `PageProvider`), and the platform's own "Getting around" page |
| `webbeans-charts` | The `Chart` component (`org.webbeans.charts`): a server-side `ChartModel` of `Series` and `Point`s (append/update/remove/sliding window, every mutation one small delta merged per flush into a single `data` call), `Axis` (linear/log/time/category, `DecimalFormat`/`DateTimeFormatter` patterns compiled for the client, markers and bands), `Legend`, tooltip/zoom/selection modes, `pointClick`/`legendToggle`/`rangeSelect` events, `exportPng`/`exportSvg` futures and client-side downloads; the dependency-free SVG renderer (`chart.js`: line/step/area with stacking and monotone smoothing, grouped/stacked/horizontal bars, scatter/bubble, pie/donut, nice ticks, legend toggles, shared tooltips, crosshair, wheel zoom, drag pan, shift-drag brush, LTTB downsampling, resize observation, PNG/SVG export); `ChartTopComponent` (toolbar with reset zoom, save as SVG/PNG and the `Charts/Actions` registry actions) |
| `webbeans-graph` | The `GraphScene<N, E>` component (`org.webbeans.graph`), in the spirit of the NetBeans Visual Library: node and edge objects mapped to `NodeWidget`s (shapes, icons, accent bands, pins as rows or sockets, custom SVG) and `EdgeWidget`s (anchors, direct/orthogonal/curved/free routers, end shapes, line styles, labels along the path), `NoteWidget`s attached to anything, `ConnectProvider`/`PopupProvider`, selection in a lookup, server-side layouts (grid, circular, layered tree, force-directed) animated in the browser, one `graph` delta per flush; the SVG renderer (`graph.js`: measuring, routing, markers, pan/zoom, drag, marquee, connect, in-place edit, minimap, export); `GraphTopComponent` (toolbar + `Graph/Actions`) |
| `webbeans-map` | The `MapView` component (`org.webbeans.map`): a slippy map (Web Mercator, XYZ raster tiles) whose base tiles are served through the server — `TileSource.proxied` fetches once and caches under the data dir (`/wb/tiles/…`, a `ContentProvider`), `directory` serves local tile folders, `direct` loads from the provider with a CSP allowance, `BLANK` draws a graticule; server-owned overlays with per-flush deltas (markers with icons/labels/drag, polylines, polygons with holes, circles in metres, rectangles, georeferenced images, GeoJSON with per-feature styles, sanitised popups), toggleable layers, view/click/hover/menu/drag/draw events, draw modes, `Geo` helpers; the dependency-free renderer (`map.js`: tiles, pan/zoom/pinch, SVG + HTML overlays, scale bar, layer control, drawing); `MapTopComponent` (toolbar + `Map/Actions`) |
| `webbeans-scene3d` | The `Scene3D` component (`org.webbeans.scene3d`): a server-side scene graph of `Node3D`s (`Group`, `Mesh`, `InstancedMesh`, `Line`, `Points`, `Label3D`, lights) under a root group, content-addressed `Geometry` (primitives as parameters, arbitrary meshes and glTF as binary blobs served from `/wb/blob/<sha256>`), shared mutable `Material`s, immutable `Texture`s, `Camera`/`Controls`/`Fog`, client-run `Animation`s, `SceneBinder` (Children.Keys-style diffing of domain objects into nodes), `Gltf` (minimal glTF 2.0 reader), events `PICK`/`HOVER`/`DRAG` (validated against a `DragConstraint` server-side, rejectable)/`CAMERA_CHANGE`/`ANIMATION_DONE`/`READY`, `snapshot()` futures and client-side downloads; every mutation is one small op merged per flush into a single `scene` call. The renderer (`scene3d.js` over a vendored three.js r152 loaded on first use): on-demand rendering, orbit/map/first-person controls, picking with instance ids, hover effects, constrained drags with snapping, selection outlines, DOM labels, tween engine. `Scene3DTopComponent` (toolbar: fit, reset view, grid, save PNG, `Scene3D/Actions`), `Scene3DChildren` + `Node3DNode` (the scene graph as explorer nodes with editable position/scale/visibility, two-way selection sync) |
| `webbeans-test` | `HeadlessUI`: a complete session without a server or browser for application tests — the registry from the test class path, the real main window, a temporary data directory, injected client events (`click`, `type`, `inject`), dialog answering, `byTestId`, the recorded wire (`callsOf`, `lastDeltas`), extra tabs, users and roles |
| `webbeans-windows-processor` | Annotation handler for `@Mode.Registration`, `@TopComponent.Registration/Description/OpenActionRegistration`, `@TopComponentGroup.Registration` (processor path) |
| `webbeans-actions-processor` | Annotation handler for `@ActionID` + `@ActionRegistration` (the constructor picks the kind: no-arg, `(T)`, `(List<T>)`, `key` for callbacks) and `@ActionReference(s)` (menus, toolbars, `Shortcuts`, popup folders, separators) |
| `webbeans-server` | HTTP front end on embedded Jetty 9.4: cookie sessions, login/logout/`/auth/*` endpoints, the bundled client (`/wb/app-<hash>.js|css`), class-path resources (`/wb/res/`), the WebSocket transport (`/wb/ws`, one socket per tab) and the long-poll fallback (`POST /wb/poll`) for clients that cannot open a WebSocket |
| `webbeans-launcher` | `org.webbeans.launcher.Main`: starts the platform and the server; creates the `admin` account on first start |
| `workbench/*` | The **Workbench** sample application (`org.webbeans.workbench.*`): `core` (Output window over a shared `LogMessage` bus type, status cells, Workbench options — theme and language — account seeding, About, Log out), `files` (explorer over a per-user working directory with an `OutlineView`, a multi-instance editor persisted across logins, New/Open/Save/Refresh actions with menu, toolbar, shortcut and popup placements), `dashboard` (live charts from a `MetricsSampler`, high-load notifications, export action, dashboard options), `viewer3d` (a turbine from a session model through a `SceneBinder`, parts tree with two-way selection, picking, blade count actions, a spin checkbox, sensor readings pushed from a simulation thread), `admin` (Server Status over the `SessionDirectory` for administrators only, end-session action, a shipped admin layout) and `app` (branding overlay, launcher script, the application jar) — see `workbench/` and `docs/user-guide.md` |
| `webbeans-tutorial` | The code of the **User's Guide** (`docs/user-guide.md`): a Hello window, a validated contact form with a confirmation dialog and a timer-driven progress bar, a per-session `TaskList` service shown as nodes in an `OutlineView`, an always-enabled and a context action (menu, toolbar, shortcut, node popup), an Options page, and `HeadlessUI` tests of all of it; loaded next to the demo |
| `webbeans-demo` | An application jar with five registered windows: a form, an about page, an events log on the session bus, a Planets explorer (`OutlineView` over a per-session model with editable columns, context menu, rename/delete, drag source, paste) opening one persisted window per planet, and a Properties window (`PropertySheet` following the global context); status bar cells (user, clock, locale, sign-out); registered actions of every kind (`ExploreAction` single-context, `RemovePlanetsAction` multi-context with confirmation, `NewPlanetAction` with an input dialog, `ScanAction` asynchronous with cancellable progress, `PlanetWizardAction`, `AboutAction`, `LogoutAction`, `ShowEventsAction` check item) in the `Planets` menu/toolbar, node popups and shortcuts; a Dashboard window with four charts over the model (live time series with a sliding window, bars that follow the planets, a donut, a zoomable bubble scatter) and a `ChartTopComponent` with the export round trip; a "Solar system 3D" window (`Scene3DTopComponent`: sun and orbiting, spinning planets bound to the model with a `SceneBinder`, orbit lines and labels, a draggable probe constrained to the ecliptic with snapping and server-side rejection, picking that selects in the scene and in a scene-outline `TreeView` whose nodes edit position and scale from the Properties window, and a registered toolbar action); a *Components* window (the form inputs under a `Form`, uploads and downloads, Tree → ListBox in a SplitPane) and an *Orders* window (a 10 000-row `Table`); step 12's *Editors* (CodeEditor, MarkdownEditor, DiffView, OutputView), *Widgets* (every small widget, a `PropertyGrid` fed by a `TreeTable`) and *Boards* (Calendar, Kanban, Diagram + Palette, ImageViewer, PdfViewer) windows; Quick Search (Ctrl+I) over all of it |

Platform pieces added in step 8: the Options dialog (`OptionsPanelController` + `@TopLevelRegistration`, `OptionsDisplayer`, Tools ▸ Options), `@LayoutRegistration` (window layouts shipped per role), `TopComponent.localeChanged()`, the `SessionDirectory` service, `--branding`, and one-jar application packaging (`maven-shade-plugin` / `tools/fatjar.py`). Step 9 added `org.webbeans.ui.HelpCtx` (+ `Provider`/`Displayer`), `helpId` on `@TopComponent.Description`, `NotifyDescriptor.setHelpCtx` (a Help button in dialogs, `F1` presses it), `OptionsPanelController.getHelpId`, the `PageProvider` SPI served by the HTTP front end, and expansion memory in the node views (trees keep their expanded nodes across reconnects and root rebuilds). The Workbench, the tutorial and the demo ship help pages. The Maven plugin and the archetype remain to do.

## Building and running

The smallest possible application — one jar with one menu action, started with the launcher — is in
[`examples/hello`](examples/hello/README.md) (two files: a pom and an action class).

Standard build (needs Maven Central access; JDK 8 or newer — on JDK 9+ the `jdk9-plus` profile adds
`--release 8` so the Java 8 API is enforced too):

```
mvn verify
java -cp 'webbeans-launcher/target/dist/lib/*:webbeans-demo/target/classes' org.webbeans.launcher.Main --datadir data
```

In the NetBeans IDE: *File ▸ Open Project*, choose the repository folder (the `WebBeans Platform` parent) and
tick *Open Required Projects*; then *Clean and Build* the parent, which builds every project in dependency order
and runs the tests. The JDK the build runs with is set per project under *Properties ▸ Build ▸ Compile ▸ Java
Platform* (register the JDK first under *Tools ▸ Java Platforms*). The generated `Bundle` classes and
`WbRegistrations` come from the annotation processors declared in each project's `pom.xml`, so the editor shows
them after the first build. To run the demo from the IDE, use the `webbeans-launcher` project's *Run* with the
arguments above, or the `tools/fatjar.py`/`build.sh` output from a terminal.

Offline build used during development (compiles for Java 8 against jars in `../third_party/lib`
and runs the JUnit tests): `./build.sh`. On the first run it calls `tools/fetch-third-party.sh`, which downloads
the jars it needs (NetBeans lookup/util, JUnit, the Servlet API, Jetty and its WebSocket modules) from Maven
Central into that directory; without network access, run that script elsewhere or copy the jars there.
The JDK is taken from the `PATH`, or from `JAVA_HOME` / `./build.sh --jdk /path/to/jdk` (JDK 8 compiles with
`-source/-target 8`, JDK 9+ with `--release 8`; `LIB` and `OUT` override the jar and output directories). It leaves a runnable layout
in `build/dist`:

```
java -cp 'build/dist/lib/*' org.webbeans.launcher.Main --datadir data --port 8080
```

Open `http://localhost:8080/`. On the first start an `admin` account is created; its password is printed once
(or taken from `--admin-password` / `WEBBEANS_ADMIN_PASSWORD`). Accounts live in `data/system/users.properties`
(`PasswordTool` adds or changes them). Other options: `--host`, `--context /app`, `--title`, `--locale de`,
`--session-timeout 30m`, `--dev` (rebuild the client bundle on every page load), `--secure-cookies`,
`--trusted-proxy <ip>`, `--no-websocket` (long polling only, for a proxy that cannot forward the upgrade),
`--http-threads N`, `--max-upload 256m`.

HTTPS: `--https-port 8443 --keystore server.p12 --keystore-password …` serves TLS from the embedded Jetty
(PKCS12 or JKS; `--https-redirect` sends plain-HTTP requests over, `--no-http` closes the plain port). With
`--https-port` alone a self-signed certificate for `localhost` is generated into `data/system/selfsigned.p12`
for development. Behind a TLS-terminating proxy instead, keep plain HTTP and pass `--trusted-proxy <ip>`
(and `--secure-cookies`).

Everything is JDK 8 compatible (`--release 8`); the build runs on JDK 8 or newer. The browser end-to-end checks
used during development are `webbeans-demo/e2e/windows.js`, `nodes.js`, `actions.js`, `charts.js`, `scene3d.js`, `transport.js`, `components.js`, `components2.js`, `webbeans-tutorial/e2e/tutorial.js`, `workbench/e2e/help.js` and
`workbench/e2e/workbench.js` (Playwright): they log in, drive the dock, the explorer, the menus, dialogs, notifications, charts and the 3D
viewport (headless Chromium renders WebGL through SwiftShader), drop the WebSocket to exercise reconnect/replay (and
the long-poll fallback), reload to exercise persistence, and log out.

## The Workbench sample application

`./build.sh` also builds `build/workbench-dist/workbench.jar` (the platform and the five Workbench jars merged into one;
`tools/fatjar.py` is the offline stand-in for the `maven-shade-plugin` configuration in `workbench/workbench-app/pom.xml`):

```
cd build/workbench-dist && ./workbench.sh            # java -jar workbench.jar --branding workbench --title Workbench --datadir data
```

Log in as `alice` (user + admin) or `bob` (user), password `secret`. The browser check `workbench/e2e/workbench.js` drives it
end to end: branding, the editor round trip (Ctrl+S writes the file), picking in 3D with tree sync, server push,
Options (German + dark theme without a reload), layout persistence across a reload, and role filtering for bob.

## Dependencies

- `org.netbeans.api:org-openide-util-lookup` and `org-openide-util` (RELEASE126) — `Lookup`, `NbBundle`,
  `RequestProcessor`, `@ServiceProvider`.
- `org.eclipse.jetty:jetty-server`, `jetty-servlet` and `org.eclipse.jetty.websocket:websocket-server` (9.4.x, the
  last Jetty line that runs on JDK 8) with `javax.servlet:javax.servlet-api:3.1.0` — the HTTP front end
  (`webbeans-server` only; application code never sees Jetty).
- JUnit 4 and `websocket-client` for tests.

## Layout of an application jar

Any jar on the class path takes part; what it contributes is in its `META-INF/services`:

```
META-INF/services/org.webbeans.core.registry.RegistrationProvider   (generated by the processor)
META-INF/services/org.webbeans.ui.UIProvider                        (from @ServiceProvider, NetBeans' processor)
META-INF/services/org.webbeans.core.Installer                       (optional start-up/shutdown hook)
<pkg>/generated/WbRegistrations.class                              (generated by the processor)
<pkg>/Bundle-generated.properties                                  (generated by the processor from @Messages)
<pkg>/Bundle.properties, Bundle_<locale>.properties                (hand-written, next to the sources, NetBeans
                                                                    style; merged with the generated one at run time)
```

Registrations are annotation-only: `@Registration(path = "Services/Session", ...)` on a class or static
method, `@Hide`, `@Renderer`, `@Messages`, and the platform API annotations of later steps. At runtime
`Registry.getDefault().folder("Menu/File")` gives the session view (roles, locale, user customisation);
`RegistryLookup.forPath("Services")` is the Lookup over a folder; `Lookup.getDefault()` sees every jar's
`@ServiceProvider`s and the `Services` folder. Entries at the same path: the later jar on the class path wins
(logged); `@Hide` masks entries of other jars.

## Writing a window

```java
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "FilesTC", iconBase = "org/acme/files/files.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_FilesAction")
@Messages({"CTL_FilesAction=Files", "CTL_Files=Files"})
public final class FilesTopComponent extends TopComponent {
    public FilesTopComponent() {
        setDisplayName(Bundle.CTL_Files());
        setLayout(new Layout.Border());
        add(new Label("..."), Layout.Border.CENTER);
    }
    @Override protected void componentOpened() { /* start listening */ }
    @Override protected void componentClosed() { /* stop */ }
}
```

The jar needs `webbeans-processor` and `webbeans-windows-processor` on its annotation processor path. Multi-instance
windows (editors) are plain subclasses created by code: `new EditorTC(file).open(); tc.requestActive();` — they get ids
`EditorTC#1`, `#2`, … `WindowManager.getDefault()` (on the session thread) finds modes and components, follows the
activation (`getRegistry()`, `getGlobalContext()`), and saves/loads layouts. Shortcuts: `Ctrl+Shift+Enter` maximize,
`Alt+W` close, `Alt+Shift+←/→` previous/next tab (the browser keeps `Ctrl+W` and `Ctrl+Tab` for itself).

## Showing data: nodes and views

```java
public final class FilesTopComponent extends TopComponent implements ExplorerManager.Provider {
    private final ExplorerManager em = new ExplorerManager();
    public FilesTopComponent() {
        ActionMap map = new ActionMap();
        ExplorerUtils.activateActions(em, map, true);              // Edit menu / Ctrl+C,X,V / Delete reach this explorer
        associateLookup(ExplorerUtils.createLookup(em, map));      // selection → window lookup → global context
        OutlineView view = new OutlineView("Name");
        view.setPropertyColumns("size", "Size", "modified", "Modified");
        add(view, Layout.Border.CENTER);
    }
    @Override protected void componentOpened() { em.setRootContext(new FolderNode(root)); }
    @Override public ExplorerManager getExplorerManager() { return em; }
}
```

Nodes (`AbstractNode` with `Children.create(new MyChildFactory(), true)`) carry the represented object and its
capabilities in their lookup, a `Sheet` of properties (edited in place in the outline and in the `PropertySheet`),
actions for the context menu, and `canRename/canDestroy/canCut`. Views send only expanded children and patch the
browser incrementally from node events.

## Actions, menus, dialogs

```java
@ActionID(category = "Files", id = "org.acme.files.CompressAction")
@ActionRegistration(displayName = "#CTL_Compress", iconBase = "org/acme/files/zip.svg", asynchronous = true)
@ActionReferences({
    @ActionReference(path = "Menu/Files", position = 300, separatorAfter = 350),
    @ActionReference(path = "Toolbars/Files", position = 100),
    @ActionReference(path = "Files/Actions", position = 100),          // the folder FileNode.getActions reads with Actions.forPath
    @ActionReference(path = "Shortcuts", name = "DS-Z")})              // Ctrl+Shift+Z
public final class CompressAction implements ActionListener {
    private final List<FileObject> files;                              // List<T> → enabled for one or more T in the context
    public CompressAction(List<FileObject> files) { this.files = files; }
    public void actionPerformed(ActionEvent e) {                       // asynchronous: a background thread, progress in the status bar
        ProgressHandle h = ProgressHandleFactory.createHandle("Compressing", null, e.getUI().getSession());
        ... e.getUI().access(() -> ...) to touch components ...
    }
}
```

The constructor decides the kind (no-arg → always enabled, `(T)` → exactly one, `(List<T>)` → one or more,
`key = "copy"` → delegates to the active window's `ActionMap`); an `Action` class is registered as is and may
provide its own presenter (`Presenter.Menu`). Menus are declared with `@FolderRegistration(path = "Menu/Files",
displayName = "#CTL_Files")`, filled when first opened, and rebuilt on a locale change; `View ▸ Toolbars` hides
toolbars through the user's registry overlay. Dialogs never block:

```java
DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation("Delete it?", "Delete", NotifyDescriptor.YES_NO_OPTION))
    .thenAccept(option -> { if (option == NotifyDescriptor.YES_OPTION) { ... } });
```

`NotificationDisplayer.getDefault().notify(...)` reaches every tab of the session (toast + drawer);
`ProgressHandle`s show in the status bar and can be cancelled there.

## Charts

```java
Chart cpu = new Chart(ChartType.LINE);
cpu.setTitle("CPU").setZoom(ZoomMode.X).setTooltip(TooltipMode.SHARED);
cpu.getXAxis().setType(AxisType.TIME).setFormat("HH:mm:ss");
cpu.getYAxis().setMin(0.0).setMax(100.0).setFormat("#0'%'").addBand("hot", 80, 100, "#b43b3b");
Series s = cpu.getModel().addSeries("cpu", "CPU");
s.setWindow(300);                                                      // sliding window: server and client drop the oldest
ui.scheduleAtFixedRate(() -> s.addPoint(Point.of(Instant.now(), sampler.cpu())), 1000, 1000);   // one ~60-byte delta per tick
cpu.addPointClickListener(e -> StatusDisplayer.getDefault().setStatusText(String.valueOf(e.getPoint())));
cpu.exportPng(800, 500).thenAccept(bytes -> ...);                      // rendered in the browser, completes on the session executor
```

Bars use a category axis (`Point.of("Mars", 2)`), pies one series of slices, scatter `Point.of(x, y).withSize(...)` bubbles;
`Series.setYAxis(1)` puts a series on the secondary axis, `setStack("g")`/`Chart.setStacked(true)` stacks bars and areas.
`ChartTopComponent` wraps a chart with a toolbar.

## 3D scenes

```java
Scene3D scene = new Scene3D();
scene.setGrid(true).setHoverEffect(HoverEffect.HIGHLIGHT);
scene.getRoot().add(new AmbientLight(0xffffff, 0.4), new DirectionalLight(0xffffff, 1).setPosition(5, 10, 5));
Material steel = Material.standard(0x8a8a8a).setMetalness(0.8).setRoughness(0.3);     // shared: one definition, one update for all users
SceneBinder<Part> parts = new SceneBinder<Part>(scene.getRoot(), Part::getId,
    p -> new Mesh(p.getName(), Geometry.box(1, 1, 1), steel).setDraggable(DragConstraint.plane(Axis.Y, 0).snap(0.5)),
    (p, n) -> n.setPosition(p.x, p.y, p.z));
parts.setItems(model.getParts());                                                     // diffed: adds/updates/removes only
scene.addListener(Scene3D.PICK, e -> scene.setSelection(e.getNode()));                // e.getLookup() holds the node and its user data
scene.addListener(Scene3D.DRAG, e -> { if (e.getPhase() == DragEvent.Phase.END && !model.allows(e.getPosition())) e.reject(); });
turbine.animate(Animation.rotate(Axis.Y, 0.5));                                       // runs in the browser, no per-frame traffic
scene.snapshot(1920, 1080).thenAccept(png -> ...);
Group ship = Gltf.load(getClass().getResourceAsStream("ship.glb"));                   // decoded server-side, geometry as blobs
```

`Scene3DTopComponent` wraps a scene with a toolbar; `new AbstractNode(new Scene3DChildren(scene.getRoot()))` turns the
graph into explorer nodes and `Scene3DChildren.bindSelection(em, scene)` syncs selection both ways. three.js is loaded
only by tabs that open a 3D window.

## How a page comes to be

1. `GET /` — the server checks the session cookie (else redirects to `/login`), creates a `UI` for the new tab
   and serves a small bootstrap page with `window.WB = {uiId, token, locale, ...}` and the bundle links.
2. The client says `hello`; the `UIProvider` from the lookup builds the component tree on the session thread
   (by default `MainWindowProvider`: main window, dock, status bar, menu bar/toolbars/shortcuts from the actions
   the application's `MainWindowDecorator`, the user's restored layout); the `init`
   message carries the whole tree.
3. Clicks, edits and the like are posted as events (`ev`), handled on the session executor, and every change
   made during that handling comes back as one `delta`. Server-initiated changes (timers, other sessions)
   arrive through the pending `poll` request.
4. If the connection breaks the client reconnects with the last sequence number it applied; the server replays
   the missing deltas from its history or sends a fresh `init`.
