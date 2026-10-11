package org.webbeans.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.files.UserFiles;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.registry.RegistrationBuilder;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Component;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.windows.MainWindow;
import org.webbeans.windows.MainWindowProvider;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/**
 * A headless main window for tests: a registry with modes, two windows (one with a selection lookup), the
 * standard main window provider plus the actions decorator; messages sent to the client are recorded.
 */
public final class TestUI {

    public static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    /** A window whose lookup content the test controls. */
    public static final class SelectionTC extends TopComponent {
        public final InstanceContent content = new InstanceContent();
        public final ActionMap actionMap = new ActionMap();

        public SelectionTC() {
            setDisplayName("Selection");
            associateLookup(new org.openide.util.lookup.ProxyLookup(new AbstractLookup(content), Lookups.singleton(actionMap)));
        }
    }

    public static final class PlainTC extends TopComponent {
        public PlainTC() {
            setDisplayName("Plain");
        }
    }

    public final Path dir;
    public final Registry registry = new Registry();
    public final RegistrationBuilder builder = new RegistrationBuilder(null, TestUI.class.getClassLoader());
    public final List<String> sent = new ArrayList<String>();
    public final Map<String, List<String>> sentBy = new LinkedHashMap<String, List<String>>();
    public UISession session;
    public UI ui;
    public WindowManager wm;

    public TestUI() throws Exception {
        dir = Files.createTempDirectory("wb-actions");
        builder.instance("Windows/Modes/explorer").supplier(constant("explorer")).attr("side", "LEFT").attr("defaultSize", 200).position(100);
        builder.instance("Windows/Modes/editor").supplier(constant("editor")).attr("side", "CENTER").attr("permanent", true).position(100);
        builder.instance("Windows/Components/Selection").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                return new SelectionTC();
            }
        }).instanceOf(TopComponent.class);
        builder.reference("Windows/Modes/explorer/Selection", "Windows/Components/Selection").attr("openAtStartup", true).position(100);
        builder.instance("Windows/Components/Plain").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                return new PlainTC();
            }
        }).instanceOf(TopComponent.class);
        builder.reference("Windows/Modes/editor/Plain", "Windows/Components/Plain").attr("openAtStartup", true).position(100);
        builder.instance("StatusBar/progress").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                return org.webbeans.progress.ProgressStatusCell.create();
            }
        }).instanceOf(Component.class).position(800);
        builder.instance("StatusBar/notifications").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                return org.webbeans.notifications.NotificationsCell.create();
            }
        }).instanceOf(Component.class).position(900);
    }

    /** Finishes the registry and opens the first tab. */
    public void start() throws Exception {
        registry.rebuild(Collections.singletonList(builder));
        DataDir dataDir = new DataDir(dir);
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(registry, dataDir, new MainWindowProvider(), new ActionsDecorator()));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        UserFiles files = dataDir.userFiles("alice");
        session = new UISession(auth, Locale.ENGLISH, files, DIRECT);
        ui = openUI("u1");
    }

    public UI openUI(String id) {
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
        u.access(new Runnable() {
            @Override
            public void run() {
                u.connect(t, -1);
            }
        });
        if (ui == null) {
            wm = u.getLookup().lookup(WindowManager.class);
        }
        return u;
    }

    public void close() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(TestUI.class.getClassLoader()));
    }

    /** Runs on the session executor and waits for it (another thread may be running a timer task right now). */
    public void run(Runnable r) {
        await(ui.access(r));
    }

    static void await(java.util.concurrent.Future<?> f) {
        try {
            f.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public MainWindow mainWindow() {
        return (MainWindow) ui.getContent();
    }

    public SelectionTC selectionTC() {
        return (SelectionTC) wm.findTopComponent("Selection");
    }

    /** Dispatches a client event to a component. */
    public void fire(final Component c, final String type, final Map<String, Object> data) {
        fire(ui, c, type, data);
    }

    public static void fire(final UI ui, final Component c, final String type, final Map<String, Object> data) {
        await(ui.access(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(Collections.<Object>singletonList(map("id", c.getId(), "type", type, "data", data)));
            }
        }));
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Supplier<Object> constant(final String s) {
        return new Supplier<Object>() {
            @Override
            public Object get() {
                return s;
            }
        };
    }

    /** The {@code call} ops for a component, in order ({@code m} and {@code args}). */
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

    /** The first descendant with the given test id, or null. */
    public static Component byTestId(Component root, String testId) {
        if (testId.equals(root.get(Component.ID_FOR_TEST))) {
            return root;
        }
        if (root instanceof org.webbeans.ui.Container) {
            for (Component c : ((org.webbeans.ui.Container) root).getChildren()) {
                Component found = byTestId(c, testId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static Lookup emptyLookup() {
        return Lookup.EMPTY;
    }
}
