package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A node with children; transforms compose down the tree. The scene's root is a group. */
public class Group extends Node3D {

    private final List<Node3D> children = new ArrayList<Node3D>();

    public Group() {
    }

    public Group(String name) {
        setName(name);
    }

    @Override
    String type() {
        return "group";
    }

    public List<Node3D> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public int getChildCount() {
        return children.size();
    }

    /** Adds a node (removing it from its previous parent first); returns the group for chaining. */
    public Group add(Node3D... nodes) {
        for (Node3D n : nodes) {
            addChild(n);
        }
        return this;
    }

    /** Adds and returns the child, for {@code Mesh m = group.addChild(new Mesh(...))} style code. */
    public <N extends Node3D> N addChild(N n) {
        if (n == null || n == this) {
            throw new IllegalArgumentException("Cannot add " + n);
        }
        for (Group g = this; g != null; g = g.getParent()) {
            if (g == n) {
                throw new IllegalArgumentException("Cycle: " + n + " is an ancestor of " + this);
            }
        }
        if (n.getParent() != null) {
            n.getParent().remove(n);
        }
        children.add(n);
        n.setParentInternal(this);
        if (getScene() != null) {
            n.attachTo(getScene());
        }
        return n;
    }

    public Group remove(Node3D n) {
        if (children.remove(n)) {
            n.detachFromScene();
            n.setParentInternal(null);
        }
        return this;
    }

    public Group removeAll() {
        for (Node3D n : new ArrayList<Node3D>(children)) {
            remove(n);
        }
        return this;
    }

    /** Depth-first walk over this group's descendants (not itself). */
    public List<Node3D> descendants() {
        List<Node3D> out = new ArrayList<Node3D>();
        collect(this, out);
        return out;
    }

    private static void collect(Group g, List<Node3D> out) {
        for (Node3D n : g.children) {
            out.add(n);
            if (n instanceof Group) {
                collect((Group) n, out);
            }
        }
    }

    @Override
    void attachTo(Scene3D s) {
        super.attachTo(s);
        for (Node3D n : children) {
            n.attachTo(s);
        }
    }

    @Override
    void detachFromScene() {
        for (Node3D n : children) {
            n.detachFromScene();
        }
        super.detachFromScene();
    }
}
