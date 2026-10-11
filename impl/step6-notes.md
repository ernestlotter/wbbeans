# WebBeans — Step 6 Implementation Notes (v0.1)

What was built for roadmap step 6 (charts), how it maps to `design/charts.md`, and where it deviates. Source: `webbeans-step6.zip` (99 JUnit tests; `webbeans-demo/e2e/charts.js` plus the step 3–5 scripts drive Chromium).

## 1. What exists

| Module / package | Classes | Design § |
|---|---|---|
| `webbeans-charts` — `org.webbeans.charts` | `Chart` (`wb.Chart`: properties title/subtitle/type/xAxis/yAxis/y2Axis/legend/palette/animation/tooltip/zoom/stacked/selection/horizontal/labels; events `POINT_CLICK`, `LEGEND_TOGGLE`, `RANGE_SELECT` (lazy 100 ms); calls `data`, `resetZoom`, `highlight`, `setRange`, `export`, `download`; `exportPng`/`exportSvg` futures, `download(format, name)`), `ChartModel` (ordered series, categories, category → index resolution), `Series` (style, `setPoints`/`addPoint(s)`/`updatePoint`/`removePoint`/`setWindow`, `setYAxis`, `setStack`, `setSmooth`, `setLineStyle`, `setMarker`), `Point` (number/instant/category x, size, key, label, colour, meta; wire form `[x,y]`, `[x,y,s]` or an object), `Axis` (type, title, min/max, format, grid, visible, `addMarker`, `addBand`), `AxisType`, `Legend` (position, visible, interactive), `ChartType`, `TooltipMode`, `ZoomMode`, `SelectionMode`, `LineStyle`, `Marker`, `PointEvent`, `LegendEvent`, `RangeEvent`, `FormatSpec` (DecimalFormat/DateTimeFormatter patterns → client spec), `ChartTopComponent` (toolbar: reset zoom, save as SVG/PNG, `Charts/Actions` registry actions over the chart in the lookup) | §2, 3, 6 |
| client | `chart.js` (~600 lines, no dependencies): scales linear/log/time/category with nice ticks (1-2-5 steps, calendar-aligned time steps), measured margins, grid, axis titles, secondary axis, markers/bands, legend (top/bottom/left/right, click toggles with a `legendToggle` event), line/step/area (stacked by group or chart-wide, monotone smoothing, LTTB downsampling above 2 points per pixel), bars (grouped by series, stacked, horizontal, per-point colours, value labels), scatter/bubble (sqrt size scale, labels), pie/donut (labels with percentages, legend from slices), tooltip (POINT nearest / SHARED all series at x / AXIS, bar hit testing by rectangle, crosshair), wheel zoom, drag pan, shift-drag brush, double-click/Escape reset (`rangeSelect` events), keyboard point stepping, highlight, `ResizeObserver` relayout throttled to one frame, SVG export with computed styles inlined, PNG via an offscreen canvas at 2×, downloads through a blob URL; `chart.css` with `--wb-chart-*` tokens | §5 |
| `webbeans-ui` additions | `UI.beforeFlush(Runnable)` — a component that coalesces many mutations into one call registers once per task and emits when the message is assembled (run before a snapshot too, so components reset their state); `Layout.Grid.rowHeights`/`fill()` (dashboards need stretched rows) | §4 |
| `webbeans-demo` | `DashboardTopComponent` (2×2 grid: live "Solar wind" line + area on a secondary axis with a 60-point window, band and marker; "Moons per planet" bars with click → status; "Planet types" donut; "Gravity vs. moons" bubble scatter on a log axis with XY zoom → status), `MoonsChartTopComponent` (`ChartTopComponent`, horizontal sorted bars, follows the model), `ExportChartAction` (registered context action over `Chart`: `exportPng` + `exportSvg` → notification with the sizes) | §6 |

