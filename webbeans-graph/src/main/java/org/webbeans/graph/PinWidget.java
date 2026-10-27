package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Resource;

/**
 * A connection point of a {@link NodeWidget} (NetBeans' VMD pin): edges attach to it instead of to the node.
 * With {@link Side#AUTO} the pin is a labelled row inside the node and edges leave from the row's left or
 * right end; with a fixed side it is a small socket on that side of the shape.
 */
public class PinWidget extends Widget {

    private final NodeWidget node;
    private final String pinId;
    private String label;
    private Resource icon;
    private Side side = Side.AUTO;

    PinWidget(String id, NodeWidget node, String pinId) {
        super(id);
        this.node = node;
        this.pinId = pinId;
        this.label = pinId;
    }

    public NodeWidget getNode() {
        return node;
    }

    /** The id within the node (the argument of {@link NodeWidget#addPin}). */
    public String getPinId() {
        return pinId;
    }

    public String getLabel() {
        return label;
    }

    public PinWidget setLabel(String l) {
        label = l;
        changed();
        return this;
    }

    public Resource getIcon() {
        return icon;
    }

    public PinWidget setIcon(Resource r) {
        icon = r;
        changed();
        return this;
    }

    public Side getSide() {
        return side;
    }

    public PinWidget setSide(Side s) {
        side = s == null ? Side.AUTO : s;
        changed();
        return this;
    }

    @Override
    public PinWidget setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public PinWidget setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public PinWidget setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    /** A pin change is a change of its node (pins travel inside the node's description). */
    @Override
    public Object getObject() {
        return node.getObject();
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("id", pinId);
        put(m, "l", label);
        put(m, "i", icon);
        if (side != Side.AUTO) {
            m.put("side", lower(side));
        }
    }
}
