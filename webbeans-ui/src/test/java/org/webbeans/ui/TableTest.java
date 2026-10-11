package org.webbeans.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.table.Cell;
import org.webbeans.ui.table.Column;
import org.webbeans.ui.table.LazyTableModel;
import org.webbeans.ui.table.ListTableModel;
import org.webbeans.ui.table.Query;
import org.webbeans.ui.table.Table;

/** The Table: columns on the wire, paging, server-side sort and filter, keyed updates, selection, inline edits, lazy models. */
public class TableTest {

    static final class Order {
        final int id;
        String customer;
        double total;
        boolean paid;

        Order(int id, String customer, double total, boolean paid) {
            this.id = id;
            this.customer = customer;
            this.total = total;
            this.paid = paid;
        }

        @Override
        public String toString() {
            return "o" + id;
        }
    }

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private UISession session;
    private UI ui;
    private RecordingTransport t;
    private Panel root;
    private Table<Order> table;
    private ListTableModel<Order> model;
    private final List<Order> orders = new ArrayList<Order>();

    @Before
    public void setUp() {
        for (int i = 1; i <= 250; i++) {
            orders.add(new Order(i, "Customer " + (char) ('A' + i % 26), i * 10.5, i % 3 == 0));
        }
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI ui) {
                root = new Panel();
                table = new Table<Order>();
                table.addColumn("id", "Id", new Function<Order, Object>() {
                    @Override
                    public Object apply(Order o) {
                        return o.id;
                    }
                }).width(80).align(Column.Align.RIGHT);
                table.addColumn("customer", "Customer", new Function<Order, Object>() {
                    @Override
                    public Object apply(Order o) {
                        return o.customer;
                    }
                }).editable(new java.util.function.BiConsumer<Order, String>() {
                    @Override
                    public void accept(Order o, String text) {
                        if (text.trim().isEmpty()) {
                            throw new IllegalArgumentException("Name needed");
                        }
                        o.customer = text.trim();
                    }
                });
                table.addColumn("total", "Total", new Function<Order, Object>() {
                    @Override
                    public Object apply(Order o) {
                        return o.total;
                    }
                }).align(Column.Align.RIGHT);
                table.addColumn("paid", "Paid", new Function<Order, Object>() {
                    @Override
                    public Object apply(Order o) {
                        return o.paid ? Cell.of("yes").style("ok") : Boolean.FALSE;
                    }
                }).sortable(false);
                table.setPageSize(100).setSelectionMode(Table.SelectionMode.MULTI);
                model = new ListTableModel<Order>(orders);
                table.setModel(model);
                root.add(table);
                ui.setContent(root);
            }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(provider));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, Locale.US, null, DIRECT);
        t = new RecordingTransport();
        ui = session.createUI("u1");
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, -1);
            }
        });
    }

    @After
    public void tearDown() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    private void send(final String type, final Map<String, Object> data) {
        Map<String, Object> ev = new LinkedHashMap<String, Object>();
        ev.put("id", table.getId());
        ev.put("type", type);
        ev.put("data", data);
        final List<Object> events = Collections.<Object>singletonList(ev);
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(events);
            }
        });
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** All call ops for the table across the recorded messages: m → args. */
    private List<Map<String, Object>> calls(String method) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String m : t.messages) {
            for (Object o : Json.array(Json.parseObject(m), "ops")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mo = (Map<String, Object>) o;
                if ("call".equals(mo.get("op")) && Json.integer(mo, "id", -1) == table.getId() && method.equals(mo.get("m"))) {
                    out.add(mo);
                }
            }
        }
        return out;
    }

    private Map<String, Object> lastRows() {
        List<Map<String, Object>> c = calls("rows");
        return obj(Json.array(c.get(c.size() - 1), "args").get(0));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(Map<String, Object> payload) {
        return (List<Map<String, Object>>) (List<?>) Json.array(payload, "rows");
    }

    @Test
    public void columnsAndPaging() {
        Map<String, Object> init = Json.parseObject(t.messages.get(0));
        Map<String, Object> create = null;
        for (Object o : Json.array(init, "ops")) {
            Map<String, Object> op = obj(o);
            if ("create".equals(op.get("op")) && "wb.Table".equals(op.get("type"))) {
                create = op;
            }
        }
        assertNotNull(create);
        List<Object> cols = Json.array(Json.object(create, "props"), "columns");
        assertEquals(4, cols.size());
        assertEquals("right", obj(cols.get(0)).get("align"));
        assertEquals(Boolean.TRUE, obj(cols.get(1)).get("editable"));
        assertEquals(Boolean.FALSE, obj(cols.get(3)).get("sortable"));
        assertEquals("MULTI", Json.object(create, "props").get("selectionMode"));
        assertEquals("No rows", Json.object(create, "props").get("emptyText"));

        // the client asks for the first page
        t.clear();
        send("page", data("offset", 0, "limit", 100));
        Map<String, Object> rows = lastRows();
        assertEquals(250L, rows.get("total"));
        assertEquals(0L, rows.get("offset"));
        assertEquals(100, rowsOf(rows).size());
        Map<String, Object> first = rowsOf(rows).get(0);
        assertEquals("o1", first.get("k"));
        List<Object> cells = Json.array(first, "c");
        assertEquals("1", cells.get(0));
        assertEquals("Customer B", cells.get(1));
        assertEquals("10.5", cells.get(2));
        assertEquals("", cells.get(3));                          // false → empty
        assertEquals("yes", obj(Json.array(rowsOf(rows).get(2), "c").get(3)).get("t"));   // Cell
        assertEquals("ok", obj(Json.array(rowsOf(rows).get(2), "c").get(3)).get("s"));
        assertEquals(250, table.getTotal());

        // a second request for the same page while it is "loading" is ignored; a later page is served
        t.clear();
        send("page", data("offset", 200, "limit", 100));
        assertEquals(50, rowsOf(lastRows()).size());
        assertEquals(200L, lastRows().get("offset"));
    }

    @Test
    public void sortFilterAndKeyedUpdates() {
        send("page", data("offset", 0, "limit", 100));
        t.clear();
        send("sort", data("col", "total", "asc", false));
        // a reset first, then the client re-requests
        assertEquals(Boolean.TRUE, obj(Json.array(calls("rows").get(0), "args").get(0)).get("reset"));
        assertEquals("total", table.getSortColumn());
        assertFalse(table.isSortAscending());
        t.clear();
        send("page", data("offset", 0, "limit", 100));
        assertEquals("o250", rowsOf(lastRows()).get(0).get("k"));

        // filter across visible columns (case-insensitive)
        t.clear();
        send("filter", data("text", "customer z"));
        send("page", data("offset", 0, "limit", 100));
        Map<String, Object> rows = lastRows();
        assertEquals("customer z", table.getFilter());
        assertTrue(((Number) rows.get("total")).intValue() > 0 && ((Number) rows.get("total")).intValue() < 20);
        for (Map<String, Object> r : rowsOf(rows)) {
            assertEquals("Customer Z", Json.array(r, "c").get(1));
        }

        // a model update re-renders the row in place
        t.clear();
        Order o = orders.get(24);                                // id 25, Customer Z
        o.customer = "Zed Ltd";
        ui.access(new Runnable() {
            @Override
            public void run() {
                model.update(orders.get(24));
            }
        });
        List<Map<String, Object>> upd = calls("update");
        assertEquals(1, upd.size());
        Map<String, Object> row = obj(arr(Json.array(upd.get(0), "args").get(0)).get(0));
        assertEquals("o25", row.get("k"));
        assertEquals("Zed Ltd", Json.array(row, "c").get(1));

        // adding a row resets
        t.clear();
        ui.access(new Runnable() {
            @Override
            public void run() {
                model.add(new Order(999, "Customer Z", 1, false));
            }
        });
        assertEquals(Boolean.TRUE, obj(Json.array(calls("rows").get(0), "args").get(0)).get("reset"));

        // clearing the sort and filter from the server
        t.clear();
        ui.access(new Runnable() {
            @Override
            public void run() {
                table.setSort((String) null, true).setFilter(null);
            }
        });
        send("page", data("offset", 0, "limit", 100));
        assertEquals(251L, lastRows().get("total"));
        assertEquals("o1", rowsOf(lastRows()).get(0).get("k"));
    }

    @Test
    public void selectionActivationAndInlineEdit() {
        send("page", data("offset", 0, "limit", 100));
        final List<String> log = new ArrayList<String>();
        ui.access(new Runnable() {
            @Override
            public void run() {
                table.addSelectionListener(new Listener<Table.SelectionEvent>() {
                    @Override
                    public void onEvent(Table.SelectionEvent e) {
                        log.add("sel" + e.getSelected());
                    }
                });
                table.addActivateListener(new Listener<Table.RowEvent>() {
                    @Override
                    public void onEvent(Table.RowEvent e) {
                        log.add("act" + e.<Order>getRow());
                    }
                });
                table.addRowMenuListener(new Listener<Table.RowEvent>() {
                    @Override
                    public void onEvent(Table.RowEvent e) {
                        log.add("menu" + e.getRow() + "@" + e.getX() + "," + e.getY());
                    }
                });
                table.addCellEditListener(new Listener<Table.CellEditEvent>() {
                    @Override
                    public void onEvent(Table.CellEditEvent e) {
                        log.add("edit" + e.getRow() + ":" + e.getColumnId() + "=" + e.getText());
                    }
                });
            }
        });
        send("select", data("keys", Arrays.asList("o3", "o7")));
        assertEquals(2, table.getSelected().size());
        assertEquals(3, table.getSelectedRow().id);
        send("activate", data("key", "o7"));
        send("rowMenu", data("key", "o3", "x", 10, "y", 20));
        assertEquals(Arrays.asList("sel[o3, o7]", "acto7", "menuo3@10,20"), log);

        t.clear();
        send("edit", data("key", "o3", "col", "customer", "value", "  New Name "));
        assertEquals("New Name", orders.get(2).customer);
        assertEquals("edito3:customer=  New Name ", log.get(3));
        assertEquals("the edited row is re-sent", "New Name", Json.array(obj(arr(Json.array(calls("update").get(0), "args").get(0)).get(0)), "c").get(1));

        t.clear();
        send("edit", data("key", "o3", "col", "customer", "value", "   "));
        assertEquals("New Name", orders.get(2).customer);       // rejected
        assertEquals(1, calls("editError").size());
        assertEquals("Name needed", Json.array(calls("editError").get(0), "args").get(2));
        assertEquals(1, calls("update").size());                 // re-rendered to restore the text

        ui.access(new Runnable() {
            @Override
            public void run() {
                table.setSelectedRow(orders.get(9));
            }
        });
        assertEquals(Arrays.asList("o10"), table.get(Table.SELECTED));
    }

    @Test
    public void lazyModelLoadsInTheBackground() throws Exception {
        final List<String> queries = new ArrayList<String>();
        final LazyTableModel<Order> lazy = new LazyTableModel<Order>() {
            @Override
            protected int count(Query<Order> query) {
                queries.add(String.valueOf(query));
                return 5000;
            }

            @Override
            protected List<Order> fetch(Query<Order> query, int offset, int limit) {
                List<Order> out = new ArrayList<Order>();
                for (int i = offset; i < Math.min(5000, offset + limit); i++) {
                    out.add(new Order(i, query.isAscending() ? "asc" : "desc", i, false));
                }
                return out;
            }
        };
        // the session executor is direct here, so the background delivery runs on the RequestProcessor thread;
        // wait for it with a latch released by the transport
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final RecordingTransport waiting = new RecordingTransport() {
            @Override
            public void send(String json) {
                super.send(json);
                if (json.contains("\"total\":5000")) {
                    latch.countDown();
                }
            }
        };
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(waiting, -1);
                table.setModel(lazy);
                table.setSort("id", false);
            }
        });
        send("page", data("offset", 300, "limit", 100));
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        t = waiting;
        Map<String, Object> rows = lastRows();
        assertEquals(5000L, rows.get("total"));
        assertEquals(300L, rows.get("offset"));
        assertEquals(100, rowsOf(rows).size());
        assertEquals("desc", Json.array(rowsOf(rows).get(0), "c").get(1));
        assertEquals("Query[sort=id desc, filter=null]", queries.get(0));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> arr(Object o) {
        return (List<Object>) o;
    }
}
