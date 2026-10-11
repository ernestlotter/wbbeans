package org.webbeans.quicksearch;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.ui.UI;

/** Edit ▸ Quick Search (Ctrl+I): opens the {@link QuickSearch} box of the current UI. */
@ActionID(category = "Edit", id = "org.webbeans.quicksearch.QuickSearchAction")
@ActionRegistration(displayName = "#CTL_QuickSearch")
@ActionReferences({
    @ActionReference(path = "Menu/Edit", position = 700, separatorBefore = 650),
    @ActionReference(path = "Shortcuts", name = "D-I")})
public final class QuickSearchAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        UI ui = e.getUI() != null ? e.getUI() : UI.current();
        if (ui != null) {
            QuickSearch.open(ui);
        }
    }
}
