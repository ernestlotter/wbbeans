package org.webbeans.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.ui.components.Accordion;
import org.webbeans.ui.components.Calendar;
import org.webbeans.ui.components.Diagram;
import org.webbeans.ui.components.DiffView;
import org.webbeans.ui.components.Kanban;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.MarkdownEditor;
import org.webbeans.ui.components.OutputView;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.PasswordField;
import org.webbeans.ui.components.PropertyGrid;
import org.webbeans.ui.components.TagInput;
import org.webbeans.ui.components.TimePicker;
import org.webbeans.ui.table.TreeTable;
import org.webbeans.ui.text.LineDiff;

/** Step 12: the text, data and board components — server-side logic and what reaches the wire. */
public class Components2Test {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    enum Shade { LIGHT, DARK }

    static final class Bean {
        String name = "Earth";
        int moons = 1;
        Shade shade = Shade.LIGHT;
        LocalDate found = LocalDate.of(2000, 1, 1);
    }

    private UISession session;
    private UI ui;
    private RecordingTransport t;
    private Panel root;

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI ui) {
                root = new Panel();
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

    private void run(Runnable r) {
        ui.access(r);
    }

    private void send(final Component c, final String type, final Map<String, Object> data) {
        Map<String, Object> ev = new LinkedHashMap<String, Object>();
        ev.put("id", c.getId());
        ev.put("type", type);
        ev.put("data", data);
        final List<Object> events = Collections.<Object>singletonList(ev);
        run(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(events);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Call ops for a component across all recorded messages. */
    private List<Map<String, Object>> calls(Component c, String method) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String m : t.messages) {
            for (Object o : Json.array(Json.parseObject(m), "ops")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mo = (Map<String, Object>) o;
                if ("call".equals(mo.get("op")) && Json.integer(mo, "id", -1) == c.getId() && method.equals(mo.get("m"))) {
                    out.add(mo);
                }
            }
        }
        return out;
    }

    /** The last value sent for a property of a component (create or set op), or null. */
    private Object sentProp(Component c, String name) {
        Object found = null;
        for (String m : t.messages) {
            for (Object o : Json.array(Json.parseObject(m), "ops")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mo = (Map<String, Object>) o;
                if (Json.integer(mo, "id", -1) != c.getId()) {
                    continue;
                }
                if ("create".equals(mo.get("op")) && Json.object(mo, "props").containsKey(name)) {
                    found = Json.object(mo, "props").get(name);
                } else if ("set".equals(mo.get("op")) && Json.object(mo, "props").containsKey(name)) {
                    found = Json.object(mo, "props").get(name);
                }
            }
        }
        return found;
    }

    // ---- LineDiff / DiffView ---------------------------------------------------------------------------------------

    @Test
    public void lineDiffFindsShortestScript() {
        List<LineDiff.Op> ops = LineDiff.diff("a\nb\nc\nd\n", "a\nc\nd\ne\n");
        StringBuilder sb = new StringBuilder();
        for (LineDiff.Op op : ops) {
            sb.append(op).append('|');
        }
        assertEquals(" a|-b| c| d|+e|", sb.toString());
        assertEquals(0, LineDiff.diff("same\n", "same\n").size() - 1);
        assertEquals("@@ -1,4 +1,4 @@\n a\n-b\n c\n d\n+e\n", LineDiff.unified(ops, 3));
        List<LineDiff.Op> all = LineDiff.diff("", "x\ny");
        assertEquals(2, all.size());
        assertEquals(LineDiff.Type.INSERT, all.get(0).type);
        assertEquals(LineDiff.Type.DELETE, LineDiff.diff("x", "").get(0).type);
        // a larger, scrambled pair still yields a consistent script
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            a.append("line ").append(i).append('\n');
            if (i % 7 != 0) {
                b.append("line ").append(i % 5 == 0 ? i + 1000 : i).append('\n');
            }
        }
        List<LineDiff.Op> big = LineDiff.diff(a.toString(), b.toString());
        StringBuilder left = new StringBuilder(), right = new StringBuilder();
        for (LineDiff.Op op : big) {
            if (op.type != LineDiff.Type.INSERT) {
                left.append(op.text).append('\n');
            }
            if (op.type != LineDiff.Type.DELETE) {
                right.append(op.text).append('\n');
            }
        }
        assertEquals(a.toString(), left.toString());
        assertEquals(b.toString(), right.toString());
    }

    @Test
    public void diffViewSendsOpsAndFolds() {
        final DiffView[] d = new DiffView[1];
        run(new Runnable() {
            @Override
            public void run() {
                d[0] = new DiffView("1\n2\n3\n4\n5\n6\n7\n8\n9\n", "1\n2\n3\n4\n5\n6\n7\n8\nnine\n");
                root.add(d[0]);
            }
        });
        assertEquals(1, d[0].getAddedCount());
        assertEquals(1, d[0].getRemovedCount());
        List<Object> ops = list(sentProp(d[0], "ops"));
        assertEquals(10, ops.size());
        run(new Runnable() {
            @Override
            public void run() {
                d[0].setContext(2);
            }
        });
        ops = list(sentProp(d[0], "ops"));
        // 6 folded + 2 context + delete + insert
        assertEquals(5, ops.size());
        Map<String, Object> fold = obj(ops.get(0));
        assertEquals("~", fold.get("op"));
        assertEquals(6, Json.array(fold, "lines").size());
        assertTrue(d[0].toUnified(1).startsWith("@@ -8,2 +8,2 @@"));
    }

    // ---- OutputView ------------------------------------------------------------------------------------------------

    @Test
    public void outputViewBatchesAppendsAndResyncsTheBuffer() {
        final OutputView out = new OutputView();
        run(new Runnable() {
            @Override
            public void run() {
                out.setMaxLines(5);
                root.add(out);
                out.append("one").error("two").append("three\nfour");
            }
        });
        List<Map<String, Object>> appends = calls(out, "append");
        assertEquals("one flush, one batch", 1, appends.size());
        List<Object> batch = list(Json.array(appends.get(0), "args").get(0));
        assertEquals(4, batch.size());
        assertEquals("error", obj(batch.get(1)).get("s"));
        assertEquals("four", obj(batch.get(3)).get("t"));
        run(new Runnable() {
            @Override
            public void run() {
                out.append("five").append("six").append("seven");
            }
        });
        assertEquals("ring buffer keeps the last five", 5, out.getLineCount());
        assertEquals("three", out.getLines().get(0).getText());
        assertEquals("three\nfour\nfive\nsix\nseven", out.getText());
        // follow is client-owned
        send(out, "follow", data("value", false));
        assertFalse(out.isFollow());
        // a resync (the client's sequence is unknown → full snapshot) re-sends the whole buffer as one "set"
        t.clear();
        run(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, -1);
            }
        });
        List<Map<String, Object>> sets = calls(out, "set");
        assertEquals(1, sets.size());
        assertEquals(5, list(Json.array(sets.get(0), "args").get(0)).size());
        run(new Runnable() {
            @Override
            public void run() {
                out.clear();
            }
        });
        assertEquals(0, out.getLineCount());
        assertEquals(1, calls(out, "clear").size());
    }

    // ---- TreeTable -------------------------------------------------------------------------------------------------

    @Test
    public void treeTableFlattensExpandedNodesAndFilters() {
        final TreeTable<String> tt = new TreeTable<String>();
        final Map<String, List<String>> kids = new LinkedHashMap<String, List<String>>();
        kids.put("fruit", Arrays.asList("apple", "banana"));
        kids.put("veg", Arrays.asList("carrot"));
        run(new Runnable() {
            @Override
            public void run() {
                tt.addColumn("name", "Name", new Function<String, Object>() {
                    @Override
                    public Object apply(String s) {
                        return s;
                    }
                });
                tt.setTree(kids.keySet(), new Function<String, List<String>>() {
                    @Override
                    public List<String> apply(String s) {
                        return kids.get(s);
                    }
                });
                root.add(tt);
            }
        });
        send(tt, "page", data("offset", 0, "limit", 100));
        List<Map<String, Object>> rows = calls(tt, "rows");
        Map<String, Object> last = obj(Json.array(rows.get(rows.size() - 1), "args").get(0));
        assertEquals(2, Json.integer(last, "total", -1));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rs = (List<Map<String, Object>>) (List<?>) Json.array(last, "rows");
        assertEquals(Boolean.TRUE, rs.get(0).get("x"));
        assertNull(rs.get(0).get("e"));
        assertEquals(0, Json.integer(rs.get(0), "d", -1));
        // toggle from the client expands and resets
        send(tt, "toggle", data("key", "fruit"));
        assertTrue(tt.isExpanded("fruit"));
        send(tt, "page", data("offset", 0, "limit", 100));
        rows = calls(tt, "rows");
        last = obj(Json.array(rows.get(rows.size() - 1), "args").get(0));
        assertEquals(4, Json.integer(last, "total", -1));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rs2 = (List<Map<String, Object>>) (List<?>) Json.array(last, "rows");
        assertEquals("apple", rs2.get(1).get("k"));
        assertEquals(1, Json.integer(rs2.get(1), "d", -1));
        assertEquals("fruit", rs2.get(1).get("p"));
        assertEquals(Boolean.TRUE, rs2.get(0).get("e"));
        assertEquals(1, tt.getDepth("banana"));
        // filtering shows matches with their ancestors, expanded
        send(tt, "filter", data("text", "carr"));
        send(tt, "page", data("offset", 0, "limit", 100));
        rows = calls(tt, "rows");
        last = obj(Json.array(rows.get(rows.size() - 1), "args").get(0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rs3 = (List<Map<String, Object>>) (List<?>) Json.array(last, "rows");
        assertEquals(2, rs3.size());
        assertEquals("veg", rs3.get(0).get("k"));
        assertEquals("carrot", rs3.get(1).get("k"));
        run(new Runnable() {
            @Override
            public void run() {
                tt.collapseAll();
            }
        });
        assertTrue(tt.getExpandedKeys().isEmpty());
    }

    // ---- PropertyGrid ----------------------------------------------------------------------------------------------

    @Test
    public void propertyGridDescribesEditsAndRejects() {
        final Bean bean = new Bean();
        final PropertyGrid<Bean> g = new PropertyGrid<Bean>();
        final List<String> changes = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                g.addProperty("name", "Name", String.class, new Function<Bean, String>() {
                    @Override
                    public String apply(Bean b) {
                        return b.name;
                    }
                }, new BiConsumer<Bean, String>() {
                    @Override
                    public void accept(Bean b, String v) {
                        if (v.isEmpty()) {
                            throw new IllegalArgumentException("empty");
                        }
                        b.name = v;
                    }
                }).category("Id");
                g.addProperty("moons", "Moons", Integer.class, new Function<Bean, Integer>() {
                    @Override
                    public Integer apply(Bean b) {
                        return b.moons;
                    }
                }, new BiConsumer<Bean, Integer>() {
                    @Override
                    public void accept(Bean b, Integer v) {
                        b.moons = v;
                    }
                });
                g.addProperty("shade", "Shade", Shade.class, new Function<Bean, Shade>() {
                    @Override
                    public Shade apply(Bean b) {
                        return b.shade;
                    }
                }, new BiConsumer<Bean, Shade>() {
                    @Override
                    public void accept(Bean b, Shade v) {
                        b.shade = v;
                    }
                });
                g.addProperty("found", "Found", LocalDate.class, new Function<Bean, LocalDate>() {
                    @Override
                    public LocalDate apply(Bean b) {
                        return b.found;
                    }
                }, new BiConsumer<Bean, LocalDate>() {
                    @Override
                    public void accept(Bean b, LocalDate v) {
                        b.found = v;
                    }
                });
                g.addProperty("ro", "Read only", Object.class, new Function<Bean, Object>() {
                    @Override
                    public Object apply(Bean b) {
                        return b;
                    }
                });
                g.setBean(bean);
                g.addChangeListener(new Listener<PropertyGrid.ChangeEvent>() {
                    @Override
                    public void onEvent(PropertyGrid.ChangeEvent e) {
                        changes.add(e.getPropertyId() + ":" + e.getOldValue() + "->" + e.getNewValue());
                    }
                });
                root.add(g);
            }
        });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> props = (List<Map<String, Object>>) (List<?>) sentProp(g, "properties");
        assertEquals(5, props.size());
        assertEquals("text", props.get(0).get("kind"));
        assertEquals("Id", props.get(0).get("cat"));
        assertEquals("number", props.get(1).get("kind"));
        assertEquals("choice", props.get(2).get("kind"));
        assertEquals("Dark", obj(Json.array(props.get(2), "choices").get(1)).get("l"));
        assertEquals("date", props.get(3).get("kind"));
        assertEquals(Boolean.TRUE, props.get(4).get("ro"));
        Map<String, Object> values = obj(sentProp(g, "values"));
        assertEquals("Earth", obj(values.get("name")).get("t"));
        assertEquals("Light", obj(values.get("shade")).get("t"));
        assertEquals("LIGHT", obj(values.get("shade")).get("k"));
        send(g, "edit", data("id", "moons", "value", "3"));
        assertEquals(3, bean.moons);
        send(g, "edit", data("id", "shade", "value", "DARK"));
        assertEquals(Shade.DARK, bean.shade);
        send(g, "edit", data("id", "found", "value", "2024-05-06"));
        assertEquals(LocalDate.of(2024, 5, 6), bean.found);
        assertEquals(Arrays.asList("moons:1->3", "shade:LIGHT->DARK", "found:2000-01-01->2024-05-06"), changes);
        t.clear();
        send(g, "edit", data("id", "name", "value", ""));
        assertEquals("Earth", bean.name);
        List<Map<String, Object>> errs = calls(g, "editError");
        assertEquals(1, errs.size());
        assertEquals("empty", Json.array(errs.get(0), "args").get(1));
        assertEquals(3, changes.size());
        send(g, "edit", data("id", "ro", "value", "x"));
        assertEquals(3, changes.size());
    }

    // ---- Kanban / Calendar / Diagram --------------------------------------------------------------------------------

    @Test
    public void kanbanAppliesDropsAndReports() {
        final Kanban k = new Kanban();
        final List<String> moves = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                k.addColumn("todo", "To do");
                k.addColumn("done", "Done").limit(1);
                k.addCard(new Kanban.Card("a", "A", "todo"));
                k.addCard(new Kanban.Card("b", "B", "todo"));
                k.addCard(new Kanban.Card("c", "C", "todo").readOnly());
                k.addMoveListener(new Listener<Kanban.MoveEvent>() {
                    @Override
                    public void onEvent(Kanban.MoveEvent e) {
                        moves.add(e.getKey() + ":" + e.getFromColumn() + "->" + e.getColumn() + "@" + e.getIndex());
                    }
                });
                root.add(k);
            }
        });
        send(k, "drop", data("key", "a", "column", "done", "index", 0));
        assertEquals("done", k.getCard("a").getColumn());
        assertEquals(Arrays.asList("a:todo->done@0"), moves);
        send(k, "drop", data("key", "c", "column", "done", "index", 0));
        assertEquals("read-only cards stay", "todo", k.getCard("c").getColumn());
        assertEquals(1, moves.size());
        send(k, "drop", data("key", "b", "column", "done", "index", 0));
        assertEquals(Arrays.asList("b", "a"), Arrays.asList(k.getCards("done").get(0).getKey(), k.getCards("done").get(1).getKey()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) (List<?>) sentProp(k, "cards");
        assertEquals(3, cards.size());
        assertEquals("todo", cards.get(0).get("col"));
        assertEquals("b", cards.get(1).get("k"));
        send(k, "select", data("key", "b"));
        assertEquals("B", k.getSelected().getTitle());
    }

    @Test
    public void calendarEntriesAndMoves() {
        final Calendar c = new Calendar();
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                c.setEntries(Arrays.asList(new Calendar.Entry("e1", "Trip", LocalDate.of(2026, 10, 3)).until(LocalDate.of(2026, 10, 5)).color("#c62828"),
                        new Calendar.Entry("e2", "Call", LocalDate.of(2026, 10, 9)).at(LocalTime.of(10, 0), LocalTime.of(11, 0))));
                c.setDate(LocalDate.of(2026, 10, 1));
                c.addMoveListener(new Listener<Calendar.MoveEvent>() {
                    @Override
                    public void onEvent(Calendar.MoveEvent e) {
                        Calendar.Entry moved = e.getMoved();
                        log.add(moved.getKey() + " " + moved.getStart() + ".." + moved.getEnd());
                        c.addEntry(moved);
                    }
                });
                c.addRangeListener(new Listener<Calendar.RangeEvent>() {
                    @Override
                    public void onEvent(Calendar.RangeEvent e) {
                        log.add("range " + e.getFrom() + ".." + e.getTo());
                    }
                });
                root.add(c);
            }
        });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) (List<?>) sentProp(c, "entries");
        assertEquals(2, entries.size());
        assertEquals("2026-10-05", entries.get(0).get("e"));
        assertEquals("10:00", entries.get(1).get("st"));
        assertEquals("2026-10-01", sentProp(c, "date"));
        send(c, "move", data("key", "e1", "date", "2026-10-10"));
        assertEquals("e1 2026-10-10..2026-10-12", log.get(0));
        assertEquals(LocalDate.of(2026, 10, 12), c.getEntry("e1").getEnd());
        send(c, "nav", data("date", "2026-11-01", "view", "WEEK", "selected", "2026-11-03"));
        assertEquals(LocalDate.of(2026, 11, 1), c.getDate());
        assertEquals(Calendar.View.WEEK, c.getView());
        assertEquals(LocalDate.of(2026, 11, 3), c.getSelected());
        send(c, "range", data("from", "2026-10-26", "to", "2026-12-06"));
        assertEquals("range 2026-10-26..2026-12-06", log.get(1));
    }

    @Test
    public void diagramMovesNodesAndLaysOut() {
        final Diagram d = new Diagram();
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                d.addNode("a", "A", 0, 0);
                d.addNode("b", "B", 0, 0);
                d.addNode("c", "C", 0, 0).locked(true);
                d.addEdge("ab", "a", "b");
                d.addEdge("bc", "b", "c").label("x");
                d.addNodeMoveListener(new Listener<Diagram.NodeEvent>() {
                    @Override
                    public void onEvent(Diagram.NodeEvent e) {
                        log.add(e.getKey() + "@" + (int) e.getX() + "," + (int) e.getY());
                    }
                });
                d.addConnectListener(new Listener<Diagram.ConnectEvent>() {
                    @Override
                    public void onEvent(Diagram.ConnectEvent e) {
                        log.add(e.getFrom() + "->" + e.getTo());
                    }
                });
                root.add(d);
            }
        });
        assertEquals(3, list(sentProp(d, "nodes")).size());
        List<Map<String, Object>> moveList = new ArrayList<Map<String, Object>>();
        moveList.add(data("key", "a", "x", 100, "y", 50));
        moveList.add(data("key", "c", "x", 5, "y", 5));
        send(d, "move", data("nodes", moveList));
        assertEquals(100.0, d.getNode("a").getX(), 0.001);
        assertEquals("locked nodes do not move", 0.0, d.getNode("c").getX(), 0.001);
        assertEquals(Arrays.asList("a@100,50"), log);
        send(d, "connect", data("from", "a", "to", "c"));
        assertEquals("a->c", log.get(1));
        run(new Runnable() {
            @Override
            public void run() {
                d.autoLayout();
            }
        });
        assertTrue("layered: b after a, c after b", d.getNode("b").getX() > d.getNode("a").getX() && d.getNode("c").getX() > d.getNode("b").getX());
        run(new Runnable() {
            @Override
            public void run() {
                d.removeNode("b");
            }
        });
        assertEquals("edges of a removed node go with it", 0, d.getEdges().size());
        send(d, "select", data("nodes", Arrays.asList("a"), "edges", Collections.emptyList()));
        assertEquals("A", d.getSelectedNodes().get(0).getLabel());
    }

    // ---- small inputs ----------------------------------------------------------------------------------------------

    @Test
    public void inputsValidateOnTheServer() {
        final TimePicker tp = new TimePicker(LocalTime.of(9, 0));
        final TagInput tags = new TagInput();
        final PasswordField pw = new PasswordField();
        final MarkdownEditor md = new MarkdownEditor("# Hi");
        run(new Runnable() {
            @Override
            public void run() {
                tp.setMin(LocalTime.of(8, 0));
                tags.setMaxTags(2).setLowerCase(true);
                root.add(tp);
                root.add(tags);
                root.add(pw);
                root.add(md);
            }
        });
        assertEquals("09:00", sentProp(tp, "value"));
        send(tp, "change", data("value", "07:30"));
        assertEquals("stored, but invalid", LocalTime.of(7, 30), tp.getValue());
        assertFalse(tp.isValid());
        assertTrue(String.valueOf(sentProp(tp, "error")).contains("before"));
        send(tp, "change", data("value", "10:45"));
        assertEquals(LocalTime.of(10, 45), tp.getValue());
        assertNull(sentProp(tp, "error"));
        send(tags, "change", data("value", Arrays.asList("A", "b", "a ")));
        assertEquals(Arrays.asList("a", "b"), tags.getTags());
        send(tags, "change", data("value", Arrays.asList("a", "b", "c")));
        assertEquals("too many: unchanged", Arrays.asList("a", "b"), tags.getTags());
        assertNotNull(sentProp(tags, "error"));
        assertEquals(PasswordField.Strength.WEAK, PasswordField.strength("abc"));
        assertEquals(PasswordField.Strength.WEAK, PasswordField.strength("abcdefgh"));
        assertEquals(PasswordField.Strength.FAIR, PasswordField.strength("Abcdefgh1"));
        assertEquals(PasswordField.Strength.GOOD, PasswordField.strength("Abcdefgh1!"));
        assertEquals(PasswordField.Strength.STRONG, PasswordField.strength("Abcdefgh1!xyz"));
        send(pw, "change", data("value", "Abcdefgh1!xyz"));
        assertEquals(PasswordField.Strength.STRONG, pw.getStrength());
        assertEquals("<h1 id=\"hi\">Hi</h1>", md.getHtml().trim());
        assertTrue(String.valueOf(sentProp(md, "previewHtml")).contains("<h1"));
        send(md, "input", data("value", "**bold**"));
        assertEquals("**bold**", md.getValue());
        assertTrue(String.valueOf(sentProp(md, "previewHtml")).contains("<strong>bold</strong>"));
    }

    @Test
    public void accordionExpansionIsClientOwned() {
        final Accordion acc = new Accordion();
        run(new Runnable() {
            @Override
            public void run() {
                acc.addSection("One", new Label("1"));
                acc.addSection(new Accordion.Section("Two", null, "3"), new Label("2"));
                root.add(acc);
            }
        });
        assertEquals(Arrays.asList(0), acc.getExpanded());
        send(acc, "toggle", data("expanded", Arrays.asList(1)));
        assertTrue(acc.isExpanded(1));
        assertFalse(acc.isExpanded(0));
    }
}
