package org.webbeans.graph;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Something drawn on a {@link GraphScene}: a node, a pin, an edge or a note. Every setter marks the widget
 * dirty; the scene sends the widget's full description once per flush, so any number of changes to one widget
 * cost one message entry. Appearance that every widget has is here; the subclasses add their own.
 *
 * <p>Rendering is data-driven: besides the typed properties, {@link #setAttribute} passes arbitrary values to
 * the renderer, which applies them as {@code data-*} attributes and as CSS custom properties ({@code --wb-g-<name>})
 * on the widget's element — the hook for styling a widget kind from your own CSS, or for a renderer you
 * registered yourself.
 */
public abstract class Widget {

    private final String id;
    private GraphScene<?, ?> scene;
    private boolean visible = true;
    private double opacity = 1;
    private String styleName;
    private String tooltip;
    private String foreground;
    private String background;
    private Border border;
    private Map<String, Object> attributes;

    Widget(String id) {
        this.id = id;
    }

    /** The scene-unique id (also the element's {@code data-id} in the browser). */
    public final String getId() {
        return id;
    }

    /** The scene this widget is on, or null once removed. */
    public final GraphScene<?, ?> getScene() {
        return scene;
    }

    final void setScene(GraphScene<?, ?> s) {
        this.scene = s;
    }

    /** Marks this widget for the next flush. */
    protected final void changed() {
        if (scene != null) {
            scene.widgetChanged(this);
        }
    }

    /** The domain object this widget stands for (a node or edge object of the scene), or null. */
    public Object getObject() {
        return scene == null ? null : scene.findObject(this);
    }

    public boolean isVisible() {
        return visible;
    }

    public Widget setVisible(boolean b) {
        visible = b;
        changed();
        return this;
    }

    public double getOpacity() {
        return opacity;
    }

    /** 0..1. */
    public Widget setOpacity(double o) {
        opacity = Math.max(0, Math.min(1, o));
        changed();
        return this;
    }

    public String getStyleName() {
        return styleName;
    }

    /** Extra CSS class(es) on the widget's element. */
    public Widget setStyleName(String css) {
        styleName = css;
        changed();
        return this;
    }

    public String getTooltip() {
        return tooltip;
    }

    public Widget setTooltip(String t) {
        tooltip = t;
        changed();
        return this;
    }

    public String getForeground() {
        return foreground;
    }

    /** Text and outline colour (CSS); null = theme. */
    public Widget setForeground(String css) {
        foreground = css;
        changed();
        return this;
    }

    public String getBackground() {
        return background;
    }

    /** Fill colour (CSS); null = theme. */
    public Widget setBackground(String css) {
        background = css;
        changed();
        return this;
    }

    public Border getBorder() {
        return border;
    }

    public Widget setBorder(Border b) {
        border = b;
        changed();
        return this;
    }

    /** A renderer attribute: {@code data-<name>} and {@code --wb-g-<name>} on the element; null removes it. */
    public Widget setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<String, Object>();
        }
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
        changed();
        return this;
    }

    public Object getAttribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }

    /** The wire description; subclasses add their fields to the map {@link #writeJson} fills. */
    final Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (!visible) {
            m.put("vis", false);
        }
        if (opacity != 1) {
            m.put("op", opacity);
        }
        put(m, "st", styleName);
        put(m, "tt", tooltip);
        put(m, "fg", foreground);
        put(m, "bg", background);
        if (border != null) {
            m.put("bd", lower(border));
        }
        if (attributes != null && !attributes.isEmpty()) {
            m.put("a", new LinkedHashMap<String, Object>(attributes));
        }
        writeJson(m);
        return m;
    }

    abstract void writeJson(Map<String, Object> m);

    static void put(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    static String lower(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
