# WebBeans — Window System (detailed design v0.3)

Roadmap step 3. JDK 8. **v0.3** (step 15): activated nodes separate from the global context, layout `*` invariant (§ Activation, end). **v0.2 reflects the implementation** (`webbeans-windows`, `webbeans-windows-processor`; notes in `impl/step3-notes.md`). Package: `org.webbeans.windows` (API and implementation; implementation classes are `WindowManagerImpl`, `LayoutModel`, `Dock`, `MainWindow`). Client renderers `dock.js`, `topcomponent.js`, `mainwindow.js`, `statusbar.js` + `windows.css`. Depends on step 1 (registry, Lookup) and step 2 (`UI`, component model, deltas).

## 1. Scope and NetBeans mapping

| NetBeans | WebBeans | Notes |
|---|---|---|
| `TopComponent` (a `JComponent`) | `TopComponent extends Panel` | same lifecycle callbacks and persistence types; panel layouts |
| `Mode` (`org.openide.windows.Mode`) | `Mode` | docking slot; registered with `@Mode.Registration` |
| `WindowManager` (singleton) | `WindowManager` (one per `UI`) | tabs have independent layouts |
| `TopComponentGroup` | `TopComponentGroup` | open/close a set together |
| `WindowManager.getRegistry()` | `WindowManager.getRegistry()` | opened set, activated TC, activated nodes |
| `Utilities.actionsGlobalContext()` | `WindowManager.getGlobalContext()` | follows the activated component's lookup |
| `.wsmode` / `.wstcref` / `.settings` XML | registry entries + `layout.json` | no XML |
| Swing `Mode.dockInto` drag & drop | client-side dock renderer, semantic events | |

## 2. TopComponent

```java
public abstract class TopComponent extends Panel {              // renderer wb.TopComponent
    public static final int PERSISTENCE_ALWAYS = 0, PERSISTENCE_ONLY_OPENED = 1, PERSISTENCE_NEVER = 2;

    // identity
    protected String preferredID();                       // @Description.preferredID, else the simple class name
    public final String getTopComponentId();              // preferredID, or preferredID#n for further instances (null before first open)
    public final WindowManager getWindowManager();

    // state
    public final void open();                             // registered mode, else the mode it was closed in, else "editor"
    public final void open(Mode mode);
    public final boolean close();                         // canClose() first; false if vetoed
    public final void requestActive();  public final void requestVisible();
    public final boolean isOpened();  public final boolean isActive();  public final boolean isShowing();
    public final Mode getMode();

    // display (tab)
    public final void setDisplayName(String n);  getDisplayName();
    public final void setHtmlDisplayName(String html);              // restricted HTML (b, i, em, strong, u, s, span, font, small)
    public final void setIcon(Resource icon);  public final void setToolTipText(String t);
    public final void setClosable(boolean b);                       // default true
    public final void makeBusy(boolean busy);                       // spinner on the tab
    public final void setModified(boolean m);                       // "modified" marker on the tab

    // lookup: Component.associateLookup(Lookup) — selection and capabilities; getLookup() = own over parent/UI/session

    // persistence
    public int getPersistenceType();                      // @Description.persistenceType, default ALWAYS
    protected void writeState(Map<String,Object> out) {}  // collected on save and on close
    protected void readState(Map<String,Object> in) {}    // before componentOpened() of a reopened component

    // lifecycle callbacks (session executor)
    protected void componentOpened() {}   protected void componentClosed() {}
    protected void componentShowing() {}  protected void componentHidden() {}
    protected void componentActivated() {} protected void componentDeactivated() {}
    protected boolean canClose() { return true; }

    @interface Registration { String mode(); boolean openAtStartup() default false; int position() default MAX; String[] roles() default {}; }
    @interface Description { String preferredID(); String iconBase() default ""; int persistenceType() default PERSISTENCE_ALWAYS; }   // RUNTIME
    @interface OpenActionRegistration { String displayName(); String preferredID() default ""; int position() default MAX; }
}
```

Rules, as in NetBeans:

- A `TopComponent` instance belongs to exactly one `UI`; it is attached to the component tree (as a child of the `Dock`, constraint = its id) when opened and detached when closed; it keeps its Java state while closed.
- Singleton components (`@Registration` + `@Description`, public no-arg constructor) are created on demand by `WindowManager.findTopComponent(preferredID)` from `Windows/Components/<id>`; their display name and icon default to the registry entry's.
- Multi-instance components (editors) are created by the application (`new EditorTC(file).open()`); they get ids `preferredID#1`, `#2`… A `@Description` without a no-arg constructor registers the id with `multiInstance = true` (described but not creatable). They are not persisted in v1; `TopComponent.Factory` (step 4) adds that.
- `getActions()` (tab menu) and `getUndoRedo()` arrive with step 5; the dock renders a local tab menu until then.
- Inside a `TopComponent` subclass the name `Registration` means `TopComponent.Registration`; refer to `org.webbeans.ui.Registration` (listener handles) by its qualified name.

