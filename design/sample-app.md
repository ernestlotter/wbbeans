# WebBeans — Sample Application "Workbench" (design v0.2, as built)

Roadmap step 8. A small but complete application that exercises every platform API: a file explorer with an editor, a live dashboard, a 3D parts viewer and an administration window. It is the acceptance test for the platform and the worked example of the developer docs (`docs/user-guide.md` walks through it module by module). Source: `workbench/` in the repository; `impl/step8-notes.md` lists what changed against v0.1.

## 1. What the user sees

Login page → main window:

```
┌ File  Edit  View  Tools  Window  Workbench Help ────────────────────────────────────┐
│ [New] [Save] [Refresh] │ [Export] │ [+] [−] │ [Copy] [Cut] [Paste]                  │
├────────────┬────────────────────────────────────────────────────────┬──────────────┤
│ Files      │ Dashboard  │ 3D Viewer  │ README.md•  │ todo.txt       │ (Properties  │
│ ▾ workdir  │                                                        │  follows the │
│   ▸ src    │   CPU %        Requests / s                            │  selection)  │
│   notes    │   Traffic by source                                    │              │
│   README   │                                                        │              │
│ Parts      │                                                        │              │
│ ▾ Turbine  │                                                        │              │
│   Hub …    │                                                        │              │
├────────────┴────────────────────────────────────────────────────────┴──────────────┤
│ Output                                                                              │
│ 10:37:12 [files] Opened README.md     10:37:40 [files] Saved README.md             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Server Status  (administrators: the shipped "admin" layout)                         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Saved README.md                                  Signed in as Alice Admin   10:37  🔔│
└─────────────────────────────────────────────────────────────────────────────────────┘
```

- Two accounts seeded on the first start: `alice` (roles `user, admin`) and `bob` (`user`), password `secret`. Only alice gets "Window ▸ Server Status", the `admin` mode and the shipped admin layout.
- `en` and `de` ship; Tools ▸ Options ▸ Workbench switches language and theme without a reload (menus, dock, windows, status cells relabel) and remembers both per user.
- The Help menu reads "Workbench Help": the application jar carries a branding overlay for the platform's bundle.
- A second browser tab gets its own layout; notifications (high CPU) and Output lines appear in both.

## 2. Modules and packaging

| Module (code name) | Depends on | Contents |
|---|---|---|
| `workbench-core` (`org.webbeans.workbench.core`, exports `…core.api`) | platform | `LogMessage`/`Log` bus type, Output window, status cells, Workbench options (theme, language), `SessionSetup` decorator, `WorkbenchInstaller` (accounts), About, Log out |
| `workbench-files` | core | `FileService`, `FileNode` + `FolderChildFactory`, `FilesTopComponent` (OutlineView), `EditorTopComponent` (multi-instance, persisted), `FileActions` (Save, New File, New Folder, Open, Refresh) |
| `workbench-dashboard` | core, charts | `MetricsSampler`, `DashboardTopComponent` (3 charts on a `UI.Timer`), `DashboardOptions`, `ExportChartAction` |
| `workbench-viewer3d` | core, scene3d | `Part`/`PartsModel` (session service + simulation thread), `Geometries`, `PartNode`/`PartsRootNode`, `ViewerTopComponent` (`Scene3DTopComponent` + `SceneBinder`), `PartsTopComponent`, `ViewerActions` |
| `workbench-admin` | core | `SessionNode`, `ServerStatusTopComponent` (role `admin`, own `admin` mode), `EndSessionAction`, `AdminLayout` (shipped layout + `setRole`) |
| `workbench-app` (not a module) | all | branding overlay `org/webbeans/actions/Bundle_workbench[_de].properties`, `workbench.sh`, the application jar |

Layout:

```
workbench/
  pom.xml                      packaging pom; parent org.webbeans:webbeans-parent
  workbench-core/ -files/ -dashboard/ -viewer3d/ -admin/     one module jar each (manifest in src/main/resources/META-INF)
  workbench-app/               application jar: platform + branding + WEBBEANS-INF/modules/*.jar (dependency + shade plugins)
  e2e/workbench.js             Playwright end-to-end check
```

