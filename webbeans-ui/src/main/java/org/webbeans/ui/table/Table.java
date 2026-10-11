package org.webbeans.ui.table;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;
import org.webbeans.ui.ValueComponent;
import org.webbeans.ui.components.DatePicker;
import org.webbeans.ui.components.DateTimePicker;

/**
 * A virtualised data grid over domain objects. Columns are declared with {@link #addColumn}; rows come from a
 * {@link TableModel} ({@link #setItems} wraps a list in a {@link ListTableModel}) in pages the client asks for
 * as it scrolls, so a 100 000-row model costs what is on screen. Cells are rendered on the server to text (or a
 * {@link Cell}), the client never sees {@code T}. Sorting and filtering are the model's job (the table hands it a
 * comparator and a matcher built from the columns); selection is client-owned (keys on the wire).
 *
 * <pre>
 * Table&lt;Order&gt; t = new Table&lt;Order&gt;();
 * t.addColumn("Id", Order::getId).width(80);
 * t.addColumn("Customer", Order::getCustomer);
 * t.addColumn("Total", Order::getTotal).align(Column.Align.RIGHT).width(110);
 * t.setItems(orders);
 * t.addSelectionListener(e -&gt; details.show(t.getSelectedRow()));
 * </pre>
 */
@Renderer(id = "wb.Table", resource = "org/webbeans/ui/client/renderers/table.js")
public class Table<T> extends Component {

    private static final Logger LOG = Logger.getLogger(Table.class.getName());

    public enum SelectionMode { NONE, SINGLE, MULTI }

    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> COLUMNS = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("columns", List.class, new ArrayList<Object>());
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> SELECTED = (Property<List<String>>) (Property<?>) Property.of("selected", List.class, new ArrayList<String>()).clientOwned();
    public static final Property<SelectionMode> SELECTION_MODE = Property.of("selectionMode", SelectionMode.class, SelectionMode.SINGLE);
    @SuppressWarnings("unchecked")
    public static final Property<Map<String, Object>> SORT = (Property<Map<String, Object>>) (Property<?>) Property.of("sort", Map.class, null);
    public static final Property<String> FILTER = Property.of("filter", String.class, null).clientOwned();
    public static final Property<Boolean> SHOW_FILTER = Property.of("showFilter", Boolean.class, Boolean.FALSE);
    public static final Property<String> FILTER_PLACEHOLDER = Property.of("filterPlaceholder", String.class, null);
    public static final Property<Boolean> SHOW_HEADER = Property.of("showHeader", Boolean.class, Boolean.TRUE);
    public static final Property<Integer> PAGE_SIZE = Property.of("pageSize", Integer.class, Integer.valueOf(100));
    public static final Property<Integer> ROW_HEIGHT = Property.of("rowHeight", Integer.class, Integer.valueOf(28));
    public static final Property<String> EMPTY_TEXT = Property.of("emptyText", String.class, null);
    public static final Property<Boolean> STRIPED = Property.of("striped", Boolean.class, Boolean.TRUE);

    public static final EventType<SelectionEvent> SELECTION = EventType.of("select", SelectionEvent::new);
    public static final EventType<RowEvent> ACTIVATE = EventType.of("activate", RowEvent::new);
    public static final EventType<RowEvent> ROW_MENU = EventType.of("rowMenu", RowEvent::new);
    public static final EventType<CellEditEvent> CELL_EDIT = EventType.of("edit", CellEditEvent::new);
    public static final EventType<Event> SORT_CHANGE = EventType.of("sort", Event::new);
    static final EventType<Event> PAGE = EventType.of("page", Event::new);
    static final EventType<Event> FILTER_CHANGE = EventType.of("filter", Event::new).lazy(300);

    /** {@code select {keys}}. */
    public static class SelectionEvent extends Event {
        public SelectionEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        @SuppressWarnings("unchecked")
        public <T> List<T> getSelected() {
            return ((Table<T>) getSource()).getSelected();
        }
    }

