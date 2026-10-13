package org.webbeans.windows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.files.UserFiles;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.registry.RegistrationBuilder;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Label;

/** Headless window system: registry-built modes and components, a direct executor, a recording transport. */
public class WindowManagerTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    /** Records every lifecycle callback. */
    public static class LoggingTC extends TopComponent {
        static final List<String> LOG = new ArrayList<String>();
        private final String name;

        public LoggingTC(String name) {
            this.name = name;
            setDisplayName(name);
            add(new Label(name));
        }

        @Override
        protected String preferredID() {
            return name;
        }

        @Override
        protected void componentOpened() {
            LOG.add(name + ":opened");
        }

        @Override
        protected void componentClosed() {
            LOG.add(name + ":closed");
        }

        @Override
        protected void componentShowing() {
            LOG.add(name + ":showing");
        }

        @Override
        protected void componentHidden() {
            LOG.add(name + ":hidden");
        }

        @Override
        protected void componentActivated() {
            LOG.add(name + ":activated");
        }

        @Override
        protected void componentDeactivated() {
            LOG.add(name + ":deactivated");
        }

        @Override
        protected void writeState(Map<String, Object> out) {
            out.put("note", "state-of-" + name);
        }

        @Override
        protected void readState(Map<String, Object> in) {
            LOG.add(name + ":read:" + in.get("note"));
        }
    }

    public static final class VetoTC extends LoggingTC {
        boolean veto = true;

        public VetoTC() {
            super("Veto");
        }

        @Override
        protected boolean canClose() {
            return !veto;
        }
    }

    private Path dir;
    private UISession session;
    private UI ui;
    private WindowManagerImpl wm;
    private final List<String> sent = new ArrayList<String>();

    @Before
    public void setUp() throws Exception {
        LoggingTC.LOG.clear();
        dir = Files.createTempDirectory("wb-windows");
        DataDir dataDir = new DataDir(dir);
        Registry registry = new Registry();
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Windows/Modes/explorer").supplier(constant("explorer")).attr("side", "LEFT").attr("defaultSize", 200).position(100);
        b.instance("Windows/Modes/editor").supplier(constant("editor")).attr("side", "CENTER").attr("permanent", true).position(100);
        b.instance("Windows/Modes/output").supplier(constant("output")).attr("side", "BOTTOM").attr("defaultSize", 150).position(100);
        b.instance("Windows/Modes/properties").supplier(constant("properties")).attr("side", "RIGHT").position(100);
        b.instance("Windows/Components/Files").supplier(tc("Files")).instanceOf(TopComponent.class);
        b.reference("Windows/Modes/explorer/Files", "Windows/Components/Files").attr("openAtStartup", true).position(100);
        b.instance("Windows/Components/Main").supplier(tc("Main")).instanceOf(TopComponent.class);
        b.reference("Windows/Modes/editor/Main", "Windows/Components/Main").attr("openAtStartup", true).position(100);
        b.instance("Windows/Components/Log").supplier(tc("Log")).instanceOf(TopComponent.class);
        b.reference("Windows/Modes/output/Log", "Windows/Components/Log").attr("openAtStartup", false).position(100);
        b.instance("Windows/Components/Veto").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                return new VetoTC();
            }
        }).instanceOf(TopComponent.class);
        b.reference("Windows/Modes/editor/Veto", "Windows/Components/Veto").attr("openAtStartup", false);
        b.instance("Windows/Groups/debug").supplier(constant("debug")).attr("topComponents", new String[] {"Log", "Veto"});
        registry.rebuild(Collections.singletonList(b));
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(registry, dataDir, new MainWindowProvider()));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        UserFiles files = dataDir.userFiles("alice");
        session = new UISession(auth, Locale.ENGLISH, files, DIRECT);
        openUI("u1");
    }

    private static Supplier<Object> constant(final String s) {
        return new Supplier<Object>() {
            @Override
            public Object get() {
                return s;
            }
        };
    }

    private static Supplier<Object> tc(final String name) {
        return new Supplier<Object>() {
            @Override
            public Object get() {
                return new LoggingTC(name);
            }
        };
    }

    private void openUI(String id) {
        ui = session.createUI(id);
        final Transport t = new Transport() {
            @Override
            public void send(String json) {
                sent.add(json);
            }

            @Override
            public void close() {
            }
        };
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, -1);
            }
        });
        wm = (WindowManagerImpl) ui.getLookup().lookup(WindowManager.class);
    }

    @After
    public void tearDown() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    /** Runs on the session executor; the executor logs a failing task instead of throwing, so re-throw here. */
    private void run(final Runnable r) {
        final Throwable[] failure = new Throwable[1];
        ui.access(new Runnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        if (failure[0] instanceof Error) {
            throw (Error) failure[0];
        }
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    private static List<String> tcs(LayoutModel.ModeNode n) {
        return n == null ? null : n.tcs;
    }

    @Test
    public void startupOpensRegisteredComponentsIntoTheirModes() {
        assertNotNull(wm);
        List<String> opened = new ArrayList<String>();
        for (TopComponent tc : wm.getOpened()) {
            opened.add(tc.getTopComponentId());
        }
        assertEquals(new java.util.HashSet<String>(Arrays.asList("Files", "Main")), new java.util.HashSet<String>(opened));
        LayoutModel l = wm.getLayout();
        assertEquals(Arrays.asList("Files"), tcs(l.findMode("explorer")));
        assertEquals(Arrays.asList("Main"), tcs(l.findMode("editor")));
        assertNull("empty non-permanent modes are not in the tree", l.findMode("output"));
        assertEquals("Main", wm.getActivated().getTopComponentId());
        assertTrue(LoggingTC.LOG.toString(), LoggingTC.LOG.contains("Main:opened") && LoggingTC.LOG.contains("Main:showing")
                && LoggingTC.LOG.contains("Main:activated"));
        // the dock got the layout and the components as children
        String last = sent.get(sent.size() - 1);
        assertTrue(last, last.contains("\"wb.Dock\"") && last.contains("\"layout\"") && last.contains("\"wb.TopComponent\""));
        assertEquals(Mode.Side.LEFT, wm.findMode("explorer").getSide());
    }

    @Test
    public void openCloseActivateCallbackOrder() {
        run(new Runnable() {
            @Override
            public void run() {
                LoggingTC.LOG.clear();
                TopComponent log = wm.findTopComponent("Log");
                log.open();
                assertTrue(log.isOpened());
                assertEquals("output", log.getMode().getName());
                assertNotNull(wm.getLayout().findMode("output"));
                assertEquals(Arrays.asList("Log:opened", "Log:showing"), LoggingTC.LOG);
                LoggingTC.LOG.clear();
                log.requestActive();
                assertEquals(Arrays.asList("Main:deactivated", "Log:activated"), LoggingTC.LOG);
                assertSame(log, wm.getActivated());
                LoggingTC.LOG.clear();
                assertTrue(log.close());
                assertEquals(Arrays.asList("Log:deactivated", "Log:hidden", "Log:closed", "Main:activated"), LoggingTC.LOG);
                assertFalse(log.isOpened());
                assertNull("empty mode pruned", wm.getLayout().findMode("output"));
                assertEquals("state saved for a later reopen", "state-of-Log", wm.getLayout().tcState.get("Log").get("note"));
                LoggingTC.LOG.clear();
                log.open();
                assertTrue(LoggingTC.LOG.toString(), LoggingTC.LOG.get(0).equals("Log:read:state-of-Log"));
            }
        });
    }

    @Test
    public void canCloseVetoes() {
        run(new Runnable() {
            @Override
            public void run() {
                VetoTC v = (VetoTC) wm.findTopComponent("Veto");
                v.open();
                assertFalse(v.close());
                assertTrue(v.isOpened());
                v.veto = false;
                assertTrue(v.close());
                assertFalse(v.isOpened());
            }
        });
    }

    @Test
    public void multiInstanceIdsAndLookupSelection() {
        run(new Runnable() {
            @Override
            public void run() {
                LoggingTC a = new LoggingTC("Doc");
                LoggingTC b2 = new LoggingTC("Doc");
                a.open();
                b2.open();
                assertEquals("Doc#1", a.getTopComponentId());
                assertEquals("Doc#2", b2.getTopComponentId());
                assertEquals(Arrays.asList("Main", "Doc#1", "Doc#2"), tcs(wm.getLayout().findMode("editor")));
                final List<Object> events = new ArrayList<Object>();
                wm.getRegistry().addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent e) {
                        events.add(e.getPropertyName());
                    }
                });
                a.requestActive();
                assertTrue(events.contains(WindowManager.Registry.PROP_ACTIVATED));
                assertSame(a, wm.getActivated());
                assertTrue(a.isShowing());
                assertFalse(b2.isShowing());
                assertNull(wm.findTopComponent("Doc#1") == a ? null : "known instance");
            }
        });
    }

    /** Without the nodes module on the class path the activated "nodes" are every object of the lookup chain; the test data are the Strings. */
    private static List<String> strings(java.util.Collection<?> c) {
        List<String> out = new ArrayList<String>();
        for (Object o : c) {
            if (o instanceof String) {
                out.add((String) o);
            }
        }
        return out;
    }

    /** A window with a selection in its lookup, as ExplorerUtils.createLookup would build it. */
    private static final class ExplorerTC extends LoggingTC {
        final org.openide.util.lookup.InstanceContent content = new org.openide.util.lookup.InstanceContent();

        ExplorerTC(String name) {
            super(name);
            associateLookup(new org.openide.util.lookup.AbstractLookup(content));
            setActivatedNodesOwner(true);     // the windows module alone has no Node class: say so explicitly
        }
    }

    @Test
    public void activatedNodesStayWithTheirOwnerUntilAnotherOwnerIsActivated() {
        run(new Runnable() {
            @Override
            public void run() {
                ExplorerTC explorer = new ExplorerTC("Explorer");
                ExplorerTC other = new ExplorerTC("Other");
                LoggingTC props = new LoggingTC("Props");          // no nodes, not an owner
                explorer.open();
                other.open();
                props.open();
                final List<Object> changes = new ArrayList<Object>();
                wm.getRegistry().addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent e) {
                        if (WindowManager.Registry.PROP_ACTIVATED_NODES.equals(e.getPropertyName())) {
                            changes.add(e.getNewValue());
                        }
                    }
                });
                explorer.content.add("n1");
                explorer.requestActive();
                assertEquals(Arrays.asList("n1"), strings(wm.getRegistry().getActivatedNodes()));
                assertEquals("n1", wm.getActivatedNodesContext().lookup(String.class));
                assertEquals(1, changes.size());

                // clicking the properties window moves the global context but not the activated nodes
                props.requestActive();
                assertSame(props, wm.getActivated());
                assertNull(wm.getGlobalContext().lookup(String.class));
                assertEquals("n1", wm.getActivatedNodesContext().lookup(String.class));
                assertEquals(1, changes.size());

                // the owner's selection changes while another window is active: the nodes follow
                explorer.content.add("n2");
                assertEquals(Arrays.asList("n1", "n2"), strings(wm.getRegistry().getActivatedNodes()));
                assertEquals(2, changes.size());

                // another owner with nothing selected takes over: empty, as in NetBeans
                other.requestActive();
                assertTrue(strings(wm.getRegistry().getActivatedNodes()).isEmpty());
                assertEquals(3, changes.size());
                other.content.add("o1");
                assertEquals(Arrays.asList("o1"), strings(wm.getRegistry().getActivatedNodes()));

                // closing the owner clears them
                other.close();
                assertTrue(strings(wm.getRegistry().getActivatedNodes()).isEmpty());
                assertNull(wm.getActivatedNodesContext().lookup(String.class));

                // an explicit opt-out keeps a window with nodes from taking over
                explorer.requestActive();
                assertEquals("n1", wm.getActivatedNodesContext().lookup(String.class));
                ExplorerTC viewer = new ExplorerTC("Viewer");
                viewer.setActivatedNodesOwner(false);
                viewer.content.add("v1");
                viewer.open();
                viewer.requestActive();
                assertEquals("n1", wm.getActivatedNodesContext().lookup(String.class));
            }
        });
    }

    @Test
    public void dockEventsMoveSplitMaximizeFloatMinimize() {
        run(new Runnable() {
            @Override
            public void run() {
                wm.findTopComponent("Log").open();
                // move Files into the editor at index 0
                fire("move", map("tc", "Files", "mode", "editor", "index", 0));
                assertEquals(Arrays.asList("Files", "Main"), tcs(wm.getLayout().findMode("editor")));
                assertNull("explorer emptied and pruned", wm.getLayout().findMode("explorer"));
                // split: Log beside editor on the right → anonymous mode
                fire("move", map("tc", "Log", "beside", "editor", "side", "right"));
                LayoutModel.ModeNode anon = wm.getLayout().findModeOf("Log");
                assertTrue(anon.name, anon.name.startsWith("anonymousMode_"));
                assertTrue(wm.findMode(anon.name).isAnonymous());
                assertSame(anon.parent, wm.getLayout().findMode("editor").parent);
                // illegal moves are ignored
                fire("move", map("tc", "Nope", "mode", "editor"));
                fire("move", map("tc", "Main", "mode", "nowhere"));
                assertEquals(Arrays.asList("Files", "Main"), tcs(wm.getLayout().findMode("editor")));
                // resize with a wrong path/size count is ignored, a right one applied
                fire("resize", map("path", Arrays.asList(99), "sizes", Arrays.asList(1, 2)));
                LayoutModel.Split row = anon.parent;
                List<Object> sizes = new ArrayList<Object>();
                for (int i = 0; i < row.children.size(); i++) {
                    sizes.add(i == 0 ? (Object) 333 : "*");
                }
                fire("resize", map("path", pathOf(row), "sizes", sizes));
                assertEquals(333.0, row.sizes.get(0), 0.001);
                // maximize toggles; floating a mode removes it from the tree; dock brings it back
                fire("maximize", map("mode", "editor"));
                assertEquals("editor", wm.getLayout().maximized);
                fire("maximize", map("mode", "editor"));
                assertNull(wm.getLayout().maximized);
                fire("float", map("mode", anon.name, "x", 10, "y", 20, "w", 300, "h", 200));
                assertTrue(wm.getLayout().isFloating(anon.name));
                assertNull(LayoutModel.findIn(wm.getLayout().root, anon.name));
                fire("dock", map("mode", anon.name));
                assertFalse(wm.getLayout().isFloating(anon.name));
                assertNotNull(LayoutModel.findIn(wm.getLayout().root, anon.name));
                // minimise hides the selected component, dock back shows it
                TopComponent log = wm.findTopComponent("Log");
                fire("minimize", map("mode", anon.name, "side", "right"));
                assertTrue(wm.getLayout().isMinimised(anon.name));
                assertFalse(log.isShowing());
                fire("dock", map("mode", anon.name));
                assertTrue(log.isShowing());
                // close from the client honours closable
                log.setClosable(false);
                fire("close", map("tc", "Log"));
                assertTrue(log.isOpened());
                log.setClosable(true);
                fire("close", map("tc", "Log"));
                assertFalse(log.isOpened());
                // the anonymous mode held the row's remaining space; closing it must not leave only fixed sizes
                // (the editor was resized to 333px above) or the row stops filling the window
                LayoutModel.Split editorRow = wm.getLayout().findMode("editor").parent;
                assertTrue(editorRow.sizes.toString(), editorRow.sizes.contains(LayoutModel.STAR));
                assertEquals(LayoutModel.STAR, editorRow.sizes.get(editorRow.children.indexOf(wm.getLayout().findMode("editor"))), 0.0);
            }
        });
    }

    @Test
    public void savedLayoutWithoutRemainingSpaceIsRepaired() {
        LayoutModel m = new LayoutModel();
        m.setModeInfo("editor", Mode.Side.CENTER, 100);
        m.setModeInfo("properties", Mode.Side.RIGHT, 100);
        LayoutModel.Split row = new LayoutModel.Split("row");
        row.add(0, new LayoutModel.ModeNode("editor"), 1540);
        row.add(1, new LayoutModel.ModeNode("properties"), 300);
        m.root = row;
        m.ensureStars();
        assertEquals(Arrays.asList(LayoutModel.STAR, 300.0), row.sizes);
        // without a centre mode the largest child takes the rest
        LayoutModel.Split col = new LayoutModel.Split("col");
        col.add(0, new LayoutModel.ModeNode("properties"), 120);
        col.add(1, new LayoutModel.ModeNode("other"), 400);
        m.setModeInfo("other", Mode.Side.RIGHT, 200);
        m.root = col;
        m.ensureStars();
        assertEquals(Arrays.asList(120.0, LayoutModel.STAR), col.sizes);
    }

    /** Injects a client event for the dock, as the transport would. */
    private void fire(String type, Map<String, Object> data) {
        ui.handleEvents(Collections.<Object>singletonList(map("id", wm.getDock().getId(), "type", type, "data", data)));
    }

    private static List<Integer> pathOf(LayoutModel.Node n) {
        List<Integer> path = new ArrayList<Integer>();
        while (n.parent != null) {
            path.add(0, n.parent.children.indexOf(n));
            n = n.parent;
        }
        return path;
    }

    @Test
    public void layoutRoundTripsThroughJsonAndIsRestoredInANewUI() throws Exception {
        run(new Runnable() {
            @Override
            public void run() {
                wm.findTopComponent("Log").open();
                fire("move", map("tc", "Log", "beside", "editor", "side", "bottom"));
                wm.findTopComponent("Main").requestActive();
                wm.saveNow();
            }
        });
        Path file = session.getUserFiles().layoutFile();
        assertTrue(Files.exists(file));
        Map<String, Object> json = Json.parseObject(new String(Files.readAllBytes(file), "UTF-8"));
        LayoutModel copy = LayoutModel.fromJson(json);
        assertEquals(wm.getLayout().toJson(), copy.toJson());
        assertEquals("Main", copy.active);
        // a second tab restores the saved layout, including the anonymous mode and the selection
        sent.clear();
        LoggingTC.LOG.clear();
        openUI("u2");
        LayoutModel l2 = wm.getLayout();
        assertEquals(Arrays.asList("Log"), tcs(l2.findModeOf("Log")));
        assertTrue(l2.findModeOf("Log").name.startsWith("anonymousMode_"));
        assertEquals("Main", wm.getActivated().getTopComponentId());
        assertTrue(LoggingTC.LOG.toString(), LoggingTC.LOG.contains("Log:read:state-of-Log"));
    }

    @Test
    public void groupsOpenAndCloseTogether() {
        run(new Runnable() {
            @Override
            public void run() {
                TopComponentGroup g = wm.findTopComponentGroup("debug");
                assertNotNull(g);
                assertEquals(Arrays.asList("Log", "Veto"), g.getTopComponentIds());
                g.open();
                assertTrue(wm.findTopComponent("Log").isOpened());
                assertTrue(wm.findTopComponent("Veto").isOpened());
                ((VetoTC) wm.findTopComponent("Veto")).veto = false;
                g.close();
                assertFalse(wm.findTopComponent("Log").isOpened());
                assertFalse(wm.findTopComponent("Veto").isOpened());
            }
        });
    }

    @Test
    public void unknownEntriesInASavedLayoutAreDropped() throws Exception {
        session.close();
        LayoutModel l = new LayoutModel();
        LayoutModel.ModeNode ghost = new LayoutModel.ModeNode("ghostMode");
        ghost.tcs.add("GhostTC");
        ghost.tcs.add("Main");
        ((LayoutModel.Split) l.root).add(0, ghost, LayoutModel.STAR);
        l.active = "GhostTC";
        Authentication auth = new Authentication(Principal.builder("bob").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        DataDir dataDir = new DataDir(dir);
        UserFiles files = dataDir.userFiles("bob");
        files.writeString(files.layoutFile(), Json.write(l.toJson()));
        session = new UISession(auth, Locale.ENGLISH, files, DIRECT);
        openUI("u3");
        assertNull(wm.getLayout().findMode("ghostMode"));
        assertNull(wm.findTopComponent("GhostTC"));
        assertTrue(wm.findTopComponent("Main").isOpened());
        assertEquals("startup components still open", 2, wm.getOpened().size());
        assertNotNull(wm.getActivated());
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
