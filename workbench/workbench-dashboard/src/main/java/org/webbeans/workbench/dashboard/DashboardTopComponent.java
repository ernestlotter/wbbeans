package org.webbeans.workbench.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.webbeans.charts.AxisType;
import org.webbeans.charts.Chart;
import org.webbeans.charts.ChartType;
import org.webbeans.charts.Point;
import org.webbeans.charts.PointEvent;
import org.webbeans.charts.Series;
import org.webbeans.charts.TooltipMode;
import org.webbeans.charts.ZoomMode;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;
import org.webbeans.workbench.core.api.Log;

/**
 * The Dashboard: three charts fed by the {@link MetricsSampler} on a {@code UI.Timer} — a CPU line with a
 * sliding window and a "hot" band, requests per path as bars, traffic by source as a donut. Clicking a chart
 * puts it into the window's lookup, so the actions registered over {@code Chart} (export) apply to it. CPU above
 * 90 % raises one notification per minute in every tab of the user.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 200)
@TopComponent.Description(preferredID = "DashboardTC", iconBase = "org/webbeans/workbench/dashboard/icons/chart.svg", helpId = "dashboard")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenDashboard", position = 200)
@Messages({"CTL_DashboardModule=Workbench Dashboard", "CTL_DashboardTC=Dashboard", "CTL_OpenDashboard=Dashboard", "CHART_Cpu=CPU %",
    "CHART_Requests=Requests / s", "CHART_Traffic=Traffic by source", "SER_Cpu=CPU", "SER_Requests=Requests", "SER_Traffic=Share",
    "MSG_HighLoad=High load", "MSG_HighLoadDetails=CPU at {0} %", "MSG_Clicked={0}: {1}", "BAND_Hot=hot"})
public final class DashboardTopComponent extends TopComponent {

    private static final List<DashboardTopComponent> OPEN = Collections.synchronizedList(new ArrayList<DashboardTopComponent>());

    private final Chart cpu = new Chart(ChartType.LINE);
    private final Chart requests = new Chart(ChartType.BAR);
    private final Chart traffic = new Chart(ChartType.DONUT);
    private final InstanceContent content = new InstanceContent();
    private UI.Timer timer;
    private long lastNotified;

    public DashboardTopComponent() {
        setDisplayName(Bundle.CTL_DashboardTC());
        associateLookup(new AbstractLookup(content));
        cpu.setTitle(Bundle.CHART_Cpu()).setTooltip(TooltipMode.SHARED).setZoom(ZoomMode.X).setTestId("chart-cpu");
        cpu.getXAxis().setType(AxisType.TIME).setFormat("HH:mm:ss");
        cpu.getYAxis().setMin(0.0).setMax(100.0).setFormat("#0'%'").addBand(Bundle.BAND_Hot(), 90, 100, "#b43b3b");
        cpu.getLegend().setVisible(false);
        cpu.getModel().addSeries("cpu", Bundle.SER_Cpu());
        requests.setTitle(Bundle.CHART_Requests()).setLabels(true).setTestId("chart-requests");
        requests.getLegend().setVisible(false);
        requests.getModel().setCategories(Arrays.asList("/api", "/static", "/ws", "/auth"));
        requests.getModel().addSeries("req", Bundle.SER_Requests());
        traffic.setTitle(Bundle.CHART_Traffic()).setTestId("chart-traffic");
        traffic.getModel().addSeries("share", Bundle.SER_Traffic());
        Listener<PointEvent> click = new Listener<PointEvent>() {
            @Override
            public void onEvent(PointEvent e) {
                Chart c = (Chart) e.getSource();
                content.set(Collections.singleton(c), null);
                Point p = e.getPoint();
                StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Clicked(c.get(Chart.TITLE), p == null ? "" : p.getLabel() != null ? p.getLabel() : String.valueOf(p.getY())));
            }
        };
        cpu.addPointClickListener(click);
        requests.addPointClickListener(click);
        traffic.addPointClickListener(click);
        Panel grid = new Panel(new Layout.Grid(2, 2, 8, 8).fill());
        grid.setPadding(8);
        grid.add(cpu);
        grid.add(requests);
        grid.add(traffic);
        setLayout(new Layout.Border());
        add(grid, Layout.Border.CENTER);
    }

    /** The chart a registered action should work on (the last clicked, else the CPU chart). */
    public Chart getSelectedChart() {
        Chart c = getLookup().lookup(Chart.class);
        return c != null ? c : cpu;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_DashboardTC());
    }

    @Override
    protected void componentOpened() {
        OPEN.add(this);
        content.set(Collections.singleton(cpu), null);
        start();
    }

    @Override
    protected void componentClosed() {
        OPEN.remove(this);
        stop();
    }

    private void start() {
        stop();
        Preferences prefs = DashboardOptions.prefs();
        final int seconds = Math.max(1, prefs.getInt(DashboardOptions.PREF_REFRESH, 1));
        cpu.getModel().getSeries("cpu").setWindow(Math.max(10, prefs.getInt(DashboardOptions.PREF_WINDOW, 120)));
        timer = getUI().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sample();
            }
        }, 0, seconds * 1000L);
    }

    private void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void sample() {
        MetricsSampler s = Lookup.getDefault().lookup(MetricsSampler.class);
        if (s == null) {
            return;
        }
        double load = s.cpu();
        cpu.getModel().getSeries("cpu").addPoint(Point.of(Instant.now(), load));
        List<Point> req = new ArrayList<Point>();
        for (Map.Entry<String, Double> e : s.requestsByPath().entrySet()) {
            req.add(Point.of(e.getKey(), Math.round(e.getValue())).withKey(e.getKey()));
        }
        requests.getModel().getSeries("req").setPoints(req);
        List<Point> share = new ArrayList<Point>();
        for (Map.Entry<String, Double> e : s.trafficBySource().entrySet()) {
            share.add(Point.of(e.getKey(), Math.round(e.getValue())).withKey(e.getKey()).withLabel(e.getKey()));
        }
        traffic.getModel().getSeries("share").setPoints(share);
        if (load > 90 && System.currentTimeMillis() - lastNotified > 60000 && DashboardOptions.prefs().getBoolean(DashboardOptions.PREF_NOTIFY, true)) {
            lastNotified = System.currentTimeMillis();
            NotificationDisplayer.getDefault().notify(Bundle.MSG_HighLoad(), Bundle.MSG_HighLoadDetails(String.valueOf(load)),
                    NotificationDisplayer.Category.WARNING);
            Log.warn("dashboard", Bundle.MSG_HighLoadDetails(String.valueOf(load)));
        }
    }

    /** Options changed: every open dashboard of the JVM re-reads them on its own executor. */
    static void settingsChanged() {
        for (final DashboardTopComponent d : new ArrayList<DashboardTopComponent>(OPEN)) {
            if (d.getUI() != null) {
                d.getUI().access(new Runnable() {
                    @Override
                    public void run() {
                        if (d.isOpened()) {
                            d.start();
                        }
                    }
                });
            }
        }
    }

    Series cpuSeries() {
        return cpu.getModel().getSeries("cpu");
    }
}
