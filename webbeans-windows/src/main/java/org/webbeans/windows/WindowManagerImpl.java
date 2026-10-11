package org.webbeans.windows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.core.files.UserFiles;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.json.Json;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Folder;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/**
 * The window manager of one {@link UI}: owns the {@link LayoutModel}, the {@link Dock} and the open
 * {@link TopComponent}s; translates dock events into model changes; persists the layout per user.
 *
 * <p>Install with {@link #install(UI)} (done by {@link MainWindowProvider}), put {@link #getDock()} somewhere
 * in the page, then call {@link #restore()} once the page is built.
 */
public final class WindowManagerImpl extends WindowManager {

    private static final Logger LOG = Logger.getLogger(WindowManagerImpl.class.getName());
    static final String MODES_PATH = "Windows/Modes";
    static final String COMPONENTS_PATH = "Windows/Components";
    static final String GROUPS_PATH = "Windows/Groups";
    static final String DEFAULT_MODE = "editor";
    private static final long SAVE_DELAY_MS = 2000;

    private static final class GlobalContext extends ProxyLookup {
        void retarget(Lookup l) {
            setLookups(l == null ? new Lookup[0] : new Lookup[] {l});
        }
    }

    private final UI ui;
    private final Dock dock = new Dock();
    private final UserFiles files;
    private LayoutModel layout = new LayoutModel();
    private final Map<String, ModeImpl> modes = new LinkedHashMap<String, ModeImpl>();
    private final Map<String, TopComponent> components = new LinkedHashMap<String, TopComponent>();
    private final Map<String, GroupImpl> groups = new LinkedHashMap<String, GroupImpl>();
    private final GlobalContext globalContext = new GlobalContext();
    private TopComponent activated;
    private boolean ready;
    private final List<Runnable> whenReady = new ArrayList<Runnable>();
    private UI.Timer saveTimer;
    private int anonymousCounter;

    private WindowManagerImpl(UI ui) {
        this.ui = ui;
        this.files = ui.getSession().getUserFiles();
        wireDockEvents();
        ui.getSession().subscribe(UISession.LocaleChangeEvent.class, new Consumer<UISession.LocaleChangeEvent>() {
            @Override
            public void accept(UISession.LocaleChangeEvent e) {
                pushLabels();
                pushModes();
                for (TopComponent tc : new ArrayList<TopComponent>(getOpened())) {
                    try {
                        tc.fireLocaleChanged();
                    } catch (RuntimeException ex) {
                        LOG.log(Level.WARNING, "localeChanged failed for " + tc, ex);
                    }
                }
            }
        });
        ui.addCloseListener(new Runnable() {
            @Override
            public void run() {
                saveNow();
            }
        });
    }

    /** Creates the window manager for the UI and publishes it in the UI lookup. One per UI. */
    public static WindowManagerImpl install(UI ui) {
        WindowManager existing = ui.getLookup().lookup(WindowManager.class);
        if (existing instanceof WindowManagerImpl) {
            return (WindowManagerImpl) existing;
        }
        WindowManagerImpl wm = new WindowManagerImpl(ui);
        ui.addToLookup(wm);
        wm.pushLabels();
        return wm;
    }

    /** The docking component; place it in the page. */
    public Dock getDock() {
        return dock;
    }

    LayoutModel getLayout() {
        return layout;
    }

    // ---- startup / restore ---------------------------------------------------------------------------------------

    /** Loads the registered modes and the user's layout (or the defaults), opens the components, activates one. */
    public void restore() {
        loadModes();
        LayoutModel saved = readLayout(files == null ? null : files.layoutFile());
        if (saved == null) {
            saved = shippedLayout("default");
        }
        applyLayout(saved != null ? saved : new LayoutModel(), true);
        ready = true;
        List<Runnable> rs = new ArrayList<Runnable>(whenReady);
        whenReady.clear();
        for (Runnable r : rs) {
            r.run();
        }
    }

    private void loadModes() {
        modes.values().removeIf(new java.util.function.Predicate<ModeImpl>() {
            @Override
            public boolean test(ModeImpl m) {
                return !m.isAnonymous();
            }
        });
        for (Entry e : org.webbeans.core.registry.Registry.getDefault().folder(MODES_PATH).getEntries()) {
            Entry t = e.getTarget();
            if (t == null) {
                continue;
            }
            Mode.Side side = Mode.Side.CENTER;
            String s = t.getString("side");
            if (s != null) {
                try {
                    side = Mode.Side.valueOf(s);
                } catch (IllegalArgumentException ex) {
                    LOG.log(Level.WARNING, "Mode {0}: unknown side {1}", new Object[] {t.getName(), s});
                }
            }
            Mode.Constraints c = new Mode.Constraints(side, intAttr(t, "defaultSize", 250), boolAttr(t, "closeable", true),
                    boolAttr(t, "floatable", true), boolAttr(t, "maximizable", true), boolAttr(t, "permanent", false));
            ModeImpl m = new ModeImpl(this, t.getName(), c, false, t.getDisplayName());
            modes.put(m.getName(), m);
            layout.setModeInfo(m.getName(), side, t.getPosition());
        }
        if (!modes.containsKey(DEFAULT_MODE)) {
            ModeImpl m = new ModeImpl(this, DEFAULT_MODE, new Mode.Constraints(Mode.Side.CENTER, 0, true, false, true, true), false, "Editor");
            modes.put(DEFAULT_MODE, m);
            layout.setModeInfo(DEFAULT_MODE, Mode.Side.CENTER, 0);
        }
        pushModes();
    }

