package org.webbeans.map;

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
import java.util.concurrent.Executor;

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

/** The map's wire format and batching, overlays and layers, events, geometry helpers, resync — on a headless UI. */
public class MapViewTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private UISession session;
    private UI ui;
    private final List<String> sent = new ArrayList<String>();
    private MapView map;
    private final LatLng hobart = LatLng.of(-42.8821, 147.3272);

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI u) {
                Panel root = new Panel(new Layout.Border());
                map = new MapView(TileSource.OSM);
                map.setView(hobart, 11);
                map.addMarker("hq", hobart).setLabel("HQ").setColor("#b43b3b").setDraggable(true);
                map.addLayer("sites", "Sites");
                map.addCircle("range", hobart, 2500).setLayer("sites").setColor("#0f7f86").setFillOpacity(0.1);
                root.add(map, Layout.Border.CENTER);
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

    private void run(final Runnable r) {
        final Throwable[] failure = new Throwable[1];
        ui.access(new Runnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        if (failure[0] instanceof Error) {
            throw (Error) failure[0];
        }
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapCalls(String message) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object o : Json.array(Json.parseObject(message), "ops")) {
            Map<String, Object> op = (Map<String, Object>) o;
            if ("call".equals(op.get("op")) && "map".equals(op.get("m")) && ((Number) op.get("id")).intValue() == map.getId()) {
                out.add((Map<String, Object>) ((List<Object>) op.get("args")).get(0));
            }
        }
        return out;
    }

    private Map<String, Object> last() {
        List<Map<String, Object>> calls = mapCalls(sent.get(sent.size() - 1));
        assertEquals("one map call per flush", 1, calls.size());
        return calls.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> overlay(Map<String, Object> payload, String id) {
        Map<String, Object> m = (Map<String, Object>) payload.get("overlays");
        return m == null ? null : (Map<String, Object>) m.get(id);
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
                ui.handleEvents(Collections.<Object>singletonList(map("id", map.getId(), "type", type, "data", data)));
            }
        });
    }

    private static double d(Object o) {
        return ((Number) o).doubleValue();
    }

    @Test
    public void initialDescriptionHasTilesViewLayersAndOverlays() {
        Map<String, Object> first = mapCalls(sent.get(0)).get(0);
        assertEquals(Boolean.TRUE, first.get("reset"));
        Map<?, ?> view = (Map<?, ?>) first.get("view");
        assertEquals(11.0, d(view.get("z")), 0.0);
        assertEquals(-42.8821, d(((List<?>) view.get("c")).get(0)), 1e-9);
        assertEquals("[sites]", ((Map<?, ?>) first.get("layers")).keySet().toString());
        Map<String, Object> hq = overlay(first, "hq");
        assertEquals("marker", hq.get("k"));
        assertEquals("HQ", hq.get("l"));
        assertEquals(Boolean.TRUE, hq.get("drag"));
        Map<String, Object> range = overlay(first, "range");
        assertEquals("circle", range.get("k"));
        assertEquals(2500.0, d(range.get("r")), 0.0);
        assertEquals("sites", range.get("ly"));
        assertEquals(0.1, d(range.get("fo")), 1e-9);
        // tiles property: the proxied OSM source points at the server endpoint, never at the internet
        assertTrue(sent.get(0).contains("\"url\":\"/wb/tiles/osm/{z}/{x}/{y}\""));
        assertTrue(sent.get(0).contains("OpenStreetMap"));
    }

    @Test
    public void changesMergePerFlushAndAddRemoveCancels() {
        run(new Runnable() {
            @Override
            public void run() {
                Marker m = (Marker) map.getOverlay("hq");
                m.setLabel("Head office");
                m.setTooltip("Hobart");
                m.setPosition(LatLng.of(-42.9, 147.3));
                map.addPolyline("route", Arrays.asList(hobart, LatLng.of(-42.5, 147.4), LatLng.of(-41.4, 147.1))).setWeight(3).setArrow(true).setDash("6 4");
                map.addPolygon("zone", Arrays.asList(LatLng.of(-43, 147), LatLng.of(-43, 148), LatLng.of(-42, 148))).setFillColor("#f00").setFill(false);
                map.addRectangle("box", new LatLngBounds(-43.5, 146.5, -42.5, 147.5));
                map.addImage("plan", new LatLngBounds(-42.9, 147.3, -42.88, 147.34)).setImageUrl("/wb/blob/abc").setOpacity(0.5);
                map.addGeoJson("gj", "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"id\":\"a\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[147.3,-42.9]},\"properties\":{\"name\":\"A\"}}]}")
                        .setPointsAsCircles(true).setLabelProperty("name");
                map.addPopup("pop", hobart, "<b>HQ</b><script>alert(1)</script>").setMaxWidth(200);
                map.addMarker("tmp", hobart);
                map.removeOverlay("tmp");                 // added and removed in one flush: not sent at all
                map.getLayer("sites").setName("Sites & zones");
                map.setView(hobart, 12, true);
            }
        });
        Map<String, Object> g = last();
        Map<String, Object> hq = overlay(g, "hq");
        assertEquals("Head office", hq.get("l"));
        assertEquals("Hobart", hq.get("tt"));
        assertEquals(-42.9, d(((List<?>) hq.get("p")).get(0)), 1e-9);
        assertEquals(1, ((Map<?, ?>) g.get("layers")).size());
        assertEquals("Sites & zones", ((Map<?, ?>) ((Map<?, ?>) g.get("layers")).get("sites")).get("name"));
        Map<String, Object> route = overlay(g, "route");
        assertEquals("line", route.get("k"));
        assertEquals(3, ((List<?>) route.get("pts")).size());
        assertEquals(Boolean.TRUE, route.get("arrow"));
        assertEquals("6 4", route.get("dash"));
        Map<String, Object> zone = overlay(g, "zone");
        assertEquals("polygon", zone.get("k"));
        assertEquals(1, ((List<?>) zone.get("rings")).size());
        assertEquals(Boolean.FALSE, zone.get("fill"));
        assertEquals("rect", overlay(g, "box").get("k"));
        assertEquals("/wb/blob/abc", overlay(g, "plan").get("url"));
        Map<String, Object> gj = overlay(g, "gj");
        assertEquals("geojson", gj.get("k"));
        assertEquals("FeatureCollection", ((Map<?, ?>) gj.get("geo")).get("type"));
        assertEquals("name", gj.get("lp"));
        Map<String, Object> pop = overlay(g, "pop");
        assertEquals("popup", pop.get("k"));
        assertFalse("sanitised", String.valueOf(pop.get("html")).contains("<script"));
        assertTrue(String.valueOf(pop.get("html")).contains("<b>HQ</b>"));
        assertFalse(((Map<?, ?>) g.get("overlays")).containsKey("tmp"));
        Map<?, ?> view = (Map<?, ?>) g.get("view");
        assertEquals(12.0, d(view.get("z")), 0.0);
        assertEquals(Boolean.TRUE, view.get("anim"));

        // removal sends null; fitBounds sends a fit view
        run(new Runnable() {
            @Override
            public void run() {
                map.removeOverlay("route");
                map.fitOverlays(20);
            }
        });
        g = last();
        assertTrue(((Map<?, ?>) g.get("overlays")).containsKey("route"));
        assertNull(((Map<?, ?>) g.get("overlays")).get("route"));
        assertNotNull(((Map<?, ?>) g.get("view")).get("fit"));
        assertEquals(20, ((Number) ((Map<?, ?>) g.get("view")).get("pad")).intValue());
        assertNull(map.getOverlay("route"));
    }

    @Test
    public void eventsApplyViewDragLayerAndPopupStateAndReachListeners() {
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                map.addViewListener(new Listener<ViewEvent>() {
                    @Override
                    public void onEvent(ViewEvent e) {
                        log.add("view:" + e.getZoom() + ":" + e.getBounds());
                    }
                });
                map.addMapClickListener(new Listener<MapEvent>() {
                    @Override
                    public void onEvent(MapEvent e) {
                        log.add("click:" + e.getOverlay() + ":" + e.getFeature() + ":" + e.getPosition());
                    }
                });
                map.addDragListener(new Listener<MapEvent>() {
                    @Override
                    public void onEvent(MapEvent e) {
                        log.add("drag:" + ((Marker) e.getOverlay()).getPosition());
                    }
                });
                map.addDrawListener(new Listener<DrawEvent>() {
                    @Override
                    public void onEvent(DrawEvent e) {
                        log.add("draw:" + e.getMode() + ":" + e.getPoints().size() + ":" + Math.round(e.getRadius()));
                    }
                });
                map.addPopup("pop", hobart, "x");
            }
        });
        fire("view", map("c", Arrays.asList(-42.0, 147.0), "z", 9.5, "b", Arrays.asList(-43.0, 146.0, -41.0, 148.0)));
        assertEquals(9.5, map.getZoom(), 0.0);
        assertEquals(LatLng.of(-42, 147), map.getCenter());
        assertEquals(new LatLngBounds(-43, 146, -41, 148), map.getBounds());
        assertEquals("view:9.5:[-43.0,146.0 .. -41.0,148.0]", log.get(0));

        fire("mapClick", map("id", "hq", "p", Arrays.asList(-42.88, 147.33), "x", 10, "y", 20));
        assertEquals("click:Marker[hq]:-1:-42.880000,147.330000", log.get(1));
        fire("mapClick", map("p", Arrays.asList(-42.0, 147.0)));
        assertEquals("click:null:-1:-42.000000,147.000000", log.get(2));

        fire("drag", map("id", "hq", "p", Arrays.asList(-42.7, 147.2), "dragged", true));
        assertEquals(LatLng.of(-42.7, 147.2), ((Marker) map.getOverlay("hq")).getPosition());
        assertEquals("drag:-42.700000,147.200000", log.get(3));

        fire("draw", map("mode", "circle", "pts", Arrays.asList(Arrays.asList(-42.88, 147.33), Arrays.asList(-42.88, 147.36))));
        assertTrue(log.get(4), log.get(4).startsWith("draw:CIRCLE:2:24"));

        fire("layer", map("layer", "sites", "vis", false));
        assertFalse(map.getLayer("sites").isVisible());

        assertTrue(((Popup) map.getOverlay("pop")).isOpen());
        fire("popupClose", map("popup", "pop"));
        assertFalse(((Popup) map.getOverlay("pop")).isOpen());
    }

    @Test
    public void geometryHelpers() {
        LatLng melbourne = LatLng.of(-37.8136, 144.9631);
        assertEquals(598000, Geo.distance(hobart, melbourne), 5000);
        assertEquals(340, Geo.bearing(hobart, melbourne), 2);   // roughly NNW
        LatLng back = Geo.destination(hobart, Geo.distance(hobart, melbourne), Geo.bearing(hobart, melbourne));
        assertEquals(melbourne.lat, back.lat, 0.01);
        assertEquals(melbourne.lng, back.lng, 0.01);
        double[] px = Geo.project(hobart, 11);
        assertEquals(hobart.lat, Geo.unproject(px[0], px[1], 11).lat, 1e-9);
        assertEquals(hobart.lng, Geo.unproject(px[0], px[1], 11).lng, 1e-9);
        assertTrue(Geo.metresPerPixel(0, 0) > 156000 && Geo.metresPerPixel(0, 0) < 157000);
        LatLngBounds b = new LatLngBounds(-43, 147, -42, 148);
        assertEquals(LatLng.of(-42.5, 147.5), b.getCenter());
        assertTrue(b.contains(hobart));
        assertFalse(b.contains(melbourne));
        assertEquals(new LatLngBounds(-43.1, 146.9, -41.9, 148.1), b.pad(0.1));
        double z = Geo.zoomToFit(b, 800, 600, 0);
        assertTrue(String.valueOf(z), z > 8 && z < 10);
        assertEquals(LatLng.of(10, -170), LatLng.of(10, 190));
        List<LatLng> square = Arrays.asList(LatLng.of(0, 0), LatLng.of(0, 0.01), LatLng.of(0.01, 0.01), LatLng.of(0.01, 0));
        assertEquals(1.236e6, Geo.area(square), 2e4);   // ~1.11 km × 1.11 km
    }

    @Test
    public void resyncResendsEverything() {
        sent.clear();
        connect(-1);
        boolean found = false;
        for (String m : sent) {
            for (Map<String, Object> g : mapCalls(m)) {
                if (Boolean.TRUE.equals(g.get("reset"))) {
                    found = true;
                    assertEquals(2, ((Map<?, ?>) g.get("overlays")).size());
                    assertEquals(1, ((Map<?, ?>) g.get("layers")).size());
                    assertNotNull(g.get("view"));
                }
            }
        }
        assertTrue(found);
        assertSame(map, map.getOverlay("hq").getMap());
    }
}