## 3. Mode

```java
public interface Mode {
    enum Side { LEFT, RIGHT, TOP, BOTTOM, CENTER, FLOATING }
    final class Constraints { Side getSide(); int getDefaultSize(); boolean isCloseable(), isFloatable(), isMaximizable(), isPermanent(); }
    String getName();  Side getSide();  Constraints getConstraints();
    List<TopComponent> getTopComponents();  TopComponent getSelectedTopComponent();
    boolean dockInto(TopComponent tc);  boolean isAnonymous();

    @interface Registration { String name(); Side side(); int defaultSize() default 250; int position() default MAX;
        boolean closeable() default true; boolean floatable() default true; boolean maximizable() default true;
        boolean permanent() default false; String displayName() default ""; }
    @interface Registrations { Registration[] value(); }
}
```

`StandardModes` ships `explorer` (LEFT 260), `navigator` (LEFT 260, below explorer), `editor` (CENTER, permanent, not floatable), `properties` (RIGHT 300), `output` (BOTTOM 220). Registry: `Windows/Modes/<name>` (attrs `side, defaultSize, closeable, floatable, maximizable, permanent, displayName`, position) and `Windows/Modes/<name>/<tcId>` references (attr `openAtStartup`, position, roles). An unknown mode in `@TopComponent.Registration` is not a compile error (modes may come from other modules); at runtime the component falls back to `editor`.

## 4. WindowManager (per `UI`)

```java
public abstract class WindowManager {
    public static WindowManager getDefault();                 // UI.current().getLookup().lookup(WindowManager.class); null without a window system
    public abstract UI getUI();
    public abstract Mode findMode(String name);  public abstract Set<? extends Mode> getModes();  public abstract Mode findMode(TopComponent tc);
    public abstract TopComponent findTopComponent(String id);  public String findTopComponentID(TopComponent tc);
    public abstract Collection<TopComponent> getOpened();  public abstract TopComponent getActivated();
    public abstract Lookup getGlobalContext();                // ProxyLookup retargeted on activation
    public abstract Lookup getActivatedNodesContext();        // ProxyLookup of the last activated node-owning window (step 15)
    public Registry getRegistry();                            // PROP_OPENED, PROP_ACTIVATED, PROP_ACTIVATED_NODES, PROP_TC_OPENED, PROP_TC_CLOSED
    public abstract TopComponentGroup findTopComponentGroup(String name);
    public abstract void setRole(String role);  public abstract String getRole();
    public abstract void saveLayout(String name);  public abstract boolean loadLayout(String name);  public abstract void resetLayout();
    public abstract void invokeWhenUIReady(Runnable r);
    public abstract void setMaximized(TopComponent tc, boolean max);  public abstract boolean isMaximized(TopComponent tc);
    public abstract void setFloating(TopComponent tc, boolean floating);
    public abstract void setMinimized(TopComponent tc, boolean minimized);
    public abstract boolean closeAll(boolean force);
}
```

`WindowManagerImpl` (`install(UI)` publishes it in the UI lookup; `getDock()`; `restore()`; `bindShortcuts()`; `saveNow()`) owns:

- the **layout model** (§6), the source of truth for where every component lives;
- the **`Dock` component** (renderer `wb.Dock`), whose children are the open components and whose `layout`, `active`, `modes` and `labels` properties drive the renderer — modes and tab groups are not components;
- the **activation tracker**: `requestActive` → un-minimise/un-maximise if needed, select the tab, `componentDeactivated` on the old, `componentActivated` on the new, `PROP_ACTIVATED`, `getGlobalContext()` retargeted to `tc.getLookup()`.

## 5. MainWindow

