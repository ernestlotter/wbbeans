package org.webbeans.notifications;

import org.webbeans.core.registry.Registration;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;

/** The bell in the status bar ({@code wb.NotificationsCell}): shows the drawer count, click toggles the drawer. */
@Renderer(id = "wb.NotificationsCell", resource = "org/webbeans/actions/client/notifications.js", css = "org/webbeans/actions/client/actions.css")
public final class NotificationsCell extends Component {

    public static final Property<Integer> COUNT = Property.of("count", Integer.class, 0);

    public NotificationsCell() {
        super("wb.NotificationsCell");
        setTestId("notifications-cell");
        addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                NotificationHost host = getUI().getLookup().lookup(NotificationHost.class);
                if (host != null) {
                    host.toggleDrawer();
                }
            }
        });
    }

    @Registration(path = "StatusBar", type = Component.class, position = 900)
    public static Component create() {
        return new NotificationsCell();
    }

    public void setCount(int n) {
        set(COUNT, n);
    }

    @Override
    protected void attached() {
        getUI().addToLookup(this);
        NotificationHost host = getUI().getLookup().lookup(NotificationHost.class);
        if (host != null) {
            host.setCell(this);
        }
    }

    @Override
    protected void detached() {
        getUI().removeFromLookup(this);
    }
}
