package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.UI;

/**
 * A 3D viewport ({@code wb.Scene3D}): the scene graph lives on the server as {@link Node3D} objects under
 * {@link #getRoot()}; the browser (three.js, loaded on first use) only renders. Every mutation becomes a small
 * op merged per flush into one {@code scene} call: adds, per-field sets, removes, shared geometry/material/
 * texture definitions (sent once per scene, large ones through the blob store), camera and controls, animation
 * ops and one-shot commands (fit, fly, snapshot). Picking, hovering, dragging and camera moves come back as
 * events carrying the node and its user data.
 *
 * <pre>
 * Scene3D scene = new Scene3D();
 * scene.getRoot().add(new AmbientLight(0xffffff, 0.4), new DirectionalLight(0xffffff, 1).setPosition(5, 10, 5));
 * Mesh box = scene.getRoot().addChild(new Mesh(Geometry.box(1, 1, 1), Material.standard(0x3b82f6)));
 * box.animate(Animation.rotate(Axis.Y, 0.5));
 * scene.addListener(Scene3D.PICK, e -&gt; scene.setSelection(e.getNode()));
 * </pre>
 */
@Renderer(id = "wb.Scene3D", resource = "org/webbeans/scene3d/client/scene3d.js", css = "org/webbeans/scene3d/client/scene3d.css")
public class Scene3D extends Component {