    private static int intAttr(Entry e, String name, int def) {
        Object v = e.getAttributes().get(name);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException ex) {
                return def;
            }
        }
        return def;
    }

    private static boolean boolAttr(Entry e, String name, boolean def) {
        Object v = e.getAttributes().get(name);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }
        return def;
    }

    /**
     * Replaces the layout: closes everything open (forced), adopts the new model (dropping unknown modes and
     * components), places permanent modes, opens the listed components plus the registered start-up ones.
     */
    private void applyLayout(LayoutModel model, boolean includeStartup) {
        for (TopComponent tc : new ArrayList<TopComponent>(getOpened())) {
            closeInternal(tc, true, false);
        }
        model.sides.putAll(layout.sides);
        model.positions.putAll(layout.positions);
        layout = model;
        // anonymous modes present in the layout
        for (LayoutModel.ModeNode n : layout.allModes()) {
            if (!modes.containsKey(n.name)) {
                if (n.name.startsWith("anonymousMode_")) {
                    modes.put(n.name, new ModeImpl(this, n.name, anonymousConstraints(), true, null));
                    try {
                        anonymousCounter = Math.max(anonymousCounter, Integer.parseInt(n.name.substring("anonymousMode_".length())));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                } else {
                    LOG.log(Level.INFO, "Dropping unknown mode {0} from the saved layout", n.name);
                    layout.removeMode(n);
                }
            }
        }
        // permanent modes always exist
        for (ModeImpl m : modes.values()) {
            if (m.getConstraints().isPermanent()) {
                ensureModeNode(m.getName());
            }
        }
        // components listed in the layout
        List<String> ids = layout.allTcs();
        Map<String, String> wanted = new LinkedHashMap<String, String>();
        for (LayoutModel.ModeNode n : layout.allModes()) {
            for (String id : new ArrayList<String>(n.tcs)) {
                wanted.put(id, n.name);
            }
            n.tcs.clear();
        }
        Map<String, String> selected = new LinkedHashMap<String, String>();
        for (LayoutModel.ModeNode n : layout.allModes()) {
            if (n.selected != null) {
                selected.put(n.name, n.selected);
            }
            n.selected = null;
        }
        String active = layout.active;
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            TopComponent tc = findTopComponent(e.getKey());
            if (tc == null) {
                LOG.log(Level.INFO, "Dropping unknown component {0} from the saved layout", e.getKey());
                continue;
            }
            openInternal(tc, e.getValue(), false);
        }
        if (includeStartup) {
            for (Entry ref : startupComponents()) {
                String id = ref.getName();
                if (components.containsKey(id) && components.get(id).isOpened()) {
                    continue;
                }
                if (ids.contains(id)) {
                    continue; // was in the layout but could not be created
                }
                TopComponent tc = findTopComponent(id);
                if (tc != null) {
                    openInternal(tc, ref.getFolderPath().substring(MODES_PATH.length() + 1), false);
                }
            }
        }
        // restore selections
        for (LayoutModel.ModeNode n : layout.allModes()) {
            String sel = selected.get(n.name);
            if (sel != null && n.tcs.contains(sel)) {
                selectInMode(n, sel);
            } else if (!n.tcs.isEmpty() && n.selected == null) {
                selectInMode(n, n.tcs.get(0));
            }
        }
        pruneEmptyModes();
        TopComponent act = active != null ? components.get(active) : null;
        if (act == null || !act.isOpened()) {
            act = firstCentreComponent();
        }
        if (act != null) {
            requestActive(act);
        } else {
            layout.active = null;
            dock.set(Dock.ACTIVE, null);
        }
        push();
    }

    private TopComponent firstCentreComponent() {
        for (LayoutModel.ModeNode n : layout.allModes()) {
            Mode.Side s = layout.sideOf(n);
            if (s == Mode.Side.CENTER && n.selected != null) {
                return components.get(n.selected);
            }
        }
        for (TopComponent tc : getOpened()) {
            return tc;
        }
        return null;
    }

    /** Component references with {@code openAtStartup}, in mode/position order. */
    private List<Entry> startupComponents() {
        List<Entry> out = new ArrayList<Entry>();
        for (Folder modeFolder : org.webbeans.core.registry.Registry.getDefault().folder(MODES_PATH).getFolders()) {
            for (Entry ref : modeFolder.getEntries()) {
                if (boolAttr(ref, "openAtStartup", false)) {
                    out.add(ref);
                }
            }
        }
        return out;
    }

    private static Mode.Constraints anonymousConstraints() {
        return new Mode.Constraints(Mode.Side.CENTER, 250, true, true, true, false);
    }

    @Override
    public void invokeWhenUIReady(Runnable r) {
        if (ready) {
            r.run();
        } else {
            whenReady.add(r);
        }
    }

    // ---- lookup of modes and components -------------------------------------------------------------------------

    @Override
    public UI getUI() {
        return ui;
    }

    @Override
    public Mode findMode(String name) {
        return modes.get(name);
    }

    @Override
    public Set<? extends Mode> getModes() {
        return new LinkedHashSet<ModeImpl>(modes.values());
    }

    @Override
    public Mode findMode(TopComponent tc) {
        return tc.getMode();
    }

    @Override
    public TopComponent findTopComponent(String id) {
        TopComponent tc = components.get(id);
        if (tc != null) {
            return tc;
        }
        if (id.contains("#")) {
            // multi-instance: only a registered Factory can recreate it from its saved state
            Entry reg = org.webbeans.core.registry.Registry.getDefault().entry(COMPONENTS_PATH + "/" + id.substring(0, id.indexOf('#')));
            Map<String, Object> state = layout.tcState.get(id);
            TopComponent created = reg == null || state == null ? null : createFromFactory(reg, state);
            if (created == null) {
                return null;
            }
            created.setManager(this);
            created.setId(id);
            components.put(id, created);
            return created;
        }
        Entry e = org.webbeans.core.registry.Registry.getDefault().entry(COMPONENTS_PATH + "/" + id);
        if (e == null) {
            return null;
        }
        tc = e.newInstance(TopComponent.class);
        if (tc == null) {
            return null;
        }
        if (tc.getDisplayName() == null) {
            String dn = e.getDisplayName();
            tc.setDisplayName(dn != null ? dn : id);
        }
        if (tc.getIcon() == null && e.getString("iconBase") != null && !e.getString("iconBase").isEmpty()) {
            tc.setIcon(org.webbeans.ui.Resource.of(e.getString("iconBase")));
        }
        tc.setManager(this);
        tc.setId(id);
        components.put(id, tc);
        return tc;
    }

    private static TopComponent createFromFactory(Entry reg, Map<String, Object> state) {
        Object f = reg.getAttributes().get("factory");
        if (!(f instanceof Class)) {
            return null;
        }
        try {
            TopComponent.Factory factory = (TopComponent.Factory) ((Class<?>) f).getDeclaredConstructor().newInstance();
            return factory.create(state);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.log(Level.WARNING, "Factory " + f + " could not restore a component", e);
            return null;
        }
    }

    private static boolean hasFactory(String id) {
        if (!id.contains("#")) {
            return false;
        }
        Entry reg = org.webbeans.core.registry.Registry.getDefault().entry(COMPONENTS_PATH + "/" + id.substring(0, id.indexOf('#')));
        return reg != null && reg.getAttributes().get("factory") instanceof Class;
    }

    @Override
    public Collection<TopComponent> getOpened() {
        List<TopComponent> out = new ArrayList<TopComponent>();
        for (TopComponent tc : components.values()) {
            if (tc.isOpened()) {
                out.add(tc);
            }
        }
        return out;
    }

    @Override
    public TopComponent getActivated() {
        return activated;
    }

    @Override
    public Lookup getGlobalContext() {
        return globalContext;
    }

    @Override
    Collection<?> getActivatedNodes() {
        if (activated == null) {
            return Collections.emptyList();
        }
        // the Nodes API is a separate module layered above the window system: resolve its base class by name
        ClassLoader l = Lookup.getDefault().lookup(ClassLoader.class);
        try {
            Class<?> node = Class.forName("org.webbeans.nodes.Node", false, l != null ? l : getClass().getClassLoader());
            return new ArrayList<Object>(activated.getLookup().lookupAll(node));
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public TopComponentGroup findTopComponentGroup(String name) {
        GroupImpl g = groups.get(name);
        if (g == null) {
            Entry e = org.webbeans.core.registry.Registry.getDefault().entry(GROUPS_PATH + "/" + name);
            if (e == null) {
                return null;
            }
            Object ids = e.getAttributes().get("topComponents");
            List<String> list = new ArrayList<String>();
            if (ids instanceof String[]) {
                list.addAll(Arrays.asList((String[]) ids));
            } else if (ids instanceof String) {
                for (String s : ((String) ids).split(",")) {
                    if (!s.trim().isEmpty()) {
                        list.add(s.trim());
                    }
                }
            }
            g = new GroupImpl(this, name, list);
            groups.put(name, g);
        }
        return g;
    }

    /** Opened components of a mode, in tab order. */
    List<TopComponent> componentsOf(String mode) {
        LayoutModel.ModeNode n = layout.findMode(mode);
        List<TopComponent> out = new ArrayList<TopComponent>();
        if (n != null) {
            for (String id : n.tcs) {
                TopComponent tc = components.get(id);
                if (tc != null) {
                    out.add(tc);
                }
            }
        }
        return out;
    }

    TopComponent selectedOf(String mode) {
        LayoutModel.ModeNode n = layout.findMode(mode);
        return n == null || n.selected == null ? null : components.get(n.selected);
    }

    // ---- open / close / activate -------------------------------------------------------------------------------------

    @Override
    void open(TopComponent tc, Mode mode) {
        openInternal(tc, mode == null ? null : mode.getName(), true);
        push();
    }

    private void openInternal(TopComponent tc, String modeName, boolean select) {
        if (tc.isOpened()) {
            if (modeName != null && !modeName.equals(tc.getMode().getName())) {
                moveTo(tc, modeName, -1);
            }
            if (select) {
                selectInMode(layout.findModeOf(tc.getTopComponentId()), tc.getTopComponentId());
            }
            return;
        }
        String id = assignId(tc);
        if (modeName == null || !modes.containsKey(modeName)) {
            modeName = registeredMode(tc.preferredID());
        }
        if (modeName == null) {
            modeName = layout.closedTcModes.get(id);
        }
        if (modeName == null || !modes.containsKey(modeName)) {
            modeName = DEFAULT_MODE;
        }
        LayoutModel.ModeNode node = ensureModeNode(modeName);
        node.tcs.add(id);
        layout.closedTcModes.remove(id);
        dock.add(tc, id);
        Map<String, Object> state = layout.tcState.get(id);
        if (state != null) {
            try {
                tc.loadState(state);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "readState failed for " + tc, e);
            }
        }
        tc.fireOpened(modes.get(modeName));
        if (select || node.selected == null) {
            selectInMode(node, id);
        }
        getRegistry().fire(Registry.PROP_TC_OPENED, null, tc);
        getRegistry().fire(Registry.PROP_OPENED, null, getRegistry().getOpened());
        markDirty();
    }

    private String assignId(TopComponent tc) {
        if (tc.getTopComponentId() != null && components.get(tc.getTopComponentId()) == tc) {
            return tc.getTopComponentId();
        }
        String pid = tc.preferredID();
        String id = pid;
        Entry reg = org.webbeans.core.registry.Registry.getDefault().entry(COMPONENTS_PATH + "/" + pid);
        boolean singleton = reg != null && !boolAttr(reg, "multiInstance", false);
        if (!singleton || (components.containsKey(pid) && components.get(pid) != tc)) {
            int n = 1;
            while (components.containsKey(pid + "#" + n)) {
                n++;
            }
            id = pid + "#" + n;
        }
        tc.setManager(this);
        tc.setId(id);
        components.put(id, tc);
        return id;
    }

    /** The mode a preferredID is registered into ({@code Windows/Modes/<mode>/<id>}), or null. */
    private String registeredMode(String preferredID) {
        for (Folder modeFolder : org.webbeans.core.registry.Registry.getDefault().folder(MODES_PATH).getFolders()) {
            if (modeFolder.getEntry(preferredID) != null) {
                return modeFolder.getName();
            }
        }
        return null;
    }

    private LayoutModel.ModeNode ensureModeNode(String name) {
        LayoutModel.ModeNode n = layout.findMode(name);
        if (n == null) {
            n = new LayoutModel.ModeNode(name);
            ModeImpl m = modes.get(name);
            layout.place(n, m == null ? 250 : m.getConstraints().getDefaultSize());
        }
        return n;
    }

    private void selectInMode(LayoutModel.ModeNode node, String id) {
        if (node == null || !node.tcs.contains(id)) {
            return;
        }
        if (id.equals(node.selected)) {
            return;
        }
        TopComponent old = node.selected == null ? null : components.get(node.selected);
        node.selected = id;
        if (old != null) {
            old.fireShowing(false);
        }
        TopComponent tc = components.get(id);
        if (tc != null && !layout.isMinimised(node.name)) {
            tc.fireShowing(true);
        }
        markDirty();
    }

    @Override
    boolean close(TopComponent tc, boolean force) {
        boolean ok = closeInternal(tc, force, true);
        push();
        return ok;
    }

    private boolean closeInternal(TopComponent tc, boolean force, boolean reactivate) {
        if (!tc.isOpened()) {
            return true;
        }
        if (!force) {
            try {
                if (!tc.canClose()) {
                    return false;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "canClose failed for " + tc, e);
            }
        }
        String id = tc.getTopComponentId();
        LayoutModel.ModeNode node = layout.findModeOf(id);
        if (activated == tc) {
            setActivated(null);
        }
        tc.fireShowing(false);
        String next = null;
        if (node != null) {
            int i = node.tcs.indexOf(id);
            node.tcs.remove(id);
            if (id.equals(node.selected)) {
                node.selected = null;
                if (!node.tcs.isEmpty()) {
                    next = node.tcs.get(Math.min(i, node.tcs.size() - 1));
                    selectInMode(node, next);
                }
            }
            if (tc.getPersistenceType() != TopComponent.PERSISTENCE_NEVER) {
                layout.closedTcModes.put(id, node.name);
                try {
                    Map<String, Object> st = tc.saveState();
                    if (!st.isEmpty()) {
                        layout.tcState.put(id, st);
                    }
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "writeState failed for " + tc, e);
                }
            }
        }
        dock.remove(tc);
        tc.fireClosed();
        if (id.contains("#")) {
            components.remove(id); // instances are not reusable
        }
        pruneEmptyModes();
        getRegistry().fire(Registry.PROP_TC_CLOSED, null, tc);
        getRegistry().fire(Registry.PROP_OPENED, null, getRegistry().getOpened());
        if (reactivate && activated == null) {
            TopComponent n = next != null ? components.get(next) : firstCentreComponent();
            if (n != null) {
                requestActive(n);
            }
        }
        markDirty();
        return true;
    }

    private void pruneEmptyModes() {
        for (LayoutModel.ModeNode n : new ArrayList<LayoutModel.ModeNode>(layout.allModes())) {
            if (!n.tcs.isEmpty()) {
                continue;
            }
            ModeImpl m = modes.get(n.name);
            if (m != null && m.getConstraints().isPermanent() && !layout.isFloating(n.name) && !layout.isMinimised(n.name)) {
                continue;
            }
            layout.removeMode(n);
            if (m != null && m.isAnonymous()) {
                modes.remove(n.name);
            }
            layout.returnSides.remove(n.name);
        }
        if (layout.maximized != null && layout.findMode(layout.maximized) == null) {
            layout.maximized = null;
        }
    }

    @Override
    public boolean closeAll(boolean force) {
        boolean all = true;
        for (TopComponent tc : new ArrayList<TopComponent>(getOpened())) {
            all &= closeInternal(tc, force, false);
        }
        push();
        return all;
    }

    @Override
    void requestActive(TopComponent tc) {
        if (!tc.isOpened()) {
            openInternal(tc, null, true);
        }
        LayoutModel.ModeNode node = layout.findModeOf(tc.getTopComponentId());
        if (node != null) {
            if (layout.isMinimised(node.name)) {
                dockBack(node);
            }
            if (layout.maximized != null && !layout.maximized.equals(node.name) && !layout.isFloating(node.name)) {
                layout.maximized = null;
            }
            selectInMode(node, tc.getTopComponentId());
        }
        setActivated(tc);
        push();
    }

    @Override
    void requestVisible(TopComponent tc) {
        if (!tc.isOpened()) {
            openInternal(tc, null, true);
        } else {
            selectInMode(layout.findModeOf(tc.getTopComponentId()), tc.getTopComponentId());
        }
        push();
    }

    private void setActivated(TopComponent tc) {
        if (activated == tc) {
            return;
        }
        TopComponent old = activated;
        activated = tc;
        if (old != null) {
            old.fireActive(false);
        }
        layout.active = tc == null ? null : tc.getTopComponentId();
        dock.set(Dock.ACTIVE, layout.active);
        globalContext.retarget(tc == null ? null : tc.getLookup());
        if (tc != null) {
            tc.fireActive(true);
        }
        getRegistry().fire(Registry.PROP_ACTIVATED, old, tc);
        markDirty();
    }

    // ---- layout operations ---------------------------------------------------------------------------------------------

    /** Moves an open component into a mode at the index (-1 = end). */
    void moveTo(TopComponent tc, String modeName, int index) {
        if (!tc.isOpened() || !modes.containsKey(modeName)) {
            return;
        }
        String id = tc.getTopComponentId();
        LayoutModel.ModeNode from = layout.findModeOf(id);
        LayoutModel.ModeNode to = ensureModeNode(modeName);
        if (from == to) {
            int i = to.tcs.indexOf(id);
            to.tcs.remove(id);
            if (index < 0 || index > to.tcs.size()) {
                index = to.tcs.size();
            } else if (index > i) {
                index--;
            }
            to.tcs.add(index, id);
            markDirty();
            return;
        }
        boolean wasShowing = tc.isShowing();
        if (from != null) {
            int i = from.tcs.indexOf(id);
            from.tcs.remove(id);
            if (id.equals(from.selected)) {
                from.selected = null;
                tc.fireShowing(false);
                if (!from.tcs.isEmpty()) {
                    selectInMode(from, from.tcs.get(Math.min(i, from.tcs.size() - 1)));
                }
            }
        }
        if (index < 0 || index > to.tcs.size()) {
            index = to.tcs.size();
        }
        to.tcs.add(index, id);
        tc.setModeInternal(modes.get(modeName));
        selectInMode(to, id);
        if (!wasShowing && !tc.isShowing()) {
            tc.fireShowing(true);
        }
        pruneEmptyModes();
        markDirty();
    }

    /** Creates an anonymous mode beside {@code beside} and moves the component into it. */
    void moveBeside(TopComponent tc, String beside, String side) {
        LayoutModel.ModeNode target = layout.findMode(beside);
        if (target == null || !tc.isOpened() || layout.isFloating(beside) || layout.isMinimised(beside)) {
            return;
        }
        LayoutModel.ModeNode from = layout.findModeOf(tc.getTopComponentId());
        if (from == target && target.tcs.size() == 1) {
            return; // splitting a mode with only itself changes nothing
        }
        String name = "anonymousMode_" + (++anonymousCounter);
        ModeImpl m = new ModeImpl(this, name, anonymousConstraints(), true, null);
        modes.put(name, m);
        LayoutModel.ModeNode node = new LayoutModel.ModeNode(name);
        layout.splitBeside(target, node, side);
        moveTo(tc, name, -1);
        pushModes();
    }

    void floatMode(String name, int[] rect) {
        LayoutModel.ModeNode n = layout.findMode(name);
        ModeImpl m = modes.get(name);
        if (n == null || m == null || !m.getConstraints().isFloatable()) {
            return;
        }
        layout.floatMode(n, rect);
        for (TopComponent tc : componentsOf(name)) {
            if (tc.getTopComponentId().equals(n.selected)) {
                tc.fireShowing(true);
            }
        }
        markDirty();
    }

    void minimise(String name, String side) {
        LayoutModel.ModeNode n = layout.findMode(name);
        ModeImpl m = modes.get(name);
        if (n == null || m == null || layout.isMinimised(name)) {
            return;
        }
        Mode.Side s = layout.sideOf(n);
        layout.returnSides.put(name, s == null ? "CENTER" : s.name());
        layout.minimise(n, side);
        TopComponent sel = selectedOf(name);
        if (sel != null) {
            sel.fireShowing(false);
            if (activated == sel) {
                setActivated(null);
            }
        }
        markDirty();
    }

    void dockBack(LayoutModel.ModeNode n) {
        if (!layout.isFloating(n.name) && !layout.isMinimised(n.name)) {
            return;
        }
        layout.detachFromTree(n);
        ModeImpl m = modes.get(n.name);
        layout.place(n, m == null ? 250 : m.getConstraints().getDefaultSize());
        TopComponent sel = selectedOf(n.name);
        if (sel != null) {
            sel.fireShowing(true);
        }
        markDirty();
    }

    @Override
    public void setMaximized(TopComponent tc, boolean maximized) {
        LayoutModel.ModeNode n = tc.isOpened() ? layout.findModeOf(tc.getTopComponentId()) : null;
        if (n == null) {
            return;
        }
        ModeImpl m = modes.get(n.name);
        if (maximized && (m == null || !m.getConstraints().isMaximizable() || layout.isFloating(n.name))) {
            return;
        }
        layout.maximized = maximized ? n.name : null;
        markDirty();
        push();
    }

    @Override
    public boolean isMaximized(TopComponent tc) {
        return tc.isOpened() && layout.maximized != null && layout.maximized.equals(layout.findModeOf(tc.getTopComponentId()).name);
    }

    @Override
    public void setFloating(TopComponent tc, boolean floating) {
        LayoutModel.ModeNode n = tc.isOpened() ? layout.findModeOf(tc.getTopComponentId()) : null;
        if (n == null) {
            return;
        }
        if (floating) {
            floatMode(n.name, new int[] {120, 120, 640, 420});
        } else {
            dockBack(n);
        }
        push();
    }

    @Override
    public void setMinimized(TopComponent tc, boolean minimized) {
        LayoutModel.ModeNode n = tc.isOpened() ? layout.findModeOf(tc.getTopComponentId()) : null;
        if (n == null) {
            return;
        }
        if (minimized) {
            Mode.Side s = layout.sideOf(n);
            minimise(n.name, s == Mode.Side.RIGHT ? "right" : s == Mode.Side.BOTTOM ? "bottom" : s == Mode.Side.TOP ? "top" : "left");
        } else {
            dockBack(n);
        }
        push();
    }

    // ---- dock events -------------------------------------------------------------------------------------------------------

    private void wireDockEvents() {
        dock.addListener(Dock.SELECT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                TopComponent tc = components.get(e.getString("tc"));
                LayoutModel.ModeNode n = layout.findMode(e.getString("mode"));
                if (tc != null && n != null && n.tcs.contains(tc.getTopComponentId())) {
                    requestActive(tc);
                }
            }
        });
        dock.addListener(Dock.ACTIVATE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                TopComponent tc = components.get(e.getString("tc"));
                if (tc != null && tc.isOpened()) {
                    requestActive(tc);
                }
            }
        });
        dock.addListener(Dock.CLOSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                TopComponent tc = components.get(e.getString("tc"));
                if (tc != null && tc.isOpened() && tc.isClosable()) {
                    ModeImpl m = (ModeImpl) tc.getMode();
                    if (m == null || m.getConstraints().isCloseable()) {
                        close(tc, false);
                    }
                }
            }
        });
        dock.addListener(Dock.MOVE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                TopComponent tc = components.get(e.getString("tc"));
                if (tc == null || !tc.isOpened()) {
                    return;
                }
                if (e.getString("beside") != null) {
                    moveBeside(tc, e.getString("beside"), e.getString("side"));
                } else if (e.getString("mode") != null) {
                    moveTo(tc, e.getString("mode"), e.getInt("index", -1));
                } else if (e.getData().get("floating") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> r = (Map<String, Object>) e.getData().get("floating");
                    int[] rect = {(int) Json.integer(r, "x", 100), (int) Json.integer(r, "y", 100),
                        (int) Json.integer(r, "w", 600), (int) Json.integer(r, "h", 400)};
                    LayoutModel.ModeNode from = layout.findModeOf(tc.getTopComponentId());
                    if (from != null && from.tcs.size() == 1 && !layout.isFloating(from.name)) {
                        floatMode(from.name, rect);
                    } else {
                        String name = "anonymousMode_" + (++anonymousCounter);
                        modes.put(name, new ModeImpl(WindowManagerImpl.this, name, anonymousConstraints(), true, null));
                        LayoutModel.ModeNode node = new LayoutModel.ModeNode(name);
                        node.rect = rect;
                        layout.floating.add(node);
                        moveTo(tc, name, -1);
                        pushModes();
                    }
                }
                requestActive(tc);
                push();
            }
        });
        dock.addListener(Dock.RESIZE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Object p = e.getData().get("path");
                Object s = e.getData().get("sizes");
                if (!(p instanceof List) || !(s instanceof List)) {
                    return;
                }
                List<Integer> path = new ArrayList<Integer>();
                for (Object o : (List<?>) p) {
                    path.add(o instanceof Number ? ((Number) o).intValue() : -1);
                }
                LayoutModel.Node n = layout.nodeAt(path);
                List<?> sizes = (List<?>) s;
                if (!(n instanceof LayoutModel.Split) || ((LayoutModel.Split) n).children.size() != sizes.size()) {
                    return;
                }
                LayoutModel.Split split = (LayoutModel.Split) n;
                for (int i = 0; i < sizes.size(); i++) {
                    Object v = sizes.get(i);
                    split.sizes.set(i, v instanceof Number ? Math.max(40, ((Number) v).doubleValue()) : LayoutModel.STAR);
                }
                markDirty();
                push();
            }
        });
        dock.addListener(Dock.MAXIMIZE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String mode = e.getString("mode");
                ModeImpl m = modes.get(mode);
                if (m == null || layout.findMode(mode) == null) {
                    return;
                }
                if (mode.equals(layout.maximized)) {
                    layout.maximized = null;
                } else if (m.getConstraints().isMaximizable() && !layout.isFloating(mode) && !layout.isMinimised(mode)) {
                    layout.maximized = mode;
                }
                markDirty();
                push();
            }
        });
        dock.addListener(Dock.FLOAT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                int[] rect = {e.getInt("x", 100), e.getInt("y", 100), Math.max(200, e.getInt("w", 600)), Math.max(120, e.getInt("h", 400))};
                floatMode(e.getString("mode"), rect);
                push();
            }
        });
        dock.addListener(Dock.DOCK, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                LayoutModel.ModeNode n = layout.findMode(e.getString("mode"));
                if (n != null) {
                    dockBack(n);
                    push();
                }
            }
        });
        dock.addListener(Dock.MINIMIZE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String side = e.getString("side");
                minimise(e.getString("mode"), side == null ? "left" : side);
                push();
            }
        });
    }

    // ---- wire + persistence -----------------------------------------------------------------------------------------------

    /** Sends the layout to the client if it changed (the property set is a no-op for an equal map). */
    void push() {
        dock.set(Dock.LAYOUT, layout.toJson());
    }

    private void pushModes() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (ModeImpl mode : modes.values()) {
            Map<String, Object> e = new LinkedHashMap<String, Object>();
            Mode.Constraints c = mode.getConstraints();
            e.put("side", c.getSide().name().toLowerCase(java.util.Locale.ROOT));
            e.put("permanent", c.isPermanent());
            e.put("closeable", c.isCloseable());
            e.put("floatable", c.isFloatable());
            e.put("maximizable", c.isMaximizable());
            e.put("anonymous", mode.isAnonymous());
            if (mode.getDisplayName() != null) {
                e.put("displayName", mode.getDisplayName());
            }
            m.put(mode.getName(), e);
        }
        dock.set(Dock.MODES, m);
    }

    private void pushLabels() {
        Map<String, Object> l = new LinkedHashMap<String, Object>();
        for (String key : new String[] {"close", "closeOthers", "maximize", "restore", "float", "dock", "minimize", "empty", "modified"}) {
            l.put(key, Bundles.getMessage(WindowManagerImpl.class, "LBL_Dock_" + key));
        }
        dock.set(Dock.LABELS, l);
    }

    private void markDirty() {
        if (!ready || files == null) {
            return;
        }
        if (saveTimer == null || saveTimer.isCancelled()) {
            saveTimer = ui.schedule(new Runnable() {
                @Override
                public void run() {
                    saveTimer = null;
                    saveNow();
                }
            }, SAVE_DELAY_MS);
        }
    }

    /** Writes the layout now (called on UI close and by the debounce timer). */
    public void saveNow() {
        if (files == null || !ready) {
            return;
        }
        if (saveTimer != null) {
            saveTimer.cancel();
            saveTimer = null;
        }
        writeLayout(layoutFileFor(layout.role));
    }

    private Path layoutFileFor(String role) {
        return role == null || role.equals("default") ? files.layoutFile() : files.layoutFile("role-" + role);
    }

    private void writeLayout(Path file) {
        try {
            files.writeString(file, Json.write(persistentCopy().toJson()));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot save layout to " + file, e);
        }
    }

    /** The layout without components that must not be persisted. */
    private LayoutModel persistentCopy() {
        // open components contribute their current state (closed ones stored theirs when they closed)
        for (TopComponent tc : getOpened()) {
            if (tc.getPersistenceType() != TopComponent.PERSISTENCE_NEVER) {
                try {
                    Map<String, Object> st = tc.saveState();
                    if (!st.isEmpty()) {
                        layout.tcState.put(tc.getTopComponentId(), st);
                    }
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "writeState failed for " + tc, e);
                }
            }
        }
        LayoutModel copy = layout.copy();
        for (LayoutModel.ModeNode n : copy.allModes()) {
            for (String id : new ArrayList<String>(n.tcs)) {
                TopComponent tc = components.get(id);
                if (tc == null || tc.getPersistenceType() == TopComponent.PERSISTENCE_NEVER || (id.contains("#") && !hasFactory(id))) {
                    n.tcs.remove(id);
                    if (id.equals(n.selected)) {
                        n.selected = n.tcs.isEmpty() ? null : n.tcs.get(0);
                    }
                }
            }
        }
        for (String id : new ArrayList<String>(copy.tcState.keySet())) {
            TopComponent tc = components.get(id);
            boolean open = tc != null && tc.isOpened();
            if ((id.contains("#") && !hasFactory(id)) || (tc != null && tc.getPersistenceType() == TopComponent.PERSISTENCE_NEVER)
                    || (!open && tc != null && tc.getPersistenceType() == TopComponent.PERSISTENCE_ONLY_OPENED)) {
                copy.tcState.remove(id);
                copy.closedTcModes.remove(id);
            }
        }
        if (copy.active != null && copy.active.contains("#") && !hasFactory(copy.active)) {
            copy.active = null;
        }
        return copy;
    }

    /** A layout a module ships for the role ({@code Windows/Layouts/<role>}, see {@link LayoutRegistration}), or null. */
    static LayoutModel shippedLayout(String role) {
        Entry e = org.webbeans.core.registry.Registry.getDefault().entry("Windows/Layouts/" + role);
        String resource = e == null ? null : e.getString("resource");
        if (resource == null) {
            return null;
        }
        ClassLoader loader = Lookup.getDefault().lookup(ClassLoader.class);
        if (loader == null) {
            loader = WindowManagerImpl.class.getClassLoader();
        }
        java.io.InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            LOG.log(Level.WARNING, "Shipped layout {0} not found for role {1}", new Object[] {resource, role});
            return null;
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            Map<String, Object> m = Json.parseObject(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            if (Json.integer(m, "version", 0) != LayoutModel.VERSION) {
                return null;
            }
            return LayoutModel.fromJson(m);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot read shipped layout " + resource, ex);
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
                // nothing to do
            }
        }
    }

    private static LayoutModel readLayout(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> m = Json.parseObject(text);
            if (Json.integer(m, "version", 0) != LayoutModel.VERSION) {
                LOG.log(Level.INFO, "Ignoring layout {0} of version {1}", new Object[] {file, Json.integer(m, "version", 0)});
                return null;
            }
            return LayoutModel.fromJson(m);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Cannot read layout " + file + "; using defaults", e);
            return null;
        }
    }

    @Override
    public void setRole(String role) {
        if (role == null || role.isEmpty()) {
            role = "default";
        }
        if (role.equals(layout.role)) {
            return;
        }
        saveNow();
        LayoutModel saved = files == null ? null : readLayout(layoutFileFor(role));
        if (saved == null) {
            saved = shippedLayout(role);
        }
        if (saved == null) {
            saved = new LayoutModel();
        }
        saved.role = role;
        applyLayout(saved, true);
        markDirty();
    }

    @Override
    public String getRole() {
        return layout.role;
    }

    @Override
    public void saveLayout(String name) {
        if (files != null) {
            writeLayout(files.layoutFile(name));
        }
    }

    @Override
    public boolean loadLayout(String name) {
        LayoutModel saved = files == null ? null : readLayout(files.layoutFile(name));
        if (saved == null) {
            return false;
        }
        saved.role = layout.role;
        applyLayout(saved, false);
        markDirty();
        return true;
    }

    @Override
    public void resetLayout() {
        LayoutModel fresh = new LayoutModel();
        fresh.role = layout.role;
        applyLayout(fresh, true);
        markDirty();
    }

    // ---- keyboard --------------------------------------------------------------------------------------------------------

    /** Binds the standard window shortcuts on the UI (the main window provider calls this). */
    public void bindShortcuts() {
        ui.bindShortcut("Ctrl+Shift+Enter", "windows.maximize", new Runnable() {
            @Override
            public void run() {
                if (activated != null) {
                    setMaximized(activated, !isMaximized(activated));
                }
            }
        });
        ui.bindShortcut("Alt+W", "windows.close", new Runnable() {
            @Override
            public void run() {
                if (activated != null && activated.isClosable()) {
                    close(activated, false);
                }
            }
        });
        ui.bindShortcut("Alt+Shift+ArrowRight", "windows.nextTab", new Runnable() {
            @Override
            public void run() {
                cycle(1);
            }
        });
        ui.bindShortcut("Alt+Shift+ArrowLeft", "windows.previousTab", new Runnable() {
            @Override
            public void run() {
                cycle(-1);
            }
        });
    }

    private void cycle(int delta) {
        if (activated == null) {
            return;
        }
        LayoutModel.ModeNode n = layout.findModeOf(activated.getTopComponentId());
        if (n == null || n.tcs.size() < 2) {
            return;
        }
        int i = (n.tcs.indexOf(activated.getTopComponentId()) + delta + n.tcs.size()) % n.tcs.size();
        TopComponent next = components.get(n.tcs.get(i));
        if (next != null) {
            requestActive(next);
        }
    }

    // ---- inner implementations -----------------------------------------------------------------------------------------

    static final class ModeImpl implements Mode {
        private final WindowManagerImpl wm;
        private final String name;
        private final Constraints constraints;
        private final boolean anonymous;
        private final String displayName;

        ModeImpl(WindowManagerImpl wm, String name, Constraints constraints, boolean anonymous, String displayName) {
            this.wm = wm;
            this.name = name;
            this.constraints = constraints;
            this.anonymous = anonymous;
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return name;
        }

        String getDisplayName() {
            return displayName;
        }

        @Override
        public Side getSide() {
            return constraints.getSide();
        }

        @Override
        public Constraints getConstraints() {
            return constraints;
        }

        @Override
        public List<TopComponent> getTopComponents() {
            return wm.componentsOf(name);
        }

        @Override
        public TopComponent getSelectedTopComponent() {
            return wm.selectedOf(name);
        }

        @Override
        public boolean dockInto(TopComponent tc) {
            if (!tc.isOpened()) {
                wm.openInternal(tc, name, true);
            } else {
                wm.moveTo(tc, name, -1);
            }
            wm.push();
            return true;
        }

        @Override
        public boolean isAnonymous() {
            return anonymous;
        }

        @Override
        public String toString() {
            return "Mode[" + name + "]";
        }
    }

    static final class GroupImpl implements TopComponentGroup {
        private final WindowManagerImpl wm;
        private final String name;
        private final List<String> ids;
        private final Set<String> openedByGroup = new LinkedHashSet<String>();
        private boolean opened;

        GroupImpl(WindowManagerImpl wm, String name, List<String> ids) {
            this.wm = wm;
            this.name = name;
            this.ids = Collections.unmodifiableList(new ArrayList<String>(ids));
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getTopComponentIds() {
            return ids;
        }

        @Override
        public void open() {
            openedByGroup.clear();
            for (String id : ids) {
                TopComponent tc = wm.findTopComponent(id);
                if (tc != null && !tc.isOpened()) {
                    wm.openInternal(tc, null, false);
                    openedByGroup.add(id);
                }
            }
            opened = true;
            wm.push();
        }

        @Override
        public void close() {
            for (String id : openedByGroup) {
                TopComponent tc = wm.components.get(id);
                if (tc != null && tc.isOpened()) {
                    wm.closeInternal(tc, false, true);
                }
            }
            openedByGroup.clear();
            opened = false;
            wm.push();
        }

        @Override
        public boolean isOpened() {
            return opened;
        }
    }

    /** For tests and diagnostics: the component with that id if it is known to this manager. */
    TopComponent known(String id) {
        return components.get(id);
    }

    /** Adds something to the UI lookup; convenience for providers building the page. */
    Component dockComponent() {
        return dock;
    }
}
