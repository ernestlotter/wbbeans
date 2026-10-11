package org.webbeans.demo;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

import org.webbeans.core.i18n.Messages;
import org.webbeans.core.registry.Registration;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/**
 * Status bar cells (registry folder {@code StatusBar}, one instance per UI): the signed-in user, a clock driven
 * by a server timer, the locale switch and sign-out.
 */
@Messages({"LBL_Welcome=Signed in as {0}", "BTN_German=Deutsch", "BTN_English=English", "BTN_Logout=Sign out"})
public final class DemoStatusCells {

    private DemoStatusCells() {
    }

    @Registration(path = "StatusBar", name = "user", position = 100, type = Component.class)
    public static Component user() {
        final UISession session = UISession.current();
        final Label welcome = new Label(Bundle.LBL_Welcome(session.getPrincipal().getDisplayName()));
        welcome.setTestId("welcome");
        session.subscribe(UISession.LocaleChangeEvent.class, new Consumer<UISession.LocaleChangeEvent>() {
            @Override
            public void accept(UISession.LocaleChangeEvent e) {
                welcome.setText(Bundle.LBL_Welcome(session.getPrincipal().getDisplayName()));
            }
        });
        return welcome;
    }

    @Registration(path = "StatusBar", name = "clock", position = 200, type = Component.class)
    public static Component clock() {
        final UI ui = UI.current();
        final Label clock = new Label();
        clock.setTestId("clock");
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                clock.setText(DateFormat.getTimeInstance(DateFormat.MEDIUM, ui.getSession().getLocale()).format(new Date()));
            }
        };
        tick.run();
        ui.scheduleAtFixedRate(tick, 1000, 1000);
        return clock;
    }

    @Registration(path = "StatusBar", name = "locale", position = 300, type = Component.class)
    public static Component locale() {
        final UISession session = UISession.current();
        Panel p = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 2));
        Button de = new Button(Bundle.BTN_German());
        de.setKind(Button.Kind.LINK).setTestId("lang-de");
        de.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                session.setLocale(Locale.GERMAN);
            }
        });
        Button en = new Button(Bundle.BTN_English());
        en.setKind(Button.Kind.LINK).setTestId("lang-en");
        en.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                session.setLocale(Locale.ENGLISH);
            }
        });
        p.add(de);
        p.add(en);
        return p;
    }

    @Registration(path = "StatusBar", name = "logout", position = 400, type = Component.class)
    public static Component logout() {
        final UI ui = UI.current();
        final Button logout = new Button(Bundle.BTN_Logout());
        logout.setKind(Button.Kind.LINK).setTestId("logout");
        logout.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                ui.logout();
            }
        });
        ui.getSession().subscribe(UISession.LocaleChangeEvent.class, new Consumer<UISession.LocaleChangeEvent>() {
            @Override
            public void accept(UISession.LocaleChangeEvent e) {
                logout.setText(Bundle.BTN_Logout());
            }
        });
        return logout;
    }
}
