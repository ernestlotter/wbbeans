package org.webbeans.demo;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDescriptor;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.ui.components.Html;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/** Help ▸ About: a dialog around a component, with a single Close button. */
@ActionID(category = "Help", id = "org.webbeans.demo.AboutAction")
@ActionRegistration(displayName = "#CTL_About")
@ActionReference(path = "Menu/Help", position = 1000)
@Messages({"CTL_About=About WebBeans demo...", "TITLE_About=About", "CTL_Close=Close"})
public final class AboutAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        Panel content = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 8));
        content.add(new Html("<h3 style=\"margin:0\">WebBeans demo</h3><p>A NetBeans-style rich client platform for the browser: "
                + "server-side Java components, modules and a thin generated client.</p><p>Step 5: actions, menus, toolbars, dialogs, "
                + "notifications and progress.</p>"));
        content.setTestId("about-content");
        DialogDescriptor d = new DialogDescriptor(content, Bundle.TITLE_About(), true, new Object[] {Bundle.CTL_Close()}, Bundle.CTL_Close(),
                DialogDescriptor.BOTTOM_ALIGN, null);
        d.setMessageType(NotifyDescriptor.PLAIN_MESSAGE);
        DialogDisplayer.getDefault().notify(d);
    }
}