    /** Background {@code 0xRRGGBB}; null follows the theme. */
    public static final Property<Integer> BACKGROUND = Property.of("background", Integer.class, null);
    public static final Property<Map<String, Object>> FOG = Property.of("fog", mapClass(), null);
    /** A ground grid in the XZ plane. */
    public static final Property<Boolean> GRID = Property.of("grid", Boolean.class, Boolean.FALSE);
    /** Size of the grid (world units) and number of divisions. */
    public static final Property<List<Double>> GRID_SIZE = Property.of("gridSize", listClass(), null);
    /** The xyz axes helper at the origin. */
    public static final Property<Boolean> AXES = Property.of("axes", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> SHADOWS = Property.of("shadows", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> ANTIALIAS = Property.of("antialias", Boolean.class, Boolean.TRUE);
    /** Upper bound for the device pixel ratio the renderer uses (HiDPI cost control). */
    public static final Property<Double> PIXEL_RATIO_CAP = Property.of("pixelRatioCap", Double.class, 2.0);
    public static final Property<HoverEffect> HOVER_EFFECT = Property.of("hoverEffect", HoverEffect.class, HoverEffect.CURSOR);
    /** Ids of the selected nodes (outlined in the browser). */
    public static final Property<List<String>> SELECTION = Property.of("selection", listClass(), null);
    public static final Property<Integer> SELECTION_COLOR = Property.of("selectionColor", Integer.class, 0xffb000);
    /** Toggles the small frames-per-second / draw-call overlay. */
    public static final Property<Boolean> STATS = Property.of("stats", Boolean.class, Boolean.FALSE);

    /** A click on a pickable node or the background. */
    public static final EventType<PickEvent> PICK = EventType.of("pick", PickEvent::new);
    /** Pointer over a pickable node (throttled in the browser, only sent when listened). */
    public static final EventType<HoverEvent> HOVER = EventType.of("hover", HoverEvent::new);
    /** A draggable node moves; validated against its constraint before listeners run. */
    public static final EventType<DragEvent> DRAG = EventType.of("drag", DragEvent::new);
    /** The user moved the camera (sent when the interaction settles). */
    public static final EventType<CameraEvent> CAMERA_CHANGE = EventType.of("camera", CameraEvent::new);
    /** A timed animation finished. */
    public static final EventType<AnimationEvent> ANIMATION_DONE = EventType.of("animDone", AnimationEvent::new);
    /** The renderer is up: data has {@code webgl2}, {@code renderer}, {@code maxTextureSize}. */
    public static final EventType<Event> READY = EventType.of("ready", Event::new);
    /** Internal: a snapshot result. */
    static final EventType<Event> SNAPSHOT = EventType.of("snapshot", Event::new);

    private static final class NodeOp {
        final Node3D node;
        final String id;
        boolean add;
        boolean remove;
        Map<String, Object> sets;

        NodeOp(Node3D node) {
            this.node = node;
            this.id = node.getId();
        }
    }

    private final Group root = new Group("root");
    private final Map<String, Node3D> nodes = new HashMap<String, Node3D>();
    private int nextNode = 1;
    private int nextAnim = 1;
    private int nextSnapshot = 1;
    private Camera camera = new PerspectiveCamera();
    private Controls controls = Controls.orbit();

    private final LinkedHashMap<String, NodeOp> pendingNodes = new LinkedHashMap<String, NodeOp>();
    private final Map<String, Geometry> pendingGeoms = new LinkedHashMap<String, Geometry>();
    private final Map<String, Material> pendingMats = new LinkedHashMap<String, Material>();
    private final Map<String, Texture> pendingTexs = new LinkedHashMap<String, Texture>();
    private final Set<String> sentGeoms = new HashSet<String>();
    private final Set<String> sentMats = new HashSet<String>();
    private final Set<String> sentTexs = new HashSet<String>();
    private final Set<Material> materials = new LinkedHashSet<Material>();
    private boolean cameraDirty;
    private boolean controlsDirty;
    private final List<Map<String, Object>> anims = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> cmds = new ArrayList<Map<String, Object>>();
    private boolean scheduled;
    private final Map<Integer, CompletableFuture<byte[]>> snapshots = new LinkedHashMap<Integer, CompletableFuture<byte[]>>();
    private final Map<String, Vector3> dragStarts = new HashMap<String, Vector3>();
    private boolean ready;
    private Map<String, Object> capabilities = Collections.emptyMap();
    private final List<SceneListener> sceneListeners = new ArrayList<SceneListener>();

    public Scene3D() {
        super("wb.Scene3D");
        root.attachTo(this);
        camera.setScene(this);
        controls.setScene(this);
        addListener(DRAG, new Listener<DragEvent>() {
            @Override
            public void onEvent(DragEvent e) {
                afterDrag(e);
            }
        });
        addListener(SNAPSHOT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                completeSnapshot(e);
            }
        });
        addListener(ANIMATION_DONE, new Listener<AnimationEvent>() {
            @Override
            public void onEvent(AnimationEvent e) {
            }
        });
        addListener(CAMERA_CHANGE, new Listener<CameraEvent>() {
            @Override
            public void onEvent(CameraEvent e) {
            }
        });
        addListener(READY, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                ready = true;
                capabilities = new LinkedHashMap<String, Object>(e.getData());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<List<T>> listClass() {
        return (Class<List<T>>) (Class<?>) List.class;
    }

    // ---- scene graph ---------------------------------------------------------------------------------------------

    /** The root group; add lights, meshes and groups here. */
    public Group getRoot() {
        return root;
    }

    /** The node with the given id, or null. */
    public Node3D findNode(String id) {
        return nodes.get(id);
    }

    /** All attached nodes (unordered). */
    public Collection<Node3D> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /** The first node whose user data equals {@code o}, or null. */
    public Node3D findByUserData(Object o) {
        if (o == null) {
            return null;
        }
        for (Node3D n : nodes.values()) {
            if (o.equals(n.getUserData())) {
                return n;
            }
        }
        return null;
    }

    public void addSceneListener(SceneListener l) {
        sceneListeners.add(l);
    }

    public void removeSceneListener(SceneListener l) {
        sceneListeners.remove(l);
    }

    public Camera getCamera() {
        return camera;
    }

    public Scene3D setCamera(Camera c) {
        if (c == null) {
            throw new IllegalArgumentException("camera");
        }
        camera.setScene(null);
        camera = c;
        camera.setScene(this);
        cameraChanged();
        return this;
    }

    /** The current camera pose as the server knows it (updated when the user's interaction settles). */
    public CameraState getCameraState() {
        return camera.getState();
    }

    public Controls getControls() {
        return controls;
    }

    public Scene3D setControls(Controls c) {
        controls.setScene(null);
        controls = c == null ? Controls.none() : c;
        controls.setScene(this);
        controlsChanged();
        return this;
    }

    public Scene3D setBackground(Integer rgb) {
        return (Scene3D) set(BACKGROUND, rgb);
    }

    public Scene3D setFog(Fog fog) {
        return (Scene3D) set(FOG, fog == null ? null : fog.toJson());
    }

    public Scene3D setGrid(boolean b) {
        return (Scene3D) set(GRID, b);
    }

    public Scene3D setGrid(double size, int divisions) {
        set(GRID_SIZE, Arrays.asList(size, (double) divisions));
        return setGrid(true);
    }

    public Scene3D setAxes(boolean b) {
        return (Scene3D) set(AXES, b);
    }

    public Scene3D setShadows(boolean b) {
        return (Scene3D) set(SHADOWS, b);
    }

    public Scene3D setHoverEffect(HoverEffect h) {
        return (Scene3D) set(HOVER_EFFECT, h == null ? HoverEffect.NONE : h);
    }

    public Scene3D setStats(boolean b) {
        return (Scene3D) set(STATS, b);
    }

    @Override
    public Scene3D setTestId(String id) {
        super.setTestId(id);
        return this;
    }

    /** Selects the given nodes (null or none clears). */
    public Scene3D setSelection(Node3D... selected) {
        return setSelection(selected == null ? Collections.<Node3D>emptyList() : Arrays.asList(selected));
    }

    public Scene3D setSelection(Collection<? extends Node3D> selected) {
        List<String> ids = new ArrayList<String>();
        for (Node3D n : selected) {
            if (n != null && n.getId() != null && n.getScene() == this) {
                ids.add(n.getId());
            }
        }
        return (Scene3D) set(SELECTION, ids.isEmpty() ? null : ids);
    }

    public List<Node3D> getSelection() {
        List<String> ids = get(SELECTION);
        List<Node3D> out = new ArrayList<Node3D>();
        if (ids != null) {
            for (String id : ids) {
                Node3D n = nodes.get(id);
                if (n != null) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /** Whether the browser reported its renderer; {@link #getCapabilities()} is valid then. */
    public boolean isReady() {
        return ready;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    // ---- commands ------------------------------------------------------------------------------------------------

    /** Frames the whole scene. */
    public void fit() {
        cmd("fit", null);
    }

    /** Frames one node (and its subtree). */
    public void fit(Node3D n) {
        Map<String, Object> m = cmd("fit", null);
        if (n != null && n.getId() != null) {
            m.put("node", n.getId());
        }
    }

    /** Animates the camera to a pose; the server copy of the camera is updated at once. */
    public void flyTo(CameraState to, long durationMs) {
        camera.fromClient(to.getPosition(), to.getTarget(), to.getUp());
        Map<String, Object> m = cmd("flyTo", to.toJson());
        m.put("duration", durationMs);
    }

    public void flyTo(Vector3 position, Vector3 target, long durationMs) {
        flyTo(new CameraState(position, target, camera.getUp()), durationMs);
    }

    /** Turns the camera (in place) towards a point. */
    public void lookAt(Vector3 target) {
        camera.lookAt(target);
    }

    /** Renders the scene at the given size and completes with PNG bytes on the session executor. */
    public CompletableFuture<byte[]> snapshot(int width, int height) {
        CompletableFuture<byte[]> f = new CompletableFuture<byte[]>();
        int id = nextSnapshot++;
        snapshots.put(id, f);
        Map<String, Object> m = cmd("snapshot", null);
        m.put("id", id);
        m.put("w", width);
        m.put("h", height);
        return f;
    }

    /** Lets the browser save a rendering as a PNG file without a round trip. */
    public void download(String fileName, int width, int height) {
        Map<String, Object> m = cmd("download", null);
        m.put("name", fileName);
        m.put("w", width);
        m.put("h", height);
    }

    private Map<String, Object> cmd(String name, Map<String, Object> args) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("c", name);
        if (args != null) {
            m.putAll(args);
        }
        cmds.add(m);
        schedule();
        return m;
    }

    private void completeSnapshot(Event e) {
        CompletableFuture<byte[]> f = snapshots.remove(e.getInt("id", -1));
        if (f == null) {
            return;
        }
        String error = e.getString("error");
        if (error != null) {
            f.completeExceptionally(new IllegalStateException(error));
            return;
        }
        String data = e.getString("data");
        String b64 = data == null ? "" : data.substring(data.indexOf(',') + 1);
        f.complete(Base64.getDecoder().decode(b64));
    }

    // ---- events --------------------------------------------------------------------------------------------------

    @Override
    protected void onEvent(Event event) {
        if (event instanceof DragEvent) {
            beforeDrag((DragEvent) event);
        } else if (event instanceof CameraEvent) {
            CameraEvent e = (CameraEvent) event;
            CameraState s = e.getState();
            camera.fromClient(s.getPosition(), s.getTarget(), s.getUp());
        } else if (event instanceof AnimationEvent) {
            AnimationEvent e = (AnimationEvent) event;
            Node3D n = e.getNode();
            if (n != null) {
                n.fromClient(e.vector("pos"), e.quaternion("rot"), e.vector("scl"));
                for (Animation a : new ArrayList<Animation>(n.getAnimations())) {
                    if (e.getAnimationId() != null && e.getAnimationId().equals(a.getId())) {
                        if (!a.isLoop()) {
                            n.animationStopped(a);
                            a.unbind();
                        }
                        if (a.completion() != null) {
                            a.completion().run();
                        }
                    }
                }
            }
        }
    }

    /** Validates the reported position against the constraint and applies it before listeners see the event. */
    private void beforeDrag(DragEvent e) {
        Node3D n = e.getNode();
        Vector3 reported = e.vector("pos");
        if (n == null || reported == null) {
            return;
        }
        e.setReported(reported);
        Vector3 start;
        if (e.getPhase() == DragEvent.Phase.START || !dragStarts.containsKey(n.getId())) {
            start = n.getPosition();
            dragStarts.put(n.getId(), start);
        } else {
            start = dragStarts.get(n.getId());
        }
        DragConstraint c = n.getDraggable();
        Vector3 valid = c == null ? start : c.apply(start, reported);
        n.fromClient(valid, null, null);
        e.getData().put("pos", valid.toList());
        e.getData().put("start", start.toList());
        if (c == null) {
            e.reject();
        }
    }

    /** After all listeners: snap back when rejected, send the corrected position when the client's differs. */
    private void afterDrag(final DragEvent e) {
        final Node3D n = e.getNode();
        if (n == null) {
            return;
        }
        final boolean end = e.getPhase() == DragEvent.Phase.END;
        final Vector3 reported = e.getReportedPosition();
        UI ui = getUI();
        if (ui == null) {
            return;
        }
        ui.beforeFlush(new Runnable() {
            @Override
            public void run() {
                if (e.isRejected()) {
                    Vector3 start = dragStarts.get(n.getId());
                    if (start != null) {
                        n.fromClient(start, null, null);
                        n.changed("pos", start.toList());
                    }
                } else if (end || !n.getPosition().equals(reported)) {
                    n.changed("pos", n.getPosition().toList());
                }
                if (end || e.isRejected()) {
                    dragStarts.remove(n.getId());
                }
            }
        });
    }

    // ---- sinks called by nodes, materials, camera, controls ---------------------------------------------------

    String nextNodeId() {
        return "n" + nextNode++;
    }

    void nodeAdded(Node3D n) {
        nodes.put(n.getId(), n);
        NodeOp op = new NodeOp(n);
        op.add = true;
        pendingNodes.put(n.getId(), op);
        schedule();
        for (SceneListener l : new ArrayList<SceneListener>(sceneListeners)) {
            l.nodeAdded(n);
        }
    }

    void nodeSet(Node3D n, String field, Object value) {
        if (n.getId() == null) {
            return;
        }
        for (SceneListener l : new ArrayList<SceneListener>(sceneListeners)) {
            l.nodeChanged(n, field);
        }
        NodeOp op = pendingNodes.get(n.getId());
        if (op == null) {
            op = new NodeOp(n);
            pendingNodes.put(n.getId(), op);
        }
        if (op.add || op.remove) {
            return; // the add is generated from the node's current state at flush time
        }
        if (op.sets == null) {
            op.sets = new LinkedHashMap<String, Object>();
        }
        op.sets.put(field, value);
        schedule();
    }

    void nodeRemoved(Node3D n) {
        nodes.remove(n.getId());
        dragStarts.remove(n.getId());
        NodeOp op = pendingNodes.get(n.getId());
        if (op != null && op.add) {
            pendingNodes.remove(n.getId()); // never seen by the client
        } else {
            op = new NodeOp(n);
            op.remove = true;
            pendingNodes.put(n.getId(), op);
            schedule();
        }
        List<String> sel = get(SELECTION);
        if (sel != null && sel.contains(n.getId())) {
            List<String> rest = new ArrayList<String>(sel);
            rest.remove(n.getId());
            set(SELECTION, rest.isEmpty() ? null : rest);
        }
        for (SceneListener l : new ArrayList<SceneListener>(sceneListeners)) {
            l.nodeRemoved(n);
        }
    }

    void register(Geometry g) {
        if (g != null && !sentGeoms.contains(g.getId()) && !pendingGeoms.containsKey(g.getId())) {
            pendingGeoms.put(g.getId(), g);
            schedule();
        }
    }

    void register(Material m) {
        if (m == null) {
            return;
        }
        if (materials.add(m)) {
            m.addUser(this);
        }
        for (Texture t : m.textures()) {
            register(t);
        }
        if (!sentMats.contains(m.getId()) && !pendingMats.containsKey(m.getId())) {
            pendingMats.put(m.getId(), m);
            schedule();
        }
    }

    void register(Texture t) {
        if (t != null && !sentTexs.contains(t.getId()) && !pendingTexs.containsKey(t.getId())) {
            pendingTexs.put(t.getId(), t);
            schedule();
        }
    }

    /** A shared material changed: re-send its definition (from any thread). */
    void materialChanged(final Material m) {
        final UI ui = getUI();
        if (ui == null) {
            return;
        }
        if (ui.getSession().isSessionThread()) {
            for (Texture t : m.textures()) {
                register(t);
            }
            pendingMats.put(m.getId(), m);
            schedule();
        } else {
            ui.access(new Runnable() {
                @Override
                public void run() {
                    materialChanged(m);
                }
            });
        }
    }

    void cameraChanged() {
        cameraDirty = true;
        schedule();
    }

    void controlsChanged() {
        controlsDirty = true;
        schedule();
    }

    void animStart(Node3D n, Animation a) {
        if (a.getId() == null || a.getNode() != n) {
            a.bind(n, "a" + nextAnim++);
        }
        anims.add(a.toJson());
        schedule();
    }

    void animOp(Map<String, Object> op) {
        anims.add(op);
        schedule();
    }

    // ---- batching ------------------------------------------------------------------------------------------------

    private void schedule() {
        UI ui = getUI();
        if (ui != null && !scheduled) {
            scheduled = true;
            ui.beforeFlush(new Runnable() {
                @Override
                public void run() {
                    scheduled = false;
                    emitPending();
                }
            });
        }
    }

    private void emitPending() {
        if (!hasPending()) {
            return;
        }
        List<Object> nodeOps = new ArrayList<Object>();
        for (NodeOp op : pendingNodes.values()) {
            if (op.add) {
                nodeOps.add(op.node.toJson(this)); // registers geometry/material/textures
            } else if (op.remove) {
                if (!ancestorRemoved(op.node)) {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("op", "remove");
                    m.put("id", op.id);
                    nodeOps.add(m);
                }
            } else if (op.sets != null && !op.sets.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("op", "set");
                m.put("id", op.node.getId());
                for (Map.Entry<String, Object> e : op.sets.entrySet()) {
                    Object v = e.getValue();
                    if ("inst".equals(e.getKey()) && op.node instanceof InstancedMesh) {
                        v = ((InstancedMesh) op.node).instanceJson(false);
                    }
                    m.put(e.getKey(), v);
                }
                nodeOps.add(m);
            }
        }
        pendingNodes.clear();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        defs(payload);
        if (!nodeOps.isEmpty()) {
            payload.put("nodes", nodeOps);
        }
        if (cameraDirty) {
            payload.put("camera", camera.toJson());
            cameraDirty = false;
        }
        if (controlsDirty) {
            payload.put("controls", controls.toJson());
            controlsDirty = false;
        }
        if (!anims.isEmpty()) {
            payload.put("anim", new ArrayList<Object>(anims));
            anims.clear();
        }
        if (!cmds.isEmpty()) {
            payload.put("cmd", new ArrayList<Object>(cmds));
            cmds.clear();
        }
        if (!payload.isEmpty()) {
            call("scene", payload);
        }
    }

    private static boolean ancestorRemoved(Node3D n) {
        for (Group g = n.getParent(); g != null; g = g.getParent()) {
            if (g.getScene() == null) {
                return true;
            }
        }
        return false;
    }

    private void defs(Map<String, Object> payload) {
        if (!pendingTexs.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Texture t : pendingTexs.values()) {
                m.put(t.getId(), t.toJson());
                sentTexs.add(t.getId());
            }
            pendingTexs.clear();
            payload.put("texs", m);
        }
        if (!pendingMats.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Material mat : new ArrayList<Material>(pendingMats.values())) {
                m.put(mat.getId(), mat.toJson());
                sentMats.add(mat.getId());
            }
            pendingMats.clear();
            payload.put("mats", m);
        }
        if (!pendingGeoms.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Geometry g : pendingGeoms.values()) {
                m.put(g.getId(), g.toJson());
                sentGeoms.add(g.getId());
            }
            pendingGeoms.clear();
            payload.put("geoms", m);
        }
    }

    /** The whole scene as one {@code scene} call with {@code reset} (first attach, resync). */
    private void sendAll() {
        pendingNodes.clear();
        pendingGeoms.clear();
        pendingMats.clear();
        pendingTexs.clear();
        sentGeoms.clear();
        sentMats.clear();
        sentTexs.clear();
        anims.clear();
        List<Object> nodeOps = new ArrayList<Object>();
        List<Object> animOps = new ArrayList<Object>();
        addTree(root, nodeOps, animOps);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("reset", true);
        defs(payload);
        payload.put("nodes", nodeOps);
        payload.put("camera", camera.toJson());
        payload.put("controls", controls.toJson());
        cameraDirty = false;
        controlsDirty = false;
        if (!animOps.isEmpty()) {
            payload.put("anim", animOps);
        }
        if (!cmds.isEmpty()) {
            payload.put("cmd", new ArrayList<Object>(cmds));
            cmds.clear();
        }
        call("scene", payload);
    }

    private void addTree(Node3D n, List<Object> nodeOps, List<Object> animOps) {
        nodeOps.add(n.toJson(this));
        for (Animation a : n.getAnimations()) {
            if (a.getId() == null) {
                a.bind(n, "a" + nextAnim++);
            }
            animOps.add(a.toJson());
        }
        if (n instanceof Group) {
            for (Node3D c : ((Group) n).getChildren()) {
                addTree(c, nodeOps, animOps);
            }
        }
    }

    @Override
    protected void attached() {
        sendAll();
    }

    @Override
    protected void resync() {
        ready = false;
        sendAll();
    }

    @Override
    protected void detached() {
        for (Material m : materials) {
            m.removeUser(this);
        }
    }

    /** For tests: whether mutations wait for the next flush. */
    boolean hasPending() {
        return !pendingNodes.isEmpty() || !pendingGeoms.isEmpty() || !pendingMats.isEmpty() || !pendingTexs.isEmpty()
                || cameraDirty || controlsDirty || !anims.isEmpty() || !cmds.isEmpty();
    }

    /** For tests: the number of nodes the scene knows. */
    int nodeCount() {
        return nodes.size();
    }
}
