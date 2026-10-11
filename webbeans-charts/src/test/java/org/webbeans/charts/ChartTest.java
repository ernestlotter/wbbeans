package org.webbeans.charts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/** The chart model's wire format, batching, events and export on a headless UI. */
public class ChartTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private UISession session;
    private UI ui;
    private final List<String> sent = new ArrayList<String>();
    private Chart chart;
    private Series cpu;

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI u) {
                Panel root = new Panel(new Layout.Border());
                chart = new Chart(ChartType.LINE);
                chart.setTitle("CPU");
                chart.getXAxis().setType(AxisType.TIME).setFormat("HH:mm");
                chart.getYAxis().setMin(0.0).setMax(100.0).setFormat("#0'%'");
                cpu = chart.getModel().addSeries("cpu", "CPU");
                cpu.setWindow(3);
                cpu.setPoints(Arrays.asList(Point.of(1000, 10), Point.of(2000, 20)));
                root.add(chart, Layout.Border.CENTER);
                u.setContent(root);
            }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(provider));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, Locale.ENGLISH, null, DIRECT);
        ui = session.createUI("u1");
        connect(-1);
    }

    private void connect(final long lastSeq) {
        final Transport t = new Transport() {
            @Override
            public void send(String json) {
                sent.add(json);
            }

            @Override
            public void close() {
            }
        };
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, lastSeq);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataCalls(String message) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object o : Json.array(Json.parseObject(message), "ops")) {
            Map<String, Object> op = (Map<String, Object>) o;
            if ("call".equals(op.get("op")) && "data".equals(op.get("m")) && ((Number) op.get("id")).intValue() == chart.getId()) {
                out.add((Map<String, Object>) ((List<Object>) op.get("args")).get(0));
            }
        }
        return out;
    }

    private Map<String, Object> lastData() {
        List<Map<String, Object>> calls = dataCalls(sent.get(sent.size() - 1));
        assertEquals("one data call per flush", 1, calls.size());
        return calls.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> series(Map<String, Object> data, String id) {
        return (Map<String, Object>) ((Map<String, Object>) data.get("series")).get(id);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private void fire(final String type, final Map<String, Object> data) {
        run(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(Collections.<Object>singletonList(map("id", chart.getId(), "type", type, "data", data)));
            }
        });
    }

    @Test
    public void initCarriesTheWholeModelAndMutationsMergeIntoOneCall() {
        Map<String, Object> init = Json.parseObject(sent.get(0));
        assertEquals("init", init.get("t"));
        Map<String, Object> full = dataCalls(sent.get(0)).get(0);
        assertEquals(Boolean.TRUE, full.get("reset"));
        Map<String, Object> s = series(full, "cpu");
        assertEquals("CPU", ((Map<?, ?>) s.get("set")).get("name"));
        assertEquals(3, ((Number) ((Map<?, ?>) s.get("set")).get("window")).intValue());
        assertEquals(2, ((List<?>) s.get("points")).size());
        // properties: compiled axis formats
        boolean sawAxis = false;
        for (Object o : Json.array(init, "ops")) {
            Map<?, ?> op = (Map<?, ?>) o;
            if ("create".equals(op.get("op")) && "wb.Chart".equals(op.get("type"))) {
                Map<?, ?> props = (Map<?, ?>) op.get("props");
                Map<?, ?> y = (Map<?, ?>) props.get("yAxis");
                Map<?, ?> fmt = (Map<?, ?>) y.get("format");
                assertEquals("number", fmt.get("kind"));
                assertEquals("%", fmt.get("suffix"));
                assertEquals("time", ((Map<?, ?>) ((Map<?, ?>) props.get("xAxis")).get("format")).get("kind"));
                sawAxis = true;
            }
        }
        assertTrue(sawAxis);
        // several mutations in one task → one data call with everything merged
        run(new Runnable() {
            @Override
            public void run() {
                cpu.addPoint(Point.of(3000, 30));
                cpu.addPoint(Point.of(4000, 40));           // window 3: the model drops the first point
                cpu.setColor("#ff0000");
                Series mem = chart.getModel().addSeries("mem", "Memory", ChartType.AREA);
                mem.setPoints(Arrays.asList(Point.of(1000, 5)));
                chart.getModel().addSeries("tmp", "Temp");
                chart.getModel().removeSeries("tmp");
            }
        });
        assertEquals(3, cpu.getPointCount());
        Map<String, Object> data = lastData();
        Map<String, Object> c = series(data, "cpu");
        assertEquals(2, ((List<?>) c.get("append")).size());
        assertEquals(3, ((Number) c.get("trim")).intValue());
        assertEquals("#ff0000", ((Map<?, ?>) c.get("set")).get("color"));
        assertNull("no full re-send", c.get("points"));
        Map<String, Object> m = series(data, "mem");
        assertEquals("area", ((Map<?, ?>) m.get("set")).get("type"));
        assertEquals(1, ((List<?>) m.get("points")).size());
        assertTrue("removed series is null", ((Map<?, ?>) data.get("series")).containsKey("tmp"));
        assertNull(series(data, "tmp"));
        assertFalse(chart.hasPending());
    }

    @Test
    public void setDiscardsEarlierAppendsAndKeyedUpdatesMerge() {
        run(new Runnable() {
            @Override
            public void run() {
                cpu.addPoint(Point.of(3000, 30));
                cpu.setPoints(Arrays.asList(Point.of(5, 5).withKey("a"), Point.of(6, 6).withKey("b")));
            }
        });
        Map<String, Object> c = series(lastData(), "cpu");
        assertNull(c.get("append"));
        assertEquals(2, ((List<?>) c.get("points")).size());
        assertEquals("a", ((Map<?, ?>) ((List<?>) c.get("points")).get(0)).get("k"));
        run(new Runnable() {
            @Override
            public void run() {
                cpu.updatePoint("a", Point.of(5, 50));
                cpu.updatePoint("c", Point.of(7, 7));
                cpu.removePoint("b");
                cpu.removePoint("c");            // cancels the pending update of c
            }
        });
        c = series(lastData(), "cpu");
        Map<?, ?> update = (Map<?, ?>) c.get("update");
        assertEquals(1, update.size());
        assertEquals(50.0, ((Number) ((Map<?, ?>) update.get("a")).get("y")).doubleValue(), 0.0);
        assertEquals(Arrays.asList("b", "c"), c.get("remove"));
        assertEquals(1, cpu.getPointCount());
        assertEquals("a", String.valueOf(cpu.getPoints().get(0).getKey()));
    }

    @Test
    public void categoryPointsResolveToIndexesAndExtendTheCategories() {
        final Chart bars = new Chart(ChartType.BAR);
        run(new Runnable() {
            @Override
            public void run() {
                bars.getModel().setCategories(Arrays.asList("Q1", "Q2"));
                bars.getModel().addSeries("rev", "Revenue").setPoints(Arrays.asList(Point.of("Q1", 1), Point.of("Q2", 2), Point.of("Q3", 3)));
                ((Panel) ui.getContent()).add(bars, Layout.Border.SOUTH);
            }
        });
        assertEquals(Arrays.asList("Q1", "Q2", "Q3"), bars.getModel().getCategories());
        List<Map<String, Object>> calls = new ArrayList<Map<String, Object>>();
        for (Object o : Json.array(Json.parseObject(sent.get(sent.size() - 1)), "ops")) {
            Map<?, ?> op = (Map<?, ?>) o;
            if ("call".equals(op.get("op")) && "data".equals(op.get("m")) && ((Number) op.get("id")).intValue() == bars.getId()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> arg = (Map<String, Object>) ((List<?>) op.get("args")).get(0);
                calls.add(arg);
            }
        }
        assertEquals(1, calls.size());
        assertEquals(Arrays.asList("Q1", "Q2", "Q3"), calls.get(0).get("categories"));
        List<?> pts = (List<?>) series(calls.get(0), "rev").get("points");
        assertEquals(2.0, ((Number) ((List<?>) pts.get(2)).get(0)).doubleValue(), 0.0);
        assertEquals(3.0, ((Number) ((List<?>) pts.get(2)).get(1)).doubleValue(), 0.0);
    }

    @Test
    public void eventsResolvePointsAndExportFuturesComplete() throws Exception {
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                cpu.setPoints(Arrays.asList(Point.of(1, 10).withKey("k1"), Point.of(2, 20)));
                chart.addPointClickListener(new Listener<PointEvent>() {
                    @Override
                    public void onEvent(PointEvent e) {
                        Point p = e.getPoint();
                        log.add("click:" + e.getSeriesId() + ":" + (p == null ? "?" : p.getY()));
                    }
                });
                chart.addLegendToggleListener(new Listener<LegendEvent>() {
                    @Override
                    public void onEvent(LegendEvent e) {
                        log.add("legend:" + e.getSeriesId() + ":" + e.isVisible());
                    }
                });
                chart.addRangeSelectListener(new Listener<RangeEvent>() {
                    @Override
                    public void onEvent(RangeEvent e) {
                        log.add("range:" + e.getXFrom() + "-" + e.getXTo() + (e.isReset() ? ":reset" : ""));
                    }
                });
            }
        });
        fire("pointClick", map("series", "cpu", "k", "k1", "index", 0, "x", 1, "y", 10));
        fire("pointClick", map("series", "cpu", "index", 1, "x", 2, "y", 20));
        fire("legendToggle", map("series", "cpu", "visible", false));
        assertFalse(cpu.isVisible());
        fire("rangeSelect", map("xFrom", 1.5, "xTo", 1.8));
        fire("rangeSelect", map("reset", true));
        assertEquals(Arrays.asList("click:cpu:10.0", "click:cpu:20.0", "legend:cpu:false", "range:1.5-1.8", "range:NaN-NaN:reset"), log);
        // export: the call carries an id; the client answers with an event
        final List<CompletableFuture<byte[]>> png = new ArrayList<CompletableFuture<byte[]>>();
        final List<CompletableFuture<String>> svg = new ArrayList<CompletableFuture<String>>();
        run(new Runnable() {
            @Override
            public void run() {
                png.add(chart.exportPng(640, 480));
                svg.add(chart.exportSvg());
            }
        });
        String last = sent.get(sent.size() - 1);
        assertTrue(last, last.contains("\"export\""));
        assertTrue(last.contains("\"png\"") && last.contains("\"svg\""));
        fire("export", map("id", 1, "format", "png", "data", "data:image/png;base64,AQID"));
        fire("export", map("id", 2, "format", "svg", "data", "<svg/>"));
        assertEquals(Arrays.asList((byte) 1, (byte) 2, (byte) 3), boxed(png.get(0).get(1, TimeUnit.SECONDS)));
        assertEquals("<svg/>", svg.get(0).get(1, TimeUnit.SECONDS));
        run(new Runnable() {
            @Override
            public void run() {
                png.add(chart.exportPng(10, 10));
            }
        });
        fire("export", map("id", 3, "format", "png", "error", "no canvas"));
        assertTrue(png.get(1).isCompletedExceptionally());
    }

    private static List<Byte> boxed(byte[] b) {
        List<Byte> l = new ArrayList<Byte>();
        for (byte x : b) {
            l.add(x);
        }
        return l;
    }

    @Test
    public void resyncResendsTheWholeModel() {
        run(new Runnable() {
            @Override
            public void run() {
                cpu.addPoint(Point.of(3000, 30));
            }
        });
        int before = sent.size();
        connect(-1); // a fresh client: full init with reset
        assertTrue(sent.size() > before);
        Map<String, Object> full = dataCalls(sent.get(sent.size() - 1)).get(0);
        assertEquals(Boolean.TRUE, full.get("reset"));
        assertEquals(3, ((List<?>) series(full, "cpu").get("points")).size());
        assertSame(cpu, chart.getModel().getSeries("cpu"));
    }

    @Test
    public void formatSpecCompilesDecimalFormatPatterns() {
        Map<String, Object> f = FormatSpec.compile("#,##0.0", AxisType.LINEAR, Locale.ENGLISH);
        assertEquals("number", f.get("kind"));
        assertEquals(Boolean.TRUE, f.get("grouping"));
        assertEquals(1, f.get("minFrac"));
        assertEquals(1, f.get("maxFrac"));
        assertEquals(".", f.get("decimal"));
        assertEquals(",", f.get("group"));
        Map<String, Object> pct = FormatSpec.compile("#0%", AxisType.LINEAR, Locale.ENGLISH);
        assertEquals(100, ((Number) pct.get("multiplier")).intValue());
        assertEquals("%", pct.get("suffix"));
        Map<String, Object> de = FormatSpec.compile("#,##0.00 '€'", AxisType.LINEAR, Locale.GERMANY);
        assertEquals(",", de.get("decimal"));
        assertEquals(".", de.get("group"));
        assertEquals(" €", de.get("suffix"));
        assertEquals("1.234,50 €", FormatSpec.format("#,##0.00 '€'", 1234.5, Locale.GERMANY));
        Map<String, Object> t = FormatSpec.compile("HH:mm", AxisType.TIME, Locale.ENGLISH);
        assertEquals("time", t.get("kind"));
        assertEquals("HH:mm", t.get("pattern"));
        assertNotNull(Point.of("Q1", 1).withLabel("x").withSize(2).withMeta(map("a", 1)).getMeta().get("a"));
    }
}
