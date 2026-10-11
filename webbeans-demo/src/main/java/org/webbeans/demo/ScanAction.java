package org.webbeans.demo;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.progress.Cancellable;
import org.webbeans.progress.ProgressHandle;
import org.webbeans.progress.ProgressHandleFactory;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/**
 * An asynchronous action: runs on a background thread (the platform shows "Running ..." in the status bar),
 * reports its own cancellable progress and posts a notification when done. UI access goes through
 * {@code ui.access}.
 */
@ActionID(category = "Planets", id = "org.webbeans.demo.ScanAction")
@ActionRegistration(displayName = "#CTL_Scan", asynchronous = true, iconBase = "org/webbeans/demo/icons/scan.svg")
@ActionReferences({
    @ActionReference(path = "Menu/Planets", position = 300),
    @ActionReference(path = "Toolbars/Planets", position = 300)})
@Messages({"CTL_Scan=Scan the sky", "MSG_Scanning=Scanning sector {0}", "MSG_ScanDone=Scan complete: {0} sectors", "MSG_ScanCancelled=Scan cancelled"})
public final class ScanAction implements ActionListener {

    static volatile long stepMillis = 150;

    @Override
    public void actionPerformed(ActionEvent e) {
        final UI ui = e.getUI();
        final UISession session = ui.getSession();
        final boolean[] cancelled = {false};
        ProgressHandle h = ProgressHandleFactory.createHandle(Bundle.CTL_Scan(), new Cancellable() {
            @Override
            public boolean cancel() {
                cancelled[0] = true;
                return true;
            }
        }, session);
        int sectors = 12;
        h.start(sectors);
        int i = 0;
        try {
            for (; i < sectors && !cancelled[0]; i++) {
                h.progress(Bundle.MSG_Scanning(i + 1), i);
                try {
                    Thread.sleep(stepMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            h.finish();
        }
        final String msg = cancelled[0] ? Bundle.MSG_ScanCancelled() : Bundle.MSG_ScanDone(i);
        NotificationDisplayer.getDefault(session).notify(msg, null, null, null,
                cancelled[0] ? NotificationDisplayer.Priority.LOW : NotificationDisplayer.Priority.NORMAL,
                cancelled[0] ? NotificationDisplayer.Category.WARNING : NotificationDisplayer.Category.INFO);
        ui.access(new Runnable() {
            @Override
            public void run() {
                session.publish(new LogLine(msg));
            }
        });
    }
}
