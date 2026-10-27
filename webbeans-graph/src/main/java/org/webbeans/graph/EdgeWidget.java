package org.webbeans.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A connection between two widgets (a node or a pin at either end): a {@link Router} chooses the path, an
 * {@link Anchor} where it attaches, {@link AnchorShape}s decorate the ends, and any number of labels sit along it.
 */
public class EdgeWidget extends Widget {

    /** A text along the edge. */
    public static final class Label {
        private final String text;
        private final double position;
        private final boolean above;

        Label(String text, double position, boolean above) {
            this.text = text;
            this.position = position;
            this.above = above;
        }

        public String getText() {
            return text;
        }

        /** 0 = at the source, 1 = at the target. */
        public double getPosition() {
            return position;
        }

        public boolean isAbove() {
            return above;
        }
    }

    private Widget source;
    private Widget target;
    private Router router;
    private Anchor sourceAnchor;
    private Anchor targetAnchor;
    private AnchorShape sourceShape = AnchorShape.NONE;
    private AnchorShape targetShape = AnchorShape.ARROW;
    private LineStyle lineStyle = LineStyle.SOLID;
    private double strokeWidth = 1.5;
    private int cornerRadius = 8;
    private boolean selectable = true;
    private List<Point> controlPoints = Collections.emptyList();
    private final List<Label> labels = new ArrayList<Label>();

    EdgeWidget(String id) {
        super(id);
    }

    /** The widget at the source end: a {@link NodeWidget} or a {@link PinWidget}; null while unattached. */
    public Widget getSource() {
        return source;
    }

    public Widget getTarget() {
        return target;
    }

    /** The node at the source end (the pin's node when attached to a pin). */
    public NodeWidget getSourceNode() {
        return nodeOf(source);
    }

    public NodeWidget getTargetNode() {
        return nodeOf(target);
    }

    /** The node of a node or pin widget; null for anything else. */
    public static NodeWidget nodeOf(Widget w) {
        if (w instanceof PinWidget) {
            return ((PinWidget) w).getNode();
        }
        return w instanceof NodeWidget ? (NodeWidget) w : null;
    }

    public EdgeWidget setSource(Widget w) {
        check(w);
        source = w;
        changed();
        return this;
    }

    public EdgeWidget setTarget(Widget w) {
        check(w);
        target = w;
        changed();
        return this;
    }

    private static void check(Widget w) {
        if (w != null && !(w instanceof NodeWidget) && !(w instanceof PinWidget)) {
            throw new IllegalArgumentException("An edge attaches to a node or a pin, not " + w);
        }
    }

    public Router getRouter() {
        return router;
    }

    /** null = the scene's default router. */
    public EdgeWidget setRouter(Router r) {
        router = r;
        changed();
        return this;
    }

    /** null = BOUNDARY for nodes, SIDE for pins. */
    public EdgeWidget setSourceAnchor(Anchor a) {
        sourceAnchor = a;
        changed();
        return this;
    }

    public EdgeWidget setTargetAnchor(Anchor a) {
        targetAnchor = a;
        changed();
        return this;
    }

    public EdgeWidget setAnchors(Anchor both) {
        sourceAnchor = both;
        targetAnchor = both;
        changed();
        return this;
    }

    public AnchorShape getSourceShape() {
        return sourceShape;
    }

    public EdgeWidget setSourceShape(AnchorShape s) {
        sourceShape = s == null ? AnchorShape.NONE : s;
        changed();
        return this;
    }

    public AnchorShape getTargetShape() {
        return targetShape;
    }

    public EdgeWidget setTargetShape(AnchorShape s) {
        targetShape = s == null ? AnchorShape.NONE : s;
        changed();
        return this;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }

    public EdgeWidget setLineStyle(LineStyle s) {
        lineStyle = s == null ? LineStyle.SOLID : s;
        changed();
        return this;
    }

    public double getStrokeWidth() {
        return strokeWidth;
    }

