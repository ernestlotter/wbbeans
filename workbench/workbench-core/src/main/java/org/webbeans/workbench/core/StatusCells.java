package org.webbeans.workbench.core;

import java.text.DateFormat;
import java.util.Date;
import java.util.function.Consumer;

import org.webbeans.core.i18n.Messages;
import org.webbeans.core.registry.Registration;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Label;

/** Status bar cells: the signed-in user (relabelled on locale changes) and a clock. */
@Messages({"LBL_User=Signed in as {0}"})
public final class StatusCells {

    private StatusCells() {
    }

    @Registration(path = "StatusBar", name = "wb-user", position = 100, type = Component.class)
    public static Component user() {
        final UISession session = UISession.current();
        final Label l = new Label(Bundle.LBL_User(session.getPrincipal().getDisplayName()));
        l.setTestId("status-user");
        session.subscribe(UISession.LocaleChangeEvent.class, new Consumer<UISession.LocaleChangeEvent>() {
            @Override
            public void accept(UISession.LocaleChangeEvent e) {
                l.setText(Bundle.LBL_User(session.getPrincipal().getDisplayName()));
            }
        });
        return l;
    }

    @Registration(path = "StatusBar", name = "wb-clock", position = 200, type = Component.class)
    public static Component clock() {
        final UI ui = UI.current();
        final Label clock = new Label();
        clock.setTestId("status-clock");
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT, ui.getSession().getLocale()).format(new Date()));
            }
        };
        tick.run();
        ui.scheduleAtFixedRate(tick, 10000, 10000);
        return clock;
    }
}
