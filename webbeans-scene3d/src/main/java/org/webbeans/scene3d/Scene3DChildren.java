package org.webbeans.scene3d;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;

import org.webbeans.explorer.ExplorerManager;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;

/**
 * Explorer children mirroring a {@link Group}'s scene nodes (one {@link Node3DNode} per child, recursively),
 * kept in sync through the scene's {@link SceneListener}. {@link #bindSelection} ties an explorer manager's
 * selection to the scene's: picking in 3D selects in the tree and the other way round.
 *
 * <pre>
 * AbstractNode rootNode = new AbstractNode(new Scene3DChildren(scene.getRoot()));
 * explorerManager.setRootContext(rootNode);
 * Scene3DChildren.bindSelection(explorerManager, scene);
 * </pre>
 */
public class Scene3DChildren extends Children.Keys<Node3D> implements SceneListener {

    private final Group group;
    private Scene3D scene;

    public Scene3DChildren(Group group) {
        this.group = group;
    }

    @Override
    protected void addNotify() {
        scene = group.getScene();
        if (scene != null) {
            scene.addSceneListener(this);
        }
        setKeys(new ArrayList<Node3D>(group.getChildren()));
    }

    @Override
    protected void removeNotify() {
        if (scene != null) {
            scene.removeSceneListener(this);
            scene = null;
        }
        super.removeNotify();
    }

    @Override
    protected Node[] createNodes(Node3D key) {
        return new Node[] {new Node3DNode(key)};
    }

    @Override
    public void nodeAdded(Node3D node) {
        if (node.getParent() == group) {
            setKeys(new ArrayList<Node3D>(group.getChildren()));
        }
    }

    @Override
    public void nodeRemoved(Node3D node) {
        if (node.getParent() == group) {
            List<Node3D> keys = new ArrayList<Node3D>(group.getChildren());
            keys.remove(node);
            setKeys(keys);
        }
    }

    @Override
    public void nodeChanged(Node3D node, String field) {
        if (node.getParent() == group) {
            for (Node n : getNodes()) {
                if (n instanceof Node3DNode && ((Node3DNode) n).getNode3D() == node) {
                    ((Node3DNode) n).sceneNodeChanged(field);
                }
            }
        }
    }

    /** The explorer node showing a scene node anywhere under {@code root}, or null. */
    public static Node3DNode find(Node root, Node3D target) {
        for (Node n : root.getChildren().getNodes()) {
            if (n instanceof Node3DNode && ((Node3DNode) n).getNode3D() == target) {
                return (Node3DNode) n;
            }
            if (!n.isLeaf() && n instanceof Node3DNode) {
                Node3DNode found = find(n, target);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Two-way selection sync; the explorer manager's root must show the scene through these children. */
    public static void bindSelection(final ExplorerManager em, final Scene3D scene) {
        final boolean[] guard = {false};
        em.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (!ExplorerManager.PROP_SELECTED_NODES.equals(e.getPropertyName()) || guard[0]) {
                    return;
                }
                List<Node3D> sel = new ArrayList<Node3D>();
                for (Node n : em.getSelectedNodes()) {
                    Node3D s = n.getLookup().lookup(Node3D.class);
                    if (s != null) {
                        sel.add(s);
                    }
                }
                guard[0] = true;
                try {
                    scene.setSelection(sel);
                } finally {
                    guard[0] = false;
                }
            }
        });
        scene.addListener(Scene3D.PICK, new org.webbeans.ui.Listener<PickEvent>() {
            @Override
            public void onEvent(PickEvent e) {
                if (guard[0]) {
                    return;
                }
                Node3D n = e.getNode();
                List<Node> nodes = new ArrayList<Node>();
                if (n != null && em.getRootContext() != null) {
                    Node3DNode nn = find(em.getRootContext(), n);
                    if (nn != null) {
                        nodes.add(nn);
                    }
                }
                guard[0] = true;
                try {
                    scene.setSelection(n == null ? new ArrayList<Node3D>() : java.util.Collections.singletonList(n));
                    em.setSelectedNodes(nodes.toArray(new Node[nodes.size()]));
                } catch (PropertyVetoException ex) {
                    // vetoed: keep the previous selection
                } finally {
                    guard[0] = false;
                }
            }
        });
    }
}
