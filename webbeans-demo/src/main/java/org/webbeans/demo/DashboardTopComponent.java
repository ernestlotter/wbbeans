package org.webbeans.demo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.webbeans.charts.AxisType;
import org.webbeans.charts.Chart;
import org.webbeans.charts.ChartType;
import org.webbeans.charts.Legend;
import org.webbeans.charts.Marker;
import org.webbeans.charts.Point;
import org.webbeans.charts.PointEvent;
import org.webbeans.charts.RangeEvent;
import org.webbeans.charts.Series;
import org.webbeans.charts.TooltipMode;
import org.webbeans.charts.ZoomMode;
import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * Four charts over the solar system: a live time series (one append per second, sliding window), bars per
 * planet that follow the model, a donut of planet types, and a zoomable bubble scatter. Shows the data path:
 * server-side model → one {@code data} delta per flush → SVG.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 50)
@TopComponent.Description(preferredID = "DashboardTC", iconBase = "org/webbeans/demo/icons/chart.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenDashboard", position = 300)
@Messages({"CTL_DashboardTC=Dashboard", "CTL_OpenDashboard=Dashboard", "CHART_Wind=Solar wind", "CHART_WindSub=live, one sample per second",
    "CHART_Moons=Moons per planet", "CHART_Types=Planet types", "CHART_Gravity=Gravity vs. moons", "AXIS_Speed=km/s", "AXIS_Gravity=Gravity (m/s²)",
    "AXIS_Moons=Moons", "SER_Speed=Speed", "SER_Density=Density", "SER_Moons=Moons", "MSG_BarClicked={0}: {1} moons", "MSG_Range=Zoomed to gravity {0}–{1}",
    "MSG_RangeReset=Zoom reset", "LBL_Storm=storm"})
public final class DashboardTopComponent extends TopComponent implements SolarSystem.Listener {

    private final Chart wind = new Chart(ChartType.LINE);
    private final Chart moons = new Chart(ChartType.BAR);
    private final Chart types = new Chart(ChartType.DONUT);
    private final Chart gravity = new Chart(ChartType.SCATTER);
    private final Random random = new Random(7);
    private double speed = 400;
    private UI.Timer timer;
    private int ticks;

    public DashboardTopComponent() {
        setDisplayName(Bundle.CTL_DashboardTC());
        setLayout(new Layout.Grid(2, 2, 8, 8).fill());
        setPadding(8);

        wind.setTitle(Bundle.CHART_Wind()).setSubtitle(Bundle.CHART_WindSub()).setTooltip(TooltipMode.SHARED).setTestId("chart-wind");
        wind.getXAxis().setType(AxisType.TIME).setFormat("HH:mm:ss");
        wind.getYAxis().setTitle(Bundle.AXIS_Speed()).setMin(250.0).setMax(800.0).setFormat("#,##0");
        wind.getYAxis().addBand("storm", 650, 800, "#b43b3b").addMarker("avg", 400, Bundle.LBL_Storm(), "#b8741c");
        wind.getLegend().setPosition(Legend.Position.TOP);
        Series s = wind.getModel().addSeries("speed", Bundle.SER_Speed());
        s.setWindow(60).setSmooth(true);
        Series d = wind.getModel().addSeries("density", Bundle.SER_Density());
        d.setYAxis(1).setWindow(60).setType(ChartType.AREA);
        wind.getY2Axis().setMin(0.0).setMax(20.0).setFormat("0.0");

        moons.setTitle(Bundle.CHART_Moons()).setLabels(true).setTestId("chart-moons").setTooltip(TooltipMode.POINT);
        moons.getYAxis().setFormat("#0").setTitle(Bundle.AXIS_Moons());
        moons.getLegend().setVisible(false);
        moons.addPointClickListener(new Listener<PointEvent>() {
            @Override
            public void onEvent(PointEvent e) {
                Point p = e.getPoint();
                if (p != null) {
                    String msg = Bundle.MSG_BarClicked(p.getCategory(), (int) p.getY());
                    StatusDisplayer.getDefault().setStatusText(msg);
                    UISession.current().publish(new LogLine(msg));
                }
            }
        });

        types.setTitle(Bundle.CHART_Types()).setTestId("chart-types");
        types.getLegend().setPosition(Legend.Position.RIGHT);

        gravity.setTitle(Bundle.CHART_Gravity()).setZoom(ZoomMode.XY).setTestId("chart-gravity").setTooltip(TooltipMode.POINT);
        gravity.getXAxis().setTitle(Bundle.AXIS_Gravity()).setFormat("0.0");
        gravity.getYAxis().setTitle(Bundle.AXIS_Moons()).setType(AxisType.LOG).setFormat("#0");
        gravity.getLegend().setVisible(false);
        gravity.addRangeSelectListener(new Listener<RangeEvent>() {
            @Override
            public void onEvent(RangeEvent e) {
                String msg = e.isReset() || Double.isNaN(e.getXFrom()) ? Bundle.MSG_RangeReset()
                        : Bundle.MSG_Range(String.format("%.1f", e.getXFrom()), String.format("%.1f", e.getXTo()));
                StatusDisplayer.getDefault().setStatusText(msg);
            }
        });

        add(wind, Layout.Grid.cell(0, 0, 1, 1));
        add(moons, Layout.Grid.cell(0, 1, 1, 1));
        add(types, Layout.Grid.cell(1, 0, 1, 1));
        add(gravity, Layout.Grid.cell(1, 1, 1, 1));
    }

