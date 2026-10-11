package org.webbeans.workbench.admin;

import java.util.List;
import java.util.function.Consumer;

import org.openide.util.Lookup;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.ui.SessionDirectory;
import org.webbeans.ui.UISession;
import org.webbeans.workbench.core.api.Log;

/** Ends the selected sessions after a confirmation (administrators; a context action over {@link UISession}). */
@ActionID(category = "Admin", id = "org.webbeans.workbench.admin.EndSessionAction")
@ActionRegistration(displayName = "#CTL_EndSession", iconBase = "org/webbeans/workbench/admin/icons/logout.svg", roles = {"admin"})
@ActionReference(path = SessionNode.ACTIONS_FOLDER, position = 100)
@Messages({"CTL_EndSession=End session", "MSG_ConfirmEnd=End {0} session(s)? The users are signed out of every tab.", "MSG_Ended=Ended session of {0}"})
public final class EndSessionAction implements ActionListener {

    private final List<UISession> sessions;

    public EndSessionAction(List<UISession> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(Bundle.MSG_ConfirmEnd(sessions.size()), NotifyDescriptor.YES_NO_OPTION))
                .thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object r) {
                if (r != NotifyDescriptor.YES_OPTION) {
                    return;
                }
                SessionDirectory dir = Lookup.getDefault().lookup(SessionDirectory.class);
                for (UISession s : sessions) {
                    if (dir != null && dir.invalidate(s.getId())) {
                        Log.warn("admin", Bundle.MSG_Ended(s.getPrincipal().getName()));
                    }
                }
            }
        });
    }
}
