package org.webbeans.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/** The scene's object↔widget mapping, wire format and batching, events, layouts, resync and export on a headless UI. */
public class GraphSceneTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    /** A node object. */
    static final class Service {
        final String name;

        Service(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** An edge object. */
    static final class Call {
        final Service from;
        final Service to;

        Call(Service from, Service to) {
            this.from = from;
            this.to = to;
        }
    }

    private UISession session;
    private UI ui;
    private final List<String> sent = new ArrayList<String>();
    private GraphScene<Service, Call> scene;
    private final Service web = new Service("web");
    private final Service api = new Service("api");
    private final Service db = new Service("db");
    private final Call c1 = new Call(web, api);
    private final Call c2 = new Call(api, db);

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI u) {
                Panel root = new Panel(new Layout.Border());
                scene = new GraphScene<Service, Call>() {
                    @Override
                    protected NodeWidget attachNodeWidget(Service s) {
                        NodeWidget w = newNodeWidget().setLabel(s.name).setSecondaryLabel("service");
                        if (s == db) {
                            w.setShape(Shape.CYLINDER).addPin("in").setSide(Side.LEFT);
                        }
                        return w;
                    }

                    @Override
                    protected EdgeWidget attachEdgeWidget(Call c) {
                        return newEdgeWidget().setRouter(Router.ORTHOGONAL).setLabel("calls");
                    }
                };
                scene.addNode(web).setLocation(10, 20);
                scene.addNode(api).setLocation(200, 20);
                scene.addNode(db).setLocation(400, 20);
                scene.addEdge(c1);
                scene.setEdgeSource(c1, web);
                scene.setEdgeTarget(c1, api);
                root.add(scene, Layout.Border.CENTER);
                u.setContent(root);
            }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(provider));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, Locale.ENGLISH, null, DIRECT);
        ui = session.createUI("u1");
        connect(-1);
    }

    private void connect(final long lastSeq) {
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
                ui.connect(t, lastSeq);
            }
        });
    }

    @After
    public void tearDown() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> graphCalls(String message) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object o : Json.array(Json.parseObject(message), "ops")) {
            Map<String, Object> op = (Map<String, Object>) o;
            if ("call".equals(op.get("op")) && "graph".equals(op.get("m")) && ((Number) op.get("id")).intValue() == scene.getId()) {
                out.add((Map<String, Object>) ((List<Object>) op.get("args")).get(0));
            }
        }
        return out;
    }

    private Map<String, Object> lastGraph() {
        List<Map<String, Object>> calls = graphCalls(sent.get(sent.size() - 1));
        assertEquals("one graph call per flush", 1, calls.size());
        return calls.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(Map<String, Object> payload, String kind, String id) {
        Map<String, Object> m = (Map<String, Object>) payload.get(kind);
        return m == null ? null : (Map<String, Object>) m.get(id);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private void fire(final String type, final Map<String, Object> data) {
        run(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(Collections.<Object>singletonList(map("id", scene.getId(), "type", type, "data", data)));
            }
        });
    }

    @Test
    public void initialGraphIsOneCallWithEveryWidget() {
        Map<String, Object> g = graphCalls(sent.get(0)).get(0);
        Map<String, Object> webN = entry(g, "nodes", scene.findNodeWidget(web).getId());
        assertEquals("web", webN.get("l"));
        assertEquals("service", webN.get("l2"));
        assertEquals("rounded", webN.get("sh"));
        assertEquals(10.0, ((Number) webN.get("x")).doubleValue(), 0.0);
        Map<String, Object> dbN = entry(g, "nodes", scene.findNodeWidget(db).getId());
        assertEquals("cylinder", dbN.get("sh"));
        assertEquals("[{id=in, l=in, side=left}]", dbN.get("pins").toString());
        Map<String, Object> e = entry(g, "edges", scene.findEdgeWidget(c1).getId());
        assertEquals(map("n", scene.findNodeWidget(web).getId()), e.get("s"));
        assertEquals(map("n", scene.findNodeWidget(api).getId()), e.get("t"));
        assertEquals("orthogonal", e.get("r"));
        assertEquals("arrow", e.get("ts"));
        assertEquals("[{t=calls, at=0.5}]", e.get("lb").toString());
        assertTrue(((Map<?, ?>) g.get("notes")).isEmpty());
        assertEquals(Boolean.TRUE, g.get("reset"));
    }

    @Test
    public void changesOfOneFlushMergeIntoOneDescriptionAndRemovalsCancel() {
        run(new Runnable() {
            @Override
            public void run() {
                NodeWidget w = scene.findNodeWidget(api);
                w.setLabel("API");
                w.setShape(Shape.HEXAGON);
                w.setAccent("#f00");
                w.addPin("out").setLabel("Out").setSide(Side.RIGHT);
                scene.addEdge(c2);
                scene.setEdgeSourcePin(c2, api, "out");
                scene.setEdgeTargetPin(c2, db, "in");
                scene.findEdgeWidget(c2).setTargetShape(AnchorShape.DIAMOND_HOLLOW).setLineStyle(LineStyle.DASHED).setStrokeWidth(2);
                scene.addNote("note:1", "Primary path").attachTo(scene.findEdgeWidget(c2)).setLocation(300, 120).setBackground("#ffe");
                Service tmp = new Service("tmp");
                scene.addNode(tmp);
                scene.removeNode(tmp);          // added and removed in one flush: must not appear at all
            }
        });
        Map<String, Object> g = lastGraph();
        Map<String, Object> apiN = entry(g, "nodes", scene.findNodeWidget(api).getId());
        assertEquals("API", apiN.get("l"));
        assertEquals("hexagon", apiN.get("sh"));
        assertEquals("#f00", apiN.get("ac"));
        assertEquals("[{id=out, l=Out, side=right}]", apiN.get("pins").toString());
        assertEquals(1, ((Map<?, ?>) g.get("nodes")).size());
        Map<String, Object> e = entry(g, "edges", scene.findEdgeWidget(c2).getId());
        assertEquals(map("n", scene.findNodeWidget(api).getId(), "p", "out"), e.get("s"));
        assertEquals(map("n", scene.findNodeWidget(db).getId(), "p", "in"), e.get("t"));
        assertEquals("diamond_hollow", e.get("ts"));
        assertEquals("dashed", e.get("ls"));
        assertEquals(2.0, ((Number) e.get("sw")).doubleValue(), 0.0);
        Map<String, Object> n = entry(g, "notes", "note:1");
        assertEquals("Primary path", n.get("tx"));
        assertEquals(scene.findEdgeWidget(c2).getId(), n.get("to"));
        assertEquals("#ffe", n.get("bg"));

        // object model
        assertSame(api, scene.getEdgeSource(c2));
        assertSame(db, scene.getEdgeTarget(c2));
        assertEquals(Arrays.asList(c1, c2), scene.findNodeEdges(api, true, true));
        assertSame(api, scene.findWidget(api).getObject());
        assertSame(api, scene.findNodeWidget(api).getPin("out").getObject());

        // removing a node takes its edges and the note's attachment
        final String dbId = scene.findNodeWidget(db).getId();
        final String c2Id = scene.findEdgeWidget(c2).getId();
        run(new Runnable() {
            @Override
            public void run() {
                scene.removeNode(db);
            }
        });
        g = lastGraph();
        assertTrue(((Map<?, ?>) g.get("nodes")).containsKey(dbId));
        assertNull(((Map<?, ?>) g.get("nodes")).get(dbId));
        assertTrue(((Map<?, ?>) g.get("edges")).containsKey(c2Id));
        assertNull(((Map<?, ?>) g.get("edges")).get(c2Id));
        assertFalse(scene.getEdges().contains(c2));
        assertNull(scene.getNote("note:1").getAttachedTo());
        assertNull(entry(g, "notes", "note:1").get("to"));
    }

    @Test
    public void nothingIsSentWhenNothingChanged() {
        int before = sent.size();
        run(new Runnable() {
            @Override
            public void run() {
                scene.getNodes();
            }
        });
        assertTrue(sent.size() == before || graphCalls(sent.get(sent.size() - 1)).isEmpty());
    }

    @Test
    public void selectionMoveMeasureRenameAndDeleteEvents() {
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                scene.addSelectListener(new Listener<SelectionEvent>() {
                    @Override
                    public void onEvent(SelectionEvent e) {
                        log.add("select:" + e.getObjects());
                    }
                });
                scene.addMoveListener(new Listener<MoveEvent>() {
                    @Override
                    public void onEvent(MoveEvent e) {
                        log.add("move:" + e.getWidgets());
                    }
                });
                scene.addRenameListener(new Listener<RenameEvent>() {
                    @Override
                    public void onEvent(RenameEvent e) {
                        e.accept();
                        log.add("rename:" + e.getObject() + "=" + e.getText());
                    }
                });
                scene.addDeleteListener(new Listener<DeleteEvent>() {
                    @Override
                    public void onEvent(DeleteEvent e) {
                        log.add("delete:" + e.getObjects());
                    }
                });
            }
        });
        String webId = scene.findNodeWidget(web).getId();
        String c1Id = scene.findEdgeWidget(c1).getId();
        fire("select", map("ids", Arrays.asList(webId, c1Id)));
        assertEquals("[select:[web, " + c1 + "]]", log.toString());
        assertEquals(Arrays.asList(scene.findNodeWidget(web), scene.findEdgeWidget(c1)), scene.getSelectedWidgets());
        assertSame(web, scene.getSelectionLookup().lookup(Service.class));
        assertSame(c1, scene.getSelectionLookup().lookup(Call.class));
        assertNotNull(scene.getSelectionLookup().lookup(NodeWidget.class));

        fire("move", map("moves", map(webId, Arrays.asList(55, 66))));
        assertEquals(new Point(55, 66), scene.findNodeWidget(web).getLocation());
        assertEquals("move:[NodeWidget[" + webId + "]]", log.get(1));

        fire("measure", map("sizes", map(webId, Arrays.asList(120, 44))));
        assertEquals(new Size(120, 44), scene.findNodeWidget(web).getSize());

        fire("rename", map("id", webId, "text", "Web tier"));
        assertEquals("Web tier", scene.findNodeWidget(web).getLabel());
        assertEquals("Web tier", entry(lastGraph(), "nodes", webId).get("l"));

        fire("delete", map("ids", Arrays.asList(webId)));
        assertEquals("delete:[web, " + c1 + "]", log.get(log.size() - 1));   // the current selection, not the ids sent

        // server-side selection reaches the client and listeners
        run(new Runnable() {
            @Override
            public void run() {
                scene.setSelectedObjects(Collections.singleton(api));
            }
        });
        assertEquals("select:[api]", log.get(log.size() - 1));
        assertTrue(sent.get(sent.size() - 1).contains("\"m\":\"select\""));
        assertEquals(Collections.singleton(api), scene.getSelectedObjects());
    }

    @Test
    public void connectProviderCreatesEdgesAndFlagsSources() {
        run(new Runnable() {
            @Override
            public void run() {
                scene.setConnectProvider(new ConnectProvider() {
                    @Override
                    public boolean isSource(Widget source) {
                        return source instanceof NodeWidget && source.getObject() != db;
                    }

                    @Override
                    public boolean canConnect(Widget source, Widget target) {
                        return EdgeWidget.nodeOf(target) != EdgeWidget.nodeOf(source);
                    }

                    @Override
                    public void connect(Widget source, Widget target) {
                        Call c = new Call((Service) source.getObject(), (Service) target.getObject());
                        scene.addEdge(c);
                        scene.findEdgeWidget(c).setSource(source).setTarget(target);
                    }
                });
            }
        });
        assertEquals(Boolean.TRUE, scene.get(GraphScene.CONNECTABLE));
        Map<String, Object> g = lastGraph();
        assertEquals(Boolean.TRUE, entry(g, "nodes", scene.findNodeWidget(web).getId()).get("cs"));
        assertNull(entry(g, "nodes", scene.findNodeWidget(db).getId()).get("cs"));

        fire("connect", map("source", scene.findNodeWidget(api).getId(), "target", scene.findNodeWidget(db).getId() + "/in"));
        assertEquals(2, scene.getEdges().size());
        Call made = null;
        for (Call c : scene.getEdges()) {
            if (c != c1) {
                made = c;
            }
        }
        assertSame(api, made.from);
        assertSame(db, made.to);
        assertTrue(scene.findEdgeWidget(made).getTarget() instanceof PinWidget);
        // refused: same node
        fire("connect", map("source", scene.findNodeWidget(api).getId(), "target", scene.findNodeWidget(api).getId()));
        assertEquals(2, scene.getEdges().size());
    }

    @Test
    public void layoutsPlaceEveryNodeApartAndCanAnimate() {
        run(new Runnable() {
            @Override
            public void run() {
                scene.addEdge(c2);
                scene.setEdgeSource(c2, api);
                scene.setEdgeTarget(c2, db);
                scene.layout(new TreeGraphLayout(), true);
            }
        });
        NodeWidget w = scene.findNodeWidget(web);
        NodeWidget a = scene.findNodeWidget(api);
        NodeWidget d = scene.findNodeWidget(db);
        assertTrue(w.getLocation().y < a.getLocation().y && a.getLocation().y < d.getLocation().y);
        assertEquals(w.getLocation().x, a.getLocation().x, 0.001);
        assertEquals(Boolean.TRUE, entry(lastGraph(), "nodes", w.getId()).get("anim"));
        assertNull("the flag is one-shot", w.toJson().get("anim"));

        run(new Runnable() {
            @Override
            public void run() {
                scene.layout(new TreeGraphLayout().setOrientation(TreeGraphLayout.Orientation.LEFT_RIGHT), false);
            }
        });
        assertTrue(w.getLocation().x < a.getLocation().x && a.getLocation().x < d.getLocation().x);
        assertNull(entry(lastGraph(), "nodes", w.getId()).get("anim"));

        for (GraphLayout l : new GraphLayout[] {new GridGraphLayout().setColumns(2), new CircularGraphLayout(), new ForceGraphLayout().setIterations(50)}) {
            final GraphLayout layout = l;
            run(new Runnable() {
                @Override
                public void run() {
                    scene.layout(layout, false);
                }
            });
            List<NodeWidget> ns = Arrays.asList(w, a, d);
            for (int i = 0; i < ns.size(); i++) {
                for (int j = i + 1; j < ns.size(); j++) {
                    double dx = ns.get(i).getLocation().x - ns.get(j).getLocation().x;
                    double dy = ns.get(i).getLocation().y - ns.get(j).getLocation().y;
                    assertTrue(l.getClass().getSimpleName() + " overlaps", Math.sqrt(dx * dx + dy * dy) > 30);
                }
            }
        }
    }

    @Test
    public void resyncResendsEverythingAndExportCompletes() throws Exception {
        run(new Runnable() {
            @Override
            public void run() {
                scene.addNote("note:1", "hello");
                scene.setSelectedObjects(Collections.singleton(web));
            }
        });
        sent.clear();
        connect(-1);              // lost history: full init
        boolean found = false;
        for (String m : sent) {
            for (Map<String, Object> g : graphCalls(m)) {
                if (Boolean.TRUE.equals(g.get("reset"))) {
                    found = true;
                    assertEquals(3, ((Map<?, ?>) g.get("nodes")).size());
                    assertEquals(1, ((Map<?, ?>) g.get("edges")).size());
                    assertEquals(1, ((Map<?, ?>) g.get("notes")).size());
                }
            }
        }
        assertTrue(found);
        assertTrue(sent.get(sent.size() - 1).contains("\"m\":\"select\""));

        final List<CompletableFuture<String>> svg = new ArrayList<CompletableFuture<String>>();
        run(new Runnable() {
            @Override
            public void run() {
                svg.add(scene.exportSvg());
            }
        });
        fire("export", map("id", 1, "format", "svg", "data", "<svg/>"));
        assertEquals("<svg/>", svg.get(0).get(1, TimeUnit.SECONDS));
    }
}
