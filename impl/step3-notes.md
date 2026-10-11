# WebBeans — Step 3 Implementation Notes (v0.1)

What was built for roadmap step 3 (the window system), how it maps to `design/window-system.md`, and where it deviates. Source: `webbeans-step3.zip` (62 JUnit tests; `webbeans-demo/e2e/e2e.js` drives Chromium through the whole dock).

## 1. What exists

| Module / package | Classes | Design § |
|---|---|---|
| `webbeans-windows` — `org.webbeans.windows` | `TopComponent` (extends `Panel`: layouts + `DISPLAY_NAME`, `HTML_DISPLAY_NAME`, `ICON`, `CLOSABLE`, `BUSY`, `MODIFIED`, `TC_ID`; `open/close/requestActive/requestVisible`, `isOpened/isShowing/isActive`, `getMode`, `associateLookup`, `writeState/readState`, lifecycle callbacks, `canClose`; annotations `Registration`, `Description`, `OpenActionRegistration`), `Mode` (+`Side`, `Constraints`, `Registration`, `Registrations`), `WindowManager` (+`Registry` with `PROP_OPENED/ACTIVATED/ACTIVATED_NODES/TC_OPENED/TC_CLOSED`), `TopComponentGroup`, `LayoutModel` (`Split`, `ModeNode`, floating, minimised, maximized, `tcState`, `closedTcModes`; placement by side, `splitBeside`, `removeMode` with split collapsing, JSON in/out), `WindowManagerImpl` (+`ModeImpl`, `GroupImpl`), `Dock`, `MainWindow`, `StatusBar`, `StatusDisplayer`, `MainWindowProvider`, `StandardModes` | §2–6, 8, 9 |
| `org/webbeans/windows/client/` | `dock.js`, `topcomponent.js`, `mainwindow.js`, `statusbar.js`, `windows.css` | §7 |
| `webbeans-windows-processor` | `WindowsAnnotationHandler` (`META-INF/services/org.webbeans.processor.spi.AnnotationHandler`) | §10 |
| `webbeans-ui` additions | `UI.bindShortcut(key, actionId, Runnable)` + `Root.SHORTCUT`; protected `Panel(rendererId, layout)` | — |
| `webbeans-demo` | `FormTopComponent` (editor), `AboutTopComponent` (editor), `EventsTopComponent` (output; session-bus `LogLine` subscriber), `PlanetsTopComponent` (explorer; opens multi-instance `PlanetTopComponent`s with the planet in their lookup), `PropertiesTopComponent` (properties; follows `Registry` events and `getGlobalContext()`), `DemoStatusCells` (`@Registration(path = "StatusBar")` factories: user, clock, locale, sign-out) | — |

Tests: `WindowManagerTest` (registry-built modes/components, direct executor, recording transport): start-up opens `openAtStartup` components into their modes and prunes empty non-permanent modes; open/activate/close callback order (`opened, showing` / `deactivated, activated` / `deactivated, hidden, closed` + reactivation) and `readState` on reopen; `canClose` veto; multi-instance ids `Doc#1/#2` and `PROP_ACTIVATED`; injected dock events (move with index, split into an anonymous mode, illegal moves ignored, resize validation, maximize toggle, float/dock, minimise/dock, close honouring `closable`); JSON round trip + restore in a second tab (anonymous mode, selection, `readState`); groups; unknown modes/components in a saved layout dropped. The Playwright script covers: default layout, tab activation, close/reopen, multi-instance windows + modified marker, drag into another mode, drag to an edge (split), gutter resize, maximize by double-click/button/API/shortcut, minimise + slide-in + dock, float via tab menu + dock, reload persistence, second tab, reset, then the step-2 form inside a window, locale switch (tooltips/status bar), reconnect, logout.

## 2. Package and shape

- One package `org.webbeans.windows` (design: `org.webbeans.api.windows` + `org.webbeans.core.windows`): the implementation needs package-private hooks on `TopComponent` (`setId`, `fireOpened`…), and a single package keeps those out of the public API without a second module. Implementation classes are `WindowManagerImpl`, `LayoutModel` (public: applications may inspect it, tests use it), `Dock`, `MainWindow`.
- `TopComponent extends Panel` (renderer `wb.TopComponent` delegates to the panel renderer), so `setLayout`, `setPadding`, `setScrollable`, `addRow` work as on any panel — the NetBeans `JComponent` feel.
- `TopComponent.getId()` clashes with `Component.getId()` (the protocol id); the window id is `getTopComponentId()` / `WindowManager.findTopComponentID(tc)`.
- The handler is its own module (`webbeans-windows-processor`): a module cannot be on its own processor path, and Maven resolves `annotationProcessorPaths` independently of dependencies.

## 3. Behaviour as implemented