    @Override
    protected void componentOpened() {
        SolarSystem.get().addListener(this);
        refreshPlanets();
        for (int i = 0; i < 20; i++) {
            sample(Instant.now().minusSeconds(20 - i));
        }
        timer = getUI().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sample(Instant.now());
            }
        }, 1000, 1000);
    }

    @Override
    protected void componentClosed() {
        SolarSystem.get().removeListener(this);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void sample(Instant t) {
        ticks++;
        speed = Math.max(260, Math.min(790, speed + random.nextGaussian() * 25 + (speed > 550 ? -8 : 3)));
        wind.getModel().getSeries("speed").addPoint(Point.of(t, Math.round(speed * 10) / 10.0));
        wind.getModel().getSeries("density").addPoint(Point.of(t, Math.round((4 + 3 * Math.sin(ticks / 7.0) + random.nextDouble()) * 10) / 10.0));
    }

    /** Rebuilds the planet charts from the model (also on every model change). */
    private void refreshPlanets() {
        List<Planet> planets = SolarSystem.get().getPlanets();
        List<String> names = new ArrayList<String>();
        List<Point> moonPoints = new ArrayList<Point>();
        List<Point> gravityPoints = new ArrayList<Point>();
        Map<Planet.Type, Integer> byType = new LinkedHashMap<Planet.Type, Integer>();
        for (Planet p : planets) {
            names.add(p.getName());
            moonPoints.add(Point.of(p.getName(), p.getMoons()).withKey(p.getName()).withColor(colorOf(p.getType())));
            gravityPoints.add(Point.of(p.getGravity(), Math.max(1, p.getMoons())).withKey(p.getName()).withLabel(p.getName()).withSize(Math.max(1, p.getMoons())));
            Integer n = byType.get(p.getType());
            byType.put(p.getType(), n == null ? 1 : n + 1);
        }
        moons.getModel().setCategories(names);
        Series m = moons.getModel().getSeries("moons");
        if (m == null) {
            m = moons.getModel().addSeries("moons", Bundle.SER_Moons());
        }
        m.setPoints(moonPoints);
        Series g = gravity.getModel().getSeries("planets");
        if (g == null) {
            g = gravity.getModel().addSeries("planets", Bundle.SER_Moons()).setMarker(Marker.CIRCLE);
        }
        g.setPoints(gravityPoints);
        List<Point> slices = new ArrayList<Point>();
        for (Map.Entry<Planet.Type, Integer> e : byType.entrySet()) {
            slices.add(Point.of(e.getKey().name(), e.getValue()).withColor(colorOf(e.getKey())));
        }
        Series t = types.getModel().getSeries("types");
        if (t == null) {
            t = types.getModel().addSeries("types", Bundle.CHART_Types());
        }
        t.setPoints(slices);
    }

    static String colorOf(Planet.Type type) {
        switch (type) {
            case GAS_GIANT:
                return "#d9822b";
            case ICE_GIANT:
                return "#5b6fd6";
            case DWARF:
                return "#8a63c9";
            default:
                return "#0f7f86";
        }
    }

    @Override
    public void changed() {
        if (isOpened() && UISession.current() != null) {
            refreshPlanets();
        }
    }

    @Override
    public void planetChanged(Planet p, String property) {
        if (isOpened() && UISession.current() != null && ("moons".equals(property) || "gravity".equals(property) || "type".equals(property) || "name".equals(property))) {
            refreshPlanets();
        }
    }

    /** For the chart actions demo. */
    public Chart getWindChart() {
        return wind;
    }

}
