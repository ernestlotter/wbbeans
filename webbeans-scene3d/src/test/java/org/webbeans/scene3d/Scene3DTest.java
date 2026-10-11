package org.webbeans.scene3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
import org.webbeans.ui.BlobStore;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/** The scene graph's wire format, batching, shared definitions, events and the glTF reader on a headless UI. */
public class Scene3DTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private UISession session;
    private UI ui;
    private final List<String> sent = new ArrayList<String>();
    private Scene3D scene;
    private Mesh box;
    private Material blue;

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI u) {
                Panel root = new Panel(new Layout.Border());
                scene = new Scene3D();
                blue = Material.standard(0x3b82f6);
                box = scene.getRoot().addChild(new Mesh("box", Geometry.box(1, 1, 1), blue));
                box.setPosition(1, 2, 3).setDraggable(DragConstraint.plane(Axis.Y, 2).snap(0.5));
                scene.getRoot().add(new AmbientLight(0xffffff, 0.5));
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

    private void run(Runnable r) {
        ui.access(r);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sceneCalls(String message) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object o : Json.array(Json.parseObject(message), "ops")) {
            Map<String, Object> op = (Map<String, Object>) o;
            if ("call".equals(op.get("op")) && "scene".equals(op.get("m")) && ((Number) op.get("id")).intValue() == scene.getId()) {
                out.add((Map<String, Object>) ((List<Object>) op.get("args")).get(0));
            }
        }
        return out;
    }

    private Map<String, Object> lastScene() {
        List<Map<String, Object>> calls = sceneCalls(sent.get(sent.size() - 1));
        assertEquals("one scene call per flush", 1, calls.size());
        return calls.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodeOps(Map<String, Object> payload) {
        List<Object> l = Json.array(payload, "nodes");
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (l != null) {
            for (Object o : l) {
                out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    private static Map<String, Object> opFor(List<Map<String, Object>> ops, String id, String op) {
        for (Map<String, Object> m : ops) {
            if (id.equals(m.get("id")) && op.equals(m.get("op"))) {
                return m;
            }
        }
        return null;
    }

    /** JSON numbers come back as Long or Double: compare as doubles. */
    private static List<Double> d(Object list) {
        List<Double> out = new ArrayList<Double>();
        for (Object o : (List<?>) list) {
            out.add(((Number) o).doubleValue());
        }
        return out;
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
    public void initCarriesTheWholeSceneWithSharedDefinitions() {
        Map<String, Object> init = Json.parseObject(sent.get(0));
        assertEquals("init", init.get("t"));
        Map<String, Object> full = sceneCalls(sent.get(0)).get(0);
        assertEquals(Boolean.TRUE, full.get("reset"));
        List<Map<String, Object>> ops = nodeOps(full);
        assertEquals("root, box, light", 3, ops.size());
        assertEquals("group", ops.get(0).get("t"));
        assertNull("root has no parent", ops.get(0).get("p"));
        Map<String, Object> b = ops.get(1);
        assertEquals("mesh", b.get("t"));
        assertEquals("box", b.get("name"));
        assertEquals(ops.get(0).get("id"), b.get("p"));
        assertEquals(Arrays.asList(1.0, 2.0, 3.0), d(b.get("pos")));
        assertEquals(blue.getId(), b.get("m"));
        assertEquals(Geometry.box(1, 1, 1).getId(), b.get("g"));
        Map<?, ?> drag = (Map<?, ?>) b.get("drag");
        assertEquals(Arrays.asList(0.0, 1.0, 0.0, 2.0), d(drag.get("plane")));
        assertEquals(0.5, ((Number) drag.get("snap")).doubleValue(), 0);
        assertEquals("light.ambient", ops.get(2).get("t"));
        Map<?, ?> geoms = (Map<?, ?>) full.get("geoms");
        assertEquals("box", ((Map<?, ?>) geoms.get(Geometry.box(1, 1, 1).getId())).get("prim"));
        Map<?, ?> mats = (Map<?, ?>) full.get("mats");
        Map<?, ?> m = (Map<?, ?>) mats.get(blue.getId());
        assertEquals("standard", m.get("type"));
        assertEquals(0x3b82f6, ((Number) m.get("color")).intValue());
        assertEquals("persp", ((Map<?, ?>) full.get("camera")).get("proj"));
        assertEquals("orbit", ((Map<?, ?>) full.get("controls")).get("mode"));
        assertFalse(scene.hasPending());
    }

    @Test
    public void mutationsMergeIntoOneCallAndDefinitionsAreSentOnce() {
        run(new Runnable() {
            @Override
            public void run() {
                box.setPosition(4, 5, 6);
                box.setPosition(7, 8, 9);          // last set wins
                box.setVisible(false);
                Mesh second = scene.getRoot().addChild(new Mesh(Geometry.box(1, 1, 1), blue)); // same geometry + material
                second.setPosition(1, 0, 0);       // set after add: folded into the add
                Mesh temp = scene.getRoot().addChild(new Mesh(Geometry.sphere(2), blue));
                scene.getRoot().remove(temp);      // add + remove in one flush cancel out
                scene.getCamera().setPosition(0, 0, 20);
            }
        });
        Map<String, Object> p = lastScene();
        List<Map<String, Object>> ops = nodeOps(p);
        assertEquals(2, ops.size());
        Map<String, Object> set = opFor(ops, box.getId(), "set");
        assertNotNull(set);
        assertEquals(Arrays.asList(7.0, 8.0, 9.0), d(set.get("pos")));
        assertEquals(Boolean.FALSE, set.get("vis"));
        Map<String, Object> add = ops.get(1);
        assertEquals("add", add.get("op"));
        assertEquals(Arrays.asList(1.0, 0.0, 0.0), d(add.get("pos")));
        assertNull("box geometry and material were sent with init", p.get("geoms"));
        assertNull(p.get("mats"));
        assertEquals(Arrays.asList(0.0, 0.0, 20.0), d(((Map<?, ?>) p.get("camera")).get("pos")));
        assertEquals(3, scene.nodeCount() - 1); // root + box + light + second
    }

    @Test
    public void removingAGroupSendsOneRemoveAndDropsTheSelection() {
        final Group[] g = new Group[1];
        run(new Runnable() {
            @Override
            public void run() {
                g[0] = scene.getRoot().addChild(new Group("g"));
                g[0].add(new Mesh(Geometry.sphere(1), blue), new Mesh(Geometry.sphere(1), blue));
            }
        });
        assertEquals(3, nodeOps(lastScene()).size());
        final String gid = g[0].getId();
        final Node3D child = g[0].getChildren().get(0);
        run(new Runnable() {
            @Override
            public void run() {
                scene.setSelection(child);
            }
        });
        assertEquals(Collections.singletonList(child.getId()), scene.get(Scene3D.SELECTION));
        run(new Runnable() {
            @Override
            public void run() {
                scene.getRoot().remove(g[0]);
            }
        });
        List<Map<String, Object>> ops = nodeOps(lastScene());
        assertEquals("children are implied by the parent's remove", 1, ops.size());
        assertEquals("remove", ops.get(0).get("op"));
        assertEquals(gid, ops.get(0).get("id"));
        assertNull(scene.get(Scene3D.SELECTION));
        assertNull(scene.findNode(gid));
        assertNull(child.getScene());
        assertEquals(2, scene.nodeCount() - 1);
    }

    @Test
    public void sharedMaterialChangeIsOneDefinitionUpdate() {
        run(new Runnable() {
            @Override
            public void run() {
                scene.getRoot().add(new Mesh(Geometry.sphere(1), blue), new Mesh(Geometry.sphere(1), blue));
            }
        });
        run(new Runnable() {
            @Override
            public void run() {
                blue.setColor(0xff0000).setOpacity(0.5);
            }
        });
        Map<String, Object> p = lastScene();
        assertNull(p.get("nodes"));
        Map<?, ?> m = (Map<?, ?>) ((Map<?, ?>) p.get("mats")).get(blue.getId());
        assertEquals(0xff0000, ((Number) m.get("color")).intValue());
        assertEquals(0.5, ((Number) m.get("opacity")).doubleValue(), 0);
        assertEquals(Boolean.TRUE, m.get("transparent"));
        // textures referenced by a material are defined before it
        run(new Runnable() {
            @Override
            public void run() {
                blue.setMap(Texture.image(new byte[] {1, 2, 3}, "image/png").repeat(2, 2));
            }
        });
        p = lastScene();
        Map<?, ?> texs = (Map<?, ?>) p.get("texs");
        assertEquals(1, texs.size());
        Map<?, ?> t = (Map<?, ?>) texs.values().iterator().next();
        assertTrue(t.get("url").toString().startsWith("/wb/blob/"));
        assertEquals(Arrays.asList(2.0, 2.0), d(t.get("repeat")));
        assertEquals("repeat", t.get("wrap"));
        assertEquals(texs.keySet().iterator().next(), ((Map<?, ?>) ((Map<?, ?>) p.get("mats")).get(blue.getId())).get("map"));
    }

    @Test
    public void dragIsValidatedAgainstTheConstraintAndCanBeRejected() {
        final List<DragEvent> seen = new ArrayList<DragEvent>();
        run(new Runnable() {
            @Override
            public void run() {
                scene.addListener(Scene3D.DRAG, new Listener<DragEvent>() {
                    @Override
                    public void onEvent(DragEvent e) {
                        seen.add(e);
                        if (e.getPhase() == DragEvent.Phase.END && e.getPosition().x > 5) {
                            e.reject();
                        }
                    }
                });
            }
        });
        // plane y=2, snap 0.5: the reported y is replaced, x/z snapped
        fire("drag", map("node", box.getId(), "phase", "start", "pos", Arrays.asList(1.0, 2.0, 3.0)));
        fire("drag", map("node", box.getId(), "phase", "move", "pos", Arrays.asList(2.3, 7.0, 3.1)));
        assertEquals(new Vector3(2.5, 2.0, 3.0), box.getPosition());
        assertEquals(new Vector3(2.5, 2.0, 3.0), seen.get(1).getPosition());
        assertEquals(new Vector3(1, 2, 3), seen.get(1).getStart());
        // the corrected position goes back to the client in the same flush
        Map<String, Object> set = opFor(nodeOps(lastScene()), box.getId(), "set");
        assertEquals(Arrays.asList(2.5, 2.0, 3.0), d(set.get("pos")));
        // accepted end
        fire("drag", map("node", box.getId(), "phase", "end", "pos", Arrays.asList(3.0, 2.0, 3.0)));
        assertEquals(new Vector3(3, 2, 3), box.getPosition());
        // rejected end: snaps back to where this drag started
        fire("drag", map("node", box.getId(), "phase", "start", "pos", Arrays.asList(3.0, 2.0, 3.0)));
        fire("drag", map("node", box.getId(), "phase", "end", "pos", Arrays.asList(9.0, 2.0, 3.0)));
        assertEquals(new Vector3(3, 2, 3), box.getPosition());
        set = opFor(nodeOps(lastScene()), box.getId(), "set");
        assertEquals(Arrays.asList(3.0, 2.0, 3.0), d(set.get("pos")));
        // a non-draggable node is always snapped back
        final Node3D light = scene.getRoot().getChildren().get(1);
        fire("drag", map("node", light.getId(), "phase", "end", "pos", Arrays.asList(9.0, 9.0, 9.0)));
        assertEquals(Vector3.ZERO, light.getPosition());
    }

    @Test
    public void pickCarriesTheNodeAndItsUserDataInTheLookup() {
        final List<PickEvent> picks = new ArrayList<PickEvent>();
        run(new Runnable() {
            @Override
            public void run() {
                box.setUserData("Mars");
                scene.addListener(Scene3D.PICK, new Listener<PickEvent>() {
                    @Override
                    public void onEvent(PickEvent e) {
                        picks.add(e);
                        scene.setSelection(e.getNode());
                    }
                });
            }
        });
        fire("pick", map("node", box.getId(), "point", Arrays.asList(1.0, 2.5, 3.0), "button", 0, "ctrl", true));
        PickEvent e = picks.get(0);
        assertSame(box, e.getNode());
        assertEquals("Mars", e.getUserData());
        assertEquals("Mars", e.getLookup().lookup(String.class));
        assertSame(box, e.getLookup().lookup(Mesh.class));
        assertEquals(2.5, e.getPoint().y, 0);
        assertTrue(e.isCtrl());
        assertEquals(Collections.singletonList(box.getId()), scene.get(Scene3D.SELECTION));
        assertSame(box, scene.findByUserData("Mars"));
        fire("pick", map("button", 0));
        assertNull(picks.get(1).getNode());
        assertNull(scene.get(Scene3D.SELECTION));
    }

    @Test
    public void animationsAreOpsAndCompletionUpdatesTheNode() {
        final boolean[] done = {false};
        final Animation[] move = new Animation[1];
        run(new Runnable() {
            @Override
            public void run() {
                box.animate(Animation.rotate(Axis.Y, 0.5));
                move[0] = Animation.moveTo(new Vector3(10, 2, 3), 1000).easing(Easing.EASE_IN_OUT).onComplete(new Runnable() {
                    @Override
                    public void run() {
                        done[0] = true;
                    }
                });
                box.animate(move[0]);
            }
        });
        Map<String, Object> p = lastScene();
        List<?> anim = (List<?>) p.get("anim");
        assertEquals(2, anim.size());
        Map<?, ?> rot = (Map<?, ?>) anim.get(0);
        assertEquals("play", rot.get("op"));
        assertEquals("rotate", rot.get("kind"));
        assertEquals(box.getId(), rot.get("node"));
        assertEquals(Boolean.TRUE, rot.get("loop"));
        Map<?, ?> mv = (Map<?, ?>) anim.get(1);
        assertEquals("ease-in-out", mv.get("easing"));
        assertEquals(1000, ((Number) mv.get("duration")).intValue());
        assertEquals(2, box.getAnimations().size());
        run(new Runnable() {
            @Override
            public void run() {
                move[0].pause();
            }
        });
        assertEquals("pause", ((Map<?, ?>) ((List<?>) lastScene().get("anim")).get(0)).get("op"));
        fire("animDone", map("id", move[0].getId(), "node", box.getId(), "pos", Arrays.asList(10.0, 2.0, 3.0)));
        assertTrue(done[0]);
        assertEquals(new Vector3(10, 2, 3), box.getPosition());
        assertEquals("the finished animation is gone, the looping one stays", 1, box.getAnimations().size());
        // a resync re-plays the running animations with the full scene
        sent.clear();
        connect(-1);
        Map<String, Object> full = sceneCalls(sent.get(0)).get(0);
        assertEquals(1, ((List<?>) full.get("anim")).size());
    }

    @Test
    public void snapshotAndCameraRoundTrips() throws Exception {
        final List<CompletableFuture<byte[]>> f = new ArrayList<CompletableFuture<byte[]>>();
        run(new Runnable() {
            @Override
            public void run() {
                f.add(scene.snapshot(320, 200));
                scene.fit();
            }
        });
        List<?> cmd = (List<?>) lastScene().get("cmd");
        assertEquals(2, cmd.size());
        Map<?, ?> snap = (Map<?, ?>) cmd.get(0);
        assertEquals("snapshot", snap.get("c"));
        assertEquals(320, ((Number) snap.get("w")).intValue());
        assertEquals("fit", ((Map<?, ?>) cmd.get(1)).get("c"));
        fire("snapshot", map("id", snap.get("id"), "data", "data:image/png;base64,iVBORw0KGgo="));
        assertEquals(8, f.get(0).get(1, TimeUnit.SECONDS).length);
        fire("camera", map("pos", Arrays.asList(1.0, 2.0, 3.0), "target", Arrays.asList(0.0, 0.0, 0.0)));
        assertEquals(new Vector3(1, 2, 3), scene.getCameraState().getPosition());
        run(new Runnable() {
            @Override
            public void run() {
                scene.flyTo(new Vector3(5, 5, 5), Vector3.ZERO, 500);
            }
        });
        Map<?, ?> fly = (Map<?, ?>) ((List<?>) lastScene().get("cmd")).get(0);
        assertEquals("flyTo", fly.get("c"));
        assertEquals(Arrays.asList(5.0, 5.0, 5.0), d(fly.get("pos")));
        assertEquals(new Vector3(5, 5, 5), scene.getCameraState().getPosition());
        fire("ready", map("webgl2", true, "renderer", "SwiftShader"));
        assertTrue(scene.isReady());
        assertEquals("SwiftShader", scene.getCapabilities().get("renderer"));
    }

    @Test
    public void sceneBinderDiffsByKey() {
        final Group holder = new Group("planets");
        final List<String> created = new ArrayList<String>();
        final SceneBinder<String> binder = new SceneBinder<String>(holder, null, new java.util.function.Function<String, Node3D>() {
            @Override
            public Node3D apply(String s) {
                created.add(s);
                return new Mesh(s, Geometry.sphere(1), blue);
            }
        }, new java.util.function.BiConsumer<String, Node3D>() {
            @Override
            public void accept(String s, Node3D n) {
                n.setPosition(s.length(), 0, 0);
            }
        });
        run(new Runnable() {
            @Override
            public void run() {
                scene.getRoot().add(holder);
                binder.setItems(Arrays.asList("Mercury", "Venus", "Earth"));
            }
        });
        assertEquals(Arrays.asList("Mercury", "Venus", "Earth"), created);
        assertEquals(4, nodeOps(lastScene()).size());
        assertEquals("Venus", binder.nodeFor("Venus").getUserData());
        assertEquals("Earth", binder.itemFor(binder.nodeFor("Earth")));
        run(new Runnable() {
            @Override
            public void run() {
                binder.setItems(Arrays.asList("Venus", "Earth", "Mars"));
            }
        });
        assertEquals("only Mars was created", 4, created.size());
        List<Map<String, Object>> ops = nodeOps(lastScene());
        assertEquals("one remove, one add; unchanged positions are no-ops on the wire", 2, ops.size());
        assertEquals("remove", ops.get(0).get("op"));
        assertEquals("add", ops.get(1).get("op"));
        assertEquals("Mars", ops.get(1).get("name"));
        assertEquals(3, holder.getChildCount());
    }

    @Test
    public void instancedMeshSendsSmallUpdatesAndBlobsForLargeSets() {
        final InstancedMesh[] im = new InstancedMesh[1];
        run(new Runnable() {
            @Override
            public void run() {
                im[0] = scene.getRoot().addChild(new InstancedMesh(Geometry.sphere(0.1), blue, 3));
                im[0].setInstanceTransform(1, new Vector3(1, 2, 3), Quaternion.IDENTITY, new Vector3(2, 2, 2));
            }
        });
        Map<String, Object> add = nodeOps(lastScene()).get(0);
        Map<?, ?> inst = (Map<?, ?>) add.get("inst");
        assertEquals(3, ((Number) inst.get("count")).intValue());
        List<?> xf = (List<?>) inst.get("xf");
        assertEquals(48, xf.size());
        assertEquals(2.0, ((Number) xf.get(16)).doubleValue(), 0);  // scale x of instance 1
        assertEquals(1.0, ((Number) xf.get(28)).doubleValue(), 0);  // translation x of instance 1
        run(new Runnable() {
            @Override
            public void run() {
                im[0].setInstancePosition(2, new Vector3(5, 5, 5));
                im[0].setInstanceColor(2, 0xff0000);
            }
        });
        Map<String, Object> set = opFor(nodeOps(lastScene()), im[0].getId(), "set");
        List<?> upd = (List<?>) ((Map<?, ?>) set.get("inst")).get("upd");
        assertEquals("one partial update per touched instance", 1, upd.size());
        assertEquals(2, ((Number) ((Map<?, ?>) upd.get(0)).get("i")).intValue());
        assertEquals(Arrays.asList(1.0, 0.0, 0.0), d(((Map<?, ?>) upd.get(0)).get("col")));
        run(new Runnable() {
            @Override
            public void run() {
                im[0].setCount(1000);
            }
        });
        inst = (Map<?, ?>) opFor(nodeOps(lastScene()), im[0].getId(), "set").get("inst");
        String blob = (String) inst.get("blob");
        assertNotNull("large sets travel as blobs", blob);
        assertEquals(1000 * 16 * 4 + 1000 * 3 * 4, BlobStore.getDefault().get(blob).getBytes().length);
    }

    @Test
    public void bufferGeometryIsAContentAddressedBlob() {
        Geometry g = Geometry.buffer().positions(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}).indices(new int[] {0, 1, 2}).computeNormals(true).build();
        Geometry same = Geometry.buffer().positions(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}).indices(new int[] {0, 1, 2}).computeNormals(true).build();
        assertEquals(g, same);
        assertEquals(3, g.getVertexCount());
        assertEquals(1, g.getTriangleCount());
        String blob = (String) g.toJson().get("blob");
        byte[] bytes = BlobStore.getDefault().get(blob).getBytes();
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int hlen = bb.getInt();
        byte[] h = new byte[hlen];
        bb.get(h);
        Map<String, Object> header = Json.parseObject(new String(h, StandardCharsets.UTF_8));
        Map<?, ?> pos = (Map<?, ?>) ((Map<?, ?>) header.get("attrs")).get("position");
        assertEquals(3, ((Number) pos.get("count")).intValue());
        assertEquals(36, ((Number) ((Map<?, ?>) header.get("index")).get("offset")).intValue());
        assertEquals(Boolean.TRUE, header.get("computeNormals"));
        assertEquals(4 + hlen + 36 + 12, bytes.length);
        assertEquals(1f, bb.getFloat(4 + hlen + 12), 0);
    }

    @Test
    public void gltfReaderBuildsTheHierarchyMaterialsAndGeometry() throws Exception {
        // a GLB with one triangle, a translated child node, a red metallic material and a tiny embedded image
        float[] pos = {0, 0, 0, 1, 0, 0, 0, 1, 0};
        float[] nrm = {0, 0, 1, 0, 0, 1, 0, 0, 1};
        float[] uv = {0, 0, 1, 0, 0, 1};
        int[] idx = {0, 1, 2};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};
        ByteBuffer bin = ByteBuffer.allocate(pos.length * 4 + nrm.length * 4 + uv.length * 4 + idx.length * 4 + png.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : pos) {
            bin.putFloat(f);
        }
        for (float f : nrm) {
            bin.putFloat(f);
        }
        for (float f : uv) {
            bin.putFloat(f);
        }
        for (int i : idx) {
            bin.putInt(i);
        }
        bin.put(png);
        String json = "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]}],"
                + "\"nodes\":[{\"name\":\"parent\",\"children\":[1],\"translation\":[1,2,3]},"
                + "{\"name\":\"tri\",\"mesh\":0,\"matrix\":[2,0,0,0, 0,2,0,0, 0,0,2,0, 4,5,6,1]}],"
                + "\"meshes\":[{\"name\":\"triangle\",\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,\"TEXCOORD_0\":2},\"indices\":3,\"material\":0}]}],"
                + "\"materials\":[{\"name\":\"red\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,0,0,1],\"metallicFactor\":0.9,\"roughnessFactor\":0.2,\"baseColorTexture\":{\"index\":0}},\"doubleSided\":true}],"
                + "\"textures\":[{\"source\":0}],\"images\":[{\"bufferView\":4,\"mimeType\":\"image/png\"}],"
                + "\"buffers\":[{\"byteLength\":" + bin.capacity() + "}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},{\"buffer\":0,\"byteOffset\":36,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":72,\"byteLength\":24},{\"buffer\":0,\"byteOffset\":96,\"byteLength\":12},{\"buffer\":0,\"byteOffset\":108,\"byteLength\":8}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},{\"bufferView\":1,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"},{\"bufferView\":3,\"componentType\":5125,\"count\":3,\"type\":\"SCALAR\"}]}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int jsonPad = (4 - jsonBytes.length % 4) % 4;
        int binPad = (4 - bin.capacity() % 4) % 4;
        ByteBuffer glb = ByteBuffer.allocate(12 + 8 + jsonBytes.length + jsonPad + 8 + bin.capacity() + binPad).order(ByteOrder.LITTLE_ENDIAN);
        glb.put("glTF".getBytes(StandardCharsets.US_ASCII)).putInt(2).putInt(glb.capacity());
        glb.putInt(jsonBytes.length + jsonPad).putInt(0x4E4F534A).put(jsonBytes);
        for (int i = 0; i < jsonPad; i++) {
            glb.put((byte) ' ');
        }
        glb.putInt(bin.capacity() + binPad).putInt(0x004E4942).put(bin.array());
        for (int i = 0; i < binPad; i++) {
            glb.put((byte) 0);
        }
        Group model = Gltf.load(glb.array());
        assertEquals(1, model.getChildCount());
        Group parent = (Group) model.getChildren().get(0);
        assertEquals("parent", parent.getName());
        assertEquals(new Vector3(1, 2, 3), parent.getPosition());
        Group tri = (Group) parent.getChildren().get(0);
        assertEquals(new Vector3(4, 5, 6), tri.getPosition());
        assertEquals(2.0, tri.getScale().x, 1e-9);
        assertEquals(Quaternion.IDENTITY, tri.getRotation());
        Mesh mesh = (Mesh) tri.getChildren().get(0);
        assertEquals("triangle", mesh.getName());
        assertEquals(3, mesh.getGeometry().getVertexCount());
        assertEquals(1, mesh.getGeometry().getTriangleCount());
        Material m = mesh.getMaterial();
        assertEquals(0xff0000, m.getColor());
        assertEquals(0.9, m.getMetalness(), 1e-9);
        assertEquals(Material.Side.DOUBLE, m.getSide());
        assertNotNull(m.getMap());
        assertEquals(png.length, BlobStore.getDefault().get(m.getMap().getUrl().substring("/wb/blob/".length())).getBytes().length);
        // it drops into a scene like anything else
        run(new Runnable() {
            @Override
            public void run() {
                scene.getRoot().add(model);
            }
        });
        Map<String, Object> p = lastScene();
        assertEquals(4, nodeOps(p).size());
        assertNotNull(((Map<?, ?>) p.get("geoms")).get(mesh.getGeometry().getId()));
        assertNotNull(((Map<?, ?>) p.get("texs")).get(m.getMap().getId()));
    }

    @Test
    public void explorerChildrenMirrorTheGraphAndSelectionSyncsBothWays() throws Exception {
        final org.webbeans.explorer.ExplorerManager em = new org.webbeans.explorer.ExplorerManager();
        final org.webbeans.nodes.AbstractNode[] rootNode = new org.webbeans.nodes.AbstractNode[1];
        run(new Runnable() {
            @Override
            public void run() {
                rootNode[0] = new org.webbeans.nodes.AbstractNode(new Scene3DChildren(scene.getRoot()));
                em.setRootContext(rootNode[0]);
                Scene3DChildren.bindSelection(em, scene);
            }
        });
        final org.webbeans.nodes.Node[][] k = new org.webbeans.nodes.Node[1][];
        run(new Runnable() {
            @Override
            public void run() {
                k[0] = rootNode[0].getChildren().getNodes();
            }
        });
        org.webbeans.nodes.Node[] kids = k[0];
        assertEquals(2, kids.length);
        assertEquals("box", kids[0].getName());
        assertSame(box, kids[0].getLookup().lookup(Node3D.class));
        run(new Runnable() {
            @Override
            public void run() {
                scene.getRoot().addChild(new Group("more")).add(new Mesh(Geometry.sphere(1), blue));
                box.setName("cube");
            }
        });
        run(new Runnable() {
            @Override
            public void run() {
                k[0] = rootNode[0].getChildren().getNodes();
                assertEquals(1, k[0][2].getChildren().getNodes().length);
            }
        });
        kids = k[0];
        assertEquals(3, kids.length);
        assertEquals("cube", kids[0].getName());
        // tree → scene
        final org.webbeans.nodes.Node first = kids[0];
        run(new Runnable() {
            @Override
            public void run() {
                try {
                    em.setSelectedNodes(new org.webbeans.nodes.Node[] {first});
                } catch (java.beans.PropertyVetoException e) {
                    throw new AssertionError(e);
                }
            }
        });
        assertEquals(Collections.singletonList(box.getId()), scene.get(Scene3D.SELECTION));
        // scene → tree (a pick of the nested sphere)
        final Node3D sphere = ((Group) scene.getRoot().getChildren().get(2)).getChildren().get(0);
        fire("pick", map("node", sphere.getId()));
        assertEquals(1, em.getSelectedNodes().length);
        assertSame(sphere, em.getSelectedNodes()[0].getLookup().lookup(Node3D.class));
        assertEquals(Collections.singletonList(sphere.getId()), scene.get(Scene3D.SELECTION));
        // property edits go to the scene node
        org.webbeans.nodes.Node.Property<?> x = em.getSelectedNodes()[0].getProperty("x");
        @SuppressWarnings("unchecked")
        final org.webbeans.nodes.Node.Property<Double> px = (org.webbeans.nodes.Node.Property<Double>) x;
        run(new Runnable() {
            @Override
            public void run() {
                try {
                    px.setValue(7.5);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        });
        assertEquals(7.5, sphere.getPosition().x, 0);
        assertEquals(Arrays.asList(7.5, 0.0, 0.0), d(opFor(nodeOps(lastScene()), sphere.getId(), "set").get("pos")));
    }
}
