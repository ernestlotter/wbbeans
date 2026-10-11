package org.webbeans.demo;

import java.util.function.BiConsumer;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.charts.Chart;
import org.webbeans.charts.ChartTopComponent;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.UISession;

/**
 * A context action over {@link Chart} (the chart window puts its chart in the lookup): renders the chart to
 * PNG in the browser and reports the size — the export round trip through {@code Chart.exportPng}.
 */
@ActionID(category = "Charts", id = "org.webbeans.demo.ExportChartAction")
@ActionRegistration(displayName = "#CTL_ExportChart", iconBase = "org/webbeans/demo/icons/info.svg")
@ActionReferences({
    @ActionReference(path = ChartTopComponent.ACTIONS_FOLDER, position = 100),
    @ActionReference(path = "Menu/Planets", position = 500, separatorBefore = 450)})
@Messages({"CTL_ExportChart=Measure PNG export", "MSG_Exported=PNG export: {0} bytes, {1} characters of SVG", "MSG_ExportFailed=Export failed: {0}"})
public final class ExportChartAction implements ActionListener {

    private final Chart chart;

    public ExportChartAction(Chart chart) {
        this.chart = chart;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final UISession session = e.getUI().getSession();
        chart.exportPng(800, 500).thenCombine(chart.exportSvg(), new java.util.function.BiFunction<byte[], String, String>() {
            @Override
            public String apply(byte[] png, String svg) {
                return Bundle.MSG_Exported(png.length, svg.length());
            }
        }).whenComplete(new BiConsumer<String, Throwable>() {
            @Override
            public void accept(String msg, Throwable error) {
                String text = error != null ? Bundle.MSG_ExportFailed(String.valueOf(error.getMessage())) : msg;
                NotificationDisplayer.getDefault(session).notify(text, null, error != null ? NotificationDisplayer.Category.ERROR : NotificationDisplayer.Category.INFO);
                session.publish(new LogLine(text));
            }
        });
    }
}
