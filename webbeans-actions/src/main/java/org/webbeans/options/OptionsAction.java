package org.webbeans.options;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;

/** Tools ▸ Options: opens the {@link OptionsDisplayer}. Hidden automatically when no category is registered. */
@ActionID(category = "Tools", id = "org.webbeans.options.OptionsAction")
@ActionRegistration(displayName = "#CTL_Options", iconBase = "org/webbeans/actions/client/options.svg")
@ActionReference(path = "Menu/Tools", position = 9000, separatorBefore = 8900)
public final class OptionsAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        OptionsDisplayer.getDefault().open();
    }
}