`MainWindowProvider` (`@ServiceProvider(service = UIProvider.class, position = 10000)`, so an application's provider wins) builds for every new UI:

```
MainWindow (wb.MainWindow — stacked slots)
 ├─ [menubar]     filled by the actions module (step 5)
 ├─ [toolbar]     filled by the actions module (step 5)
 ├─ Dock          layout model → wb.Dock
 └─ StatusBar     StatusDisplayer text + cells from registry folder "StatusBar"
```

`MainWindowProvider.build(ui)` is reusable from a custom provider. `StatusBar` cells: `@Registration(path = "StatusBar", type = Component.class)` on a static factory method, instantiated per UI with `UI.current()` set. `StatusDisplayer.getDefault().setStatusText(..)` is safe from any thread.

Startup for a new UI: `install` → page built → `restore()`: modes from the registry → `layout.json` (or `role-<r>.json`, or an empty model) → unknown modes/components dropped → permanent modes placed → components listed in the layout opened in place (`readState` first) → registered `openAtStartup` components not yet open opened into their modes → selections restored → the saved `active` component (else the first centre one) activated → `invokeWhenUIReady` runnables.

## 6. Layout model and persistence

Identical in memory (`LayoutModel`), on the wire (dock `layout` property) and on disk:

```json
{ "version": 1, "role": "default", "active": "FormTC",
  "root": { "type": "split", "dir": "col", "sizes": ["*", 220], "children": [
      { "type": "split", "dir": "row", "sizes": [260, "*", 300], "children": [
          { "type": "mode", "name": "explorer", "selected": "PlanetsTC", "tcs": ["PlanetsTC"] },
          { "type": "mode", "name": "editor", "selected": "FormTC", "tcs": ["FormTC", "AboutTC"] },
          { "type": "mode", "name": "properties", "selected": "PropertiesTC", "tcs": ["PropertiesTC"] } ] },
      { "type": "mode", "name": "output", "selected": "EventsTC", "tcs": ["EventsTC"] } ] },
  "floating": [ { "type": "mode", "name": "anonymousMode_2", "x": 120, "y": 120, "w": 640, "h": 420, "tcs": ["LogTC"] } ],
  "maximized": null,
  "minimised": { "navigator": { "type": "mode", "name": "navigator", "tcs": ["NavTC"], "side": "left" } },
  "returnSides": { "anonymousMode_2": "CENTER" },
  "tcState": { "FormTC": { "note": "..." } },
  "closedTcModes": { "AboutTC": "editor" }
}
```

- Sizes are pixels or `"*"` (share the rest). Mode constraints come from the registry, not the file.
- Placement of a mode that is not in the tree (`LayoutModel.place`): LEFT/RIGHT into the main row beside the centre, stacked into a column with modes already on that side (by `position`); TOP/BOTTOM wrap the root into a column; CENTER beside the centre modes. Empty non-permanent modes are pruned and re-placed when a component opens into them; anonymous modes come from edge drops (`splitBeside`) and floating drops and vanish when empty.
- Only `PERSISTENCE_ALWAYS` components and `PERSISTENCE_ONLY_OPENED` ones that are open are saved; `PERSISTENCE_NEVER` and `#n` instances are stripped. `tcState` holds `writeState` output (collected on save for open components, on close for closed ones). `closedTcModes` remembers where a closed component was so `open()` returns it there.
- Persistence: every change marks the UI dirty; a 2 s debounce writes `users/<u>/layout.json` atomically; UI close forces it. With several tabs the last writer wins. `saveLayout/loadLayout` use `layouts/<name>.json`; `setRole(r)` saves the current role's file and loads `role-<r>.json`; `resetLayout()` rebuilds from registry defaults.

## 7. Client dock renderer (`wb.Dock`)

A single renderer (`dock.js`) owning the docking UI. It receives the layout tree as the `layout` property, mode constraints as `modes`, localised strings as `labels`, the activated id as `active`, and the components as children (constraint = id). On every layout change it rebuilds the structure and reparents the component elements (never recreated: editors keep scroll position and focus).

- Splits (flex with draggable gutters; a `"*"` pane stays `"*"` while dragging, two stars make the first one pixels), tab groups per mode (icon, display name / HTML display name, modified marker, busy spinner, close button, tools: minimise, maximize/restore, dock), the editor area (`wb-mode-center`), minimised side bars with slide-in popups, floating windows (title bar drag, corner resize, dock button), the maximized mode alone, a local tab context menu (Close, Close others, Maximize/Restore, Float/Dock, Minimize).
- Local interactions that do not round-trip: gutter dragging in progress, drag ghost and drop-zone highlight (centre / tab strip / four edges), floating move/resize in progress, slide-in open/close.
- Semantic events:

| Event | Data | Server reaction |
|---|---|---|
| `select` | `{mode, tc}` | `requestActive(tc)` (a clicked tab is activated) |
| `activate` | `{tc}` | `requestActive(tc)` (mousedown inside a window) |
| `close` | `{tc}` | `canClose()` then close, if the component is `closable` and the mode `closeable` |
| `move` | `{tc, mode, index}` \| `{tc, beside: mode, side}` \| `{tc, floating: {x,y,w,h}}` | move within/into a mode; split beside a mode into an anonymous mode; float (the whole mode if the component is alone, else a new anonymous floating mode) |
| `resize` (lazy 200 ms) | `{path, sizes}` | sizes stored after validating the split at `path` |
| `maximize` | `{mode}` | toggle |
| `float` (lazy 200 ms) | `{mode, x, y, w, h}` | float, or store the new geometry |
| `dock` | `{mode}` | a floating or minimised mode returns to its side |
| `minimize` | `{mode, side}` | into the side bar |

The server validates every event against the model and sends the **new layout tree** back as a `set` of `layout` (a no-op when nothing changed, as the property set compares maps).

Keyboard (`WindowManagerImpl.bindShortcuts()` through `UI.bindShortcut`): `Ctrl+Shift+Enter` maximize/restore the active window, `Alt+W` close it, `Alt+Shift+←/→` previous/next tab in its mode. Browsers reserve `Ctrl+W` and `Ctrl+Tab`.

## 8. Lookup and selection integration

- `WindowManager.getGlobalContext()` is a `ProxyLookup` retargeted on `PROP_ACTIVATED` to the activated component's lookup (own content over parent/UI/session). Context-aware actions (step 5) listen to it; the demo's Properties window shows the `Planet` found there.
- **Activated nodes** (step 15) are NetBeans' `TopComponent.Registry.getActivatedNodes()`: `getActivatedNodesContext()` is a `ProxyLookup` retargeted only when the newly activated window *owns* nodes — `TopComponent.setActivatedNodesOwner(Boolean)`: explicit, else an `ExplorerManager.Provider` (resolved by class name, the window system sits below the nodes module), else a lookup holding a `Node` at activation. Clicking the Properties or Output window therefore moves the global context (actions) but not the nodes; activating an explorer with nothing selected clears them; closing the owner clears them. `Registry.getActivatedNodes()` is the `Node`s of that context and `PROP_ACTIVATED_NODES` fires (old/new lists) through one `Lookup.Result` on the proxy, so selection changes of the owner are reported even while another window is active. The demo's `PropertiesTopComponent` binds its sheet to it.
- **Layout invariant** (step 15): every split keeps one `*` child; `LayoutModel.ensureStars()` restores it after a removal (closing the window that held the rest after a neighbour was resized), before every push and when a saved layout is applied.
- A component in a minimised mode is not showing and is never the active one; `requestActive` docks the mode back first.

## 9. Threading and lifecycle

- All `WindowManager` and `TopComponent` methods are called on the session executor (strict threading throws otherwise).
- `open()`: id assigned → placed in the mode node → attached to the dock → `readState` (if saved) → `componentOpened` → `componentShowing` (selected) → `componentActivated` (if `requestActive`). `close()`: `canClose` → `componentDeactivated` (if active) → `componentHidden` → state collected → `componentClosed` → detached; the neighbouring tab is selected and activated.
- Instances are per UI (`findTopComponent` caches per window manager); two tabs showing "Planets" are two instances sharing session-level data through the session lookup/bus.
- Closing a UI saves the layout (`UI` close listener) and disposes the tree.

## 10. Generated registry entries (`webbeans-windows-processor`)

| Annotation | Entries |
|---|---|
| `@Mode.Registration(name, side, …)` / `@Mode.Registrations` | `Windows/Modes/<name>` with attrs `side, defaultSize, closeable, floatable, maximizable, permanent[, displayName]`, position |
| `@TopComponent.Registration(mode, openAtStartup, position, roles)` + `@Description` | `Windows/Components/<preferredID>` (factory, `instanceOf TopComponent`, attrs `persistenceType[, iconBase]`, roles) and the reference `Windows/Modes/<mode>/<preferredID>` (attr `openAtStartup`, position, roles) |
| `@TopComponent.Description` alone | `Windows/Components/<preferredID>` (factory if there is a public no-arg constructor, else attr `multiInstance = true`) |
| `@TopComponent.OpenActionRegistration(displayName)` | `Actions/Window/<id>` (attrs `displayName`, `topComponent`, `kind = openTopComponent`) + reference `Menu/Window/<id>`; the actions module (step 5) materialises the action |
| `@TopComponentGroup.Registration(name, topComponents)` | `Windows/Groups/<name>` (attr `topComponents`) |

Both `webbeans-processor` and `webbeans-windows-processor` go on the module's annotation processor path (Maven `annotationProcessorPaths`, or `-processorpath` in `build.sh`).

## 11. Testing

- `WindowManagerTest`: registry built in the test (`RegistrationBuilder`), direct executor, recording transport — start-up placement, callback order, veto, multi-instance ids, injected dock events (via `UI.handleEvents`), JSON round trip + second-tab restore, groups, unknown entries dropped.
- `webbeans-demo/e2e/e2e.js` (Playwright): the full dock in Chromium including drag & drop, persistence across reload and a second tab.

## 12. Open points for step 4

- `TopComponent.Factory` (`createFromState(Map)`) so multi-instance editors reopen after a reload; defined with the Nodes API since editors are usually opened from nodes.
- `Localizable`: relabel tabs on `LocaleChangeEvent` without every component subscribing by hand (e.g. `setDisplayName("#CTL_Key")` resolved per locale).
- `Dock` as a general-purpose component (nested docks) — allowed by the renderer, not in v1 scope.