    /** {@code activate {key}} (double-click / Enter) and {@code rowMenu {key, x, y}} (right-click). */
    public static class RowEvent extends Event {
        public RowEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        @SuppressWarnings("unchecked")
        public <T> T getRow() {
            return ((Table<T>) getSource()).row(getKey());
        }

        public int getX() {
            return getInt("x", 0);
        }

        public int getY() {
            return getInt("y", 0);
        }
    }

    /** {@code edit {key, col, value}}: after the column's editor applied (or rejected) the text. */
    public static class CellEditEvent extends Event {
        public CellEditEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        @SuppressWarnings("unchecked")
        public <T> T getRow() {
            return ((Table<T>) getSource()).row(getKey());
        }

        public String getColumnId() {
            return getString("col");
        }

        public String getText() {
            return getString("value");
        }
    }

    private final List<Column<T>> columns = new ArrayList<Column<T>>();
    private TableModel<T> model;
    private final TableModel.Listener<T> modelListener = new TableModel.Listener<T>() {
        @Override
        public void reset() {
            Table.this.reset();
        }

        @Override
        public void updated(T row) {
            Table.this.updated(row);
        }
    };
    private Function<T, String> keyFunction = new Function<T, String>() {
        @Override
        public String apply(T t) {
            return String.valueOf(t);
        }
    };
    private Function<T, String> rowStyle;
    private final Map<String, T> byKey = new LinkedHashMap<String, T>();
    private final Set<Integer> loading = new HashSet<Integer>();
    private int generation;
    private int total = -1;
    private int columnSeq;

    public Table() {
        this("wb.Table");
    }

