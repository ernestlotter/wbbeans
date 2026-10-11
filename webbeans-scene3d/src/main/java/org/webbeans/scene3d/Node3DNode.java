package org.webbeans.scene3d;

import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;

/**
 * An explorer node over a {@link Node3D}: name, visibility, position and uniform scale as editable properties,
 * children for groups. The lookup holds the scene node and its user data (over the scene node's own lookup),
 * so actions over the domain type apply from the tree as well as from picks.
 */
public class Node3DNode extends AbstractNode {

    private final Node3D node;

    public Node3DNode(Node3D node) {
        super(node instanceof Group ? new Scene3DChildren((Group) node) : Children.LEAF,
                new ProxyLookup(Lookups.singleton(node), node.getLookup()));
        this.node = node;
        setName(node.getName() != null ? node.getName() : defaultName(node));
        setIconBase(iconFor(node));
        setShortDescription(node.type());
        setCanRename(true);
    }

    public Node3D getNode3D() {
        return node;
    }

    /** A readable name for unnamed nodes: {@code Point light}, {@code Label "Sun"}, {@code mesh n7}. */
    static String defaultName(Node3D n) {
        if (n instanceof Label3D) {
            return "Label \"" + ((Label3D) n).getText() + "\"";
        }
        String t = n.type();
        if (t.startsWith("light.")) {
            String k = t.substring(6);
            return Character.toUpperCase(k.charAt(0)) + k.substring(1) + " light";
        }
        return t + " " + n.getId();
    }

    static String iconFor(Node3D n) {
        if (n instanceof Group) {
            return "org/webbeans/scene3d/client/group.svg";
        }
        if (n instanceof Light) {
            return "org/webbeans/scene3d/client/light.svg";
        }
        if (n instanceof Label3D) {
            return "org/webbeans/scene3d/client/label.svg";
        }
        return "org/webbeans/scene3d/client/mesh.svg";
    }

    @Override
    public void setName(String n) {
        if (node.getName() == null || !node.getName().equals(n)) {
            node.setName(n);
        }
        super.setName(n);
    }

    /** Called by the children when the scene node changed. */
    void sceneNodeChanged(String field) {
        if ("name".equals(field)) {
            super.setName(node.getName() != null ? node.getName() : defaultName(node));
        } else {
            firePropertyChange(field, null, null);
            firePropertyChange(PROP_PROPERTY_SETS, null, null);
        }
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.Name(this, msg("PROP_Name"), msg("HINT_Name")));
        set.put(new PropertySupport.ReadOnly<String>("type", String.class, msg("PROP_Type"), msg("HINT_Type")) {
            @Override
            public String getValue() {
                return node.type();
            }
        });
        set.put(new PropertySupport.ReadWrite<Boolean>("visible", Boolean.class, msg("PROP_Visible"), msg("HINT_Visible")) {
            @Override
            public Boolean getValue() {
                return node.isVisible();
            }

            @Override
            public void setValue(Boolean v) {
                node.setVisible(Boolean.TRUE.equals(v));
            }
        });
        set.put(coord("x", msg("PROP_X"), 0));
        set.put(coord("y", msg("PROP_Y"), 1));
        set.put(coord("z", msg("PROP_Z"), 2));
        set.put(new PropertySupport.ReadWrite<Double>("scale", Double.class, msg("PROP_Scale"), msg("HINT_Scale")) {
            @Override
            public Double getValue() {
                return node.getScale().x;
            }

            @Override
            public void setValue(Double v) {
                if (v == null || v <= 0) {
                    throw new IllegalArgumentException("Scale must be positive");
                }
                node.setScale(v);
            }
        });
        sheet.put(set);
        return sheet;
    }

    private PropertySupport.ReadWrite<Double> coord(String name, String display, final int axis) {
        return new PropertySupport.ReadWrite<Double>(name, Double.class, display, msg("HINT_Position")) {
            @Override
            public Double getValue() {
                Vector3 p = node.getPosition();
                return axis == 0 ? p.x : axis == 1 ? p.y : p.z;
            }

            @Override
            public void setValue(Double v) {
                double d = v == null ? 0 : v;
                Vector3 p = node.getPosition();
                node.setPosition(axis == 0 ? d : p.x, axis == 1 ? d : p.y, axis == 2 ? d : p.z);
            }
        };
    }

    private static String msg(String key) {
        return Bundles.getMessage(Node3DNode.class, key);
    }
}
