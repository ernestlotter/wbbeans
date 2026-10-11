package org.webbeans.tutorial;

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

/** Tutorial §8: Tools ▸ Options ▸ Tutorial — one per-user setting. */
@OptionsPanelController.TopLevelRegistration(categoryName = "#CTL_TutorialOptions", position = 900)
@Messages({"CTL_TutorialOptions=Tutorial", "LBL_HideDone=Hide finished tasks"})
public final class TutorialOptions extends OptionsPanelController {

    static final String PREF_HIDE_DONE = "hideDone";

    private final CheckBox hideDone = new CheckBox(Bundle.LBL_HideDone());
    private Preferences prefs;

    @Override
    public Component getComponent(Lookup master) {
        prefs = WbPreferences.forModule(TutorialOptions.class);
        hideDone.setTestId("opt-hide-done");
        hideDone.addChangeListener(new Listener<ChangeEvent<Boolean>>() {
            @Override
            public void onEvent(ChangeEvent<Boolean> e) {
                changed();                                   // lets the dialog enable Apply
            }
        });
        Panel p = new Panel(new Layout.Form());
        p.addRow("", hideDone);
        return p;
    }

    @Override
    public void update() {
        hideDone.setSelected(prefs.getBoolean(PREF_HIDE_DONE, false));
    }

    @Override
    public void applyChanges() {
        prefs.putBoolean(PREF_HIDE_DONE, hideDone.isSelected());
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return prefs.getBoolean(PREF_HIDE_DONE, false) != hideDone.isSelected();
    }
}
