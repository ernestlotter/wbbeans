package org.webbeans.workbench.dashboard;

import java.util.prefs.Preferences;

import org.openide.util.Lookup;
import org.webbeans.core.i18n.Messages;
import org.webbeans.options.OptionsPanelController;
import org.webbeans.options.WbPreferences;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.components.CheckBox;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.TextField;

/** Tools ▸ Options ▸ Dashboard: refresh rate, history window and whether high load raises a notification. */
@OptionsPanelController.TopLevelRegistration(categoryName = "#CTL_DashboardOptions", iconBase = "org/webbeans/workbench/dashboard/icons/chart.svg", position = 200)
@Messages({"CTL_DashboardOptions=Dashboard", "LBL_Refresh=Refresh every (seconds)", "LBL_Window=History (points)", "LBL_Notify=Notify on high load",
    "ERR_Range=Enter a number between {0} and {1}"})
public final class DashboardOptions extends OptionsPanelController {

    static final String PREF_REFRESH = "refreshSeconds";
    static final String PREF_WINDOW = "window";
    static final String PREF_NOTIFY = "notifyHighLoad";

    private final TextField refresh = new TextField();
    private final TextField window = new TextField();
    private final CheckBox notify = new CheckBox();
    private Preferences prefs;

    static Preferences prefs() {
        return WbPreferences.forModule(DashboardOptions.class);
    }

    static Preferences prefs(org.webbeans.ui.UISession session) {
        return WbPreferences.forModule(session, DashboardOptions.class);
    }

    @Override
    public Component getComponent(Lookup master) {
        prefs = prefs();
        refresh.setTestId("opt-refresh");
        window.setTestId("opt-window");
        notify.setTestId("opt-notify");
        refresh.setValidator(new org.webbeans.ui.Validator<String>() {
            @Override
            public String validate(String v) {
                return inRange(v, 1, 60) ? null : Bundle.ERR_Range(1, 60);
            }
        });
        window.setValidator(new org.webbeans.ui.Validator<String>() {
            @Override
            public String validate(String v) {
                return inRange(v, 10, 1000) ? null : Bundle.ERR_Range(10, 1000);
            }
        });
        Listener<ChangeEvent<String>> l = new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                changed();
            }
        };
        refresh.addChangeListener(l);
        window.addChangeListener(l);
        notify.addChangeListener(new Listener<ChangeEvent<Boolean>>() {
            @Override
            public void onEvent(ChangeEvent<Boolean> e) {
                changed();
            }
        });
        Panel p = new Panel(new Layout.Form());
        p.addRow(Bundle.LBL_Refresh(), refresh);
        p.addRow(Bundle.LBL_Window(), window);
        p.addRow(Bundle.LBL_Notify(), notify);
        return p;
    }

    private static boolean inRange(String v, int min, int max) {
        try {
            int n = Integer.parseInt(v == null ? "" : v.trim());
            return n >= min && n <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void update() {
        refresh.setValue(String.valueOf(prefs.getInt(PREF_REFRESH, 1)));
        window.setValue(String.valueOf(prefs.getInt(PREF_WINDOW, 120)));
        notify.setValue(prefs.getBoolean(PREF_NOTIFY, true));
    }

    @Override
    public void applyChanges() {
        if (!isValid()) {
            return;
        }
        prefs.putInt(PREF_REFRESH, Integer.parseInt(refresh.getValue().trim()));
        prefs.putInt(PREF_WINDOW, Integer.parseInt(window.getValue().trim()));
        prefs.putBoolean(PREF_NOTIFY, Boolean.TRUE.equals(notify.getValue()));
        DashboardTopComponent.settingsChanged();
    }

    @Override
    public String getHelpId() {
        return "dashboard.options";
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isValid() {
        return inRange(refresh.getValue(), 1, 60) && inRange(window.getValue(), 10, 1000);
    }

    @Override
    public boolean isChanged() {
        return !String.valueOf(prefs.getInt(PREF_REFRESH, 1)).equals(refresh.getValue())
                || !String.valueOf(prefs.getInt(PREF_WINDOW, 120)).equals(window.getValue())
                || prefs.getBoolean(PREF_NOTIFY, true) != Boolean.TRUE.equals(notify.getValue());
    }
}
