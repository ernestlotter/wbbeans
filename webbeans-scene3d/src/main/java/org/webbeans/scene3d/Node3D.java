package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Base of all scene nodes: a transform, visibility, pick/shadow/drag flags, a name and user data. Nodes are
 * not components; they live in a {@link Scene3D}'s own tree and every change is one small op in the next
 * {@code scene} call. Ids are assigned when the node joins an attached scene.
 */
public abstract class Node3D implements Lookup.Provider {

    private Scene3D scene;
    private String id;
    private Group parent;
    private String name;
    private Vector3 position = Vector3.ZERO;
    private Quaternion rotation = Quaternion.IDENTITY;
    private Vector3 scale = Vector3.ONE;
    private boolean visible = true;
    private boolean pickable = true;
    private boolean castShadow;
    private boolean receiveShadow;
    private DragConstraint draggable;
    private Object userData;
    private final List<Animation> animations = new ArrayList<Animation>();

    /** The wire type of the node ({@code mesh}, {@code group}, {@code light.point}, ...). */
    abstract String type();

    /** Adds the type-specific fields to an {@code add} op. */
    void typeJson(Map<String, Object> m, Scene3D into) {
    }

    /** The scene this node is attached to, or null. */
    public final Scene3D getScene() {
        return scene;
    }

    /** Scene-unique id ({@code n12}), null while detached. */
    public final String getId() {
        return id;
    }

    public final Group getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public Node3D setName(String n) {
        if (n == null ? name == null : n.equals(name)) {
            return this;
        }
        name = n;
        changed("name", n);
        return this;
    }

    public Vector3 getPosition() {
        return position;
    }

    public Node3D setPosition(Vector3 p) {
        Vector3 np = p == null ? Vector3.ZERO : p;
        if (np.equals(position)) {
            return this;
        }
        position = np;
        changed("pos", position.toList());
        return this;
    }

    public Node3D setPosition(double x, double y, double z) {
        return setPosition(new Vector3(x, y, z));
    }

    public Quaternion getRotation() {
        return rotation;
    }

    public Node3D setRotation(Quaternion q) {
        Quaternion nq = q == null ? Quaternion.IDENTITY : q;
        if (nq.equals(rotation)) {
            return this;
        }
        rotation = nq;
        changed("rot", rotation.toList());
        return this;
    }

    /** Euler angles in radians (XYZ). */
    public Node3D setRotationEuler(double x, double y, double z) {
        return setRotation(Quaternion.fromEuler(x, y, z));
    }

    public Vector3 getScale() {
        return scale;
    }

    public Node3D setScale(Vector3 s) {
        Vector3 ns = s == null ? Vector3.ONE : s;
        if (ns.equals(scale)) {
            return this;
        }
        scale = ns;
        changed("scl", scale.toList());
        return this;
    }

    public Node3D setScale(double x, double y, double z) {
        return setScale(new Vector3(x, y, z));
    }

    public Node3D setScale(double s) {
        return setScale(new Vector3(s, s, s));
    }

    public boolean isVisible() {
        return visible;
    }

    public Node3D setVisible(boolean b) {
        if (visible == b) {
            return this;
        }
        visible = b;
        changed("vis", b);
        return this;
    }

    public boolean isPickable() {
        return pickable;
    }

    public Node3D setPickable(boolean b) {
        if (pickable == b) {
            return this;
        }
        pickable = b;
        changed("pick", b);
        return this;
    }

    public boolean isCastShadow() {
        return castShadow;
    }

    public Node3D setCastShadow(boolean b) {
        castShadow = b;
        changed("shadow", Arrays.asList(castShadow, receiveShadow));
        return this;
    }

    public boolean isReceiveShadow() {
        return receiveShadow;
    }

    public Node3D setReceiveShadow(boolean b) {
        receiveShadow = b;
        changed("shadow", Arrays.asList(castShadow, receiveShadow));
        return this;
    }

    /** The drag constraint, or null when the node cannot be dragged (default). */
    public DragConstraint getDraggable() {
        return draggable;
    }

    public Node3D setDraggable(DragConstraint c) {
        draggable = c;
        changed("drag", c == null ? null : c.toJson());
        return this;
    }

    /** Server-side only: the domain object the node represents; part of {@link #getLookup()} and of events. */
    public Object getUserData() {
        return userData;
    }

    public Node3D setUserData(Object o) {
        userData = o;
        return this;
    }

    /** Starts an animation on this node (now, or when the node joins a scene). */
    public Node3D animate(Animation a) {
        if (a.getNode() != null && a.getNode() != this) {
            throw new IllegalArgumentException(a + " already animates " + a.getNode());
        }
        if (!animations.contains(a)) {
            animations.add(a);
        }
        if (scene != null) {
            scene.animStart(this, a);
        }
        return this;
    }

    /** Running (or pending) animations. */
    public List<Animation> getAnimations() {
        return Collections.unmodifiableList(animations);
    }

    /** Stops every animation of this node. */
    public Node3D stopAnimations() {
        for (Animation a : new ArrayList<Animation>(animations)) {
            a.stop();
        }
        animations.clear();
        return this;
    }

    void animationStopped(Animation a) {
        animations.remove(a);
    }

    /** Applies a transform reported by the browser (animation end, accepted drag) without echoing it back. */
    void fromClient(Vector3 pos, Quaternion rot, Vector3 scl) {
        if (pos != null) {
            position = pos;
        }
        if (rot != null) {
            rotation = rot;
        }
        if (scl != null) {
            scale = scl;
        }
    }

    /** The node and its user data (override for more). */
    @Override
    public Lookup getLookup() {
        return userData == null ? Lookups.singleton(this) : Lookups.fixed(this, userData);
    }

    // ---- scene plumbing ----------------------------------------------------------------------------------------

    final void changed(String field, Object value) {
        if (scene != null) {
            scene.nodeSet(this, field, value);
        }
    }

    void setParentInternal(Group p) {
        this.parent = p;
    }

    /** Joins a scene (recursively for groups): assigns ids and records {@code add} ops. */
    void attachTo(Scene3D s) {
        this.scene = s;
        this.id = s.nextNodeId();
        s.nodeAdded(this);
        for (Animation a : animations) {
            s.animStart(this, a);
        }
    }

    /** Leaves the scene (recursively). */
    void detachFromScene() {
        if (scene != null) {
            for (Animation a : animations) {
                a.unbind();
            }
            scene.nodeRemoved(this);
        }
        scene = null;
        id = null;
    }

    /** The full {@code add} op. */
    Map<String, Object> toJson(Scene3D into) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("op", "add");
        m.put("id", id);
        m.put("p", parent == null ? null : parent.getId());
        m.put("t", type());
        if (name != null) {
            m.put("name", name);
        }
        m.put("pos", position.toList());
        m.put("rot", rotation.toList());
        m.put("scl", scale.toList());
        if (!visible) {
            m.put("vis", false);
        }
        if (!pickable) {
            m.put("pick", false);
        }
        if (castShadow || receiveShadow) {
            m.put("shadow", Arrays.asList(castShadow, receiveShadow));
        }
        if (draggable != null) {
            m.put("drag", draggable.toJson());
        }
        typeJson(m, into);
        return m;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (name != null ? name : id) + "]";
    }
}
