package org.webbeans.demo;

import org.openide.util.Lookup;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.actions.Actions;
import org.webbeans.actions.MenuItem;
import org.webbeans.actions.Presenter;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.Messages;
import org.webbeans.options.WbPreferences;
import org.webbeans.ui.Component;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/**
 * View ▸ Events window: a check item backed by a user preference ({@link Actions#checkbox}) that opens or closes
 * the Events window; an {@link Action} class registered directly and providing its own menu presenter.
 */
@ActionID(category = "View", id = "org.webbeans.demo.ShowEventsAction")
@ActionRegistration(displayName = "#CTL_ShowEvents", lazy = false)
@ActionReference(path = "Menu/View", position = 200)
@Messages("CTL_ShowEvents=Events window")
public final class ShowEventsAction extends AbstractAction implements Presenter.Menu {

    public static final String PREF_KEY = "eventsWindow";

    public ShowEventsAction() {
        super(Bundles.getMessage(ShowEventsAction.class, "CTL_ShowEvents"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }

    @Override
    public Component getMenuPresenter() {
        Action checkbox = Actions.checkbox(ShowEventsAction.class, PREF_KEY, true, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean show = WbPreferences.forModule(ShowEventsAction.class).getBoolean(PREF_KEY, true);
                WindowManager wm = WindowManager.getDefault();
                TopComponent tc = wm == null ? null : wm.findTopComponent("EventsTC");
                if (tc != null) {
                    if (show) {
                        tc.requestActive();
                    } else if (tc.isOpened()) {
                        tc.close();
                    }
                }
            }
        }, Bundles.getMessage(ShowEventsAction.class, "CTL_ShowEvents"));
        MenuItem item = new MenuItem().setAction(checkbox, Lookup.EMPTY, false);
        item.setTestId("menu-events-window");
        return item;
    }
}
