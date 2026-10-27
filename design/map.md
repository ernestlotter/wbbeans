# WebBeans — Map View (detailed design v0.1)

Step 17. JDK 8. Module `webbeans-map` (`org.webbeans.map`): a slippy map with server-owned overlays, for
"rendering overlays on a geographical map area and interacting with them". No client-side mapping library:
the renderer is ~500 lines of plain JavaScript (Web Mercator, tiles, SVG/HTML overlays), consistent with the
rest of the platform (theme tokens, no third-party downloads, one bundle).

## 1. Goals and non-goals

- Base map from raster XYZ tiles, with the tiles served **through the WebBeans server** by default
  (`/wb/tiles/<source>/z/x/y`, fetched once from the provider and cached on disk): the application's Content
  Security Policy allows images from the server only, intranet browsers often have no internet route, and a
  cache keeps repeated views cheap and polite to public tile services. Direct loading (`TileSource.direct` +
  `ServerConfig.imageSources`), local tile folders (`TileSource.directory`) and no tiles at all
  (`TileSource.BLANK`, a graticule) are the alternatives.
- Overlays as server objects with per-flush deltas: markers (default pin tinted by colour, or an icon;
  label, tooltip, rotation, draggable), polylines (arrow), polygons (holes), circles in metres, rectangles,
  georeferenced images, GeoJSON (per-feature `properties.style`, labels/tooltips from properties), popups
  with sanitised HTML; layers the user toggles; free attributes → `data-*`/CSS variables.
- Interaction reported to the server: view changes (lazy; centre, zoom, bounds), clicks (with the overlay and
  GeoJSON feature hit), double-clicks, hovers (lazy), right-clicks, marker drags (applied before the event),
  finished drawings (marker, line, polygon, rectangle, circle), layer toggles, popup closes.
- `MapTopComponent`: toolbar (zoom, show everything, the five draw tools, `Map/Actions` with the map in context).
- Non-goals (v0.1): vector tiles, rotation/tilt, clustering, editing existing shapes by dragging vertices,
  printing, offline tile packaging (the directory source reads what tools like `gdal2tiles` produce).

## 2. API

```
LatLng(lat, lng) · LatLngBounds(s, w, n, e): of(points), extend, pad, getCenter, contains, intersects
Geo: distance, length, area, bearing, destination, project/unproject (Web Mercator), metresPerPixel, zoomToFit
TileSource: OSM, BLANK, proxied(id, template, attribution), direct(...), directory(id, dir, attribution), get(id);
            setSubdomains, setZoomRange/MaxZoom, setUserAgent, setCacheDays, setContentType, setBackgroundColor
TileContentProvider (@ServiceProvider ContentProvider): GET /wb/tiles/<id>/<z>/<x>/<y> → cache → fetch; directory sources

MapView(TileSource) extends Component                       // wb.MapView
  setTileSource, setView(c, z[, animated]), panTo, setZoom, fitBounds(b, pad), fitOverlays(pad), getCenter/Zoom/Bounds,
  setZoomRange, setMaxBounds, setInteractive, setZoomControl, setScaleBar, setLayerControl, setAttribution, setDrawMode
  addMarker/addPolyline/addPolygon/addCircle/addRectangle/addImage/addGeoJson/addPopup(id, …), openPopup(pos, html),
  closePopups, add(overlay), removeOverlay, getOverlay, getOverlays, clearOverlays, getOverlayBounds
  addLayer(id, name) → MapLayer(setName, setVisible), getLayer, removeLayer
  events VIEW, CLICK ("mapClick"), DOUBLE_CLICK, HOVER, MENU, DRAG, DRAW, LAYER_TOGGLE; add*Listener
Overlay: setVisible/Opacity/Tooltip/StyleName/Layer/ZIndex/Interactive/Attribute
PathOverlay: setColor/Weight/Dash/FillColor/FillOpacity/Fill
Marker: setPosition, setIcon, setIconSize, setIconAnchor, setLabel, setColor, setDraggable, setRotation
Polyline: setPoints, addPoint, setArrow, getLength · Polygon: setRing, addHole, getArea · Circle: setCenter, setRadius
Rectangle: setBounds · ImageOverlay: setImage(Resource) | setImageUrl("/wb/blob/…") · GeoJsonOverlay: setGeoJson,
setPointIcon, setPointsAsCircles, setLabelProperty, setTooltipProperty · Popup: setHtml (sanitised), setText, setOpen,
setCloseButton, setMaxWidth
MapTopComponent(map): setDrawTools; ACTIONS_FOLDER = "Map/Actions"
```

