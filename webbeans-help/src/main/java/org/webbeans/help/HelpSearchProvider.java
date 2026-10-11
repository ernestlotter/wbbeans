package org.webbeans.help;

import java.util.Locale;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.i18n.Messages;
import org.webbeans.core.registry.Registration;
import org.webbeans.quicksearch.QuickSearchProvider;
import org.webbeans.ui.HelpCtx;

/** Quick Search (Ctrl+I) results from the help topics: titles and keywords, opening the Help window. */
@Registration(path = "QuickSearch/Providers", type = QuickSearchProvider.class, position = 300)
@Messages("LBL_HelpResults=Help topics")
public final class HelpSearchProvider implements QuickSearchProvider {

    @Override
    public String getCategory() {
        return Bundles.getMessage(HelpSearchProvider.class, "LBL_HelpResults");
    }

    @Override
    public void search(String query, Response response) {
        Locale l = LocaleContext.get();
        for (final HelpSet.Topic t : HelpSet.current().search(query, l != null ? l : Locale.getDefault())) {
            StringBuilder path = new StringBuilder();
            for (HelpSet.Topic p : t.getPath()) {
                if (p != t) {
                    path.append(p.getTitle()).append(" › ");
                }
            }
            if (!response.add(t.getTitle(), path.length() > 0 ? path.substring(0, path.length() - 3) : null, null, new Runnable() {
                @Override
                public void run() {
                    new HelpCtx(t.getId()).display();
                }
            })) {
                return;
            }
        }
    }
}
