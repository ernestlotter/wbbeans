package org.webbeans.charts;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionPresenters;
import org.webbeans.actions.Actions;
import org.webbeans.actions.Separator;
import org.webbeans.actions.Toolbar;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/**
 * A window around one {@link Chart}: a toolbar (reset zoom, save as SVG/PNG, plus the actions registered under
 * {@code Charts/Actions}, which receive the chart in their context) and the chart filling the rest. The chart
 * is in the window's lookup, so registered actions over {@code Chart} and the global context see it.
 */
public class ChartTopComponent extends TopComponent {

    /** Registry folder of extra toolbar actions; context actions over {@link Chart} work. */
    public static final String ACTIONS_FOLDER = "Charts/Actions";

    private final Chart chart;
    private final Toolbar toolbar = new Toolbar("chart");

    public ChartTopComponent(Chart chart) {
        this.chart = chart;
        setLayout(new Layout.Border());
        associateLookup(Lookups.singleton(chart));
        add(toolbar, Layout.Border.NORTH);
        add(chart, Layout.Border.CENTER);
    }

    public Chart getChart() {
        return chart;
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    @Override
    protected void attached() {
        super.attached();
        if (toolbar.getChildCount() == 0) {
            buildToolbar();
        }
    }

    private void buildToolbar() {
        Lookup ctx = getLookup();
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ResetZoom", "org/webbeans/charts/client/zoom-reset.svg", new Runnable() {
            @Override
            public void run() {
                chart.resetZoom();
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_SaveSvg", "org/webbeans/charts/client/save-svg.svg", new Runnable() {
            @Override
            public void run() {
                chart.download("svg", fileName() + ".svg");
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_SavePng", "org/webbeans/charts/client/save-png.svg", new Runnable() {
            @Override
            public void run() {
                chart.download("png", fileName() + ".png");
            }
        }), ctx));
        boolean first = true;
        for (Action a : Actions.forPath(ACTIONS_FOLDER)) {
            if (a == null) {
                continue;
            }
            if (first) {
                toolbar.add(new Separator());
                first = false;
            }
            toolbar.add(ActionPresenters.toolbarButton(a, ctx));
        }
    }

    private String fileName() {
        String t = chart.get(Chart.TITLE);
        String base = t == null || t.isEmpty() ? getDisplayName() : t;
        return base == null || base.isEmpty() ? "chart" : base.replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    private static Action simple(String key, String icon, final Runnable r) {
        AbstractAction a = new AbstractAction(Bundles.getMessage(ChartTopComponent.class, key), Resource.of(icon)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, Bundles.getMessage(ChartTopComponent.class, key));
        return a;
    }
}