    public EdgeWidget setStrokeWidth(double w) {
        strokeWidth = Math.max(0.5, w);
        changed();
        return this;
    }

    /** Corner rounding of the orthogonal router, in pixels (0 = sharp). */
    public EdgeWidget setCornerRadius(int r) {
        cornerRadius = Math.max(0, r);
        changed();
        return this;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public EdgeWidget setSelectable(boolean b) {
        selectable = b;
        changed();
        return this;
    }

    public List<Point> getControlPoints() {
        return controlPoints;
    }

    /** Intermediate points for the {@link Router#FREE} router (sets it). */
    public EdgeWidget setControlPoints(List<Point> points) {
        controlPoints = points == null ? Collections.<Point>emptyList() : new ArrayList<Point>(points);
        router = Router.FREE;
        changed();
        return this;
    }

    public EdgeWidget setControlPoints(Point... points) {
        return setControlPoints(points == null ? null : java.util.Arrays.asList(points));
    }

    public List<Label> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    /** A label at the middle. */
    public EdgeWidget setLabel(String text) {
        labels.clear();
        if (text != null && !text.isEmpty()) {
            labels.add(new Label(text, 0.5, true));
        }
        changed();
        return this;
    }

    /** Adds a label at {@code position} (0 = source … 1 = target), above or below the line. */
    public EdgeWidget addLabel(String text, double position, boolean above) {
        labels.add(new Label(text, Math.max(0, Math.min(1, position)), above));
        changed();
        return this;
    }

    public EdgeWidget clearLabels() {
        labels.clear();
        changed();
        return this;
    }

    // ---- fluent overrides ---------------------------------------------------------------------------------------

    @Override
    public EdgeWidget setVisible(boolean b) {
        super.setVisible(b);
        return this;
    }

    @Override
    public EdgeWidget setOpacity(double o) {
        super.setOpacity(o);
        return this;
    }

    @Override
    public EdgeWidget setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public EdgeWidget setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    /** The line colour (CSS); null = theme. */
    @Override
    public EdgeWidget setForeground(String css) {
        super.setForeground(css);
        return this;
    }

    @Override
    public EdgeWidget setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    static Map<String, Object> endpoint(Widget w) {
        if (w == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (w instanceof PinWidget) {
            m.put("n", ((PinWidget) w).getNode().getId());
            m.put("p", ((PinWidget) w).getPinId());
        } else {
            m.put("n", w.getId());
        }
        return m;
    }

    @Override
    void writeJson(Map<String, Object> m) {
        put(m, "s", endpoint(source));
        put(m, "t", endpoint(target));
        put(m, "r", lower(router));
        put(m, "sa", lower(sourceAnchor));
        put(m, "ta", lower(targetAnchor));
        if (sourceShape != AnchorShape.NONE) {
            m.put("ss", lower(sourceShape));
        }
        if (targetShape != AnchorShape.NONE) {
            m.put("ts", lower(targetShape));
        }
        if (lineStyle != LineStyle.SOLID) {
            m.put("ls", lower(lineStyle));
        }
        if (strokeWidth != 1.5) {
            m.put("sw", strokeWidth);
        }
        if (cornerRadius != 8) {
            m.put("cr", cornerRadius);
        }
        if (!selectable) {
            m.put("sel", false);
        }
        if (!controlPoints.isEmpty()) {
            List<Object> l = new ArrayList<Object>();
            for (Point p : controlPoints) {
                l.add(p.toJson());
            }
            m.put("cp", l);
        }
        if (!labels.isEmpty()) {
            List<Object> l = new ArrayList<Object>();
            for (Label lb : labels) {
                Map<String, Object> lm = new LinkedHashMap<String, Object>();
                lm.put("t", lb.text);
                lm.put("at", lb.position);
                if (!lb.above) {
                    lm.put("below", true);
                }
                l.add(lm);
            }
            m.put("lb", l);
        }
    }
}