Run: `java -jar workbench.jar --branding workbench --title Workbench --datadir data` (or `workbench.sh`). The launcher extracts the embedded modules to `data/cache/modules` on start. Offline: `./build.sh` at the repository root builds everything and `build/workbench-dist/workbench.jar` (`tools/fatjar.py`).

## 3. `workbench-files`

### 3.1 Nodes

```java
public final class FileNode extends AbstractNode {
    public FileNode(final Path path) {
        super(Files.isDirectory(path) ? Children.create(new FolderChildFactory(path), true) : Children.LEAF, lookupFor(path));
        setName(path.getFileName().toString());
        setIconBase(Files.isDirectory(path) ? ".../folder.svg" : iconFor(name));
        setCanRename(true); setCanDestroy(true);
    }
    private static Lookup lookupFor(final Path path) {                // files are Openable: double-click, Enter, "Open"
        return Files.isDirectory(path) ? Lookups.singleton(path)
             : Lookups.fixed(path, (Openable) () -> EditorTopComponent.openFor(WindowManager.getDefault(), path));
    }
    @Override public Action[] getActions(boolean context) { return Actions.forPath("Files/Actions").toArray(new Action[0]); }
    @Override public void setName(String n)  { /* Files.move, then super.setName; the open editor follows */ }
    @Override public void destroy()          { /* delete (recursively), FileService.changed(parent), super.destroy() */ }
    @Override protected Sheet createSheet()  { /* name (rename), size, modified, path */ }
}
final class FolderChildFactory extends ChildFactory<Path> {           // asynchronous: a wait node shows meanwhile
    protected boolean createKeys(List<Path> keys) { /* folders first, then files, case-insensitive */ return true; }
    protected Node createNodeForKey(Path key) { return new FileNode(key); }
    static void refreshDir(Path dir) { /* every live factory showing dir: refresh(false) */ }
}
```

### 3.2 Explorer window

```java
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "FilesTC", iconBase = "org/webbeans/workbench/files/icons/files.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenFiles", position = 100)
public final class FilesTopComponent extends TopComponent implements ExplorerManager.Provider {
    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final OutlineView view = new OutlineView(Bundle.COL_Name());
    public FilesTopComponent() {
        setDisplayName(Bundle.CTL_FilesTC());
        ExplorerUtils.activateActions(em, map, true);                    // Edit menu, Ctrl+C/X/V, Delete reach this explorer
        associateLookup(ExplorerUtils.createLookup(em, map));             // selection → Properties window, actions
        view.setPropertyColumns("size", Bundle.COL_Size(), "modified", Bundle.COL_Modified());
        setLayout(new Layout.Border()); add(view, Layout.Border.CENTER);
    }
    @Override protected void componentOpened() { em.setRootContext(new FileNode(fileService.getWorkDir())); }
    @Override protected void localeChanged()   { setDisplayName(Bundle.CTL_FilesTC()); }
}
```

### 3.3 Editor (multi-instance, persisted)

```java
@TopComponent.Description(preferredID = "EditorTC", iconBase = "…/text.svg", factory = EditorTopComponent.Factory.class)
public final class EditorTopComponent extends TopComponent {
    public EditorTopComponent(Path path) {
        text.setMonospace(true); text.setValue(read(path));
        text.addChangeListener(e -> setDirty(true));                      // commit; InputEvent marks dirty while typing
        associateLookup(Lookups.fixed(path, new FileNode(path), (Savable) this::doSave));   // File ▸ Save, Ctrl+S, Properties
    }
    @Override protected boolean canClose() {                             // asynchronous question; close() again when answered
        if (!dirty) return true;
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(Bundle.MSG_SaveChanges(name), YES_NO_CANCEL_OPTION))
            .thenAccept(r -> { if (r == YES_OPTION) { doSave(); close(); } else if (r == NO_OPTION) { setDirty(false); close(); } });
        return false;
    }
    @Override protected void writeState(Map<String, Object> out) { out.put("path", path.toString()); }
    public static EditorTopComponent openFor(WindowManager wm, Path path) { /* reuse the open one or open(); requestActive() */ }
    public static final class Factory implements TopComponent.Factory {
        public TopComponent create(Map<String, Object> state) { Path p = Paths.get((String) state.get("path")); return Files.isRegularFile(p) ? new EditorTopComponent(p) : null; }
    }
}
```

