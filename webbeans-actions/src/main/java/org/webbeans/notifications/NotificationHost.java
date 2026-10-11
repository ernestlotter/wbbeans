package org.webbeans.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;

/**
 * The per-tab toast area and drawer ({@code wb.Notifications}), in the overlay layer. The status bar cell
 * ({@link NotificationsCell}) toggles the drawer and shows the count.
 */
@Renderer(id = "wb.Notifications", resource = "org/webbeans/actions/client/notifications.js", css = "org/webbeans/actions/client/actions.css")
public final class NotificationHost extends Component {

    public static final EventType<Event> CLICK_ITEM = EventType.of("clickItem", Event::new);
    public static final EventType<Event> DISMISS = EventType.of("dismiss", Event::new);
    public static final EventType<Event> DISMISS_ALL = EventType.of("dismissAll", Event::new);

    private final DefaultNotificationDisplayer displayer;
    private NotificationsCell cell;

    NotificationHost(final DefaultNotificationDisplayer displayer) {
        super("wb.Notifications");
        this.displayer = displayer;
        addListener(CLICK_ITEM, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                displayer.clicked(e.getInt("id", -1), NotificationHost.this);
            }
        });
        addListener(DISMISS, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                DefaultNotificationDisplayer.Impl n = displayer.find(e.getInt("id", -1));
                if (n != null) {
                    n.clear();
                }
            }
        });
        addListener(DISMISS_ALL, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                displayer.clearAll();
            }
        });
    }

    void show(DefaultNotificationDisplayer.Impl n, boolean toast) {
        call("add", n.toJson(), toast);
        updateCount();
    }

    void hide(int id) {
        call("remove", id);
        updateCount();
    }

    void clearAll() {
        call("clearAll");
        updateCount();
    }

    public void toggleDrawer() {
        call("toggleDrawer");
    }

    void setCell(NotificationsCell c) {
        this.cell = c;
        updateCount();
    }

    private void updateCount() {
        if (cell != null && cell.isAttached()) {
            cell.setCount(displayer.getNotifications().size());
        }
    }

    @Override
    protected void attached() {
        NotificationsCell c = getUI().getLookup().lookup(NotificationsCell.class);
        if (c != null) {
            cell = c;
        }
        resync();
    }

    @Override
    protected void resync() {
        List<Map<String, Object>> all = new ArrayList<Map<String, Object>>();
        for (DefaultNotificationDisplayer.Impl n : displayer.snapshot()) {
            all.add(n.toJson());
        }
        call("init", all);
        updateCount();
    }
}
