package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Container;
import org.webbeans.ui.Property;

/** The general-purpose container; its {@link Layout} decides how children are placed. */
@Renderer(id = "wb.Panel", resource = "org/webbeans/ui/client/renderers/panel.js")
public class Panel extends Container {

    public enum Border { NONE, LINE, TITLED }

    public static final Property<Layout> LAYOUT = Property.of("layout", Layout.class, null);
    public static final Property<Integer> PADDING = Property.of("padding", Integer.class, 0);
    public static final Property<Border> BORDER = Property.of("border", Border.class, Border.NONE);
    public static final Property<String> TITLE = Property.of("title", String.class, null);
    public static final Property<Boolean> SCROLLABLE = Property.of("scrollable", Boolean.class, Boolean.FALSE);
    public static final Property<Component> SELECTED = Property.of("selected", Component.class, null);
    /** Dims the panel under a spinner (and blocks input) while a long operation runs. */
    public static final Property<Boolean> BUSY = Property.of("busy", Boolean.class, Boolean.FALSE);
    public static final Property<String> BUSY_TEXT = Property.of("busyText", String.class, null);

    public Panel() {
        this(new Layout.Fill());
    }

    public Panel(Layout layout) {
        this("wb.Panel", layout);
    }

    /** For subclasses with their own renderer that extends the panel renderer (e.g. TopComponent). */
    protected Panel(String rendererId, Layout layout) {
        super(rendererId);
        set(LAYOUT, layout);
    }

    public Panel setLayout(Layout l) {
        set(LAYOUT, l);
        return this;
    }

    public Panel setPadding(int px) {
        set(PADDING, px);
        return this;
    }

    public Panel setBorder(Border b) {
        set(BORDER, b);
        return this;
    }

    public Panel setTitle(String t) {
        set(TITLE, t);
        set(BORDER, Border.TITLED);
        return this;
    }

    public Panel setScrollable(boolean b) {
        set(SCROLLABLE, b);
        return this;
    }

    public Panel setBusy(boolean b) {
        set(BUSY, b);
        return this;
    }

    public Panel setBusy(boolean b, String text) {
        set(BUSY_TEXT, text);
        return setBusy(b);
    }

    public boolean isBusy() {
        return Boolean.TRUE.equals(get(BUSY));
    }

    /** For a {@link Layout.Stack}: the visible child. */
    public Panel setSelected(Component child) {
        set(SELECTED, child);
        return this;
    }

    /** Adds a form row (for a {@link Layout.Form} panel). */
    public Panel addRow(String label, Component field) {
        add(field, Layout.Form.row(label));
        return this;
    }
}
