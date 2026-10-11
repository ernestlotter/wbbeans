package org.webbeans.charts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.UI;

/**
 * A chart ({@code wb.Chart}): the data lives in the {@link ChartModel} on the server, appearance in properties;
 * the browser only draws (an SVG renderer with axes, legend, tooltip, zoom/pan/brush and export). Every model
 * mutation becomes a small delta, merged per flush into one {@code data} call, so live series cost one append
 * per tick rather than a re-send.
 *
 * <pre>
 * Chart cpu = new Chart(ChartType.LINE);
 * cpu.getXAxis().setType(AxisType.TIME).setFormat("HH:mm:ss");
 * cpu.getYAxis().setMin(0.0).setMax(100.0).setFormat("#0'%'");
 * Series s = cpu.getModel().addSeries("cpu", "CPU");
 * s.setWindow(300);
 * ui.scheduleAtFixedRate(() -&gt; s.addPoint(Point.of(Instant.now(), sampler.cpu())), 1000, 1000);
 * </pre>
 */
@Renderer(id = "wb.Chart", resource = "org/webbeans/charts/client/chart.js", css = "org/webbeans/charts/client/chart.css")
public class Chart extends Component implements ChartModel.Sink {

    public static final Property<String> TITLE = Property.of("title", String.class, null);
    public static final Property<String> SUBTITLE = Property.of("subtitle", String.class, null);
    public static final Property<ChartType> TYPE = Property.of("type", ChartType.class, ChartType.LINE);
    public static final Property<Map<String, Object>> X_AXIS = Property.of("xAxis", mapClass(), null);
    public static final Property<Map<String, Object>> Y_AXIS = Property.of("yAxis", mapClass(), null);
    public static final Property<Map<String, Object>> Y2_AXIS = Property.of("y2Axis", mapClass(), null);
    public static final Property<Map<String, Object>> LEGEND = Property.of("legend", mapClass(), null);
    public static final Property<List<String>> PALETTE = Property.of("palette", listClass(), null);
    public static final Property<Boolean> ANIMATION = Property.of("animation", Boolean.class, Boolean.TRUE);
    public static final Property<TooltipMode> TOOLTIP = Property.of("tooltip", TooltipMode.class, TooltipMode.SHARED);
    public static final Property<ZoomMode> ZOOM = Property.of("zoom", ZoomMode.class, ZoomMode.NONE);
    public static final Property<Boolean> STACKED = Property.of("stacked", Boolean.class, Boolean.FALSE);
    public static final Property<SelectionMode> SELECTION = Property.of("selection", SelectionMode.class, SelectionMode.NONE);
    /** Bars grow to the right instead of up. */
    public static final Property<Boolean> HORIZONTAL = Property.of("horizontal", Boolean.class, Boolean.FALSE);
    /** Show the value next to bars/points/slices. */
    public static final Property<Boolean> LABELS = Property.of("labels", Boolean.class, Boolean.FALSE);

    public static final EventType<PointEvent> POINT_CLICK = EventType.of("pointClick", PointEvent::new);
    public static final EventType<LegendEvent> LEGEND_TOGGLE = EventType.of("legendToggle", LegendEvent::new);
    public static final EventType<RangeEvent> RANGE_SELECT = EventType.of("rangeSelect", RangeEvent::new).lazy(100);
    /** Internal: the result of an export request. */
    static final EventType<Event> EXPORT = EventType.of("export", Event::new);

    /** Pending mutations of one series until the next flush. */
    private static final class Delta {
        boolean style;
        boolean set;
        boolean removed;
        List<Object> append;
        Map<String, Object> update;
        List<String> remove;
    }

    private final ChartModel model = new ChartModel(this);
    private final Axis xAxis;
    private final Axis yAxis;
    private Axis y2Axis;
    private final Legend legend;
    private final Map<String, Delta> pending = new LinkedHashMap<String, Delta>();
    private boolean categoriesDirty;
    private boolean scheduled;
    private final Map<Integer, CompletableFuture<?>> exports = new LinkedHashMap<Integer, CompletableFuture<?>>();
    private int nextExport = 1;

    public Chart() {
        this(ChartType.LINE);
    }

