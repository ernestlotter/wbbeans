# WebBeans — Actions, Menus, Toolbars, Shortcuts, Dialogs, Notifications, Options and Help (detailed design v0.3)

Roadmap step 5 (+ Options from step 8, Help from step 9). JDK 8. **v0.3 reflects the implementation** (`impl/step5-notes.md`, `impl/step8-notes.md`, `impl/step9-notes.md` list the deviations): packages `org.webbeans.actions`, `org.webbeans.dialogs`, `org.webbeans.notifications`, `org.webbeans.progress`, `org.webbeans.options` in module `webbeans-actions` (depends on `webbeans-windows`; `webbeans-nodes` depends on it), the annotation handlers in `webbeans-actions-processor`, client renderers `org/webbeans/actions/client/{menubar,menu,toolbar,dialog,notifications,progress}.js` + `actions.css`. The help system (§10) lives in its own module `webbeans-help` (depends on `webbeans-nodes`), with `HelpCtx` in `webbeans-ui`.

## 1. NetBeans mapping

| NetBeans | WebBeans | Notes |
|---|---|---|
| `javax.swing.Action`, `AbstractAction` | `Action`, `AbstractAction` | no Swing; `Action.NAME/ICON/…` keys, `actionPerformed(ActionEvent)` |
| `java.awt.event.ActionListener` (registered classes) | `org.webbeans.actions.ActionListener` | same constructor rules |
| `ContextAwareAction`, `Utilities.actionsGlobalContext()` | same, per `UI` | the global context is the activated window's lookup |
| `Actions.context/alwaysEnabled/callback/checkbox` + `@ActionRegistration` | same | registered actions are shared, lazily created |
| `Presenter.Menu/Toolbar/Popup` | same | returns a `Component` |
| `Utilities.actionsToPopup`, `NodeOp.findActions` | `Actions.actionsToPopup`, `NodeOp.findActions` | |
| `DialogDisplayer`, `NotifyDescriptor`, `DialogDescriptor`, `WizardDescriptor` | same, asynchronous (`CompletableFuture`) | wizard minimal |
| `NotificationDisplayer`, `StatusDisplayer`, `ProgressHandle` | same | session-wide |
| `NbPreferences.forModule` | `WbPreferences.forModule` | per user, flat files |
| `OptionsDisplayer`, `OptionsPanelController` | same (step 8) | one dialog, top-level categories only |
| `HelpCtx`, `HelpCtx.Provider`, `HelpCtx.Displayer`, JavaHelp helpsets | `org.webbeans.ui.HelpCtx` (+ `Provider`, `Displayer`), `@HelpPage` (step 9) | Markdown/HTML pages in module jars instead of JavaHelp |
| `Keymap`, `Shortcuts/` layer folder | `Shortcuts/` registry folder, client router | |
| layer folders with `displayName` | `@FolderRegistration` | menus, toolbars |

## 2. Action API

