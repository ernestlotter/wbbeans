package org.webbeans.options;

import java.util.concurrent.CompletableFuture;

import org.openide.util.Lookup;
import org.webbeans.ui.UI;

/**
 * Opens the Options dialog of the current tab. The categories are the {@link OptionsPanelController}s registered
 * under {@code OptionsDialog/} (role-filtered), shown as a strip on the left with the selected controller's
 * component on the right; OK applies and closes, Apply applies, Cancel discards.
 */
public abstract class OptionsDisplayer {

    public static final String FOLDER = "OptionsDialog";

    public static OptionsDisplayer getDefault() {
        OptionsDisplayer d = Lookup.getDefault().lookup(OptionsDisplayer.class);
        return d != null ? d : DefaultOptionsDisplayer.INSTANCE;
    }

    /** Opens the dialog on the first category; completes with true if OK was pressed. */
    public CompletableFuture<Boolean> open() {
        return open(null);
    }

    /** Opens the dialog on the given category id (the entry name under {@code OptionsDialog/}). */
    public abstract CompletableFuture<Boolean> open(String categoryId);

    /** The UI the dialog opens in. */
    protected static UI currentUI() {
        return UI.current();
    }
}
