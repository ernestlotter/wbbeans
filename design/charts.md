# WebBeans — Charts (detailed design v0.2)

Roadmap step 6. JDK 8. **v0.2 reflects the implementation** (`impl/step6-notes.md` lists the deviations): package `org.webbeans.charts` in module `webbeans-charts` (depends on `webbeans-ui`, `webbeans-windows`, `webbeans-actions`), client `org/webbeans/charts/client/chart.js` + `chart.css`. Integrates with steps 3–5 (`ChartTopComponent`, registered actions over `Chart`, dialogs and notifications for export results).

## 1. Goals and non-goals

- Server-side data model; the client never computes data, only draws. Live data (dashboards, monitoring) is cheap: appending a point is one small delta (≈60 bytes of payload), not a re-send of the series.
- No external JS dependency: an own ~25 KB SVG renderer with axes, legends, tooltips, zoom/pan/brush and export. Rich/exotic chart types are out of scope; a subclass with its own `@Renderer` can reuse the model plumbing for them.
- Themed through CSS variables like every other component (`--wb-chart-grid`, `--wb-chart-axis`, `--wb-chart-tooltip-bg`, dark/light; the default series palette lives in the renderer and is overridable with `setPalette`).

## 2. Chart types (v1)

`LINE`, `AREA` (stacked optional), `BAR` (vertical/horizontal, grouped/stacked), `PIE`/`DONUT`, `SCATTER` (+ bubble via point size), `STEP`. Mixed charts: a chart has one or two value axes and each series declares its own type where compatible (`LINE` + `BAR` on a shared category axis, an `AREA` on the secondary axis).

## 3. API

```java
public class Chart extends Component {                               // renderer "wb.Chart"
    public Chart(ChartType defaultType);
    public ChartModel getModel();                                     // the data
    public Chart setTitle(String t); setSubtitle(String t); setType(ChartType t);
    public Axis getXAxis(); getYAxis(); getY2Axis();                  // y2 created on demand; Series.setYAxis(1) uses it
    public Legend getLegend();                                        // position, visible, interactive
    public Chart setPalette(String... cssColors);  setAnimation(boolean); setTooltip(TooltipMode);   // NONE, POINT, SHARED, AXIS
    public Chart setZoom(ZoomMode);                                   // NONE, X, Y, XY: wheel zoom, drag pan, shift-drag brush, double-click/Escape reset
    public Chart setStacked(boolean); setSelectionMode(SelectionMode); setHorizontal(boolean); setLabels(boolean);
    public Registration addPointClickListener(Listener<PointEvent>); addLegendToggleListener(Listener<LegendEvent>); addRangeSelectListener(Listener<RangeEvent>);
    public CompletableFuture<byte[]> exportPng(int width, int height); public CompletableFuture<String> exportSvg();
    public void download(String format, String fileName);           // client-side save, no round trip
    public void resetZoom();  void setXRange(Double from, Double to);  void highlight(String seriesId, Object keyOrX);
}
public enum ChartType { LINE, AREA, BAR, PIE, DONUT, SCATTER, STEP }

public final class ChartModel {
    public Series addSeries(String id, String displayName, ChartType type);   // type null = chart default
    public Series getSeries(String id);  List<Series> getSeries();  void removeSeries(String id);
    public void setCategories(List<String> categories);              // category x-axis; Point.of("Q3", y) appends unknown names
    public void clear();
}
public final class Series {
    public Series setDisplayName(String); setType(ChartType); setColor(String css); setYAxis(int 0|1); setVisible(boolean);
    public Series setLineStyle(LineStyle); setMarker(Marker); setSmooth(boolean); setStack(String group);
    public Series setPoints(List<Point>);                             // replaces all (one delta)
    public Series addPoint(Point); addPoints(List<Point>);           // append (delta "append" + "trim")
    public Series updatePoint(Object key, Point); removePoint(Object key);   // keyed
    public Series setWindow(int maxPoints);                           // sliding window: server trims, client drops oldest
}
public final class Point {   // immutable
    public static Point of(double x, double y);  of(Instant x, double y);  of(String category, double y);
    public Point withKey(Object).withSize(double).withLabel(String).withColor(String css).withMeta(Map<String,Object>);
}
public final class Axis {
    public Axis setTitle(String); setType(AxisType /* LINEAR, LOG, TIME, CATEGORY */); setMin(Double); setMax(Double);
    public Axis setFormat(String pattern);   // DecimalFormat / DateTimeFormatter pattern, compiled server-side (FormatSpec) per session locale
    public Axis setGrid(boolean); setVisible(boolean);
    public Axis addMarker(String id, double value, String label, String css);  addBand(String id, double from, double to, String css);  removeMarker(String id);
}
public final class Legend { setPosition(TOP|BOTTOM|LEFT|RIGHT); setVisible(boolean); setInteractive(boolean); }
```