- **Placement** (`LayoutModel.place`): LEFT/RIGHT modes go into the main row beside the centre and stack into a column with modes already on that side (ordered by `position`); TOP/BOTTOM wrap the root into a column; CENTER modes sit beside the existing centre modes. Non-permanent empty modes are pruned from the tree and recreated at their registered side when a component opens into them. Anonymous modes (`anonymousMode_n`) come from edge drops (`splitBeside`: same-direction parent → sibling with half the size, otherwise a new split) and floating drops; they vanish when empty.
- **Sizes** are pixels or `"*"` (share the rest); the client keeps stars as stars when dragging a gutter (two stars: the first becomes pixels), so the editor area keeps growing with the window.
- **Activation**: `requestActive` un-minimises and un-maximises as needed, selects the tab, fires `componentDeactivated/Activated`, retargets `getGlobalContext()` (a `ProxyLookup`) to the component's lookup and fires `PROP_ACTIVATED`. Clicking into a window's content activates it (client `activate` event on mousedown).
- **Ids**: singleton components (registered with a factory) use the `preferredID`; everything else (`@Description` without a no-arg constructor is registered with `multiInstance = true`, or unregistered classes) gets `preferredID#n`. Multi-instance components are not persisted in v1 (no `Factory` API yet, step 4); their `writeState` is still collected for `PERSISTENCE_ALWAYS/ONLY_OPENED` while open.
- **Persistence**: `layout.json` per user (`UserFiles.layoutFile()`), written 2 s after the last change and on UI close; `role-<r>.json` per role; `layouts/<name>.json` for `saveLayout/loadLayout`. Open components contribute `writeState` at save time; closed ones stored theirs when closing. `PERSISTENCE_NEVER` components and `#n` instances are stripped from the saved copy. Unknown modes/components in a saved file are dropped with a log line; components registered after the save are added through the `openAtStartup` pass.
- **Multiple tabs**: each tab restores the most recently saved layout and then evolves independently; last writer wins (as designed).
- **Keyboard**: `Ctrl+Shift+Enter` maximize/restore, `Alt+W` close, `Alt+Shift+←/→` cycle tabs; browsers reserve `Ctrl+W`/`Ctrl+Tab`, so the NetBeans keys could not be used.
- **Status bar**: `StatusDisplayer.getDefault().setStatusText()` (safe from any thread) and registry folder `StatusBar` for per-UI cells (`@Registration(path = "StatusBar", type = Component.class)` on a static factory method; `UI.current()`/`UISession.current()` are set while it runs).
- **Localisation**: dock labels (tooltips, tab menu, empty text) come from `org.webbeans.windows.Bundle` and are re-sent on `LocaleChangeEvent`; component display names are what the component set — re-setting them on locale change is the component's job until the `Localizable` hook (open point).

## 4. Deviations from the design

1. `@TopComponent.OpenActionRegistration` generates the registry entries (`Actions/Window/<id>` with `displayName`, `topComponent`, `kind = openTopComponent`; reference `Menu/Window/<id>`) but no action instance — the actions module (step 5) turns `kind = openTopComponent` entries into real actions. The demo's Properties window has buttons instead.
2. The tab context menu is rendered locally by `dock.js` (Close, Close others, Maximize/Restore, Float/Dock, Minimize); `TopComponent.getActions()` arrives with step 5 and will feed it.
3. `getActivatedNodes()` is empty until the Nodes API (step 4) marks node objects.
4. Floating windows are positioned `div`s inside the dock (the design's v1 scope); the design's `sliding` map became `minimised` with a side per mode.
5. `UIProvider` precedence is by `@ServiceProvider(position)`: `MainWindowProvider` is at 10000, so any application provider wins by default; `MainWindowProvider.build(ui)` is reusable from a custom provider.

## 5. Fixes to earlier steps found on the way

- `PollHandler` now runs `connect`/`handleEvents` through `ui.access(...)` so `UI.current()` (hence `WindowManager.getDefault()`, `StatusDisplayer.getDefault()`) is set in `UIProvider.init` and event handlers.
- Dock tab classes are prefixed `wb-dk-` because the `TabbedPane` already owned `.wb-tabs`/`.wb-tab`.
- Text areas in a border-layout centre cell now fill the cell.

## 6. Notes for step 4 (Nodes & Explorer)

- `TopComponent.Factory` for recreating multi-instance components from `writeState` (editors opened from nodes) — `WindowManagerImpl.findTopComponent` already refuses `#n` ids; the factory hook goes there and into `persistentCopy()`.
- `WindowManager.getActivatedNodes()` → `activated.getLookup().lookupAll(Node.class)` once `Node` exists; `ExplorerUtils.createLookup(em, actionMap)` feeds the component lookup, which `getGlobalContext()` already follows.
- A `Localizable` callback (or `TopComponent.setDisplayName(#KEY)` resolved per locale) so tabs relabel on locale change.
