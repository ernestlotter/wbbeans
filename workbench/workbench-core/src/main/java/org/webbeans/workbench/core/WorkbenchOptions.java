package org.webbeans.workbench.core;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.prefs.Preferences;

import org.openide.util.Lookup;
import org.webbeans.core.i18n.Messages;
import org.webbeans.options.OptionsPanelController;
import org.webbeans.options.WbPreferences;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.ComboBox;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/**
 * Tools ▸ Options ▸ Workbench: theme and language. Both apply immediately on OK/Apply — the theme is a property
 * of the page root, the language relabels every window through the session's locale — and are remembered per
 * user in {@link WbPreferences}; {@link WorkbenchInstaller} re-applies them at the next login.
 */
@OptionsPanelController.TopLevelRegistration(categoryName = "#CTL_WorkbenchOptions", iconBase = "org/webbeans/workbench/core/icons/workbench.svg", position = 100)
@Messages({"CTL_WorkbenchOptions=Workbench", "LBL_Theme=Theme", "LBL_Language=Language",
    "THEME_system=Follow the system", "THEME_light=Light", "THEME_dark=Dark", "LANG_en=English", "LANG_de=Deutsch"})
public final class WorkbenchOptions extends OptionsPanelController {

    static final String PREF_THEME = "theme";
    static final String PREF_LOCALE = "locale";

    private final ComboBox<String> theme = new ComboBox<String>();
    private final ComboBox<String> language = new ComboBox<String>();
    private Preferences prefs;

    @Override
    public Component getComponent(Lookup master) {
        prefs = WbPreferences.forModule(WorkbenchOptions.class);
        theme.setItems(Arrays.asList("system", "light", "dark"), new Function<String, String>() {
            @Override
            public String apply(String t) {
                return org.webbeans.core.i18n.Bundles.getMessage(WorkbenchOptions.class, "THEME_" + t);
            }
        }).setAllowEmpty(false).setTestId("opt-theme");
        language.setItems(Arrays.asList("en", "de"), new Function<String, String>() {
            @Override
            public String apply(String l) {
                return org.webbeans.core.i18n.Bundles.getMessage(WorkbenchOptions.class, "LANG_" + l);
            }
        }).setAllowEmpty(false).setTestId("opt-language");
        Listener<ChangeEvent<String>> onChange = new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                changed();
            }
        };
        theme.addChangeListener(onChange);
        language.addChangeListener(onChange);
        Panel p = new Panel(new Layout.Form());
        p.addRow(Bundle.LBL_Theme(), theme);
        p.addRow(Bundle.LBL_Language(), language);
        return p;
    }

    @Override
    public void update() {
        theme.setValue(prefs.get(PREF_THEME, "system"));
        language.setValue(prefs.get(PREF_LOCALE, UISession.current().getLocale().getLanguage()));
    }

    @Override
    public void applyChanges() {
        String t = theme.getValue() == null ? "system" : theme.getValue();
        String l = language.getValue() == null ? "en" : language.getValue();
        prefs.put(PREF_THEME, t);
        prefs.put(PREF_LOCALE, l);
        apply(UISession.current(), t, l);
    }

    /** Applies theme and locale to every tab of the session. */
    static void apply(UISession session, String theme, String language) {
        for (UI ui : session.getUIs()) {
            ui.getRoot().set(UI.Root.THEME, theme);
        }
        session.setLocale(new Locale(language));
    }

    @Override
    public String getHelpId() {
        return "workbench.options";
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
        return !prefs.get(PREF_THEME, "system").equals(theme.getValue()) || !prefs.get(PREF_LOCALE, "en").equals(language.getValue());
    }
}
