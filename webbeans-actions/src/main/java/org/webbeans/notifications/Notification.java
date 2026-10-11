package org.webbeans.notifications;

/** A posted notification; {@link #clear()} removes it from every tab of the session. */
public interface Notification {

    String getTitle();

    String getDetails();

    NotificationDisplayer.Priority getPriority();

    NotificationDisplayer.Category getCategory();

    void clear();
}