### 3.4 Actions

```java
@ActionID(category = "File", id = "org.webbeans.workbench.files.SaveAction")
@ActionRegistration(displayName = "#CTL_Save", iconBase = "…/save.svg", surviveFocusChange = true)
@ActionReferences({ @ActionReference(path = "Menu/File", position = 300), @ActionReference(path = "Toolbars/File", position = 300),
                    @ActionReference(path = "Shortcuts", name = "D-S") })
public static final class SaveAction implements ActionListener {
    private final Savable savable;                                       // exactly one Savable in the context → enabled
    public SaveAction(Savable savable) { this.savable = savable; }
    public void actionPerformed(ActionEvent e) { try { savable.save(); } catch (IOException ex) { DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(ex)); } }
}
```

`NewFileAction`/`NewFolderAction` (always enabled, `D-N`; an `InputLine` continued in `thenAccept`, created in the selected folder, refreshed through `FileService.changed`, the new file opened), `OpenAction` (`List<Openable>`), `RefreshAction` (`F5`); all also under `Files/Actions` for the node popup. Nested action classes in one `FileActions` holder.

### 3.5 Service

```java
@ServiceProvider(service = FileService.class)
public final class DefaultFileService implements FileService {
    public Path getWorkDir() { Path d = UISession.current().getUserFiles().dir("org.webbeans.workbench.files").resolve("workdir"); if (!Files.isDirectory(d)) seed(d); return d; }
    public void changed(Path dir) { FolderChildFactory.refreshDir(dir); }
}
```

A global service that resolves the current user's directory per call (seeded with README.md, notes.txt, src/Hello.java, src/data.csv on first use).

## 4. `workbench-dashboard`

```java
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 200)
public final class DashboardTopComponent extends TopComponent {
    private final Chart cpu = new Chart(ChartType.LINE), requests = new Chart(ChartType.BAR), traffic = new Chart(ChartType.DONUT);
    private final InstanceContent content = new InstanceContent();
    public DashboardTopComponent() {
        associateLookup(new AbstractLookup(content));                     // the clicked chart → context actions (Export)
        cpu.getXAxis().setType(AxisType.TIME).setFormat("HH:mm:ss"); cpu.getYAxis().setMin(0.0).setMax(100.0).setFormat("#0'%'").addBand("hot", 90, 100, "#b43b3b");
        requests.getModel().setCategories(Arrays.asList("/api", "/static", "/ws", "/auth"));
        cpu.addPointClickListener(e -> content.set(Collections.singleton(e.getSource()), null));
        Panel grid = new Panel(new Layout.Grid(2, 2, 8, 8).fill()); grid.add(cpu); grid.add(requests); grid.add(traffic);
    }
    @Override protected void componentOpened() {
        cpu.getModel().getSeries("cpu").setWindow(prefs.getInt("window", 120));
        timer = getUI().scheduleAtFixedRate(this::sample, 0, prefs.getInt("refreshSeconds", 1) * 1000L);
    }
    private void sample() { /* addPoint(Instant.now(), cpu) → one ~60-byte append; setPoints for bars and donut; > 90 % → notification once a minute + Log.warn */ }
}
```

