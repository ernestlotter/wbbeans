package org.webbeans.workbench.dashboard;

import java.util.function.BiConsumer;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.charts.Chart;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.UI;
import org.webbeans.workbench.core.api.Log;

/**
 * Export: a context action over {@link Chart} (the dashboard puts the clicked chart into its lookup). Saves the
 * chart as a PNG in the browser and renders a copy on the server side of the round trip to report its size.
 */
@ActionID(category = "Dashboard", id = "org.webbeans.workbench.dashboard.ExportChartAction")
@ActionRegistration(displayName = "#CTL_Export", iconBase = "org/webbeans/workbench/dashboard/icons/export.svg")
@ActionReferences({
    @ActionReference(path = "Menu/Tools", position = 200),
    @ActionReference(path = "Toolbars/Dashboard", position = 100)})
@Messages({"CTL_Export=Export chart as PNG", "MSG_Exported=Exported {0} ({1} bytes)", "MSG_ExportFailed=Export failed: {0}", "CTL_DashboardToolbar=Dashboard"})
@org.webbeans.core.registry.FolderRegistration(path = "Toolbars/Dashboard", displayName = "#CTL_DashboardToolbar", position = 300)
public final class ExportChartAction implements ActionListener {

    private final Chart chart;

    public ExportChartAction(Chart chart) {
        this.chart = chart;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final String title = chart.get(Chart.TITLE) == null ? "chart" : chart.get(Chart.TITLE);
        final UI ui = e.getUI();
        chart.download("png", title.replaceAll("[^A-Za-z0-9]+", "_") + ".png");
        chart.exportPng(1200, 700).whenComplete(new BiConsumer<byte[], Throwable>() {
            @Override
            public void accept(byte[] png, Throwable error) {
                String msg = error == null ? Bundle.MSG_Exported(title, png.length) : Bundle.MSG_ExportFailed(String.valueOf(error.getMessage()));
                NotificationDisplayer.getDefault(ui.getSession()).notify(Bundle.CTL_Export(), msg,
                        error == null ? NotificationDisplayer.Category.INFO : NotificationDisplayer.Category.ERROR);
                Log.info("dashboard", msg);
            }
        });
    }
}
