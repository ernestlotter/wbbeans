package org.webbeans.workbench.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.webbeans.charts.Chart;
import org.webbeans.test.HeadlessUI;

/** The dashboard's timer feeds the charts with small deltas; the options change the refresh rate and window. */
public class DashboardTest {

    @Test
    public void timerAppendsPointsAsDeltas() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            DashboardTopComponent d = (DashboardTopComponent) h.open("DashboardTC");
            Thread.sleep(2300);
            assertTrue("samples arrived", d.cpuSeries().getPointCount() >= 2);
            Chart cpu = (Chart) h.byTestId("chart-cpu");
            assertNotNull(cpu);
            List<Map<String, Object>> calls = h.callsOf(cpu, "data");
            assertTrue(calls.size() >= 2);
            String last = String.valueOf(calls.get(calls.size() - 1).get("args"));
            assertTrue("appends, not re-sends: " + last, last.contains("append") && !last.contains("reset"));
            assertEquals("the clicked chart is in the lookup (the cpu chart by default)", cpu, d.getSelectedChart());
            Chart requests = (Chart) h.byTestId("chart-requests");
            h.inject(requests, "pointClick", HeadlessUI.map("series", "req", "k", "/api", "index", 0));
            assertEquals(requests, d.getSelectedChart());
        }
    }

    @Test
    public void optionsDialogValidatesAndStoresTheRefreshRate() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            final DashboardTopComponent d = (DashboardTopComponent) h.open("DashboardTC");
            h.run(new Runnable() {
                @Override
                public void run() {
                    org.webbeans.options.OptionsDisplayer.getDefault().open("DashboardOptions");
                }
            });
            assertTrue(h.hasDialog());
            org.webbeans.ui.components.TextField refresh = (org.webbeans.ui.components.TextField) h.byTestId("opt-refresh");
            assertEquals("1", refresh.getValue());
            h.type(refresh, "abc");
            assertNotNull("inline error", refresh.get(org.webbeans.ui.components.TextField.ERROR));
            org.webbeans.ui.components.Button ok = (org.webbeans.ui.components.Button) h.byTestId("dialog-ok");
            assertEquals("OK disabled while invalid", Boolean.FALSE, ok.get(org.webbeans.ui.components.Button.ENABLED));
            h.type(refresh, "5");
            assertEquals(Boolean.TRUE, ok.get(org.webbeans.ui.components.Button.ENABLED));
            h.type("opt-window", "50");
            h.answerDialog("dialog-ok");
            assertEquals(5, DashboardOptions.prefs(h.session()).getInt(DashboardOptions.PREF_REFRESH, -1));
            assertEquals("the open dashboard re-read the window", 50, d.cpuSeries().getWindow());
        }
    }
}
