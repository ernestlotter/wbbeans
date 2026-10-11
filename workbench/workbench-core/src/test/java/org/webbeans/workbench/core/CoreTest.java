package org.webbeans.workbench.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;
import org.webbeans.actions.Actions;
import org.webbeans.options.OptionsDisplayer;
import org.webbeans.test.HeadlessUI;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.ComboBox;
import org.webbeans.ui.components.TextArea;
import org.webbeans.workbench.core.api.Log;

/** The Output window, the status cells, and the Options dialog applying theme and language. */
public class CoreTest {

    @Test
    public void outputShowsLogMessagesFromAnyTab() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            h.open("OutputTC");
            final UI other = h.openTab("u2");
            HeadlessUI.await(other.access(new Runnable() {
                @Override
                public void run() {
                    Log.info("test", "hello from tab 2");
                }
            }));
            TextArea out = (TextArea) h.byTestId("output");
            assertTrue(out.getValue(), out.getValue().contains("[test] hello from tab 2"));
            assertEquals("Signed in as alice", ((org.webbeans.ui.components.Label) h.byTestId("status-user")).get(org.webbeans.ui.components.Label.TEXT));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void optionsDialogAppliesThemeAndLocale() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            assertNotNull("Tools ▸ Options registered", Actions.forID("Tools", "org.webbeans.options.OptionsAction"));
            h.run(new Runnable() {
                @Override
                public void run() {
                    OptionsDisplayer.getDefault().open("WorkbenchOptions");
                }
            });
            assertTrue(h.hasDialog());
            ComboBox<String> theme = (ComboBox<String>) h.byTestId("opt-theme");
            ComboBox<String> lang = (ComboBox<String>) h.byTestId("opt-language");
            h.type(theme, "dark");
            h.type(lang, "de");
            h.answerDialog("dialog-ok");
            assertEquals("dark", h.ui().getRoot().get(UI.Root.THEME));
            assertEquals(Locale.GERMAN, h.session().getLocale());
            assertEquals("relabelled", "Angemeldet als alice", ((org.webbeans.ui.components.Label) h.byTestId("status-user")).get(org.webbeans.ui.components.Label.TEXT));
            assertEquals("dark", org.webbeans.options.WbPreferences.forModule(h.session(), WorkbenchOptions.class).get("theme", null));
        }
    }
}