```java
public interface Action {
    String NAME, ICON, SHORT_DESCRIPTION, ACCELERATOR, ENABLED, SELECTED, ASYNC, NO_ICON_IN_MENU, MENU_TEXT, POPUP_TEXT;
    String getDisplayName();  Resource getIcon();  boolean isEnabled();  void setEnabled(boolean b);
    void actionPerformed(ActionEvent e);          // e.getLookup() = invocation context, e.getSource() = presenter, e.getUI()
    Object getValue(String key);  void putValue(String key, Object v);
    void addPropertyChangeListener(PropertyChangeListener l);  void removePropertyChangeListener(...);
}
public abstract class AbstractAction implements Action { … values map, PropertyChangeSupport, setDisplayName/setIcon/setSelected … }
public interface ActionListener { void actionPerformed(ActionEvent e); }
public interface ContextAwareAction extends Action { Action createContextAwareInstance(Lookup context); }
public interface Presenter { interface Menu { Component getMenuPresenter(); }  interface Toolbar {…}  interface Popup {…} }
public final class ActionMap { COPY, CUT, PASTE, DELETE, RENAME; put/get }
public final class Actions {
    static Action alwaysEnabled(ActionListener delegate, String displayName, Resource icon, boolean noIconInMenu);
    static <T> ContextAwareAction context(Class<T> type, boolean single, boolean surviveFocusChange, ContextPerformer<T> p, String displayName, Resource icon);
    static ContextAwareAction callback(String key, Action fallback, boolean surviveFocusChange, String displayName, Resource icon);
    static Action checkbox(Class<?> module, String prefKey, boolean defaultValue, ActionListener onToggle, String displayName);
    static Action forID(String category, String id);  static List<Action> forPath(String folder);  static Action actionFor(Entry e);
    static Action fromEntry(Entry e);                               // the processor's factory for ActionListener classes
    static void invoke(Action a, Component source, Lookup context); // logs + error notification on failure
    static Popup actionsToPopup(List<Action> actions, Lookup context, int x, int y);
}
public final class Utilities { static Lookup actionsGlobalContext(); static Lookup actionsGlobalContext(UI ui); static String keyToClient(String nbKey); static String keyToDisplay(String clientKey); }
```