    /** For subclasses with their own renderer (one that extends {@code wb.Table} on the client). */
    protected Table(String rendererId) {
        super(rendererId);
        addListener(PAGE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                load(e.getInt("offset", 0), e.getInt("limit", get(PAGE_SIZE)));
            }
        });
        addListener(SELECTION, new Listener<SelectionEvent>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onEvent(SelectionEvent e) {
                Object v = e.getData().get("keys");
                setFromClient(SELECTED, v instanceof List ? new ArrayList<String>((List<String>) v) : new ArrayList<String>());
            }
        });
        addListener(SORT_CHANGE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String col = e.getString("col");
                setSort(col == null || col.isEmpty() ? null : col, e.getBoolean("asc"));
            }
        });
        addListener(FILTER_CHANGE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String text = e.getString("text");
                setFromClient(FILTER, text == null || text.isEmpty() ? null : text);
                reset();
            }
        });
        addListener(CELL_EDIT, new Listener<CellEditEvent>() {
            @Override
            public void onEvent(CellEditEvent e) {
                applyEdit(e);
            }
        });
    }

    // ---- columns ------------------------------------------------------------------------------------------------

    /** Adds a column; the id defaults to {@code c<n>} and is what sort events and {@link #getColumn} use. */
    public Column<T> addColumn(String title, Function<T, ?> value) {
        return addColumn("c" + (++columnSeq), title, value);
    }

    public Column<T> addColumn(String id, String title, Function<T, ?> value) {
        Column<T> c = new Column<T>(id, title, value);
        columns.add(c);
        scheduleColumns();
        return c;
    }

    private boolean columnsScheduled;

    /** Publishes the columns just before the next flush, so chained settings on a new column are included. */
    private void scheduleColumns() {
        if (!isAttached()) {
            return; // attached() publishes
        }
        if (!columnsScheduled) {
            columnsScheduled = true;
            getUI().beforeFlush(new Runnable() {
                @Override
                public void run() {
                    columnsScheduled = false;
                    publishColumns();
                }
            });
        }
    }

    public List<Column<T>> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public Column<T> getColumn(String id) {
        for (Column<T> c : columns) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public void removeColumn(Column<T> c) {
        if (columns.remove(c)) {
            publishColumns();
            reset();
        }
    }

    /** Re-sends the column definitions after changing one (title, width, hidden, …). */
    public void publishColumns() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Column<T> c : columns) {
            list.add(c.toJson());
        }
        set(COLUMNS, list);
    }

    // ---- model and rows -------------------------------------------------------------------------------------------

    public Table<T> setModel(TableModel<T> m) {
        if (model != null) {
            model.removeListener(modelListener);
        }
        model = m;
        if (model != null) {
            model.addListener(modelListener);
        }
        reset();
        return this;
    }

    public TableModel<T> getModel() {
        return model;
    }

    /** Shows a list of rows (a {@link ListTableModel}); the model is available from {@link #getModel}. */
    public Table<T> setItems(Collection<? extends T> rows) {
        return setModel(new ListTableModel<T>(rows));
    }

    /** How a row becomes the key the client uses (default {@code toString}); set before the model. */
    public Table<T> setKeyFunction(Function<T, String> f) {
        this.keyFunction = f;
        return this;
    }

    public String key(T row) {
        return keyFunction.apply(row);
    }

    /** The row with that key, if it has been sent to the client since the last reset. */
    public T row(String key) {
        return key == null ? null : byKey.get(key);
    }

    /** CSS class(es) per row. */
    public Table<T> setRowStyle(Function<T, String> f) {
        this.rowStyle = f;
        return this;
    }

    /** Rows matching the query (−1 until the first page arrived). */
    public int getTotal() {
        return total;
    }

    /** Re-requests everything from the model (after changes it did not announce). */
    public void refresh() {
        reset();
    }

    private void reset() {
        generation++;
        loading.clear();
        byKey.clear();
        total = -1;
        call("rows", resetPayload());
    }

    private Map<String, Object> resetPayload() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("reset", Boolean.TRUE);
        return m;
    }

    @Override
    protected void resync() {
        generation++;
        loading.clear();
        byKey.clear();
    }

    private void load(final int offset, final int limit) {
        if (model == null) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("total", 0);
            m.put("offset", 0);
            m.put("rows", new ArrayList<Object>());
            call("rows", m);
            return;
        }
        if (!loading.add(offset)) {
            return; // already on its way
        }
        final int gen = generation;
        model.load(query(), offset, limit, new TableModel.Callback<T>() {
            @Override
            public void loaded(int tot, int off, List<T> rows) {
                if (gen != generation) {
                    return; // stale: a reset happened while loading
                }
                loading.remove(offset);
                total = tot;
                List<Object> encoded = new ArrayList<Object>(rows.size());
                for (T r : rows) {
                    encoded.add(encodeRow(r));
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("total", tot);
                m.put("offset", off);
                m.put("rows", encoded);
                call("rows", m);
            }
        });
    }

    private void updated(T row) {
        String k = key(row);
        if (byKey.containsKey(k)) {
            byKey.put(k, row);
            call("update", Collections.singletonList(encodeRow(row)));
        }
    }

    /** Re-renders rows the application changed in place (same as the model's {@code updated}). */
    public void updateRow(T row) {
        updated(row);
    }

    private Query<T> query() {
        Map<String, Object> sort = get(SORT);
        String col = sort == null ? null : (String) sort.get("col");
        boolean asc = sort == null || !Boolean.FALSE.equals(sort.get("asc"));
        Comparator<T> cmp = null;
        Column<T> column = col == null ? null : getColumn(col);
        if (column != null) {
            cmp = comparatorFor(column);
            if (!asc) {
                cmp = Collections.reverseOrder(cmp);
            }
        }
        String filter = get(FILTER);
        Predicate<T> matcher = null;
        if (filter != null && !filter.trim().isEmpty()) {
            final String needle = filter.trim().toLowerCase(Locale.ROOT);
            matcher = new Predicate<T>() {
                @Override
                public boolean test(T t) {
                    for (Column<T> c : columns) {
                        if (!c.isHidden() && cellText(c, t).toLowerCase(Locale.ROOT).contains(needle)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
        return new Query<T>(col, asc, filter, cmp, matcher);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Comparator<T> comparatorFor(final Column<T> c) {
        if (c.getComparator() != null) {
            return c.getComparator();
        }
        return new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                Object va = c.getValue().apply(a), vb = c.getValue().apply(b);
                if (va instanceof Cell) {
                    va = ((Cell) va).getText();
                }
                if (vb instanceof Cell) {
                    vb = ((Cell) vb).getText();
                }
                if (va == null) {
                    return vb == null ? 0 : -1;
                }
                if (vb == null) {
                    return 1;
                }
                if (va instanceof Comparable && va.getClass() == vb.getClass()) {
                    return ((Comparable) va).compareTo(vb);
                }
                if (va instanceof Number && vb instanceof Number) {
                    return Double.compare(((Number) va).doubleValue(), ((Number) vb).doubleValue());
                }
                return String.valueOf(va).compareToIgnoreCase(String.valueOf(vb));
            }
        };
    }

    // ---- cells --------------------------------------------------------------------------------------------------

    private Map<String, Object> encodeRow(T row) {
        String k = key(row);
        byKey.put(k, row);
        List<Object> cells = new ArrayList<Object>(columns.size());
        for (Column<T> c : columns) {
            cells.add(encodeCell(c, row));
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("k", k);
        m.put("c", cells);
        if (rowStyle != null) {
            String s = rowStyle.apply(row);
            if (s != null && !s.isEmpty()) {
                m.put("s", s);
            }
        }
        decorateRow(row, m);
        return m;
    }

    /** Hook for subclasses: adds entries to the encoded row ({@code k}, {@code c}, {@code s} are taken). */
    protected void decorateRow(T row, Map<String, Object> encoded) {
    }

    private Object encodeCell(Column<T> c, T row) {
        Object v;
        try {
            v = c.getValue().apply(row);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Column " + c.getId() + " failed for " + row, e);
            return "!";
        }
        if (v instanceof Cell) {
            return v;
        }
        if (v instanceof Resource) {
            return Cell.of("", (Resource) v);
        }
        if (c.getFormat() == null && v instanceof Enum && org.webbeans.ui.EnumLabels.icon((Enum<?>) v) != null) {
            return Cell.of(org.webbeans.ui.EnumLabels.label((Enum<?>) v), org.webbeans.ui.EnumLabels.icon((Enum<?>) v));
        }
        return text(c, v);
    }

    /** The text of a cell as the client shows it (also what the filter matches against). */
    public String cellText(Column<T> c, T row) {
        Object v = c.getValue().apply(row);
        if (v instanceof Cell) {
            String t = ((Cell) v).getText();
            return t == null ? "" : t;
        }
        return text(c, v);
    }

    private String text(Column<T> c, Object v) {
        if (c.getFormat() != null) {
            String s = c.getFormat().apply(v);
            return s == null ? "" : s;
        }
        if (v == null) {
            return "";
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? "✓" : "";
        }
        if (v instanceof Number) {
            Number n = (Number) v;
            NumberFormat f = NumberFormat.getNumberInstance(locale());
            if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
                f.setMaximumFractionDigits(0);
            } else {
                f.setMaximumFractionDigits(2);
            }
            return f.format(n);
        }
        if (v instanceof LocalDate) {
            return DatePicker.format((LocalDate) v);
        }
        if (v instanceof LocalDateTime) {
            return DateTimePicker.format((LocalDateTime) v);
        }
        return String.valueOf(v);
    }

    private static Locale locale() {
        Locale l = LocaleContext.get();
        return l != null ? l : Locale.getDefault();
    }

    private void applyEdit(CellEditEvent e) {
        T row = row(e.getKey());
        Column<T> c = getColumn(e.getColumnId());
        if (row == null || c == null) {
            return;
        }
        if (c.getEditor() != null) {
            try {
                c.getEditor().accept(row, e.getText() == null ? "" : e.getText());
            } catch (IllegalArgumentException ex) {
                call("editError", e.getKey(), c.getId(), ex.getMessage() != null ? ex.getMessage() : "Invalid value");
            }
        }
        updated(row);
    }

    // ---- selection ------------------------------------------------------------------------------------------------

    public Table<T> setSelectionMode(SelectionMode m) {
        set(SELECTION_MODE, m);
        return this;
    }

    public List<T> getSelected() {
        List<T> out = new ArrayList<T>();
        for (String k : get(SELECTED)) {
            T t = byKey.get(k);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    public T getSelectedRow() {
        List<T> s = getSelected();
        return s.isEmpty() ? null : s.get(0);
    }

    /** Selects rows by key (they need not be loaded yet; the client highlights them when they appear). */
    public Table<T> setSelected(Collection<? extends T> rows) {
        List<String> keys = new ArrayList<String>();
        for (T r : rows) {
            keys.add(key(r));
        }
        set(SELECTED, keys);
        return this;
    }

    public Table<T> setSelectedRow(T row) {
        return setSelected(row == null ? Collections.<T>emptyList() : Collections.singletonList(row));
    }

    public Table<T> clearSelection() {
        return setSelected(Collections.<T>emptyList());
    }

    /** Scrolls the row with that key into view (if it is within the loaded range the client knows its index). */
    public void scrollTo(T row) {
        call("scrollTo", key(row));
    }

    public void scrollToIndex(int index) {
        call("scrollToIndex", index);
    }

    // ---- sort / filter / presentation ------------------------------------------------------------------------------

    /** Sorts by a column id (null: the model's order) and resets. */
    public Table<T> setSort(String columnId, boolean ascending) {
        if (columnId == null) {
            set(SORT, null);
        } else {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("col", columnId);
            m.put("asc", ascending);
            set(SORT, m);
        }
        reset();
        return this;
    }

    public Table<T> setSort(Column<T> column, boolean ascending) {
        return setSort(column == null ? null : column.getId(), ascending);
    }

    public String getSortColumn() {
        Map<String, Object> s = get(SORT);
        return s == null ? null : (String) s.get("col");
    }

    public boolean isSortAscending() {
        Map<String, Object> s = get(SORT);
        return s == null || !Boolean.FALSE.equals(s.get("asc"));
    }

    /** Filters by text (matched against every visible column's text by the built-in models) and resets. */
    public Table<T> setFilter(String text) {
        set(FILTER, text == null || text.isEmpty() ? null : text);
        reset();
        return this;
    }

    public String getFilter() {
        return get(FILTER);
    }

    /** A filter box above the grid. */
    public Table<T> setShowFilter(boolean b) {
        set(SHOW_FILTER, b);
        return this;
    }

    public Table<T> setShowHeader(boolean b) {
        set(SHOW_HEADER, b);
        return this;
    }

    public Table<T> setPageSize(int rows) {
        set(PAGE_SIZE, rows);
        return this;
    }

    public Table<T> setRowHeight(int px) {
        set(ROW_HEIGHT, px);
        return this;
    }

    public Table<T> setEmptyText(String t) {
        set(EMPTY_TEXT, t);
        return this;
    }

    public Table<T> setStriped(boolean b) {
        set(STRIPED, b);
        return this;
    }

    @Override
    protected void attached() {
        publishColumns();
        if (get(EMPTY_TEXT) == null) {
            set(EMPTY_TEXT, Bundles.getMessage(ValueComponent.class, "LBL_NoRows"));
        }
        if (get(FILTER_PLACEHOLDER) == null) {
            set(FILTER_PLACEHOLDER, Bundles.getMessage(ValueComponent.class, "LBL_Filter"));
        }
    }

    // ---- listeners -----------------------------------------------------------------------------------------------

    public Registration addSelectionListener(Listener<SelectionEvent> l) {
        return addListener(SELECTION, l);
    }

    /** Double-click or Enter on a row. */
    public Registration addActivateListener(Listener<RowEvent> l) {
        return addListener(ACTIVATE, l);
    }

    /** Right-click on a row (the row is selected first); show a {@code Popup} at the event's x/y. */
    public Registration addRowMenuListener(Listener<RowEvent> l) {
        return addListener(ROW_MENU, l);
    }

    public Registration addCellEditListener(Listener<CellEditEvent> l) {
        return addListener(CELL_EDIT, l);
    }

    public Registration addSortListener(Listener<Event> l) {
        return addListener(SORT_CHANGE, l);
    }
}
