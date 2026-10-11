package org.webbeans.demo;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;

/** File ▸ Log out. */
@ActionID(category = "File", id = "org.webbeans.demo.LogoutAction")
@ActionRegistration(displayName = "#CTL_Logout")
@ActionReference(path = "Menu/File", position = 9000, separatorBefore = 8900)
@Messages("CTL_Logout=Log out")
public final class LogoutAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        e.getUI().logout();
    }
}
