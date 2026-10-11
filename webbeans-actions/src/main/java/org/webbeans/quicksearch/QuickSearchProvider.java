package org.webbeans.quicksearch;

import org.webbeans.ui.Resource;

/**
 * Supplies results to the Quick Search box (Ctrl+I). Register with
 * {@code @Registration(path = "QuickSearch/Providers", type = QuickSearchProvider.class)}; the platform ships
 * providers for actions (menu items), open windows and help topics. {@link #search} runs on the session
 * executor for every keystroke (debounced), so keep it quick; return at most a dozen results.
 */
public interface QuickSearchProvider {

    /** Collects results. */
    interface Response {
        /** @return false when the response is full and the provider should stop */
        boolean add(String displayName, String detail, Resource icon, Runnable action);
    }

    /** The heading results of this provider appear under. */
    String getCategory();

    void search(String query, Response response);
}