    public Chart(ChartType defaultType) {
        super("wb.Chart");
        xAxis = new Axis(new Runnable() {
            @Override
            public void run() {
                set(X_AXIS, xAxis.toJson(locale()));
            }
        });
        yAxis = new Axis(new Runnable() {
            @Override
            public void run() {
                set(Y_AXIS, yAxis.toJson(locale()));
            }
        });
        legend = new Legend(new Runnable() {
            @Override
            public void run() {
                set(LEGEND, legend.toJson());
            }
        });
        set(TYPE, defaultType == null ? ChartType.LINE : defaultType);
        set(X_AXIS, xAxis.toJson(locale()));
        set(Y_AXIS, yAxis.toJson(locale()));
        set(LEGEND, legend.toJson());
        addListener(LEGEND_TOGGLE, new Listener<LegendEvent>() {
            @Override
            public void onEvent(LegendEvent e) {
                Series s = e.getSeries();
                if (s != null) {
                    s.setVisibleFromClient(e.isVisible());
                }
            }
        });
        addListener(EXPORT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                completeExport(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    @SuppressWarnings("unchecked")
    private static Class<List<String>> listClass() {
        return (Class<List<String>>) (Class<?>) List.class;
    }

    private Locale locale() {
        UI ui = getUI();
        if (ui != null) {
            return ui.getSession().getLocale();
        }
        org.webbeans.ui.UISession s = org.webbeans.ui.UISession.current();
        return s != null ? s.getLocale() : Locale.getDefault();
    }

    // ---- model and appearance ----------------------------------------------------------------------------------

    public ChartModel getModel() {
        return model;
    }

    public Chart setTitle(String t) {
        set(TITLE, t);
        return this;
    }

    public Chart setSubtitle(String t) {
        set(SUBTITLE, t);
        return this;
    }

    public ChartType getType() {
        return get(TYPE);
    }

    public Chart setType(ChartType t) {
        set(TYPE, t);
        return this;
    }

    public Axis getXAxis() {
        return xAxis;
    }

    public Axis getYAxis() {
        return yAxis;
    }

    /** The secondary value axis (right side), created on first use; series select it with {@code setYAxis(1)}. */
    public Axis getY2Axis() {
        if (y2Axis == null) {
            y2Axis = new Axis(new Runnable() {
                @Override
                public void run() {
                    set(Y2_AXIS, y2Axis.toJson(locale()));
                }
            });
            set(Y2_AXIS, y2Axis.toJson(locale()));
        }
        return y2Axis;
    }

    public Legend getLegend() {
        return legend;
    }

    /** CSS colours used for series without an explicit colour (default: the theme's 12). */
    public Chart setPalette(List<String> cssColors) {
        set(PALETTE, cssColors == null ? null : new ArrayList<String>(cssColors));
        return this;
    }

    public Chart setPalette(String... cssColors) {
        return setPalette(Arrays.asList(cssColors));
    }

    public Chart setAnimation(boolean b) {
        set(ANIMATION, b);
        return this;
    }

    public Chart setTooltip(TooltipMode m) {
        set(TOOLTIP, m);
        return this;
    }

    public Chart setZoom(ZoomMode m) {
        set(ZOOM, m);
        return this;
    }

    public Chart setStacked(boolean b) {
        set(STACKED, b);
        return this;
    }

    public Chart setSelectionMode(SelectionMode m) {
        set(SELECTION, m);
        return this;
    }

    public Chart setHorizontal(boolean b) {
        set(HORIZONTAL, b);
        return this;
    }

    public Chart setLabels(boolean b) {
        set(LABELS, b);
        return this;
    }

    @Override
    public Chart setTestId(String id) {
        super.setTestId(id);
        return this;
    }

    // ---- events ----------------------------------------------------------------------------------------------

    public Registration addPointClickListener(Listener<PointEvent> l) {
        return addListener(POINT_CLICK, l);
    }

    public Registration addLegendToggleListener(Listener<LegendEvent> l) {
        return addListener(LEGEND_TOGGLE, l);
    }

    public Registration addRangeSelectListener(Listener<RangeEvent> l) {
        return addListener(RANGE_SELECT, l);
    }

    // ---- calls ---------------------------------------------------------------------------------------------

    public void resetZoom() {
        call("resetZoom");
    }

    /** Highlights one point (by key or x) of a series, or clears the highlight with nulls. */
    public void highlight(String seriesId, Object keyOrX) {
        call("highlight", seriesId, keyOrX == null ? null : keyOrX instanceof Number ? keyOrX : String.valueOf(keyOrX));
    }

    /** Sets the visible x range (zoom); nulls restore the data extent. */
    public void setXRange(Double from, Double to) {
        call("setRange", "x", from, to);
    }

    /** Renders the chart to PNG in the browser; the future completes on the session executor with the bytes. */
    public CompletableFuture<byte[]> exportPng(int width, int height) {
        CompletableFuture<byte[]> f = new CompletableFuture<byte[]>();
        int id = nextExport++;
        exports.put(id, f);
        call("export", id, "png", width, height);
        return f;
    }

    /** The chart's SVG with styles inlined, as the browser shows it. */
    public CompletableFuture<String> exportSvg() {
        CompletableFuture<String> f = new CompletableFuture<String>();
        int id = nextExport++;
        exports.put(id, f);
        call("export", id, "svg", 0, 0);
        return f;
    }

    /** Lets the browser save the chart as a file ({@code "png"} or {@code "svg"}) without a round trip. */
    public void download(String format, String fileName) {
        call("download", format, fileName);
    }

    @SuppressWarnings("unchecked")
    private void completeExport(Event e) {
        int id = e.getInt("id", -1);
        CompletableFuture<?> f = exports.remove(id);
        if (f == null) {
            return;
        }
        String error = e.getString("error");
        if (error != null) {
            f.completeExceptionally(new IllegalStateException(error));
            return;
        }
        String data = e.getString("data");
        if ("png".equals(e.getString("format"))) {
            String b64 = data == null ? "" : data.substring(data.indexOf(',') + 1);
            ((CompletableFuture<byte[]>) f).complete(Base64.getDecoder().decode(b64));
        } else {
            ((CompletableFuture<String>) f).complete(data == null ? "" : data);
        }
    }

    // ---- data deltas ---------------------------------------------------------------------------------------------

    private Delta delta(Series s) {
        Delta d = pending.get(s.getId());
        if (d == null) {
            d = new Delta();
            pending.put(s.getId(), d);
        }
        schedule();
        return d;
    }

    private void schedule() {
        UI ui = getUI();
        if (ui != null && !scheduled) {
            scheduled = true;
            ui.beforeFlush(new Runnable() {
                @Override
                public void run() {
                    scheduled = false;
                    emitPending();
                }
            });
        }
    }

    @Override
    public void categoriesChanged() {
        categoriesDirty = true;
        schedule();
    }

    @Override
    public void seriesStyle(Series s) {
        Delta d = delta(s);
        if (!d.removed) {
            d.style = true;
        }
    }

    @Override
    public void seriesSet(Series s) {
        Delta d = delta(s);
        d.removed = false;
        d.set = true;
        d.style = true;
        d.append = null;
        d.update = null;
        d.remove = null;
    }

    @Override
    public void seriesAppend(Series s, List<Point> added) {
        Delta d = delta(s);
        if (d.set || d.removed) {
            return;
        }
        if (d.append == null) {
            d.append = new ArrayList<Object>();
        }
        for (Point p : added) {
            d.append.add(p.toJson(model.resolveX(p)));
        }
    }

    @Override
    public void seriesUpdate(Series s, Object key, Point p) {
        Delta d = delta(s);
        if (d.set || d.removed) {
            return;
        }
        if (d.update == null) {
            d.update = new LinkedHashMap<String, Object>();
        }
        String k = String.valueOf(key);
        d.update.put(k, p.toJson(model.resolveX(p)));
        if (d.remove != null) {
            d.remove.remove(k);
        }
    }

    @Override
    public void seriesRemovePoint(Series s, Object key) {
        Delta d = delta(s);
        if (d.set || d.removed) {
            return;
        }
        String k = String.valueOf(key);
        if (d.update != null) {
            d.update.remove(k);
        }
        if (d.remove == null) {
            d.remove = new ArrayList<String>();
        }
        if (!d.remove.contains(k)) {
            d.remove.add(k);
        }
    }

    @Override
    public void seriesRemoved(String id) {
        Delta d = pending.get(id);
        if (d == null) {
            d = new Delta();
            pending.put(id, d);
        }
        d.removed = true;
        d.set = false;
        d.style = false;
        d.append = null;
        d.update = null;
        d.remove = null;
        schedule();
    }

    private Map<String, Object> style(Series s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", s.getDisplayName());
        if (s.getType() != null) {
            m.put("type", s.getType().name().toLowerCase(Locale.ROOT));
        }
        if (s.getColor() != null) {
            m.put("color", s.getColor());
        }
        m.put("yAxis", s.getYAxis());
        m.put("visible", s.isVisible());
        m.put("lineStyle", s.getLineStyle().name().toLowerCase(Locale.ROOT));
        m.put("marker", s.getMarker().name().toLowerCase(Locale.ROOT));
        if (s.isSmooth()) {
            m.put("smooth", true);
        }
        if (s.getStack() != null) {
            m.put("stack", s.getStack());
        }
        if (s.getWindow() > 0) {
            m.put("window", s.getWindow());
        }
        return m;
    }

    private List<Object> points(Series s) {
        List<Object> out = new ArrayList<Object>(s.getPointCount());
        for (Point p : s.getPoints()) {
            out.add(p.toJson(model.resolveX(p)));
        }
        return out;
    }

    /** Sends the merged pending mutations as one {@code data} call. */
    private void emitPending() {
        if (!isAttached() || (pending.isEmpty() && !categoriesDirty)) {
            pending.clear();
            categoriesDirty = false;
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        Map<String, Object> series = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Delta> e : pending.entrySet()) {
            Delta d = e.getValue();
            Series s = model.getSeries(e.getKey());
            if (d.removed || s == null) {
                series.put(e.getKey(), null);
                continue;
            }
            Map<String, Object> sd = new LinkedHashMap<String, Object>();
            if (d.style || d.set) {
                sd.put("set", style(s));
            }
            if (d.set) {
                sd.put("points", points(s));
            } else {
                if (d.append != null) {
                    sd.put("append", d.append);
                    if (s.getWindow() > 0) {
                        sd.put("trim", s.getWindow());
                    }
                }
                if (d.update != null && !d.update.isEmpty()) {
                    sd.put("update", d.update);
                }
                if (d.remove != null && !d.remove.isEmpty()) {
                    sd.put("remove", d.remove);
                }
            }
            series.put(e.getKey(), sd);
        }
        // resolving category points may have added categories: read them last
        if (categoriesDirty) {
            payload.put("categories", new ArrayList<String>(model.getCategories()));
        }
        if (!series.isEmpty()) {
            payload.put("series", series);
        }
        pending.clear();
        categoriesDirty = false;
        call("data", payload);
    }

    /** The whole model as one {@code data} call (first attach, resync). */
    private Map<String, Object> fullPayload() {
        Map<String, Object> series = new LinkedHashMap<String, Object>();
        for (Series s : model.getSeries()) {
            Map<String, Object> sd = new LinkedHashMap<String, Object>();
            sd.put("set", style(s));
            sd.put("points", points(s));
            series.put(s.getId(), sd);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("reset", true);
        payload.put("categories", new ArrayList<String>(model.getCategories()));
        payload.put("series", series);
        return payload;
    }

    @Override
    protected void attached() {
        // axis formats depend on the session locale, known only now
        set(X_AXIS, xAxis.toJson(locale()));
        set(Y_AXIS, yAxis.toJson(locale()));
        if (y2Axis != null) {
            set(Y2_AXIS, y2Axis.toJson(locale()));
        }
        sendAll();
    }

    @Override
    protected void resync() {
        sendAll();
    }

    private void sendAll() {
        Map<String, Object> payload = fullPayload(); // may register new categories: build first, then forget the pending deltas
        pending.clear();
        categoriesDirty = false;
        call("data", payload);
    }

    /** For tests: whether mutations wait for the next flush. */
    boolean hasPending() {
        return !pending.isEmpty() || categoriesDirty;
    }
}