Tests: `ChartTest` (init carries the whole model with compiled axis formats; several mutations in one task merge into one `data` call — appends with `trim`, style `set`, a new series with `points`, a removed series as `null`; `set` discards earlier appends; keyed `update`/`remove` merge and cancel; category points resolve to indexes and extend the categories in the same call; `pointClick` resolves points by key and index, `legendToggle` updates `Series.visible`, `rangeSelect` with reset; export futures complete from the client's event, errors complete exceptionally; a fresh client gets a full `reset` payload; `FormatSpec` for grouping/fractions/percent/German separators/quoted suffixes/time). The Playwright script checks four sized charts with the expected element counts and tick formats, live appends as small deltas (< 400 bytes, `append` only), the shared tooltip and crosshair, legend toggling, bar click → keyed point, model change → chart update, wheel zoom / double-click reset / brush → `rangeSelect`, the chart window's toolbar with SVG and PNG downloads, the export round trip through a registered action, and a forced reconnect (resync).

## 2. Shape as implemented

- Package `org.webbeans.charts` in module `webbeans-charts` (design: `org.webbeans.api.charts` / `org.webbeans.modules.charts`; client `wb/chart.js`). Depends on `webbeans-actions` for `ChartTopComponent`'s toolbar.
- Colours are CSS strings (`"#4a90e2"`, `"var(--wb-danger)"`), not a `Color` class; `Series.setColor(String)`, `Point.withColor(String)`, `Chart.setPalette(String...)`.
- Batching: `Chart` implements `ChartModel.Sink`; every mutation updates a per-series `Delta` (style, set, append, update, remove, removed) and schedules one `UI.beforeFlush` task; the task emits one `call("data", {...})` with the merge rules of the design (`set` discards earlier appends; `remove` of a key cancels its pending `update`; `null` for removed series; `trim` = the window). Category points are resolved to indexes at emit time, so new categories ride in the same call. Before attach nothing is sent; `attached()` and `resync()` send the whole model with `reset: true`.
- Axis formats are compiled per session locale when the chart attaches (`FormatSpec.compile` → `{kind:"number", prefix, suffix, minFrac, maxFrac, grouping, groupSize, multiplier, decimal, group}` or `{kind:"time", pattern}`); the client formats ticks, labels and tooltips from the spec. Without a format the client picks compact numbers (`1.2k`, `3M`) and time formats by span.
- Export: `exportPng(w, h)` / `exportSvg()` send `call("export", id, format, w, h)`; the client answers with an `export` event (`data` as a PNG data URL / SVG text, or `error`) that completes the future on the session executor. `download(format, file)` saves client-side without a round trip. There is no binary channel: PNG bytes travel base64-encoded in the event.
- Events: `pointClick {series, index, k, x, y, ctrl, shift, alt}` → `PointEvent.getPoint()` resolves by key first, then index; `legendToggle {series, visible}` updates `Series.visible` as client-owned state before listeners run; `rangeSelect {xFrom, xTo, yFrom, yTo, reset}`.
- `ChartTopComponent`: the chart in the lookup (`Lookups.singleton(chart)`), a `Toolbar` of `ToolbarButton`s bound to simple actions plus `Actions.forPath("Charts/Actions")` presenters bound to the window's lookup.

## 3. Platform fixes found on the way

- A `beforeFlush` task registered during `UIProvider.init` (first connect) was dropped by `snapshot()`, leaving the chart's "scheduled" flag set forever; `snapshot()` now runs the pending tasks first (their output is superseded by `resync()`).
- `Layout.Grid` rows sized to content, so charts in a grid collapsed to their minimum height; `rowHeights`/`fill()` stretch them.
- A renderer must not use `el.hidden` for its own state — it is the HTML `hidden` attribute (the charts vanished).

## 4. Deviations from the design

1. No typed-array blob for series > 50 000 points and no binary channel: everything is JSON (the long-poll transport has no binary frames; revisit with the Jetty/WebSocket front end).
2. Animation (`setAnimation`) is accepted but the renderer redraws without transitions.
3. Tooltip formatting uses the axis format for values; per-series number formats and HTML tooltips are not there.
4. `Chart.Custom` (own renderer with the model plumbing) is possible by subclassing `Chart` with another `@Renderer`, but not exercised.
5. `ChartNode`/`SeriesNode` for the property sheet are not implemented.
6. Pie label collision avoidance is a minimum-angle filter, not a layout.
7. Accessibility: keyboard stepping through points with the tooltip and `Enter` = click; no per-point `aria-label`s.

## 5. Notes for step 7 (Scene3D)

- The `UI.beforeFlush` coalescing and the `data`-call pattern (set/append/update/remove per keyed object) carry over to scene graphs: one `scene` call per flush with node adds/updates/removals, full payload on attach/resync.
- Large geometry needs the binary channel the charts also lack; plan it with the WebSocket transport (content-addressed blobs, cached in the browser).
- Export (PNG of the canvas) can reuse the export/event round trip and `download`.
