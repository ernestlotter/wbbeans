package org.webbeans.explorer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.PropertyEditor;
import org.webbeans.ui.Property;

/**
 * A tree with property columns (NetBeans' {@code OutlineView}): the tree column shows the nodes, further columns
 * show node properties by name; writable properties are edited in place.
 */
@Renderer(id = "wb.OutlineView", resource = "org/webbeans/nodes/client/outlineview.js")
public class OutlineView extends TreeView {

    /** Column specs: {@code [{name, label, width, editable}]}. */
    public static final Property<List<Object>> COLUMNS = Property.of("columns", listClass(), null);
    public static final Property<String> TREE_COLUMN_LABEL = Property.of("treeColumnLabel", String.class, "");
    public static final Property<Boolean> SORTABLE = Property.of("sortable", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> SHOW_HEADER = Property.of("showHeader", Boolean.class, Boolean.TRUE);

    private final List<String> columnNames = new ArrayList<String>();

    public OutlineView() {
        this("");
    }

    public OutlineView(String treeColumnLabel) {
        super("wb.OutlineView");
        set(TREE_COLUMN_LABEL, treeColumnLabel);
    }

    @SuppressWarnings("unchecked")
    private static Class<List<Object>> listClass() {
        return (Class<List<Object>>) (Class<?>) List.class;
    }

    /** Pairs of property name and column label: {@code setPropertyColumns("size", "Size", "modified", "Modified")}. */
    public OutlineView setPropertyColumns(String... nameLabelPairs) {
        List<Object> cols = new ArrayList<Object>();
        columnNames.clear();
        for (int i = 0; i + 1 < nameLabelPairs.length; i += 2) {
            Map<String, Object> c = new LinkedHashMap<String, Object>();
            c.put("name", nameLabelPairs[i]);
            c.put("label", nameLabelPairs[i + 1]);
            cols.add(c);
            columnNames.add(nameLabelPairs[i]);
        }
        set(COLUMNS, cols);
        if (isAttached()) {
            rebuild();
        }
        return this;
    }

    /** Initial width of a property column in pixels (the user can drag it). */
    @SuppressWarnings("unchecked")
    public OutlineView setColumnWidth(String name, int px) {
        List<Object> cols = get(COLUMNS);
        if (cols == null) {
            return this;
        }
        List<Object> copy = new ArrayList<Object>();
        for (Object o : cols) {
            Map<String, Object> c = new LinkedHashMap<String, Object>((Map<String, Object>) o);
            if (name.equals(c.get("name"))) {
                c.put("width", px);
            }
            copy.add(c);
        }
        set(COLUMNS, copy);
        return this;
    }

    public OutlineView setSortable(boolean b) {
        set(SORTABLE, b);
        return this;
    }

    public OutlineView setShowHeader(boolean b) {
        set(SHOW_HEADER, b);
        return this;
    }

    @Override
    protected Map<String, Object> columns(Node n) {
        if (columnNames.isEmpty()) {
            return null;
        }
        Map<String, Object> cols = new LinkedHashMap<String, Object>();
        for (String name : columnNames) {
            Node.Property<?> p = n.getProperty(name);
            if (p == null) {
                continue;
            }
            Map<String, Object> cell = new LinkedHashMap<String, Object>();
            cell.put("text", p.getValueAsText());
            org.webbeans.ui.table.Cell deco = p.getValueAsCell();
            if (deco != null) {
                cell.put("cell", deco);                         // icon / style / html decoration of the displayed value
            }
            if (p.canWrite()) {
                PropertyEditor<?> e = p.getPropertyEditor();
                cell.put("edit", e == null ? "TEXT" : e.getKind().name());
                if (e != null && e.getChoices() != null) {
                    List<Object> choices = new ArrayList<Object>();
                    for (PropertyEditor.Choice<?> c : e.getChoices()) {
                        Map<String, Object> ch = new LinkedHashMap<String, Object>();
                        ch.put("value", e == null ? "" : textOf(e, c.getValue()));
                        ch.put("label", c.getLabel());
                        if (c.getIcon() != null) {
                            ch.put("icon", c.getIcon());
                        }
                        choices.add(ch);
                    }
                    cell.put("choices", choices);
                }
            }
            cols.put(name, cell);
        }
        return cols;
    }

    @SuppressWarnings("unchecked")
    private static <T> String textOf(PropertyEditor<T> e, Object v) {
        return e.getAsText((T) v);
    }
}