Example:

```java
Chart cpu = new Chart(ChartType.LINE);
cpu.setTitle(Bundle.CTL_CpuUsage()).setZoom(ZoomMode.X);
cpu.getXAxis().setType(AxisType.TIME).setFormat("HH:mm:ss");
cpu.getYAxis().setMin(0.0).setMax(100.0).setFormat("#0'%'").addBand("hot", 80, 100, "#b43b3b");
Series s = cpu.getModel().addSeries("cpu", "CPU");
s.setWindow(300);
ui.scheduleAtFixedRate(() -> s.addPoint(Point.of(Instant.now(), sampler.cpu())), 1000, 1000);
cpu.addPointClickListener(e -> DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(String.valueOf(e.getPoint()))));
```

Events: `PointEvent` (`getSeries()`, `getPoint()` — resolved by key, else index —, `getX/getY`, modifiers), `LegendEvent` (`getSeries()`, `isVisible()`; `Series.isVisible()` is already updated as client-owned state), `RangeEvent` (`getXFrom/XTo/YFrom/YTo`, `NaN` for unaffected axes, `isReset()`).

## 4. Wire format

The chart is one component. Appearance is ordinary properties (`xAxis`, `yAxis`, `y2Axis`, `legend` are small JSON objects; axis `format` is a compiled spec). Data goes through `call`s, batched per flush into a single `data` call by a `UI.beforeFlush` task:

```json
{ "op":"call", "id":77, "m":"data", "args":[{
   "categories": ["Q1","Q2","Q3","Q4"],
   "series": {
     "rev":  { "set":    { "name":"Revenue", "type":"bar", "color":"#4a90e2", "yAxis":0, "visible":true, "lineStyle":"solid", "marker":"none" },
               "points": [ [0, 120.5], [1, 98.0], [2, 143.2], [3, 160.9] ] },
     "cpu":  { "append": [ [1759920000000, 42.1], [1759920001000, 44.7] ], "trim": 300 },
     "err":  { "update": { "k17": {"x":1759920000000, "y":3, "k":"k17"} }, "remove": ["k12"] },
     "old":  null
   }
}] }
```

- Points are arrays `[x, y]`, `[x, y, size]` or objects `{x, y, s, k, l, c, m}` when keyed/labelled/coloured; time x is epoch millis; category x is the category index (resolved when the delta is emitted, so new categories ride in the same call). Formats: `{kind:"number", prefix, suffix, minFrac, maxFrac, grouping, groupSize, multiplier, decimal, group}` / `{kind:"time", pattern}`; the client needs no `DecimalFormat`.
- `set` replaces the series' points (and carries the style); `append`+`trim` implement sliding windows; `update`/`remove` need keys. `null` removes the series. A first attach or a resync sends `{"reset": true, categories, series: {id: {set, points}}}`.
- Merge rules per flush: `append` after `append` concatenates; `set` discards earlier appends/updates; `remove` of a keyed point cancels its pending `update`; removing a series drops everything pending for it.
- Upstream events: `pointClick {series, index, k, x, y, ctrl, shift, alt}`, `legendToggle {series, visible}`, `rangeSelect {xFrom, xTo, yFrom, yTo, reset}` (lazy, 100 ms), `export {id, format, data | error}` (internal: answers `call("export", id, format, w, h)`).
- Large series: all JSON for now (no binary channel on the long-poll transport); see §7 and the open points.

