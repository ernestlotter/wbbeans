package org.webbeans.help;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.HelpCtx;
import org.webbeans.ui.UI;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/** The two entry points of the help system: Help ▸ Help Contents (the table of contents) and {@code F1} (context help). */
@Messages({"CTL_HelpContents=Help Contents", "CTL_ContextHelp=Help"})
public final class HelpAction {

    private HelpAction() {
    }

    /** Help ▸ Help Contents: the Help window with the table of contents. */
    @ActionID(category = "Help", id = "org.webbeans.help.HelpContentsAction")
    @ActionRegistration(displayName = "#CTL_HelpContents", iconBase = "org/webbeans/help/help.svg")
    @ActionReference(path = "Menu/Help", position = 10, separatorAfter = 50)
    public static final class HelpContentsAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            HelpTopComponent help = HelpDisplayer.helpWindow(e.getUI());
            if (help != null) {
                help.showContents();
            }
        }
    }

    /** {@code F1}: the help page of the active window ({@link TopComponent#getHelpCtx()}), else the home page. */
    @ActionID(category = "Help", id = "org.webbeans.help.ContextHelpAction")
    @ActionRegistration(displayName = "#CTL_ContextHelp", iconBase = "org/webbeans/help/help.svg")
    @ActionReference(path = "Shortcuts", name = "F1")
    public static final class ContextHelpAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            UI ui = e.getUI();
            WindowManager wm = ui.getLookup().lookup(WindowManager.class);
            TopComponent active = wm == null ? null : wm.getActivated();
            HelpCtx ctx = active == null || active instanceof HelpTopComponent ? HelpCtx.DEFAULT_HELP : active.getHelpCtx();
            ctx.display();
        }
    }
}
