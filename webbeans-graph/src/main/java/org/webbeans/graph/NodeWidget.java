package org.webbeans.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.ui.Resource;

/**
 * A node: a shape with a label, an optional secondary label and icon, and optional {@link PinWidget pins} that
 * edges attach to. The location is the centre of the shape (scene coordinates); the size is either preferred
 * ({@link #setPreferredSize}) or measured by the browser from the content and reported back, so a layout on the
 * server works with the real extent.
 */
public class NodeWidget extends Widget {

    public enum IconPosition { LEFT, TOP }

    private String label;
    private String secondaryLabel;
    private Resource icon;
    private IconPosition iconPosition = IconPosition.LEFT;
    private Shape shape = Shape.ROUNDED;
    private String customSvg;
    private Point location = new Point(0, 0);
    private Size preferredSize;
    private Size measured;
    private boolean movable = true;
    private boolean selectable = true;
    private boolean labelEditable;
    private String accent;
    private boolean animateNextMove;
    private final Map<String, PinWidget> pins = new LinkedHashMap<String, PinWidget>();

    NodeWidget(String id) {
        super(id);
    }

    public String getLabel() {
        return label;
    }

    public NodeWidget setLabel(String l) {
        label = l;
        changed();
        return this;
    }

    public String getSecondaryLabel() {
        return secondaryLabel;
    }

    /** A smaller second line (a type, a status). */
    public NodeWidget setSecondaryLabel(String l) {
        secondaryLabel = l;
        changed();
        return this;
    }

    public Resource getIcon() {
        return icon;
    }

    public NodeWidget setIcon(Resource r) {
        icon = r;
        changed();
        return this;
    }

    public NodeWidget setIconPosition(IconPosition p) {
        iconPosition = p == null ? IconPosition.LEFT : p;
        changed();
        return this;
    }

    public Shape getShape() {
        return shape;
    }

    public NodeWidget setShape(Shape s) {
        shape = s == null ? Shape.ROUNDED : s;
        changed();
        return this;
    }

    /**
     * SVG markup drawn as the outline when the shape is {@link Shape#CUSTOM}; {@code {w}} and {@code {h}} are
     * replaced by the node's size, and the element gets the node's fill/stroke classes unless it sets its own.
     */
    public NodeWidget setCustomSvg(String svg) {
        customSvg = svg;
        shape = Shape.CUSTOM;
        changed();
        return this;
    }

    public Point getLocation() {
        return location;
    }

    /** Moves the node (centre point, scene coordinates). */
    public NodeWidget setLocation(Point p) {
        return setLocation(p, false);
    }

    public NodeWidget setLocation(double x, double y) {
        return setLocation(new Point(x, y), false);
    }

    /** As {@link #setLocation(Point)}; {@code animated} lets the browser glide there (layouts use it). */
    public NodeWidget setLocation(Point p, boolean animated) {
        location = p == null ? new Point(0, 0) : p;
        animateNextMove = animated;
        changed();
        return this;
    }

    /** Called with the browser's drag result; keeps "animated" off. */
    void moved(Point p) {
        location = p;
        animateNextMove = false;
    }

    public Size getPreferredSize() {
        return preferredSize;
    }

    /** A fixed size; null (the default) lets the browser size the node to its content. */
    public NodeWidget setPreferredSize(Size s) {
        preferredSize = s;
        changed();
        return this;
    }

    public NodeWidget setPreferredSize(double w, double h) {
        return setPreferredSize(new Size(w, h));
    }

    /** The size the node is drawn with: the preferred size, else what the browser measured, else an estimate. */
    public Size getSize() {
        if (preferredSize != null) {
            return preferredSize;
        }
        if (measured != null) {
            return measured;
        }
        int chars = Math.max(label == null ? 0 : label.length(), secondaryLabel == null ? 0 : secondaryLabel.length());
        double w = Math.max(60, 24 + chars * 7.5 + (icon != null ? 24 : 0));
        double h = 34 + (secondaryLabel != null ? 14 : 0) + pins.size() * 20;
        return new Size(w, h);
    }

    void measured(Size s) {
        measured = s;
    }

    public boolean isMovable() {
        return movable;
    }

    public NodeWidget setMovable(boolean b) {
        movable = b;
        changed();
        return this;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public NodeWidget setSelectable(boolean b) {
        selectable = b;
        changed();
        return this;
    }

    public boolean isLabelEditable() {
        return labelEditable;
    }

    /** Double-click or F2 edits the label in place; the result arrives as {@link GraphScene#RENAME}. */
    public NodeWidget setLabelEditable(boolean b) {
        labelEditable = b;
        changed();
        return this;
    }

    public String getAccent() {
        return accent;
    }

    /** A colour band at the top of the node (VMD style); null = none. */
    public NodeWidget setAccent(String css) {
        accent = css;
        changed();
        return this;
    }

    // ---- pins ----------------------------------------------------------------------------------------------------

    /** Adds (or returns the existing) pin with that id. */
    public PinWidget addPin(String pinId) {
        PinWidget p = pins.get(pinId);
        if (p == null) {
            p = new PinWidget(getId() + "/" + pinId, this, pinId);
            p.setScene(getScene());
            pins.put(pinId, p);
            changed();
        }
        return p;
    }

    public PinWidget getPin(String pinId) {
        return pins.get(pinId);
    }

    public Collection<PinWidget> getPins() {
        return Collections.unmodifiableCollection(pins.values());
    }

    public NodeWidget removePin(String pinId) {
        PinWidget p = pins.remove(pinId);
        if (p != null) {
            p.setScene(null);
            if (getScene() != null) {
                getScene().pinRemoved(p);
            }
            changed();
        }
        return this;
    }

    void attachPins(GraphScene<?, ?> scene) {
        for (PinWidget p : pins.values()) {
            p.setScene(scene);
        }
    }

    // ---- fluent overrides ---------------------------------------------------------------------------------------

    @Override
    public NodeWidget setVisible(boolean b) {
        super.setVisible(b);
        return this;
    }

    @Override
    public NodeWidget setOpacity(double o) {
        super.setOpacity(o);
        return this;
    }

    @Override
    public NodeWidget setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public NodeWidget setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public NodeWidget setForeground(String css) {
        super.setForeground(css);
        return this;
    }

    @Override
    public NodeWidget setBackground(String css) {
        super.setBackground(css);
        return this;
    }

    @Override
    public NodeWidget setBorder(Border b) {
        super.setBorder(b);
        return this;
    }

    @Override
    public NodeWidget setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    void writeJson(Map<String, Object> m) {
        put(m, "l", label);
        put(m, "l2", secondaryLabel);
        put(m, "i", icon);
        if (iconPosition != IconPosition.LEFT) {
            m.put("ip", lower(iconPosition));
        }
        m.put("sh", lower(shape));
        put(m, "svg", customSvg);
        m.put("x", location.x);
        m.put("y", location.y);
        if (animateNextMove) {
            m.put("anim", true);
            animateNextMove = false;
        }
        if (preferredSize != null) {
            m.put("w", preferredSize.width);
            m.put("h", preferredSize.height);
        }
        if (!movable) {
            m.put("mv", false);
        }
        if (!selectable) {
            m.put("sel", false);
        }
        if (labelEditable) {
            m.put("ed", true);
        }
        put(m, "ac", accent);
        if (!pins.isEmpty()) {
            List<Object> l = new ArrayList<Object>();
            for (PinWidget p : pins.values()) {
                l.add(p.toJson());
            }
            m.put("pins", l);
        }
    }
}