## 5. Client renderer (`chart.js`)

- Pure SVG; one `<svg>` per chart with `<g>` layers: bands/markers, grid, axes, series (clipped to the plot), legend, overlay (crosshair, brush). A `ResizeObserver` triggers a relayout, throttled to one per animation frame; margins come from measured tick-label widths.
- Scales: linear, log, time (1/2/5 steps for numbers; calendar-aligned steps from milliseconds to years for time), band (categories). Axis ranges pad 5 % (12 % with value labels) and start at zero for bars/areas; zoom ranges override.
- Series renderers: `line`/`step`/`area` (paths, optional monotone smoothing, stacked by group, LTTB downsampling when points > pixels × 2), `bar` (grouped per series, stacked by group, horizontal, per-point colours, value labels), `scatter` (markers, bubble sizes with a sqrt scale, labels), `pie`/`donut` (arcs with percentages, legend built from the slices).
- Interaction entirely client-side: tooltip (nearest point; SHARED lists every series at that x; bars hit by rectangle), crosshair, legend toggle (reported), wheel zoom, drag pan, shift-drag brush, double-click/Escape reset — all reported as `rangeSelect`; keyboard `←/→` step through points with the tooltip, `Enter` clicks; `highlight` marks one point; selection modes highlight clicked points/series.
- Export: `exportSvg` serialises the live SVG with computed styles inlined (crosshair/brush removed); `exportPng` rasterises it through an offscreen `<canvas>` at 2× and returns a PNG data URL in the `export` event, decoded on the server. `download` saves either format through a blob URL.
- Theme: `--wb-chart-grid`, `--wb-chart-axis`, `--wb-chart-tooltip-bg` plus the platform tokens; the default 12-colour palette is in the renderer.

## 6. Platform integration

- `ChartTopComponent`: a `TopComponent` wrapping one chart, with a toolbar (reset zoom, save as SVG, save as PNG) extended by the registry folder `Charts/Actions`; the chart is in the window's lookup, so `Actions.context(Chart.class, …)` / `@ActionRegistration` with a `(Chart)` constructor work from the toolbar, menus and the global context (the demo's `ExportChartAction`).
- A custom chart kind: `@Renderer(id = "acme.Heatmap", resource = "…/heatmap.js") class HeatmapChart extends Chart` — the model, batching and export plumbing are reused, only drawing differs.
- `ChartNode`/`SeriesNode` for a property-sheet based chart designer are not implemented.

## 7. Performance budget

| Scenario | Target | Status |
|---|---|---|
| 10 series × 10 000 points, static | first paint < 300 ms after `init`, ≤ 1.5 MB payload (JSON) | JSON only; LTTB keeps the paint cheap |
| 1 series, 1 append/s, window 300 | delta ≤ 60 bytes of payload, zero re-layout | ≈ 190 bytes per message incl. framing, one relayout per frame |
| 100 dashboard charts in one UI, 1 append/s each | one flush message per second (batched), < 16 ms client apply | batched per flush by design |

## 8. Testing

- `ChartTest` (headless UI): init payload with compiled formats; merge rules (append/trim, set discards appends, keyed update/remove, null series); category resolution; events; export futures (success and error); resync with `reset`; `FormatSpec` incl. locale separators.
- Browser (`webbeans-demo/e2e/charts.js`): element counts per type, tick formats, live append deltas, tooltip/crosshair, legend toggle, bar click → keyed point, model change → update, wheel zoom / reset / brush → `rangeSelect`, chart window toolbar with downloads, export round trip, reconnect.

## 9. Open points

- Binary channel for very large series (typed arrays) together with the WebSocket transport.
- Transitions/animation on append and set.
- Per-series value formats and richer tooltips; pie label layout; per-point `aria-label`s.
- Exposing `highlight`/`setXRange` as linked-chart helpers (one brush driving several charts).
