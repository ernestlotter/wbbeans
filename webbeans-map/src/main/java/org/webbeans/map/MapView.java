package org.webbeans.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.UI;

/**
 * A slippy map ({@code wb.MapView}): raster tiles from a {@link TileSource} with {@link Overlay}s on top —
 * markers, lines, polygons, circles, rectangles, images, GeoJSON, popups — grouped into {@link MapLayer}s.
 * The server owns the overlays; every change is a small delta merged per flush into one {@code map} call. The
 * browser pans, zooms, hovers, clicks, drags markers and draws shapes, and reports back.
 *
 * <pre>
 * MapView map = new MapView(TileSource.OSM);
 * map.setView(LatLng.of(-42.88, 147.33), 11);
 * map.addMarker("hq", LatLng.of(-42.882, 147.329)).setLabel("Head office").setColor("#b43b3b").setDraggable(true);
 * map.addCircle("range", LatLng.of(-42.882, 147.329), 2500).setColor("#0f7f86");
 * map.addPolyline("route", path).setWeight(3).setArrow(true);
 * map.addGeoJson("sites", geoJsonText).setPointsAsCircles(true).setLayer(map.addLayer("sites", "Sites").getId());
 * map.addMapClickListener(e -&gt; { if (e.getOverlay() == null) map.openPopup(e.getPosition(), "&lt;b&gt;" + e.getPosition() + "&lt;/b&gt;"); });
 * map.setDrawMode(MapView.DrawMode.POLYGON);
 * map.addDrawListener(e -&gt; map.addPolygon("area" + n++, e.getPoints()));
 * </pre>
 */
@Renderer(id = "wb.MapView", resource = "org/webbeans/map/client/map.js", css = "org/webbeans/map/client/map.css")
public class MapView extends Component {

    public enum DrawMode { NONE, MARKER, LINE, POLYGON, RECTANGLE, CIRCLE }