## 3. Wire format

Properties: `tiles` ({id, kind, url, sub, attr, min, max, bg} — `url` is the server endpoint for proxied and
directory sources, the template for direct ones, absent for blank), `minZoom`, `maxZoom`, `interactive`,
`zoomControl`, `scaleBar`, `layerControl`, `attribution`, `drawMode`, `maxBounds`.

`call("map", {reset?, view?: {c, z, anim} | {fit, pad, anim}, layers?: {id: {name, vis}|null}, overlays?: {id: spec|null}})`.
Specs carry `k` (marker, line, polygon, circle, rect, image, geojson, popup) plus the common `vis op tt st ly z ia a`
and the kind's fields (`p i iw ih ax ay l c drag rot` / `pts c w dash arrow` / `rings …` / `p r …` / `b …` /
`b i url` / `geo c w dash fc fo fill icon circles lp tp` / `p html open close mw`). A view the browser already
reported is not re-sent; a server `setView` wins over it.

Events: `view {c, z, b}` (lazy 150 ms; applied), `mapClick {p, x, y, id?, f?, fid?}`, `dblclick`, `hover`
(lazy 80 ms; `{}` on leaving), `menu`, `drag {id, p, dragged}` (applied), `draw {mode, pts}`, `layer {layer, vis}`
(applied), `popupClose {popup}` (applied; the auto popup is removed).

## 4. Renderer (`map.js`, `map.css`)

- Panes: tiles (`<img>` per tile, transform-positioned; the integer level nearest the fractional zoom, scaled),
  graticule SVG (blank source), images, vector SVG, markers (HTML, so icons and labels are DOM), popups,
  drawing SVG; controls on top (zoom, scale bar, attribution, layer control, draw hint).
- Projection is computed in the browser from the view (centre + fractional zoom) for every overlay on every
  render; a few hundred overlays re-render per frame while panning without trouble.
- Interaction: drag pan (3 px threshold, otherwise a click), wheel zoom around the pointer, double-click zoom,
  pinch and one-finger pan on touch, arrows / +/− / Enter / Escape on the keyboard, marker drag, draw modes
  (click vertices + double-click/Enter; drag for rectangle and circle), passive overlays let clicks through.
- Arrowheads are `<marker>`s per colour; polygons use `evenodd` for holes; circles convert metres to pixels at
  their latitude; images are stretched between projected corners.
- The scale bar picks the largest "nice" distance under 100 px; the graticule step follows the zoom.

## 5. Platform integration

- `ContentProvider` (new in `webbeans-ui`): a binary counterpart of `PageProvider` served by the front end
  under a prefix to signed-in users on the HTTP thread (thread-safe, may block on I/O). The tile endpoint is its
  first user; a GeoJSON or raster export endpoint would be the next.
- `ServerConfig.imageSources(origin…)` extends the CSP `img-src` for direct tile sources (or any external image).
- `MapTopComponent` puts the map in the window's lookup; `Map/Actions` toolbar actions over `MapView` work as
  with charts and 3D (the demo's basemap toggle is one).
- Roles/locale/theme as everywhere; the note about OpenStreetMap's tile usage policy belongs in the deployment
  docs: heavy use should point `TileSource.proxied` at a commercial or self-hosted tile server.

## 6. Tests

`MapViewTest` (wire format, batching and cancellation, every overlay kind, layers, view/click/drag/draw/layer/
popup events, geometry helpers, resync), `TileContentProviderTest` (directory source, path validation, proxy
fetch-once-then-cache with a local HTTP server, upstream 404, direct sources untouched). Browser:
`e2e/map.js` on the demo's Sites map (tiles requested through the endpoint, overlays and GeoJSON features,
layer control, scale/attribution, basemap switch → graticule, marker popup, map click, popup close, marker drag,
pan + wheel + zoom control → view events, layer toggle, polygon and rectangle drawing, fit).

## 7. Open points

Clustering for thousands of markers; vertex editing of drawn shapes; vector tiles / labels in the theme's
font; a `/wb/geo/` export endpoint (GeoJSON of the overlays) on the new `ContentProvider`; raster export of
the current view (tiles are same-origin through the proxy, so a canvas export is possible).
