package org.webbeans.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.webbeans.core.i18n.Messages;
import org.webbeans.map.Circle;
import org.webbeans.map.DrawEvent;
import org.webbeans.map.Geo;
import org.webbeans.map.LatLng;
import org.webbeans.map.MapEvent;
import org.webbeans.map.MapTopComponent;
import org.webbeans.map.MapView;
import org.webbeans.map.Marker;
import org.webbeans.map.Overlay;
import org.webbeans.map.TileSource;
import org.webbeans.map.ViewEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UISession;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * A {@link MapTopComponent}: monitoring sites around Hobart as draggable markers, a route, a coverage circle,
 * an exclusion zone, a GeoJSON layer, popups on click, hover and view reporting in the status bar, and the
 * draw tools adding overlays.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 80)
@TopComponent.Description(preferredID = "SitesMapTC", iconBase = "org/webbeans/demo/icons/planet.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenSitesMap", position = 340)
@Messages({"CTL_SitesMapTC=Sites map", "CTL_OpenSitesMap=Sites map", "MSG_MapView=Map at {0} zoom {1}", "MSG_MapClick=Clicked {0}",
    "MSG_MarkerMoved={0} moved to {1}", "MSG_Drawn=Drew a {0}", "MSG_Hover=Over {0}", "POPUP_Site=<b>{0}</b><br>Site {1}<br>{2} m from HQ"})
public final class SitesMapTopComponent extends MapTopComponent {

    private static final LatLng HQ = LatLng.of(-42.8821, 147.3272);
    private int drawn;

    public SitesMapTopComponent() {
        super(new MapView(TileSource.OSM));
        setDisplayName(Bundle.CTL_SitesMapTC());
        final MapView map = getMap();
        map.setTestId("sites-map").setView(HQ, 10);
        map.addLayer("sites", "Sites");
        map.addLayer("zones", "Zones");

        map.addMarker("hq", HQ).setLabel("HQ").setColor("#b43b3b").setTooltip("Head office").setLayer("sites");
        String[][] sites = {{"s1", "Mount Wellington", "-42.896", "147.237"}, {"s2", "Bruny Island", "-43.37", "147.29"}, {"s3", "Richmond", "-42.735", "147.437"}, {"s4", "Huonville", "-43.03", "147.05"}};
        List<LatLng> route = new ArrayList<LatLng>();
        route.add(HQ);
        for (String[] s : sites) {
            LatLng p = LatLng.of(Double.parseDouble(s[2]), Double.parseDouble(s[3]));
            map.addMarker(s[0], p).setLabel(s[1]).setDraggable(true).setLayer("sites").setAttribute("site", s[0]);
            route.add(p);
        }
        map.addPolyline("route", route).setWeight(3).setArrow(true).setColor("#5b6fd6").setTooltip("Maintenance route").setLayer("sites");
        map.addCircle("coverage", HQ, 12000).setColor("#0f7f86").setFillOpacity(0.08).setDash("6 4").setTooltip("12 km coverage").setLayer("zones").setInteractive(false);
        map.addPolygon("exclusion", Arrays.asList(LatLng.of(-43.0, 147.5), LatLng.of(-43.0, 147.7), LatLng.of(-43.15, 147.7), LatLng.of(-43.15, 147.5)))
                .setColor("#b43b3b").setFillColor("#b43b3b").setFillOpacity(0.2).setTooltip("Exclusion zone").setLayer("zones");
        map.addGeoJson("stations", "{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"id\":\"tide-1\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[147.335,-42.885]},\"properties\":{\"name\":\"Tide gauge\",\"style\":{\"c\":\"#2f8a4a\"}}},"
                + "{\"type\":\"Feature\",\"id\":\"line-1\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[147.0,-42.6],[147.2,-42.65],[147.5,-42.6]]},\"properties\":{\"name\":\"Fault trace\",\"style\":{\"c\":\"#c9a227\",\"w\":3,\"dash\":\"2 4\"}}}]}")
                .setPointsAsCircles(true).setLabelProperty("name").setTooltipProperty("name").setLayer("zones");

        map.addMapClickListener(new Listener<MapEvent>() {
            @Override
            public void onEvent(MapEvent e) {
                Overlay o = e.getOverlay();
                if (o instanceof Marker) {
                    Marker m = (Marker) o;
                    map.openPopup(m.getPosition(), Bundle.POPUP_Site(m.getLabel(), m.getId(), Math.round(Geo.distance(HQ, m.getPosition()))));
                } else if (o == null) {
                    map.openPopup(e.getPosition(), Bundle.MSG_MapClick(e.getPosition()));
                }
                status(Bundle.MSG_MapClick(o != null ? o.getId() + (e.getFeature() >= 0 ? "#" + e.getFeatureId() : "") : e.getPosition()));
            }
        });
        map.addDragListener(new Listener<MapEvent>() {
            @Override
            public void onEvent(MapEvent e) {
                Marker m = (Marker) e.getOverlay();
                status(Bundle.MSG_MarkerMoved(m.getLabel(), m.getPosition()));
            }
        });
        map.addViewListener(new Listener<ViewEvent>() {
            @Override
            public void onEvent(ViewEvent e) {
                StatusDisplayer sd = StatusDisplayer.getDefault();
                if (sd != null) {
                    sd.setStatusText(Bundle.MSG_MapView(e.getCenter(), Math.round(e.getZoom() * 10) / 10.0));
                }
            }
        });
        map.addHoverListener(new Listener<MapEvent>() {
            @Override
            public void onEvent(MapEvent e) {
                if (e.getOverlay() != null) {
                    StatusDisplayer sd = StatusDisplayer.getDefault();
                    if (sd != null) {
                        sd.setStatusText(Bundle.MSG_Hover(e.getOverlay().getId()));
                    }
                }
            }
        });
        map.addDrawListener(new Listener<DrawEvent>() {
            @Override
            public void onEvent(DrawEvent e) {
                String id = "drawn" + (++drawn);
                switch (e.getMode()) {
                    case MARKER: map.addMarker(id, e.getPoints().get(0)).setDraggable(true).setColor("#8a63c9").setLayer("sites"); break;
                    case LINE: map.addPolyline(id, e.getPoints()).setColor("#8a63c9").setLayer("zones"); break;
                    case POLYGON: map.addPolygon(id, e.getPoints()).setColor("#8a63c9").setLayer("zones"); break;
                    case RECTANGLE: map.addRectangle(id, e.getBounds()).setColor("#8a63c9").setLayer("zones"); break;
                    case CIRCLE: Circle c = map.addCircle(id, e.getPoints().get(0), e.getRadius()); c.setColor("#8a63c9").setLayer("zones"); break;
                    default: break;
                }
                status(Bundle.MSG_Drawn(e.getMode().name().toLowerCase(java.util.Locale.ROOT)));
            }
        });
    }

    private static void status(String text) {
        StatusDisplayer sd = StatusDisplayer.getDefault();
        if (sd != null) {
            sd.setStatusText(text);
        }
        UISession.current().publish(new LogLine(text));
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_SitesMapTC());
    }
}
