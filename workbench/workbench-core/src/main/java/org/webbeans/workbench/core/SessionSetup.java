package org.webbeans.workbench.core;

import java.util.Locale;
import java.util.prefs.Preferences;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.options.WbPreferences;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.windows.MainWindow;
import org.webbeans.windows.MainWindowDecorator;

/**
 * Applies the user's saved theme and language when a tab opens (a {@link MainWindowDecorator}, run for every
 * new main window), so Options choices survive logins.
 */
@ServiceProvider(service = MainWindowDecorator.class, position = 5000)
public final class SessionSetup implements MainWindowDecorator {

    @Override
    public void decorate(UI ui, MainWindow window) {
        UISession session = ui.getSession();
        Preferences prefs = WbPreferences.forModule(session, WorkbenchOptions.class);
        String theme = prefs.get(WorkbenchOptions.PREF_THEME, null);
        if (theme != null) {
            ui.getRoot().set(UI.Root.THEME, theme);
        }
        String lang = prefs.get(WorkbenchOptions.PREF_LOCALE, null);
        if (lang != null && !lang.equals(session.getLocale().getLanguage())) {
            session.setLocale(new Locale(lang));
        }
    }
}