`MetricsSampler` is a `@ServiceProvider` (JVM-wide, synthetic). `DashboardOptions` (`@OptionsPanelController.TopLevelRegistration`) edits refresh rate, window and notification flag with validated text fields; applying restarts every open dashboard on its own executor. `ExportChartAction` is a context action over `Chart` in `Toolbars/Dashboard` and Tools: `chart.download("png", …)` plus `exportPng(1200, 700)` → a notification with the size.

## 5. `workbench-viewer3d`

```java
@Registration(path = "Services/Session", type = PartsModel.class)
public final class PartsModel implements AutoCloseable {               // one per login; closed with the session
    public interface Listener { void partsChanged(); void partChanged(Part p); void selectionChanged(Part selected); }
    public synchronized void addListener(Listener l) { listeners.add(l); if (session == null) session = UISession.current(); if (job == null) start(); }
    private void step() {                                                 // simulation thread, every second
        synchronized (this) { /* heat the sensors, flag > 85 °C */ }
        session.access(() -> { for (Part p : changed) fire(p); });        // listeners run on the session executor
    }
}

@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 300)
public final class ViewerTopComponent extends Scene3DTopComponent implements ExplorerManager.Provider, PartsModel.Listener {
    public ViewerTopComponent() {
        super(new Scene3D());
        scene.setGrid(20, 20).setShadows(true).setHoverEffect(HoverEffect.HIGHLIGHT);
        scene.getRoot().add(new AmbientLight(0xffffff, 0.45), sun, floor, fixed, rotor);
        associateLookup(new ProxyLookup(Lookups.singleton(scene), ExplorerUtils.createLookup(em, map)));
        scene.addListener(Scene3D.PICK, e -> model.setSelected((Part) e.getUserData()));   // null for the background
    }
    @Override protected void componentOpened() {
        model = PartsModel.get(); em.setRootContext(new PartsRootNode(model));
        binder = new SceneBinder<Part>(fixed, Part::getId, this::create, this::update);
        model.addListener(this); partsChanged(); selectionChanged(model.getSelected());
    }
    private Node3D create(Part p) { /* a Group with the mesh (hub/shaft/housing/blade/sensor) and, for sensors, a Label3D */ }
    private void update(Part p, Node3D n) { /* blades → rotor group + angle; sensors → material by status, label text */ }
    @Override public void partsChanged() { binder.setItems(model.getParts()); rotor.animate(Animation.rotate(Axis.Z, 1.2)) /* when spinning */; }
    @Override public void partChanged(Part p) { binder.refresh(p); }
    @Override public void selectionChanged(Part s) { scene.setSelection(binder.nodeFor(s)); em.setSelectedNodes(PartsRootNode.find(root, s)); }
}
```

- `PartsTopComponent` (mode `explorer`, below Files) shows the same `PartsRootNode` in a `TreeView`; its selection goes to the model, so the scene highlights the part, and picks come back the same way. The platform Properties window shows the selected `PartNode` (name, kind, colour `#rrggbb` — editable, written through the model — position, live temperature and status for sensors).
- `Geometries.BLADE` is built with `Geometry.buffer()` (a tapered, twisted strip with computed normals) and uploaded once as a blob; the rest are primitives.
- Actions: `SnapshotAction` (context over `Scene3D`, in the viewport toolbar through `Scene3D/Actions` and in Tools), `MoreBladesAction`/`FewerBladesAction` (change the model; scene and tree follow by key), `SpinAction` (View ▸ Spin rotor: `Actions.checkbox` over a user preference).

## 6. `workbench-admin`

```java
@Mode.Registration(name = "admin", side = Mode.Side.BOTTOM, defaultSize = 180, position = 200)
@TopComponent.Registration(mode = "admin", openAtStartup = false, position = 100, roles = {"admin"})
@TopComponent.Description(preferredID = "ServerStatusTC", iconBase = "…/server.svg", persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED)
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenServerStatus", position = 950)
public final class ServerStatusTopComponent extends TopComponent implements ExplorerManager.Provider {
    // an OutlineView over SessionNodes (user, address, tabs, last activity, locale) from Lookup.getDefault().lookup(SessionDirectory.class),
    // refreshed every 2 s by a UI.Timer; a summary label with session/tab counts and JVM memory
}
```