- **Enablement** of a context action: the registered instance is a *template*; `isEnabled()` on it evaluates the current UI's global context on demand. Presenters bind it (`createContextAwareInstance(context)`) to a `Lookup.Result<T>` and fire `ENABLED` changes; enabled iff ≥1 (or exactly 1 when `single`) instance is present and the performer agrees. `surviveFocusChange=true` keeps the last non-empty selection when the lookup empties. Bound instances also forward the template's property changes and release their listeners when the presenter detaches.
- Presenter bindings re-read `isEnabled()` on *any* change of the context lookup's content, so actions that compute their state without firing events (explorer copy/paste) still track the selection.
- **Lazy loading**: the registry entry of an `ActionListener` class stores the class name and the constructor's context type; the class is loaded from the module loader on first use, context delegates are instantiated per invocation. `displayName`, `menuText`, `popupText` are resolved from the entry on every read, so one shared instance serves every session in its locale.
- **Asynchronous actions** (`asynchronous=true`): `actionPerformed` runs on `RequestProcessor("Actions")` with a `ProgressHandle` named after the action; UI access needs `e.getUI().access(...)`.
- **Callback actions** (`key = "copy"`): delegate to the `ActionMap` found in the context (the activated window's lookup); an abstract registered class means "no fallback" → disabled when the map has no entry. `ExplorerUtils.activateActions(em, map, true)` fills a map with the explorer's actions.

## 3. Registration (processor → registry)

```java
@ActionID(category = "Planets", id = "org.webbeans.demo.RemovePlanetsAction")
@ActionRegistration(displayName = "#CTL_RemovePlanets", iconBase = "…/delete.svg", lazy = true, asynchronous = false,
                    surviveFocusChange = false, key = "", menuText = "", popupText = "", noIconInMenu = false, roles = {})
@ActionReferences({
    @ActionReference(path = "Menu/Planets", position = 200, separatorAfter = 250),
    @ActionReference(path = "Toolbars/Planets", position = 200),
    @ActionReference(path = "Shortcuts", name = "D-R"),                 // NetBeans key syntax: D = Ctrl/Cmd, O = Alt, S = Shift
    @ActionReference(path = "Planets/Actions", position = 200)})         // a popup folder PlanetNode.getActions reads with Actions.forPath
public final class RemovePlanetsAction implements ActionListener {
    public RemovePlanetsAction(List<Planet> planets) { … }              // (T) → exactly one; (List<T>) → one or more; () → always enabled
    public void actionPerformed(ActionEvent e) { … }
}
@FolderRegistration(path = "Menu/Planets", displayName = "#CTL_PlanetsMenu", position = 250)   // the menu itself
```

Generated entries: `Actions/Planets/org-webbeans-demo-RemovePlanetsAction` (factory `Actions::fromEntry`, `instanceOf Action`, attributes `displayName`, `iconBase`, `delegate`, `type`, `selectionType = EXACTLY_ONE|ANY`, `kind = callback` + `key`, `asynchronous`, `surviveFocusChange`, `noIconInMenu`, `menuText`, `popupText`, roles), references under `Menu/…`, `Toolbars/…`, `Shortcuts/D-R`, `Planets/Actions`, plus separator entries. An `Action` class (public no-arg constructor) is registered directly (`supplier`), and may implement `Presenter.*`. Nested classes are recorded by their binary name (`Outer$Inner`). Compile errors: missing `@ActionID`/`displayName`, two public constructors, a constructor parameter that is neither a class nor `List<T>`, an abstract `ActionListener` without `key`, a concrete callback fallback without a no-arg constructor.

Node popups from the registry: `AbstractNode.getActions` appends `Actions.forPath("<Type>/Actions")` — the session-filtered view, so unauthorised actions never appear; `NodeOp.findActions` intersects multi-selections by identity, which the cached entry instances satisfy.

## 4. Menu and toolbar components

```java
public class MenuBar extends Container          // wb.MenuBar — children: Menu; findMenu(text)
public class Menu extends Container             // wb.Menu — children: MenuItem | Menu | Separator | any; props text, icon; event open; setOnOpen (lazy), invalidate()
public class MenuItem extends Component         // wb.MenuItem — props text, icon, accelerator, selected, kind (ITEM/CHECK/RADIO); event action; setAction(action, context, popup)
public class Separator extends Component        // wb.Separator
public class Popup extends Container            // wb.Popup — overlay, show(x, y), event close → detaches itself; getContext()
public class ToolbarPanel extends Container     // wb.ToolbarPanel — children: Toolbar
public class Toolbar extends Container          // wb.Toolbar — prop name; children: ToolbarButton | Separator | any
public class ToolbarButton extends Component    // wb.ToolbarButton — props icon, text, selected (+ tooltip); event action; setAction(action, context)
```

- `MenuBarBuilder.build(context)` / `ToolbarBuilder.build(context)` read the registry folders `Menu/` and `Toolbars/` for the current session (role-filtered, localised, overlay applied): every sub-folder is a menu/toolbar (`displayName`, `position` from `@FolderRegistration`), entries and sub-folders are merged by position, separator entries become `Separator`s (collapsed, never leading/trailing), a declared menu without visible content is skipped. For each entry the builder uses the action's presenter (`Presenter.Menu/Toolbar`) or a default `MenuItem`/`ToolbarButton` bound by `ActionPresenters.Binding`; entries whose instance is a `Component` are placed as they are.
- **Binding** (`ActionPresenters.Binding`): listens to the (bound) action's properties and to the context lookup; applies text (`menuText`/`popupText`), icon (unless `NO_ICON_IN_MENU`), accelerator (display form), selected/kind, enabled; the presenter's `action` event runs `Actions.invoke(action, presenter, context)`. Starts on attach, stops on detach (disposing the bound instance).
- **Laziness**: top-level menus and submenus are created without children and filled on the first `open` event (`Menu.setOnOpen`); `ToolbarsMenuAction` refills on every open.
- **Rebuilds**: the `ActionsDecorator` (a `MainWindowDecorator` service) installs the bars at login, rebuilds both on `UISession.LocaleChangeEvent`, and the toolbars on a user-overlay change. View ▸ Toolbars writes `Toolbars/<name>` hides into the overlay (`users/<u>/config/registry.json`).

## 5. Popups

`Actions.actionsToPopup(actions, context, x, y)` → a `Popup` attached to the UI's overlay (modal-exempt), filled with bound items (`null` = separator, `Presenter.Popup` substitutes its component), shown with `call("show", x, y)` in the same flush; the client emits the item's `action` and then `close`; the server runs the action with the popup's context and removes the popup. Node views use it for the context menu (context = clicked nodes + their lookups + the window's `ActionMap` + the UI lookup, not the window's current selection, which may lag behind a right-click).

## 6. Keyboard shortcuts

- Registry folder `Shortcuts/<key>` → reference to an action. Key syntax is NetBeans' (`D-S`, `DS-Z`, `O-F4`, `DELETE`, `F2`, `F1`, `D-UP`); `Utilities.keyToClient` turns it into the client form (`Ctrl+Shift+Z`, `Alt+F4`, `Delete`, `Ctrl+ArrowUp`); `D` is Ctrl on Windows/Linux and Cmd on macOS because the client's router treats Ctrl and Meta alike.
- `Shortcuts.install(ui)` binds every visible entry with `UI.bindShortcut(key, actionPath, runnable)` — the existing `keymap` property of the root and the `shortcut` event — and records the key as `Action.ACCELERATOR` so menus and tooltips show it; the runnable binds the action to the UI's global context, checks `isEnabled()` and invokes it.
- The client router keeps plain keys and `Ctrl+C/V/X/A/Z/Y` local while an editable element has the focus and ignores every shortcut while a modal dialog is open; the server drops the event anyway (modal guard). Inside a dialog, `F1` presses the dialog's Help button client-side (§10). `TopComponent`-local input maps and keymap profiles are not implemented.

## 7. Dialogs

```java
public abstract class DialogDisplayer {
    public static DialogDisplayer getDefault();  public static DialogDisplayer getDefault(UI ui);   // one per UI, in its lookup
    public abstract CompletableFuture<Object> notify(NotifyDescriptor d);   // completes on the session executor with the option chosen
    public abstract Dialog createDialog(DialogDescriptor d);                // show()/hide()/dispose()/setTitle()/getResult()
}
public class NotifyDescriptor { OK/CANCEL/YES/NO/CLOSED_OPTION (localised Option objects); DEFAULT/YES_NO/YES_NO_CANCEL/OK_CANCEL_OPTION;
    ERROR/WARNING/INFORMATION/QUESTION/PLAIN_MESSAGE; message (String or Component), title, options, initialValue, value, setValid, createNotificationLineSupport,
    getHelpCtx/setHelpCtx (PROP_HELP_CTX: a Help button while not DEFAULT_HELP),
    property changes (PROP_VALID, PROP_TITLE, PROP_MESSAGE, PROP_OPTIONS, PROP_HELP_CTX, PROP_*_NOTIFICATION);  static classes Message, Confirmation, InputLine (getInputText), Exception }
public class DialogDescriptor extends NotifyDescriptor { modal, optionsAlign, buttonListener (ActionListener with the option label as command), closingOptions, resizable;
    the NetBeans-shaped constructor (content, title, modal, options, initialValue, optionsAlign, HelpCtx, buttonListener) }
public final class WizardDescriptor<D> extends DialogDescriptor { Panel<D> (getComponent, getName, isValid, read/storeSettings, change listeners), AbstractPanel, Iterator, ArrayIterator, InstantiatingIterator, FINISH/NEXT/PREVIOUS_OPTION, properties (PROP_ERROR_MESSAGE) }
```

- Dialogs are **never blocking**: `notify` returns a `CompletableFuture` completed when the user picks an option (or `CLOSED_OPTION` on Escape / the close button); continuations (`thenAccept`, not `thenAcceptAsync`) run on the session executor.
- The `DialogComponent` (`wb.Dialog`: title bar with close button, side slot, content slot, notification line, button bar of `Button`s with test ids `dialog-<option>`, a `help` slot at the left of the bar (`dialog-help`), modal backdrop, draggable, resizable, `Esc`/`Enter`/`F1`, focus trap) is attached to the UI root's overlay layer. Modal dialogs call `UI.pushModal`; `UI.handleEvents` drops events from components that are neither inside the newest modal component nor registered with `UI.allowWhileModal` (popups, the notification host) — defence against a stale client.
- `setValid(false)` disables OK/Yes/the default button (Next/Finish in wizards); `NotificationLineSupport` shows info/warning/error under the content; both are property deltas on the dialog. `setHelpCtx` adds or removes the Help button at any time (the Options dialog changes it per category).
- Wizard: the displayer's controller swaps the content per step, lists the steps on the left (`Html`), drives Back/Next/Finish from `isValid()` and change events, shows `PROP_ERROR_MESSAGE`, calls `instantiate()` on Finish (an `IOException` keeps the dialog open with the message).

## 8. Notifications, status bar, progress

```java
public abstract class NotificationDisplayer {   // one per session (UISession attribute); getDefault() / getDefault(session)
    enum Priority { HIGH, NORMAL, LOW, SILENT }  enum Category { INFO, WARNING, ERROR }
    Notification notify(String title, Resource icon, String details, ActionListener onClick, Priority p, Category c);
    List<Notification> getNotifications();  void clearAll();  void attach(UI ui);
}
public interface Notification { getTitle, getDetails, getPriority, getCategory, clear }
public final class ProgressHandle { start(); start(int units); progress(int); progress(String); progress(String, int); switchToIndeterminate(); switchToDeterminate(int); setDisplayName; finish(); cancel(); isCancelled… }
public final class ProgressHandleFactory { createHandle(name); createHandle(name, Cancellable); createHandle(name, Cancellable, UISession) }
public interface Cancellable { boolean cancel(); }
```

- Notifications are session-scoped: delivered to **every attached tab** (toast + the drawer behind the status bar bell; `HIGH` toasts stay, `SILENT` ones go to the drawer only), kept (bounded, 100) for tabs opened later, cleared everywhere by `clear()`/`clearAll()`/dismissing. Click handlers run on the clicking tab. Component balloons and the `NotificationSink` SPI are not implemented.
- `StatusBar` cells contributed by the actions module: `StatusBar/progress` (`ProgressStatusCell`: the newest running handle's bar and message, `+n` for further tasks, a cancel button for `Cancellable` handles, a popup list of all running tasks) and `StatusBar/notifications` (the bell with the count).
- `ProgressHandle` registers with the session's `ProgressRegistry` on `start` and may be created and updated on any thread; cells coalesce updates (≤10/s per tab) through `ui.schedule`.

## 9. Options (step 8, as built)

`OptionsPanelController` (abstract `getComponent(Lookup)`, `update`, `applyChanges`, `cancel`, `isValid`, `isChanged`, `getHelpId`, `changed()` + change listeners) registered with `@OptionsPanelController.TopLevelRegistration(id, categoryName, iconBase, keywords, position, roles)` → `OptionsDialog/<id>`; `OptionsDisplayer.getDefault().open([categoryId])` → `CompletableFuture<Boolean>`; `DefaultOptionsDisplayer` builds one `DialogDescriptor` with a category strip (LINK buttons) and a `Layout.Stack` of pages created on first visit, OK / Apply / Cancel (closing options OK and Cancel), OK following every visited controller's `isValid()`, and the dialog's `HelpCtx` following the selected category's `getHelpId()`. `OptionsAction` registers Tools ▸ Options (`Menu/Tools`, position 9000). `WbPreferences.forModule(Class)` / `forModule(session, Class)` / `forNode(session, id)`: flat per-user `java.util.prefs.Preferences` on `users/<u>/config/preferences/<package>.properties`, written through immediately; `Actions.checkbox` reads/writes it. Not implemented: sub-registrations (nested categories), the system-level fallback node, keyword search in the dialog.

## 10. Help (step 9, as built)

**API (`webbeans-ui`)**: `org.webbeans.ui.HelpCtx(String id)` with `DEFAULT_HELP` (empty id = the home page), `getHelpID()`, `isDefault()`, `display()`; `HelpCtx.Provider` (`getHelpCtx()`) implemented by `TopComponent` (`setHelpCtx`, else `@TopComponent.Description(helpId)`, else default); `HelpCtx.Displayer` service looked up in `Lookup.getDefault()`. `NotifyDescriptor.setHelpCtx` (§7); `OptionsPanelController.getHelpId` (§9). `PageProvider` (`getPrefix()`, `render(session, basePath, path, query)`): a service the HTTP front end routes to for signed-in users, rendered on the session executor — the help module's stand-alone page uses it, the server stays independent of the help module.

**Pages (`webbeans-help`)**: `@HelpPage(id, title, resource, parent, position, keywords, roles)` (repeatable; on classes or `package-info.java`) → `Help/Pages/<id>` with attributes `displayName`, `resource`, `parent`, `keywords`, handled by `HelpAnnotationHandler` in `webbeans-actions-processor`. A resource is a Markdown (`.md`) or HTML fragment in the module jar; `name_<lang>.ext` next to it is the localised variant. `HelpSet.current()` builds the role-filtered, localised table of contents from the registry (`Topic`: id, title, resource, parent/children, keywords, `getPath()`; `getRoots()`, `getHome()` = first top-level topic, `find`, `byResource`, `search(query, locale)` — title/keyword hits first, then text hits). `HelpContent.html(set, topic, locale)`: load → `Markdown.toHtml` (headings with slug ids, paragraphs, fenced code, inline code, bold/italic/strike, links, images, auto-links, nested lists, block quotes, rules, pipe tables, raw HTML blocks and inline tags) → relative links resolved (`other.md` → `help:<id>` of the page with that resource, `img.png` → `wb/res/<path>`) → `Html.sanitize` (allow-list extended by `img`, `id`, `alt`, `title`, `kbd`, `dl/dt/dd`, `h5/h6`, `div`, `caption`; `help:` hrefs allowed).

**Window**: `HelpTopComponent` (`HelpTC`, its own `help` mode on the right, `PERSISTENCE_ONLY_OPENED`, `helpId = "platform"`): a bar (Contents, Back, search field, "Open in new tab" `Link` to `wb/help/<id>`) over a `Layout.Stack` of the table of contents (a `TreeView` over `HelpNode`s, fully expanded; search results as a flat list) and the page (breadcrumbs + `HelpViewer`). `HelpViewer` (`wb.HelpViewer`): sanitised HTML with help typography, `help:`/`#` link clicks come back as `navigate` events, other links open in a new tab, `scrollTo(anchor)`. `show(HelpCtx)`: topic page (with `#anchor` support), home for the default context, contents + status text for an unknown id; `localeChanged` rebuilds the set and re-renders. `HelpDisplayer` (the `HelpCtx.Displayer` service) opens the window of the current UI. Actions: `HelpContentsAction` (Help ▸ Help Contents, position 10, separator after) shows the table of contents; `ContextHelpAction` (`Shortcuts/F1`) shows the active window's `getHelpCtx()` or the home page. `HelpPageProvider` serves `GET /wb/help/<id>` as a complete document (TOC `<nav>`, breadcrumbs, article; `help:` links → page links; light/dark CSS) with the same filtering. The module ships one page, `platform` ("Getting around"), in English and German.

**Node views** gained expansion memory on the way: `NodeView` remembers expanded nodes (user toggles, `expandNode`, `reveal`) and re-expands them after every rebuild (root change, reconnect, re-attach), so trees survive a resync; `TreeView.expandNode` on a not-yet-sent node takes effect once its parent's children arrive.

Not implemented: help ids on actions, a search index (search reads page texts on demand), a sub-registration for nested categories of the Options dialog, printing beyond the browser's own print of the stand-alone page.

## 11. Threading summary

| Call | Thread |
|---|---|
| `actionPerformed` (sync action) | session executor, inside the client-event task |
| `actionPerformed` (async action) | `RequestProcessor("Actions")`; UI via `e.getUI().access` |
| `DialogDisplayer.notify` future completion, `DialogDescriptor` button listener, wizard panel callbacks | session executor |
| `NotificationDisplayer.notify`, `ProgressHandle.*`, `StatusDisplayer.setStatusText`, `NotifyDescriptor.setValid` | any thread (internally hops) |
| `HelpCtx.display()`, `HelpSet.current()`, `OptionsDisplayer.open` | session executor (a UI must be current) |
| `PageProvider.render` | session executor (the HTTP thread waits, 10 s) |
| presenter bindings (`Lookup`/property listeners) | any thread; applied inline on the session thread, else via `ui.access` |

## 12. Generated registry entries

| Annotation | Entries |
|---|---|
| `@ActionID` + `@ActionRegistration` | `Actions/<category>/<id>` (dots → dashes) |
| `@ActionReference(path, position, separatorBefore/After, name)` | reference under `path` (+ separators); `Shortcuts/<name>` for key references |
| `@FolderRegistration(path, displayName, position, attrs)` | folder with attributes (menus `Menu/<name>`, toolbars `Toolbars/<name>`, anything else) |
| `@TopComponent.OpenActionRegistration` (step 3) | `Actions/Window/<id>` (`kind = openTopComponent`) + `Menu/Window/<id>` — `Actions.actionFor` turns it into an `OpenTopComponentAction` |
| `@Registration(path = "StatusBar", type = Component.class)` | `StatusBar/<name>` |
| `@OptionsPanelController.TopLevelRegistration` | `OptionsDialog/<id>` (attrs `displayName`, `iconBase`, `keywords`; roles) |
| `@HelpPage` / `@HelpPages` | `Help/Pages/<id>` (attrs `displayName`, `resource`, `parent`, `keywords`; roles) |

## 13. Testing

Implemented in `ActionsTest` and `DialogsTest` (`webbeans-actions`), on the headless `TestUI` fixture (registry with modes, two windows, menu/toolbar/shortcut entries, the real `MainWindowProvider` + `ActionsDecorator`, recorded transport per tab), and in `HelpTest` (`webbeans-help`, on `HeadlessUI`):

- Action enablement: `Lookup.Result` changes → `enabled` property events; `surviveFocusChange`; multi vs single; callback over an `ActionMap`; template disabling.
- Registry: `fromEntry` kinds and caching; `forPath` with separators.
- Builders: menu order and lazy fill, accelerators, enablement following the activated window's lookup, the Window menu's open action; toolbar buttons following the selection; overlay hide/show; locale switch rebuild.
- Shortcuts: `keymap` payload, dispatch, disabled actions ignored.
- Popups: items bound to the popup context, `show` call, self-removal on `close`.
- Dialogs: future completion on the executor; modal event rejection; Escape; `InputLine` + `setValid` + notification line; `DialogDescriptor` button listener + closing options + `createDialog`; wizard navigation and settings.
- Notifications fan-out to two tabs from a background thread, click routing, dismissal, drawer init for a later tab; progress handle → status cell, newest first, cancel.
- Help: the Markdown subset, localised page variants, the table of contents from the registry with roles, `display` → window + page with rewritten links, missing/unknown pages, F1 vs. Help Contents, the dialog Help button appearing and disappearing with the context, search, the stand-alone page, re-expansion after a restore.
- Browser: `webbeans-demo/e2e/actions.js`, `workbench/e2e/help.js`.

## 14. Open points

- `WizardDescriptor` stays minimal (fixed `ArrayIterator`; the asynchronous/progress instantiating iterators can follow).
- Whether `Actions.forID` should be exposed to client-side JS for renderer-initiated actions (e.g. a chart renderer's toolbar) — leaning yes via a generic `action` event on the `MainWindow` with the registry id, role-checked server-side.
- Package-level `@ActionReference` to other modules' actions by id; per-button toolbar customisation and reordering; keymap profiles; `TopComponent.getActions()` tab popups through `actionsToPopup`.
- Options sub-categories and keyword search; help ids on actions and a prebuilt search index.
