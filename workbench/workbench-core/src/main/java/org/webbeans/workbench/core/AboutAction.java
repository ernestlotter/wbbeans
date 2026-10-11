package org.webbeans.workbench.core;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.ui.components.Html;

/** Help ▸ About Workbench: a message dialog with a little HTML. */
@ActionID(category = "Help", id = "org.webbeans.workbench.core.AboutAction")
@ActionRegistration(displayName = "#CTL_About", iconBase = "org/webbeans/workbench/core/icons/workbench.svg")
@ActionReference(path = "Menu/Help", position = 100)
@Messages({"CTL_About=About Workbench", "TITLE_About=About",
    "HTML_About=<b>Workbench</b> — the WebBeans sample application.<br>Files, a live dashboard, a 3D viewer and an administration window, written as five server-side Java modules."})
public final class AboutAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        Html html = new Html(Bundle.HTML_About());
        html.setTestId("about");
        NotifyDescriptor about = new NotifyDescriptor.Message(html, NotifyDescriptor.INFORMATION_MESSAGE);
        about.setHelpCtx(new org.webbeans.ui.HelpCtx("workbench"));   // a Help button in the dialog
        DialogDisplayer.getDefault().notify(about);
    }
}
