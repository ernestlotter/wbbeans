# WebBeans — Step 17 Implementation Notes (v0.1): maps (`webbeans-map`)

Asked for as "a component that allows for rendering overlays on a geographical map area and allow interaction
on it". Design: `design/map.md`. Source: `webbeans-map`; demo window `SitesMapTopComponent` (*Window ▸ Sites
map*); browser suite `e2e/map.js`.

## 1. What was built

| Piece | Content |
|---|---|
| `ContentProvider` (`webbeans-ui`) + front end | a binary `PageProvider`: prefix → bytes/content type/cache header, signed-in users, served on the HTTP thread; `ServerConfig.imageSources` extends the CSP `img-src` |
| `TileSource` + `TileContentProvider` | `OSM` (proxied), `BLANK`, `proxied(id, template, attribution)`, `direct`, `directory(dir)`; the endpoint `/wb/tiles/<id>/z/x/y` validates the path and range, serves directory tiles, and for proxied sources fetches once per tile (in-flight de-duplication, User-Agent, 10 s timeouts, 4 MB cap), caches under `<datadir>/system/tiles`, re-fetches after `cacheDays`, serves stale on failure, logs an unreachable service once a minute |
| Geometry | `LatLng`, `LatLngBounds`, `Geo` (haversine distance, bearing, destination, spherical area, Web Mercator projection, metres per pixel, zoom to fit) |
| `MapView` | tiles property, view (centre/zoom, animated, fit bounds with padding, fit overlays, max bounds, zoom range), controls on/off, draw mode; overlays by id with `add/remove/get/clear`, layers; one `map` call per flush with merged descriptions and add+remove cancellation; full description on attach/resync; events applied before listeners (view, drag, layer toggle, popup close) |
| Overlays | `Marker`, `Polyline`, `Polygon`, `Circle`, `Rectangle`, `ImageOverlay`, `GeoJsonOverlay`, `Popup` over `Overlay`/`PathOverlay`; popups sanitised with `Html.sanitize` |
| Renderer | `map.js` (~500 lines, no dependencies) + `map.css`: tile pane with fractional zoom, graticule, SVG vectors, HTML markers and popups, image pane, draw pane; pan/wheel/double-click/pinch/keyboard; zoom control, scale bar, attribution, layer control, draw hint; draw modes; hit testing with wide invisible strokes |
| `MapTopComponent` | toolbar: zoom in/out, show everything, five draw tools, `Map/Actions` (context: the map) |
| Build | `build.sh`, root/launcher/demo poms, dist and fat-jar lists |

## 2. Decisions

- **Tiles through the server.** The CSP of the application page allows images from `'self'` only — deliberately —
  so loading `tile.openstreetmap.org` straight from the browser would need loosening it. Proxying keeps the
  policy, works for intranet browsers without internet, de-duplicates and caches, and sends a proper
  `User-Agent` once from the server instead of from every browser. Direct loading stays available for sites
  that prefer it (`imageSources`).
- **No mapping library.** Leaflet would be ~150 KB vendored and a second way of doing things; the platform's
  bundle, theme tokens and delta protocol cover what was needed in ~500 lines. Vector tiles would change that
  calculus — recorded as an open point.
- **Overlays mirror the graph scene's model**: server objects with `changed()`, full descriptions per flush,
  `setAttribute` → `data-*`/`--wb-m-*`, `setInteractive(false)` for decorations.
- **GeoJSON stays GeoJSON.** The document is passed through as parsed JSON; the browser draws every geometry
  type, per-feature `properties.style` overrides the overlay's style, clicks report the feature index and id.
  Applications that need per-feature server objects add markers/shapes instead.
- **Drawing is a mode, not a tool state machine on the server**: the browser collects the vertices and sends
  one `draw` event; the listener decides what to create (the demo adds overlays in a purple colour).

## 3. Verified

- `MapViewTest` (5) and `TileContentProviderTest` (2): 173 JUnit tests green in total.
- Browser (`e2e/map.js`, demo, tiles unreachable from the sandbox so the 12 tile requests 404 — expected):
  five markers with labels, four shapes, two GeoJSON features, layer control, scale bar and attribution, tile
  requests through `/wb/tiles/osm/…`; basemap switch to blank → graticule; marker click → server popup with
  the distance; map click logged with the position; popup close; marker drag → `DRAG` with the new position;
  pan, wheel zoom and the zoom control → `VIEW` events with the zoom level; layer toggle hides the zones;
  polygon drawn with clicks + double-click and rectangle by drag → overlays added by the listener; fit.

## 4. Open

See `design/map.md` §7: clustering, vertex editing, vector tiles, GeoJSON export endpoint, raster export.
