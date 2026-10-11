package org.webbeans.notifications;

import java.util.List;

import org.webbeans.actions.ActionListener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/**
 * Posts notifications to the user (NetBeans' {@code NotificationDisplayer}): a toast in every attached tab of
 * the session and an entry in the notifications drawer (the bell in the status bar). Safe from any thread.
 * Notifications posted while no tab is attached wait in the drawer (bounded, oldest dropped).
 */
public abstract class NotificationDisplayer {

    public enum Priority { HIGH, NORMAL, LOW, SILENT }

    public enum Category { INFO, WARNING, ERROR }

    /** The displayer of the current session. */
    public static NotificationDisplayer getDefault() {
        UISession s = UISession.current();
        if (s == null) {
            throw new IllegalStateException("NotificationDisplayer.getDefault() needs a current session");
        }
        return getDefault(s);
    }

    public static NotificationDisplayer getDefault(UISession session) {
        synchronized (session) {
            NotificationDisplayer d = session.getAttribute(NotificationDisplayer.class);
            if (d == null) {
                d = new DefaultNotificationDisplayer(session);
                session.setAttribute(NotificationDisplayer.class, d);
            }
            return d;
        }
    }

    /**
     * @param title    short text
     * @param icon     optional icon
     * @param details  longer text (may be null)
     * @param onClick  runs (on the clicking tab's executor) when the user clicks the notification; may be null
     */
    public abstract Notification notify(String title, Resource icon, String details, ActionListener onClick, Priority priority, Category category);

    public Notification notify(String title, String details, Category category) {
        return notify(title, null, details, null, Priority.NORMAL, category);
    }

    /** The notifications still in the drawer, newest first. */
    public abstract List<Notification> getNotifications();

    public abstract void clearAll();

    /** Installs the toast/drawer host in a tab (the main window decorator does this); idempotent. */
    public abstract void attach(UI ui);
}
