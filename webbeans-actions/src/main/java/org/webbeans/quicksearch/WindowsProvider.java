package org.webbeans.quicksearch;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Registration;
import org.webbeans.ui.UI;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/** Finds open windows (TopComponents) of the current UI by name and brings them to the front. */
@Registration(path = QuickSearch.PROVIDERS, type = QuickSearchProvider.class, position = 200)
public final class WindowsProvider implements QuickSearchProvider {

    @Override
    public String getCategory() {
        return Bundles.getMessage(QuickSearch.class, "LBL_OpenWindows");
    }

    @Override
    public void search(String query, Response response) {
        UI ui = UI.current();
        WindowManager wm = ui != null ? ui.getLookup().lookup(WindowManager.class) : null;
        if (wm == null) {
            return;
        }
        for (final TopComponent tc : wm.getOpened()) {
            String name = tc.getDisplayName();
            if (name != null && QuickSearch.matches(name, query)) {
                if (!response.add(name, null, tc.getIcon(), new Runnable() {
                    @Override
                    public void run() {
                        tc.requestActive();
                    }
                })) {
                    return;
                }
            }
        }
    }
}