    public static final Property<Map<String, Object>> TILES = Property.of("tiles", mapClass(), null);
    public static final Property<Integer> MIN_ZOOM = Property.of("minZoom", Integer.class, Integer.valueOf(0));
    public static final Property<Integer> MAX_ZOOM = Property.of("maxZoom", Integer.class, Integer.valueOf(19));
    public static final Property<Boolean> INTERACTIVE = Property.of("interactive", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> ZOOM_CONTROL = Property.of("zoomControl", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> SCALE_BAR = Property.of("scaleBar", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> LAYER_CONTROL = Property.of("layerControl", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> ATTRIBUTION = Property.of("attribution", Boolean.class, Boolean.TRUE);
    public static final Property<DrawMode> DRAW_MODE = Property.of("drawMode", DrawMode.class, DrawMode.NONE);
    /** Keep the view inside this box (south, west, north, east); null = the world. */
    public static final Property<List<Double>> MAX_BOUNDS = Property.of("maxBounds", listClass(), null);

    /** The view changed (lazy): centre, zoom, bounds — already applied. */
    public static final EventType<ViewEvent> VIEW = EventType.of("view", ViewEvent::new).lazy(150);
    public static final EventType<MapEvent> CLICK = EventType.of("mapClick", MapEvent::new);
    public static final EventType<MapEvent> DOUBLE_CLICK = EventType.of("dblclick", MapEvent::new);
    public static final EventType<MapEvent> HOVER = EventType.of("hover", MapEvent::new).lazy(80);
    public static final EventType<MapEvent> MENU = EventType.of("menu", MapEvent::new);
    /** A draggable marker was dropped (position already applied). */
    public static final EventType<MapEvent> DRAG = EventType.of("drag", MapEvent::new);
    public static final EventType<DrawEvent> DRAW = EventType.of("draw", DrawEvent::new);
    /** The user toggled a layer in the layer control (applied). */
    public static final EventType<Event> LAYER_TOGGLE = EventType.of("layer", Event::new);
    /** The user closed a popup (applied). */
    static final EventType<Event> POPUP_CLOSE = EventType.of("popupClose", Event::new);

    private TileSource tiles;
    private LatLng center = new LatLng(0, 0);
    private double zoom = 2;
    private LatLngBounds bounds;
    private final Map<String, Overlay> overlays = new LinkedHashMap<String, Overlay>();
    private final Map<String, MapLayer> layers = new LinkedHashMap<String, MapLayer>();
    private final Map<String, Overlay> pending = new LinkedHashMap<String, Overlay>();
    private final Map<String, MapLayer> pendingLayers = new LinkedHashMap<String, MapLayer>();
    private final java.util.Set<String> fresh = new java.util.LinkedHashSet<String>();
    private boolean scheduled;
    private boolean viewDirty = true;
    private boolean animateView;
    private LatLngBounds fitBounds;
    private int fitPadding;
    private int popupCounter;

    public MapView() {
        this(TileSource.OSM);
    }

    public MapView(TileSource source) {
        super("wb.MapView");
        setTileSource(source);
        addListener(POPUP_CLOSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    @SuppressWarnings("unchecked")
    private static Class<List<Double>> listClass() {
        return (Class<List<Double>>) (Class<?>) List.class;
    }

    // ---- base map and view ---------------------------------------------------------------------------------------

    public TileSource getTileSource() {
        return tiles;
    }

    public MapView setTileSource(TileSource source) {
        tiles = source == null ? TileSource.BLANK : source;
        set(TILES, tiles.toJson());
        return this;
    }

    public LatLng getCenter() {
        return center;
    }

    /** The zoom level (fractional while the user is between levels). */
    public double getZoom() {
        return zoom;
    }

    /** The visible area as last reported by the browser; null before the first report. */
    public LatLngBounds getBounds() {
        return bounds;
    }

    /** Moves the view. */
    public MapView setView(LatLng c, double z) {
        return setView(c, z, false);
    }

    public MapView setView(LatLng c, double z, boolean animated) {
        center = c;
        zoom = z;
        animateView = animated;
        fitBounds = null;
        viewDirty = true;
        schedule();
        return this;
    }

    public MapView panTo(LatLng c) {
        return setView(c, zoom, true);
    }

    public MapView setZoom(double z) {
        return setView(center, z, true);
    }

    /** Zooms and pans so the box is visible, with a pixel padding. */
    public MapView fitBounds(LatLngBounds b, int paddingPx) {
        if (b != null) {
            fitBounds = b;
            fitPadding = paddingPx;
            center = b.getCenter();
            viewDirty = true;
            schedule();
        }
        return this;
    }

    /** Fits every visible overlay. */
    public MapView fitOverlays(int paddingPx) {
        return fitBounds(getOverlayBounds(), paddingPx);
    }

    /** The box around every visible overlay with an extent (markers count as points); null for none. */
    public LatLngBounds getOverlayBounds() {
        LatLngBounds b = null;
        for (Overlay o : overlays.values()) {
            if (!o.isVisible()) {
                continue;
            }
            LatLngBounds ob = boundsOf(o);
            if (ob != null) {
                b = b == null ? ob : b.extend(ob);
            }
        }
        return b;
    }

    private static LatLngBounds boundsOf(Overlay o) {
        if (o instanceof Marker) {
            LatLng p = ((Marker) o).getPosition();
            return new LatLngBounds(p, p);
        }
        if (o instanceof Polyline) {
            return ((Polyline) o).getBounds();
        }
        if (o instanceof Polygon) {
            return ((Polygon) o).getBounds();
        }
        if (o instanceof Circle) {
            return ((Circle) o).getBounds();
        }
        if (o instanceof Rectangle) {
            return ((Rectangle) o).getBounds();
        }
        if (o instanceof ImageOverlay) {
            return ((ImageOverlay) o).getBounds();
        }
        return null;
    }

    public MapView setZoomRange(int min, int max) {
        set(MIN_ZOOM, min);
        set(MAX_ZOOM, max);
        return this;
    }

    public MapView setMaxBounds(LatLngBounds b) {
        set(MAX_BOUNDS, b == null ? null : b.toJson());
        return this;
    }

    public MapView setInteractive(boolean b) {
        set(INTERACTIVE, b);
        return this;
    }

    public MapView setZoomControl(boolean b) {
        set(ZOOM_CONTROL, b);
        return this;
    }

    public MapView setScaleBar(boolean b) {
        set(SCALE_BAR, b);
        return this;
    }

    public MapView setLayerControl(boolean b) {
        set(LAYER_CONTROL, b);
        return this;
    }

    public MapView setAttribution(boolean b) {
        set(ATTRIBUTION, b);
        return this;
    }

    /** What the next clicks draw; {@link DrawEvent} arrives when the shape is complete, then the mode stays on. */
    public MapView setDrawMode(DrawMode m) {
        set(DRAW_MODE, m == null ? DrawMode.NONE : m);
        return this;
    }

    public DrawMode getDrawMode() {
        return get(DRAW_MODE);
    }

    @Override
    public MapView setTestId(String id) {
        super.setTestId(id);
        return this;
    }

    // ---- overlays -------------------------------------------------------------------------------------------------

    public Marker addMarker(String id, LatLng position) {
        return add(new Marker(id, position));
    }

    public Polyline addPolyline(String id, List<LatLng> points) {
        return add(new Polyline(id, points));
    }

    public Polygon addPolygon(String id, List<LatLng> ring) {
        return add(new Polygon(id, ring));
    }

    public Circle addCircle(String id, LatLng center, double radiusMetres) {
        return add(new Circle(id, center, radiusMetres));
    }

    public Rectangle addRectangle(String id, LatLngBounds b) {
        return add(new Rectangle(id, b));
    }

    public ImageOverlay addImage(String id, LatLngBounds b) {
        return add(new ImageOverlay(id, b));
    }

    public GeoJsonOverlay addGeoJson(String id, String geoJson) {
        return add(new GeoJsonOverlay(id, org.webbeans.core.json.Json.parseObject(geoJson)));
    }

    public GeoJsonOverlay addGeoJson(String id, Map<String, Object> geoJson) {
        return add(new GeoJsonOverlay(id, geoJson));
    }

    public Popup addPopup(String id, LatLng position, String html) {
        return add(new Popup(id, position, html));
    }

    /** Opens a one-off popup (replacing the previous one opened this way). */
    public Popup openPopup(LatLng position, String html) {
        removeOverlay("popup:auto");
        return addPopup("popup:auto", position, html);
    }

    public void closePopups() {
        for (Overlay o : new ArrayList<Overlay>(overlays.values())) {
            if (o instanceof Popup) {
                ((Popup) o).setOpen(false);
            }
        }
    }

    /** Adds an overlay created elsewhere (ids are unique per map; an existing id is replaced). */
    public <T extends Overlay> T add(T o) {
        Overlay old = overlays.put(o.getId(), o);
        if (old != null && old != o) {
            old.setMap(null);
        } else if (old == null) {
            fresh.add(o.getId());
        }
        o.setMap(this);
        pending.put(o.getId(), o);
        schedule();
        return o;
    }

    public void removeOverlay(String id) {
        Overlay o = overlays.remove(id);
        if (o != null) {
            o.setMap(null);
            if (fresh.remove(id)) {
                pending.remove(id);
            } else {
                pending.put(id, null);
            }
            schedule();
        }
    }

    public void removeOverlay(Overlay o) {
        if (o != null && overlays.get(o.getId()) == o) {
            removeOverlay(o.getId());
        }
    }

    public Overlay getOverlay(String id) {
        return id == null ? null : overlays.get(id);
    }

    public Collection<Overlay> getOverlays() {
        return Collections.unmodifiableCollection(overlays.values());
    }

    public void clearOverlays() {
        for (String id : new ArrayList<String>(overlays.keySet())) {
            removeOverlay(id);
        }
    }

    // ---- layers ---------------------------------------------------------------------------------------------------

    /** A toggleable group; assign overlays with {@code overlay.setLayer(id)}. */
    public MapLayer addLayer(String id, String name) {
        MapLayer l = layers.get(id);
        if (l == null) {
            l = new MapLayer(id, name, this);
            layers.put(id, l);
        } else {
            l.setName(name);
        }
        pendingLayers.put(id, l);
        schedule();
        return l;
    }

    public MapLayer getLayer(String id) {
        return layers.get(id);
    }

    public Collection<MapLayer> getLayers() {
        return Collections.unmodifiableCollection(layers.values());
    }

    public void removeLayer(String id) {
        if (layers.remove(id) != null) {
            for (Overlay o : overlays.values()) {
                if (id.equals(o.getLayer())) {
                    o.setLayer(null);
                }
            }
            pendingLayers.put(id, null);
            schedule();
        }
    }

    void layerChanged(MapLayer l) {
        if (layers.get(l.getId()) == l) {
            pendingLayers.put(l.getId(), l);
            schedule();
        }
    }

    // ---- events ----------------------------------------------------------------------------------------------------

    public Registration addViewListener(Listener<ViewEvent> l) {
        return addListener(VIEW, l);
    }

    /** Clicks on the map or on an overlay ({@code MapEvent.getOverlay()}); the plain component click is {@code CLICK}. */
    public Registration addMapClickListener(Listener<MapEvent> l) {
        return addListener(CLICK, l);
    }

    public Registration addDoubleClickListener(Listener<MapEvent> l) {
        return addListener(DOUBLE_CLICK, l);
    }

    public Registration addHoverListener(Listener<MapEvent> l) {
        return addListener(HOVER, l);
    }

    public Registration addMenuListener(Listener<MapEvent> l) {
        return addListener(MENU, l);
    }

    public Registration addDragListener(Listener<MapEvent> l) {
        return addListener(DRAG, l);
    }

    public Registration addDrawListener(Listener<DrawEvent> l) {
        return addListener(DRAW, l);
    }

    @Override
    protected void attached() {
        super.attached();
        ensureListened(VIEW);
        ensureListened(DRAG);
        ensureListened(LAYER_TOGGLE);
        resync();
    }

    private <X extends Event> void ensureListened(EventType<X> type) {
        if (!isListened(type.getName())) {
            addListener(type, new Listener<X>() {
                @Override
                public void onEvent(X e) {
                }
            });
        }
    }

    @Override
    protected void onEvent(Event event) {
        Map<String, Object> d = event.getData();
        if (event instanceof ViewEvent) {
            LatLng c = LatLng.fromJson(d.get("c"));
            if (c != null) {
                center = c;
            }
            zoom = event.getDouble("z", zoom);
            LatLngBounds b = LatLngBounds.fromJson(d.get("b"));
            if (b != null) {
                bounds = b;
            }
            viewDirty = false;      // the browser already is where it reports
            fitBounds = null;
        } else if (event instanceof MapEvent && d.containsKey("id") && d.containsKey("p") && Boolean.TRUE.equals(d.get("dragged"))) {
            Overlay o = overlays.get(event.getString("id"));
            LatLng p = LatLng.fromJson(d.get("p"));
            if (o instanceof Marker && p != null) {
                ((Marker) o).moved(p);
            }
        } else if (d.containsKey("layer") && d.containsKey("vis")) {
            MapLayer l = layers.get(event.getString("layer"));
            if (l != null) {
                l.toggledByUser(event.getBoolean("vis"));
            }
        } else if (d.containsKey("popup")) {
            Overlay o = overlays.get(event.getString("popup"));
            if (o instanceof Popup) {
                ((Popup) o).closedByUser();
                if ("popup:auto".equals(o.getId())) {
                    removeOverlay(o.getId());
                }
            }
        }
    }

    // ---- deltas ----------------------------------------------------------------------------------------------------

    void overlayChanged(Overlay o) {
        if (overlays.get(o.getId()) == o) {
            pending.put(o.getId(), o);
            schedule();
        }
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

    static List<Object> pointsJson(List<LatLng> pts) {
        List<Object> out = new ArrayList<Object>(pts.size());
        for (LatLng p : pts) {
            out.add(p.toJson());
        }
        return out;
    }

    private Map<String, Object> viewJson() {
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        if (fitBounds != null) {
            v.put("fit", fitBounds.toJson());
            v.put("pad", fitPadding);
        } else {
            v.put("c", center.toJson());
            v.put("z", zoom);
        }
        if (animateView) {
            v.put("anim", true);
        }
        return v;
    }

    private void emitPending() {
        if (!isAttached()) {
            pending.clear();
            pendingLayers.clear();
            fresh.clear();
            return;
        }
        if (pending.isEmpty() && pendingLayers.isEmpty() && !viewDirty) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (viewDirty) {
            payload.put("view", viewJson());
            viewDirty = false;
            animateView = false;
        }
        if (!pendingLayers.isEmpty()) {
            Map<String, Object> ls = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, MapLayer> e : pendingLayers.entrySet()) {
                ls.put(e.getKey(), e.getValue() == null ? null : e.getValue().toJson());
            }
            payload.put("layers", ls);
        }
        if (!pending.isEmpty()) {
            Map<String, Object> os = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Overlay> e : pending.entrySet()) {
                os.put(e.getKey(), e.getValue() == null ? null : e.getValue().toJson());
            }
            payload.put("overlays", os);
        }
        pending.clear();
        pendingLayers.clear();
        fresh.clear();
        call("map", payload);
    }

    @Override
    protected void resync() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("reset", true);
        payload.put("view", viewJson());
        Map<String, Object> ls = new LinkedHashMap<String, Object>();
        for (MapLayer l : layers.values()) {
            ls.put(l.getId(), l.toJson());
        }
        payload.put("layers", ls);
        Map<String, Object> os = new LinkedHashMap<String, Object>();
        for (Overlay o : overlays.values()) {
            os.put(o.getId(), o.toJson());
        }
        payload.put("overlays", os);
        pending.clear();
        pendingLayers.clear();
        fresh.clear();
        viewDirty = false;
        animateView = false;
        call("map", payload);
    }
}