`EndSessionAction` (`List<UISession>` context, `roles = admin`, confirmation, `SessionDirectory.invalidate`) sits in the node popup (`Sessions/Actions`). `AdminLayout` ships `layouts/admin.json` with `@LayoutRegistration(role = "admin")` and, as a `MainWindowDecorator`, calls `wm.setRole("admin")` for administrators once the UI is ready. `bob` never receives the window, its mode reference, the open action or the menu entry.

## 7. `workbench-app`

- Not a module: resources on the platform class path. `org/webbeans/actions/Bundle_workbench.properties` (`CTL_HelpMenu=Workbench Help`) and the German overlay; the launcher is started with `--branding workbench --title Workbench`.
- `pom.xml`: `maven-dependency-plugin` copies the five module jars to `WEBBEANS-INF/modules/`, a one-line exec writes `modules.list`, `maven-shade-plugin` merges the platform (services concatenated) and sets `Main-Class: org.webbeans.launcher.Main`.
- Accounts: `WorkbenchInstaller.restored()` in `workbench-core` writes `alice`/`bob` into `system/users.properties` when it is missing (PBKDF2 through `PropertiesAuthenticationProvider.putAccount`); `PasswordTool` adds more.

## 8. What each part proves

| Platform feature | Where |
|---|---|
| Module dependencies, public packages, `ModuleInstall` | `workbench-core` exports `core.api`; four modules depend on it; `WorkbenchInstaller` |
| `@ServiceProvider` + `Lookup.getDefault()` | `DefaultFileService`, `MetricsSampler`, `SessionSetup`, `AdminLayout` |
| Session lookup, per-user files, session bus | `PartsModel`, `FileService.getWorkDir`, `LogMessage` → Output in every tab |
| Roles | `ServerStatusTopComponent`, `EndSessionAction`, admin mode and layout |
| i18n + branding + runtime locale switch | every `@Messages` + `Bundle_de`, `Bundle_workbench`, `WorkbenchOptions`, `localeChanged()` |
| Window system: modes, persistence, multi-instance TC + `Factory`, shipped layouts | editors, `admin.json` |
| Nodes: async `ChildFactory`, `Children.Keys`, properties, rename/destroy, popups | `FileNode`, `PartNode`, `SessionNode` |
| Views: `OutlineView`, `TreeView`, `PropertySheet` | Files, Server Status, Parts, Properties |
| Actions: context, always-enabled, checkbox, callback (Edit menu), shortcuts, toolbars, popups | Save, New, Spin rotor, Delete/Rename, `D-S`/`D-N`/`F5`, `Toolbars/*`, `Files/Actions` |
| Dialogs (async), notifications, status, options | New file, close question, Exception, High load, Saved, Tools ▸ Options |
| Charts: live append, categories, donut, export | Dashboard |
| Scene3D: binder, buffer geometry, pick → lookup, animation, snapshot, server push | Viewer |
| Multi-tab | Output and notifications in every tab; independent layouts |
| Testing | `HeadlessUI` tests per module, `e2e/workbench.js` |

## 9. Tests shipped with the sample

- `HeadlessUI` tests per module (`CoreTest`, `FilesTest`, `DashboardTest`, `ViewerTest`, `AdminTest`): open windows, inject events, answer dialogs, assert on the model, the files, the wire and the roles.
- `workbench/e2e/workbench.js`: logs in as `alice`, checks the branded Help menu and the six windows of the admin layout, opens `README.md`, edits, presses `Ctrl+S` and asserts the file on disk; creates a file with `Ctrl+N`; picks a blade in 3D and checks the tree and the pushed sensor labels; switches language and theme in Options; reloads and asserts the layout, the editors and the options were restored; opens Server Status; logs out; logs in as `bob` and asserts the admin entries are absent.
