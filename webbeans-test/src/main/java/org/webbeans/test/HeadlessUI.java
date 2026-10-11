package org.webbeans.test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.ActionsDecorator;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.registry.RegistrationBuilder;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Component;
import org.webbeans.ui.Container;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Button;
import org.webbeans.windows.MainWindowProvider;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/**
 * A complete WebBeans session without a server or browser, for module tests: the registry is built from the
 * test class path (every module jar's generated registrations), the main window is the real one (modes, windows,
 * menus, toolbars, status bar), a temporary data directory holds the user's files, and every message the
 * browser would receive is recorded. Inject client events, click buttons, answer dialogs and assert on
 * components or on the wire.
 *
 * <pre>
 * try (HeadlessUI h = HeadlessUI.builder().user("alice", "user", "admin").start()) {
 *     TopComponent files = h.open("FilesTC");
 *     h.click("new-file");
 *     h.type("dialog-input", "notes.txt");
 *     h.answerDialog("dialog-ok");
 *     assertTrue(Files.exists(h.userDir("com.acme.files").resolve("workdir/notes.txt")));
 * }
 * </pre>
 */
public final class HeadlessUI implements AutoCloseable {

    /** Runs tasks inline: the test thread is the session executor. */
    public static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    public static final class Builder {
        private String user = "alice";
        private String[] roles = {"user"};
        private Locale locale = Locale.ENGLISH;
        private Registry registry;
        private final List<Object> services = new ArrayList<Object>();
        private UIProvider provider;
        private Path dataDir;

        public Builder user(String name, String... userRoles) {
            user = name;
            roles = userRoles.length == 0 ? new String[] {"user"} : userRoles;
            return this;
        }

        public Builder locale(Locale l) {
            locale = l;
            return this;
        }

        /** A registry built by the test instead of the class path's registrations. */
        public Builder registry(Registry r) {
            registry = r;
            return this;
        }

        /** Extra services for {@code Lookup.getDefault()}. */
        public Builder services(Object... objects) {
            services.addAll(Arrays.asList(objects));
            return this;
        }

        /** The page to build instead of the main window. */
        public Builder provider(UIProvider p) {
            provider = p;
            return this;
        }

        public Builder dataDir(Path dir) {
            dataDir = dir;
            return this;
        }

        public HeadlessUI start() throws IOException {
            return new HeadlessUI(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The default fixture: user {@code alice} (role {@code user}), English, the class path's registrations. */
    public static HeadlessUI start() throws IOException {
        return builder().start();
    }

    private final Path dir;
    private final boolean ownDir;
    private final DataDir dataDir;
    private final Registry registry;
    private final UISession session;
    private final List<String> sent = new ArrayList<String>();
    private final Map<String, List<String>> sentBy = new LinkedHashMap<String, List<String>>();
    private final UI ui;

    private HeadlessUI(Builder b) throws IOException {
        ownDir = b.dataDir == null;
        dir = ownDir ? Files.createTempDirectory("wb-headless") : b.dataDir;
        dataDir = new DataDir(dir);
        registry = b.registry != null ? b.registry : new Registry();
        if (b.registry == null) {
            registry.rebuild(HeadlessUI.class.getClassLoader());
        }
        List<Object> content = new ArrayList<Object>();
        content.add(registry);
        content.add(dataDir);
        content.add(HeadlessUI.class.getClassLoader());
        content.add(b.provider != null ? b.provider : new MainWindowProvider());
        content.add(new ActionsDecorator());
        content.addAll(b.services);
        WbDefaultLookup.getInstance().setContent(
                Lookups.fixed(content.toArray()),
                new org.webbeans.core.lookup.RegistryLookup(registry, "Services"),
                Lookups.metaInfServices(HeadlessUI.class.getClassLoader()));
        Authentication auth = new Authentication(Principal.builder(b.user).displayName(b.user).roles(b.roles).build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, b.locale, dataDir.userFiles(b.user), DIRECT);
        ui = openTab("u1");
    }

    // ---- access --------------------------------------------------------------------------------------------------

    public UI ui() {
        return ui;
    }

    public UISession session() {
        return session;
    }

    public Registry registry() {
        return registry;
    }

    public Path dataDir() {
        return dir;
    }

    /** The user's directory for a module id (see {@code UserFiles.dir}). */
    public Path userDir(String moduleId) throws IOException {
        return session.getUserFiles().dir(moduleId);
    }

    public WindowManager wm() {
        return ui.getLookup().lookup(WindowManager.class);
    }

    public Lookup globalContext() {
        return wm().getGlobalContext();
    }

    /** Opens another browser tab of the same session. */
    public UI openTab(String id) {
        final UI u = session.createUI(id);
        final List<String> mine = new ArrayList<String>();
        sentBy.put(id, mine);
        final Transport t = new Transport() {
            @Override
            public void send(String json) {
                sent.add(json);
                mine.add(json);
            }

            @Override
            public void close() {
            }
        };
        await(u.access(new Runnable() {
            @Override
            public void run() {
                u.connect(t, -1);
            }
        }));
        return u;
    }

    /** Runs on the session executor and waits for it. */
    public void run(Runnable r) {
        await(ui.access(r));
    }

    /** Opens (if needed) and activates a registered window. */
    public TopComponent open(final String tcId) {
        final TopComponent[] out = new TopComponent[1];
        run(new Runnable() {
            @Override
            public void run() {
                TopComponent tc = wm().findTopComponent(tcId);
                if (tc == null) {
                    throw new AssertionError("No window " + tcId + " in the registry");
                }
                tc.open();
                tc.requestActive();
                out[0] = tc;
            }
        });
        return out[0];
    }

    /** Activates an open window (the global context follows). */
    public void activate(final TopComponent tc) {
        run(new Runnable() {
            @Override
            public void run() {
                tc.requestActive();
            }
        });
    }

    // ---- components ----------------------------------------------------------------------------------------------

    /** The first component with the test id anywhere in the tab, or null. */
    public Component byTestId(String testId) {
        return byTestId(ui.getRoot(), testId);
    }

    /** The component with the test id, failing when absent. */
    public Component find(String testId) {
        Component c = byTestId(testId);
        if (c == null) {
            throw new AssertionError("No component with test id '" + testId + "'");
        }
        return c;
    }

    public static Component byTestId(Component root, String testId) {
        if (root == null) {
            return null;
        }
        if (testId.equals(root.get(Component.ID_FOR_TEST))) {
            return root;
        }
        if (root instanceof Container) {
            for (Component c : ((Container) root).getChildren()) {
                Component found = byTestId(c, testId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** All components of a type in the tab. */
    public <T extends Component> List<T> all(Class<T> type) {
        List<T> out = new ArrayList<T>();
        collect(ui.getRoot(), type, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> void collect(Component root, Class<T> type, List<T> out) {
        if (type.isInstance(root)) {
            out.add((T) root);
        }
        if (root instanceof Container) {
            for (Component c : ((Container) root).getChildren()) {
                collect(c, type, out);
            }
        }
    }

    // ---- events --------------------------------------------------------------------------------------------------

    /** Dispatches a client event to a component (as the browser would). */
    public void inject(final Component c, final String type, final Map<String, Object> data) {
        inject(c.getUI(), c, type, data);
    }

    public void inject(String testId, String type, Map<String, Object> data) {
        inject(find(testId), type, data);
    }

    public void inject(final UI tab, final Component c, final String type, final Map<String, Object> data) {
        await(tab.access(new Runnable() {
            @Override
            public void run() {
                tab.handleEvents(Collections.<Object>singletonList(map("id", c.getId(), "type", type, "data", data)));
            }
        }));
    }

    /** A left click. */
    public void click(Component c) {
        inject(c, "click", map("button", 0, "count", 1));
    }

    public void click(String testId) {
        click(find(testId));
    }

    /** Commits a new value into a value-bearing component (text fields, check boxes, combo boxes, ...). */
    public void type(Component c, Object value) {
        inject(c, "change", map("value", value));
    }

    public void type(String testId, Object value) {
        type(find(testId), value);
    }

    /** Presses a dialog button by its test id ({@code dialog-ok}, {@code dialog-yes}, {@code dialog-cancel}, ...). */
    public void answerDialog(String buttonTestId) {
        Component dialog = byTestId("dialog");
        if (dialog == null) {
            throw new AssertionError("No dialog is open");
        }
        Component b = byTestId(dialog, buttonTestId);
        if (b == null) {
            throw new AssertionError("No button " + buttonTestId + " in the dialog");
        }
        click(b);
    }

    /** Whether a dialog is open. */
    public boolean hasDialog() {
        return byTestId("dialog") != null;
    }

    /** The texts of the open dialog's buttons. */
    public List<String> dialogButtons() {
        List<String> out = new ArrayList<String>();
        Component d = byTestId("dialog");
        if (d != null) {
            for (Button b : all(Button.class)) {
                if (isUnder(b, d)) {
                    out.add(b.getText());
                }
            }
        }
        return out;
    }

    private static boolean isUnder(Component c, Component ancestor) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    // ---- the wire ------------------------------------------------------------------------------------------------

    /** Every message sent to any tab, in order. */
    public List<String> messages() {
        return Collections.unmodifiableList(sent);
    }

    public List<String> messages(UI tab) {
        return Collections.unmodifiableList(sentBy.get(tab.getId()));
    }

    /** The ops of the last message sent to the first tab. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> lastDeltas() {
        List<String> mine = sentBy.get(ui.getId());
        if (mine.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object o : Json.array(Json.parseObject(mine.get(mine.size() - 1)), "ops")) {
            out.add((Map<String, Object>) o);
        }
        return out;
    }

    /** The {@code call} ops for a component ({@code m} and {@code args}); {@code method} null = all. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> callsOf(Component c, String method) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String msg : sentBy.get(c.getUI().getId())) {
            for (Object o : Json.array(Json.parseObject(msg), "ops")) {
                Map<String, Object> op = (Map<String, Object>) o;
                if ("call".equals(op.get("op")) && ((Number) op.get("id")).intValue() == c.getId() && (method == null || method.equals(op.get("m")))) {
                    out.add(op);
                }
            }
        }
        return out;
    }

    /** Forgets the recorded messages. */
    public void clearMessages() {
        sent.clear();
        for (List<String> l : sentBy.values()) {
            l.clear();
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static void await(Future<?> f) {
        try {
            f.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void close() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(HeadlessUI.class.getClassLoader()));
        if (ownDir) {
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                        Files.delete(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignore) {
                // temp dir
            }
        }
    }
}
