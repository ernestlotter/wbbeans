# WebBeans — Scene3D (detailed design v0.3, as built)

Roadmap step 7. JDK 8. Module `webbeans-scene3d`, package `org.webbeans.scene3d`, client `org/webbeans/scene3d/client/scene3d.js` over a vendored three.js r152 (UMD build in the module jar, loaded on first use from `/wb/res/`; no CDN). Depends on steps 1–5 (component model, windows, actions, nodes).

**v0.3** replaces v0.2 with the API as implemented in step 7; `impl/step7-notes.md` lists what was dropped (sprites, IBL, glTF animation clips, binary transport frames).

## 1. Model

A server-side **scene graph** mirrored one-to-one on the client. The Java objects are plain value holders with change tracking; nothing renders on the server (no `java.awt`, headless-safe). Only `Scene3D` is a `Component`; scene nodes are *not* components — they are a second tree carried inside the one component with its own compact op stream (like charts' `data` calls), so a 10 000-object scene costs neither 10 000 component ids nor 10 000 `create` ops.

```
Scene3D (Component, renderer "wb.Scene3D")
 ├─ root Group
 │   └─ Group / Mesh / InstancedMesh / Line / Points / Label3D / Light…   (Node3D subclasses; ids n1, n2, …)
 ├─ Camera + Controls            (value objects owned by the scene)
 ├─ Geometry   (immutable, content-addressed; primitives as parameters, meshes as blobs)
 ├─ Material   (mutable, shared by global id; a change is one update for every scene using it)
 ├─ Texture    (immutable, content-addressed; blob or module resource)
 └─ Animation  (declared once, run on the client clock)
```

## 2. API

```java
public class Scene3D extends Component {
    // properties (ordinary component properties, resent on init)
    BACKGROUND (Integer rgb | null = theme), FOG (Map), GRID, GRID_SIZE, AXES, SHADOWS, ANTIALIAS, PIXEL_RATIO_CAP,
    HOVER_EFFECT (NONE|CURSOR|HIGHLIGHT|OUTLINE), SELECTION (List<String> ids), SELECTION_COLOR, STATS
    // events
    PICK (PickEvent), HOVER (HoverEvent), DRAG (DragEvent), CAMERA_CHANGE (CameraEvent), ANIMATION_DONE (AnimationEvent), READY (Event)
    public Group getRoot();  Node3D findNode(String id);  Node3D findByUserData(Object o);  Collection<Node3D> getNodes();
    public Camera getCamera();  Scene3D setCamera(Camera c);  CameraState getCameraState();
    public Controls getControls();  Scene3D setControls(Controls c);
    public Scene3D setBackground(Integer rgb);  setFog(Fog f);  setGrid(boolean);  setGrid(double size, int divisions);  setAxes(boolean);
    public Scene3D setShadows(boolean);  setHoverEffect(HoverEffect);  setStats(boolean);
    public Scene3D setSelection(Node3D... nodes);  setSelection(Collection<? extends Node3D>);  List<Node3D> getSelection();
    public void fit();  fit(Node3D subtree);  flyTo(CameraState to, long ms);  flyTo(Vector3 pos, Vector3 target, long ms);  lookAt(Vector3 p);
    public CompletableFuture<byte[]> snapshot(int w, int h);          // PNG bytes, completes on the session executor
    public void download(String fileName, int w, int h);              // saved client-side, no round trip
    public boolean isReady();  Map<String, Object> getCapabilities();  // after the READY event
    public void addSceneListener(SceneListener l);                    // server-side graph observation (explorer bridge)
}

public abstract class Node3D implements Lookup.Provider {
    public String getId();  Scene3D getScene();  Group getParent();
    public Node3D setName(String n);  setPosition(x, y, z | Vector3);  setRotation(Quaternion);  setRotationEuler(x, y, z);  setScale(s | x, y, z | Vector3);
    public Node3D setVisible(boolean);  setPickable(boolean);  setCastShadow(boolean);  setReceiveShadow(boolean);
    public Node3D setDraggable(DragConstraint c);                     // null = not draggable (default)
    public Node3D setUserData(Object o);  Object getUserData();        // server-side only; part of the lookup and of events
    public Lookup getLookup();                                        // Lookups.fixed(this, userData); override for more
    public Node3D animate(Animation a);  List<Animation> getAnimations();  Node3D stopAnimations();
}
public final class DragConstraint {                                   // evaluated in the parent's space, on both sides
    public static DragConstraint free();  plane(Axis normal, double offset);  axis(Axis a);
    public DragConstraint snap(double gridSize);
    public Vector3 apply(Vector3 start, Vector3 reported);           // server-side validation
}
public class Group extends Node3D { add(Node3D...); <N> N addChild(N); remove(Node3D); removeAll(); getChildren(); descendants(); }
public class Mesh extends Node3D { Mesh(Geometry, Material); Mesh(String name, Geometry, Material); setGeometry; setMaterial; }
public class InstancedMesh extends Node3D {
    InstancedMesh(Geometry, Material, int count);  setCount(int);
    setInstanceTransform(int i, Vector3 pos, Quaternion rot, Vector3 scale);  setInstancePosition(int i, Vector3);  setInstanceColor(int i, int rgb);
    setInstanceTransforms(float[] columnMajor16n);                    // bulk
}
public class Line extends Node3D { Line(List<Vector3> pts, LineMaterial m); setPoints; setClosed; static List<Vector3> circle(double r, int segments); }
public class Points extends Node3D { Points(List<Vector3> pts, PointsMaterial m); setPoints; setColors(int[] rgb); }
public class Label3D extends Node3D { Label3D(String text); setText; setStyleName(css); setOccludable(boolean); setOffset(px, py); }
public abstract class Light extends Node3D { setColor(int rgb); setIntensity(double); }
   AmbientLight(rgb, i)  HemisphereLight(sky, ground, i)  DirectionalLight(rgb, i).setTarget(v).setShadowSize(s)
   PointLight(rgb, i).setRange(distance, decay)  SpotLight(rgb, i).setTarget(v).setCone(angle, penumbra).setDistance(d)
public abstract class Camera { setPosition; lookAt(target); setUp; setClipping(near, far); CameraState getState(); }
   PerspectiveCamera(fovDeg[, near, far]).setFov   OrthographicCamera(size).setSize
public final class CameraState { position, target, up }
public final class Controls {  orbit() | map() | firstPerson(unitsPerSec) | none();
    setDistanceLimits(min, max); setPolarLimits(min, max); setPan/setZoom/setRotate(boolean); setAutoRotate(radPerSec); setLocked(boolean); setDamping(boolean); }
public final class Fog { linear(rgb, near, far); exponential(rgb, density); }
```

Events (`SceneEvent` resolves node ids and vectors):

```java
PickEvent      { Node3D getNode() /* null = background */; Object getUserData(); Vector3 getPoint(), getNormal(); int getInstance(); int getButton(); boolean isDoubleClick(), isCtrl(), isShift(); int getX(), getY(); Lookup getLookup() /* the node's */ }
HoverEvent     { Node3D getNode() /* null when leaving */; Vector3 getPoint(); int getInstance(); }
DragEvent      { Node3D getNode(); Phase getPhase() /* START, MOVE, END */; Vector3 getPosition() /* validated */; Vector3 getStart(); Vector3 getReportedPosition(); void reject(); }
CameraEvent    { CameraState getState(); }
AnimationEvent { Node3D getNode(); String getAnimationId(); }
READY          { data: webgl2, renderer, maxTextureSize, pixelRatio — or error }
```

Geometry, materials, textures:

```java
public final class Geometry {                                  // immutable; id = "g" + hash of the definition
    static Geometry box(w, h, d); sphere(r[, ws, hs]); plane(w, h); cylinder(rTop, rBottom, h, seg); cone(r, h, seg); torus(r, tube, rs, ts); circle(r, seg); ring(ri, ro, seg);
    static Builder buffer();   // positions(float[] | List<Vector3>), normals, uvs, colors, indices(int[]), computeNormals(boolean) → build() puts a blob
    int getVertexCount(); int getTriangleCount();
}
public class Material {                                        // mutable, shared; Type BASIC|LAMBERT|PHONG|STANDARD|PHYSICAL
    static Material standard(rgb); basic(rgb); phong(rgb); lambert(rgb); wireframe(rgb);
    setColor(rgb); setOpacity(0..1); setTransparent; setMetalness; setRoughness; setEmissive(rgb[, intensity]); setShininess;
    setMap(Texture); setNormalMap(Texture); setEmissiveMap(Texture); setSide(FRONT|BACK|DOUBLE); setFlatShading; setDepthTest; setWireframe;
}
public class LineMaterial extends Material { setWidth(px); setDashed(dash, gap); }
public class PointsMaterial extends Material { setSize; setSizeAttenuation; }
public final class Texture {                                   // immutable; id = "t" + hash(url + options)
    static Texture image(byte[] bytes, String contentType);    // → blob
    static Texture fromResource(String classpathPath);         // → /wb/res/<path>
    Texture repeat(u, v); wrap(CLAMP|REPEAT|MIRROR); filter(LINEAR|NEAREST); flipY(boolean); srgb(boolean);
}
public final class Gltf { static Group load(InputStream | byte[]); }   // .glb or .gltf with data: URIs
```

Animations (declared once, executed on the client; completion reported once):

```java
public final class Animation {
    static Animation rotate(Axis | Vector3 axis, double radPerSec);            // endless, local axis
    static Animation orbit(Vector3 center, Vector3 axis, double radPerSec);    // endless, revolves the position
    static Animation moveTo(Vector3 to, long ms);  scaleTo(Vector3 to, long ms);  path(List<Vector3> pts, long ms);   // Catmull-Rom
    Animation loop(boolean).yoyo(boolean).delay(ms).easing(Easing).onComplete(Runnable);
    Animation pause(); resume(); seek(ms);  void stop();
}
node.animate(animation);   // now, or when the node joins a scene; replayed on resync
```

Keeping a scene in sync with data:

```java
public final class SceneBinder<T> {
    SceneBinder(Group parent, Function<T, ?> key /* null = identity */, Function<T, Node3D> create, BiConsumer<T, Node3D> update);
    void setItems(Collection<? extends T> items);   // diff by key: create (+ userData = item, + update), update, remove
    void refresh(T item);  void refreshAll();  Node3D nodeFor(T);  T itemFor(Node3D);  List<Node3D> getNodes();  void clear();
}
```

Because node setters are no-ops for unchanged values, an `update` function can set everything every time and the wire only carries what changed.

Example (the demo window):

```java
Scene3D scene = new Scene3D();
scene.setGrid(40, 40).setHoverEffect(HoverEffect.HIGHLIGHT).setBackground(0x0b1020);
scene.getCamera().setPosition(0, 18, 26).lookAt(Vector3.ZERO);
scene.getRoot().add(new AmbientLight(0xffffff, 0.35), new PointLight(0xfff2cc, 1.6));
Mesh sun = scene.getRoot().addChild(new Mesh("Sun", Geometry.sphere(1.8, 48, 24), Material.standard(0xffcc33).setEmissive(0xff9900, 1.2)));
sun.animate(Animation.rotate(Axis.Y, 0.15));
SceneBinder<Planet> planets = new SceneBinder<Planet>(group, null, p -> createPivot(p), (p, n) -> layout(p, (Group) n));
planets.setItems(SolarSystem.get().getPlanets());
Group probe = scene.getRoot().addChild(new Group("Probe"));
probe.setDraggable(DragConstraint.plane(Axis.Y, 0).snap(0.5));
scene.addListener(Scene3D.PICK, e -> StatusDisplayer.getDefault().setStatusText("Picked " + e.getUserData()));
scene.addListener(Scene3D.DRAG, e -> { if (e.getPhase() == DragEvent.Phase.END && e.getPosition().length() > 20) e.reject(); });
```

## 3. Wire format

One `scene` call per flush, built in `UI.beforeFlush`:

```json
{ "op":"call", "id":90, "m":"scene", "args":[{
  "reset": true,                                                 // only on attach / resync: the client drops everything first
  "texs":  { "t3f0…": {"url":"/wb/blob/<sha256>", "repeat":[2,2], "wrap":"repeat", "flipY":false} },
  "mats":  { "m7": {"kind":"mesh", "type":"standard", "color":3900150, "metalness":0.1, "roughness":0.6, "map":"t3f0…"} },
  "geoms": { "g04592dcd…": {"prim":"sphere", "args":[0.38,32,16]}, "g9a1c…": {"blob":"<sha256>", "bytes":184320} },
  "nodes": [
    {"op":"add", "id":"n12", "p":"n1", "t":"mesh", "name":"Mars", "pos":[8.9,0,0], "rot":[0,0,0,1], "scl":[1,1,1], "shadow":[true,false], "g":"g0459…", "m":"m7", "drag":{"plane":[0,1,0,0],"snap":0.5}},
    {"op":"add", "id":"n13", "p":"n1", "t":"imesh", "g":"g0459…", "m":"m7", "inst":{"count":100000, "blob":"<sha256>", "colors":true}},
    {"op":"set", "id":"n7", "pos":[2,0,0], "vis":false},
    {"op":"set", "id":"n13", "inst":{"count":100000, "upd":[{"i":4,"xf":[…16…],"col":[1,0,0]}]}},
    {"op":"remove", "id":"n4"}
  ],
  "camera":   {"proj":"persp", "pos":[0,18,26], "target":[0,0,0], "up":[0,1,0], "near":0.1, "far":2000, "fov":50},
  "controls": {"mode":"orbit", "minDist":4, "maxDist":120, "minPolar":0.05, "maxPolar":1.87, "pan":true, "zoom":true, "rotate":true, "damping":true},
  "anim": [ {"op":"play", "id":"a3", "node":"n12", "kind":"rotate", "axis":[0,1,0], "rate":0.8, "loop":true},
            {"op":"play", "id":"a4", "node":"n20", "kind":"move_to", "to":[1,2,3], "duration":1000, "easing":"ease-in-out"},
            {"op":"pause", "id":"a4"} ],
  "cmd":  [ {"c":"fit", "node":"n12"}, {"c":"flyTo", "pos":[…], "target":[…], "up":[…], "duration":600}, {"c":"snapshot", "id":2, "w":1920, "h":1080}, {"c":"download", "name":"scene.png"} ]
}] }
```

Node types: `group`, `mesh`, `imesh`, `line` (`pts`, `closed`, `m`), `points` (`pts`, `col`, `m`), `label` (`text`, `css`, `occ`, `off`), `light.ambient|hemisphere|directional|point|spot`. Common fields: `name pos rot scl vis pick shadow drag`. Environment (`background`, `fog`, `grid`, `axes`, `shadows`, `selection`, …) travels as **component properties**, not inside the call.

Merge rules (per flush): one pending op per node id; repeated `set`s merge (last value wins); `set` after a pending `add` is folded into the add (which is generated from the node's current state at flush time); `add`+`remove` cancels; removing a group skips the children's removes. Definitions are sent once per scene (`sentGeoms/Mats/Texs`), a material change re-sends only that material's definition; a reset clears the sent sets.

Blobs: `Geometry.buffer().build()`, `Texture.image()` and instance arrays above 512 instances go into `BlobStore` (content-addressed, in-memory LRU; `/wb/blob/<sha256>` requires a session and is served `immutable`, so the browser cache makes reloads and other tabs free). Buffer layout: little-endian `u32` header length, JSON header `{attrs:{position:{type:"f32",size:3,count,offset},normal,uv,color}, index:{type:"u32",count,offset}, computeNormals}` with offsets relative to the data section, then the arrays. Instance blobs are `count×16 f32` transforms followed by `count×3 f32` colours when present.

Upstream events:

| Event | Data | Notes |
|---|---|---|
| `pick` | `{node?, point, normal?, inst?, dist?, button, dbl?, ctrl, shift, alt, x, y}` | nearest pickable hit; background clicks carry no node |
| `hover` | `{node?, point?, inst?, x, y}` | throttled 60 ms, only when listened; local effects need no listener |
| `drag` | `{node, phase:"start"|"move"|"end", pos}` | only for nodes with a constraint (the nearest draggable ancestor of the hit); `move` throttled 50 ms |
| `camera` | `{pos, target, up}` | when a gesture ends / 200 ms after the last wheel tick |
| `animDone` | `{id, node, pos, rot, scl}` | timed, non-looping animations |
| `snapshot` | `{id, data | error}` | PNG data URL |
| `ready` | `{webgl2, renderer, maxTextureSize, pixelRatio}` or `{error}` | after the renderer is created (again after a resync) |

## 4. Client renderer (`scene3d.js`)

- three.js r152 is loaded by a `<script>` tag on the first `wb.Scene3D` create; payloads queue until it is there. One `WebGLRenderer` per element, sized by `ResizeObserver`, pixel ratio capped by `pixelRatioCap`; **rendering is on demand** — a frame loop runs only while animations, camera flights, drags, auto-rotate or first-person keys are active.
- Payloads are applied in order texs → mats → geoms → nodes → camera → controls → anim → cmd; `reset` disposes everything first. Materials are updated in place when the kind/type is unchanged, otherwise rebuilt and swapped on every mesh using them; blob geometries start empty and fill in when the fetch completes (meshes referencing them simply appear; outlines are rebuilt; `fit`/`snapshot` commands wait for pending blobs).
- Picking: `Raycaster` over a maintained list of pickable objects (visible meshes/instanced meshes/points/lines), instance ids and face normals reported; a pointer that moved more than 3 px is a camera gesture, not a pick; double-click sends `dbl`.
- Hover: `CURSOR` (pointer cursor), `HIGHLIGHT` (a cached per-node clone of the material with an emissive tint), `OUTLINE` (temporary edge overlay); hover events only when the server listens.
- Drags: the nearest draggable ancestor of the hit is moved by intersecting the pointer ray with the constraint plane (or the closest point on the constraint axis), minus the grab offset, snapped to the grid, with the constrained coordinate pinned; events `start`/`move`/`end`. While dragging, server `pos` sets for that node are ignored until the drag ends (then the server's corrected/snapped-back value applies).
- Selection: `EdgesGeometry` overlays (depth-test off, slightly scaled) on every mesh of the selected subtree, in `selectionColor`.
- Controls: own implementation — orbit (spherical around the target with the camera's `up`, polar and distance limits), map (orbit with a polar cap), first person (drag to look, WASD/QE/arrows to move), none; pan with the right/middle button or shift-drag, dolly with the wheel (orthographic cameras change their size), pinch on touch; `locked` ignores input.
- Animations: a tween engine on `requestAnimationFrame` (rotate/orbit integrate per frame; move/scale/path interpolate with easing, loop and yoyo); `fit` computes the bounding box of the scene or subtree and flies the camera; `flyTo` eases position and target over the given time.
- Labels: one DOM element per `Label3D` positioned every frame from the projected world position (+ pixel offset), hidden behind the camera and, if occludable, when a pickable object lies in front.
- Snapshot: renders at the requested size on the same canvas (pixel ratio 1), `toDataURL('image/png')`, restores; `download` triggers an anchor click.
- Context loss: an overlay; `webglcontextrestored` re-renders from the local mirror. Stats overlay: fps, draw calls, triangles.

## 5. Threading and consistency

- All scene mutations on the session executor (the component's `checkThread` guards apply through `UI.beforeFlush`). A `Scene3D` can be built before it is attached; attaching emits the full snapshot. `Material` changes may come from any thread: scenes hop to their UI executor (`ui.access`) when needed.
- A `Scene3D` belongs to one `UI`; shared models belong in the session and each tab builds its own scene through a `SceneBinder`.
- Heavy work (`Gltf.load`, big `Geometry.buffer()` builds) can run off the executor; the results are immutable and attached later.

## 6. Interaction patterns

- **Selection**: `PickEvent.getLookup()` is the node's lookup (node + user data). Put the user data into the window's lookup (the demo uses the explorer selection) and context actions enable; `setSelection` draws the outline.
- **Drags**: the server marks a node draggable; the client moves it locally and reports; the server validates (`DragConstraint.apply`), applies the validated position to the node *before* listeners run, then echoes a correction when needed or snaps back on `reject()`.
- **Highlight on hover**: client-only via `setHoverEffect`; no traffic.
- **Camera presets**: store `getCameraState()` and `flyTo(state, ms)` later; `Scene3DTopComponent` does this for "reset view".

## 7. Platform integration

- `Scene3DTopComponent(Scene3D)`: a `TopComponent` with the scene in its lookup and a toolbar (fit, reset view, toggle grid, save PNG) plus the actions registered under `Scene3D/Actions` (context actions over `Scene3D` work, e.g. the demo's `ToggleOrbitsAction`).
- `Scene3DChildren extends Children.Keys<Node3D>` mirrors a `Group` (recursively) as `Node3DNode`s with name/type/visible/x/y/z/scale properties (renaming and editing go to the scene node), kept in sync through `SceneListener`; `Scene3DChildren.bindSelection(em, scene)` syncs the explorer selection with `setSelection`/`PICK` both ways (the `NodeView` expands ancestors to reveal a programmatic selection). With a `TreeView` and the Properties window this is a basic 3D object inspector.
- Actions over the domain type work because picked nodes and their user data enter the lookup.

## 8. Performance notes

| Scenario | As built |
|---|---|
| Demo scene (45 nodes, 19 animations) | init `scene` call ≈ 12 KB; idle = 0 renders; animated ≈ 19 draw calls/frame |
| One planet added | one delta of four `add` ops ≈ 2 KB |
| 1 000 instances | one `set inst` blob of 76 KB; partial updates ≈ 120 bytes per instance |
| 100 000 instances | 7.6 MB blob, cached by sha256 |
| three.js | 620 KB once per browser cache, only for tabs that open a 3D window |

## 9. Security and limits

- `/wb/blob/` requires a session and validates the id format; `BlobStore` is size-bounded (default 256 MB, LRU) — a dedicated store can be put into the default lookup.
- `Label3D` text is set through `textContent` (no HTML); names are plain strings.
- The glTF reader rejects external URIs (only GLB chunks and `data:` URIs), caps the node depth and reads only what it needs.
- Drag positions from the client are validated server-side; non-draggable nodes always snap back.

## 10. Testing

- `Scene3DTest`: wire format and merge rules, shared definitions, material propagation, drag validation/rejection, pick lookup, animation ops and completion, snapshot/camera futures, `SceneBinder` diffing, instanced updates and blobs, buffer blob layout, the glTF reader on a generated GLB, `Scene3DChildren` with selection sync and property edits.
- `webbeans-demo/e2e/scene3d.js`: headless Chromium with SwiftShader WebGL — ready event, rendered pixels, tree mirroring, pick/selection/property round trips, drags (accepted and rejected), camera gestures, incremental deltas on model changes, toolbar and downloads, resync.

## 11. Open points

- Binary transport frames and an IndexedDB blob cache (with the WebSocket front end); progressive loading / LOD for very large scenes.
- Sprites, extrude/lathe/text geometries, cube/HDR environment textures, morph targets and glTF animation clips (`AnimationMixer`).
- A post-processing outline pass instead of edge overlays; WebGPU renderer when three.js' is stable.
