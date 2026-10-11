package org.webbeans.demo;

import java.util.ArrayList;
import java.util.List;

import org.webbeans.charts.Chart;
import org.webbeans.charts.ChartTopComponent;
import org.webbeans.charts.ChartType;
import org.webbeans.charts.Point;
import org.webbeans.charts.Series;
import org.webbeans.charts.ZoomMode;
import org.webbeans.core.i18n.Messages;
import org.webbeans.windows.TopComponent;

/** A {@link ChartTopComponent}: the chart toolbar (reset zoom, save as SVG/PNG, registered chart actions) plus a horizontal bar chart. */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 60)
@TopComponent.Description(preferredID = "MoonsChartTC", iconBase = "org/webbeans/demo/icons/chart.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenMoonsChart", position = 310)
@Messages({"CTL_MoonsChartTC=Moons chart", "CTL_OpenMoonsChart=Moons chart", "CHART_MoonsHorizontal=Moons per planet (sorted)"})
public final class MoonsChartTopComponent extends ChartTopComponent implements SolarSystem.Listener {

    public MoonsChartTopComponent() {
        super(new Chart(ChartType.BAR));
        setDisplayName(Bundle.CTL_MoonsChartTC());
        getChart().setTitle(Bundle.CHART_MoonsHorizontal()).setHorizontal(true).setLabels(true).setZoom(ZoomMode.X).setTestId("chart-moons-h");
        getChart().getLegend().setVisible(false);
        getChart().getModel().addSeries("moons", "Moons");
    }

    @Override
    protected void componentOpened() {
        SolarSystem.get().addListener(this);
        refresh();
    }

    @Override
    protected void componentClosed() {
        SolarSystem.get().removeListener(this);
    }

    private void refresh() {
        List<Planet> planets = new ArrayList<Planet>(SolarSystem.get().getPlanets());
        java.util.Collections.sort(planets, new java.util.Comparator<Planet>() {
            @Override
            public int compare(Planet a, Planet b) {
                return Integer.compare(b.getMoons(), a.getMoons());
            }
        });
        List<String> names = new ArrayList<String>();
        List<Point> pts = new ArrayList<Point>();
        for (Planet p : planets) {
            names.add(p.getName());
            pts.add(Point.of(p.getName(), p.getMoons()).withColor(DashboardTopComponent.colorOf(p.getType())));
        }
        getChart().getModel().setCategories(names);
        Series s = getChart().getModel().getSeries("moons");
        s.setPoints(pts);
    }

    @Override
    public void changed() {
        if (isOpened()) {
            refresh();
        }
    }

    @Override
    public void planetChanged(Planet p, String property) {
        if (isOpened() && !"explored".equals(property)) {
            refresh();
        }
    }
}
