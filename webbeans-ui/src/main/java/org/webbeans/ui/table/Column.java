package org.webbeans.ui.table;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One column of a {@link Table}: a title, the value function, and presentation/behaviour settings. Created by
 * {@link Table#addColumn}; the setters return the column for chaining.
 */
public final class Column<T> {

    public enum Align { LEFT, CENTER, RIGHT }

    private final String id;
    private String title;
    private final Function<T, ?> value;
    private Integer width;
    private Integer minWidth;
    private Align align = Align.LEFT;
    private boolean sortable = true;
    private boolean resizable = true;
    private boolean hidden;
    private boolean frozen;
    private Function<Object, String> format;
    private Comparator<T> comparator;
    private BiConsumer<T, String> editor;
    private String styleName;

    Column(String id, String title, Function<T, ?> value) {
        this.id = id;
        this.title = title;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Column<T> title(String t) {
        this.title = t;
        return this;
    }

    public Function<T, ?> getValue() {
        return value;
    }

    /** Fixed width in px (unset: the column shares the remaining space). */
    public Column<T> width(int px) {
        this.width = px;
        return this;
    }

    public Integer getWidth() {
        return width;
    }

    public Column<T> minWidth(int px) {
        this.minWidth = px;
        return this;
    }

    public Column<T> align(Align a) {
        this.align = a;
        return this;
    }

    public Align getAlign() {
        return align;
    }

    public Column<T> sortable(boolean b) {
        this.sortable = b;
        return this;
    }

    public boolean isSortable() {
        return sortable;
    }

    public Column<T> resizable(boolean b) {
        this.resizable = b;
        return this;
    }

    public Column<T> hidden(boolean b) {
        this.hidden = b;
        return this;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** Frozen columns stay visible while scrolling horizontally (must be the leading columns). */
    public Column<T> frozen(boolean b) {
        this.frozen = b;
        return this;
    }

    /** Turns the value into the cell text (default: numbers and dates in the session locale, else {@code toString}). */
    public Column<T> format(Function<Object, String> f) {
        this.format = f;
        return this;
    }

    public Function<Object, String> getFormat() {
        return format;
    }

    /** The order used when sorting by this column (default: compares the values when they are {@link Comparable}). */
    public Column<T> comparator(Comparator<T> c) {
        this.comparator = c;
        return this;
    }

    public Comparator<T> getComparator() {
        return comparator;
    }

    /**
     * Makes the cells editable in place: the consumer receives the row and the text the user entered. Throw an
     * {@link IllegalArgumentException} to reject the input (its message is shown); the row is re-rendered either way.
     */
    public Column<T> editable(BiConsumer<T, String> apply) {
        this.editor = apply;
        return this;
    }

    public BiConsumer<T, String> getEditor() {
        return editor;
    }

    public boolean isEditable() {
        return editor != null;
    }

    /** Extra CSS class on every cell of the column. */
    public Column<T> styleName(String css) {
        this.styleName = css;
        return this;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("title", title);
        if (width != null) {
            m.put("width", width);
        }
        if (minWidth != null) {
            m.put("minWidth", minWidth);
        }
        if (align != Align.LEFT) {
            m.put("align", align.name().toLowerCase());
        }
        if (!sortable) {
            m.put("sortable", Boolean.FALSE);
        }
        if (!resizable) {
            m.put("resizable", Boolean.FALSE);
        }
        if (hidden) {
            m.put("hidden", Boolean.TRUE);
        }
        if (frozen) {
            m.put("frozen", Boolean.TRUE);
        }
        if (editor != null) {
            m.put("editable", Boolean.TRUE);
        }
        if (styleName != null) {
            m.put("style", styleName);
        }
        return m;
    }
}
