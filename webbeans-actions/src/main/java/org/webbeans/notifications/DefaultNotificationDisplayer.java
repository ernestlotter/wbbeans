package org.webbeans.notifications;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionListener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/** Session-scoped store of notifications fanned out to one {@link NotificationHost} per tab. */
final class DefaultNotificationDisplayer extends NotificationDisplayer {

    private static final Logger LOG = Logger.getLogger(DefaultNotificationDisplayer.class.getName());
    static final int MAX = 100;

    private final UISession session;
    private final List<Impl> items = new ArrayList<Impl>();
    private final Map<UI, NotificationHost> hosts = Collections.synchronizedMap(new LinkedHashMap<UI, NotificationHost>());
    private int nextId = 1;

    DefaultNotificationDisplayer(UISession session) {
        this.session = session;
    }

    final class Impl implements Notification {
        final int id;
        final String title;
        final Resource icon;
        final String details;
        final ActionListener onClick;
        final Priority priority;
        final Category category;
        final Instant time = Instant.now();

        Impl(int id, String title, Resource icon, String details, ActionListener onClick, Priority priority, Category category) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.details = details;
            this.onClick = onClick;
            this.priority = priority;
            this.category = category;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getDetails() {
            return details;
        }

        @Override
        public Priority getPriority() {
            return priority;
        }

        @Override
        public Category getCategory() {
            return category;
        }

        @Override
        public void clear() {
            remove(this);
        }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("title", title);
            m.put("details", details);
            m.put("category", category.name().toLowerCase(java.util.Locale.ROOT));
            m.put("priority", priority.name().toLowerCase(java.util.Locale.ROOT));
            m.put("time", time.toString());
            m.put("clickable", onClick != null);
            if (icon != null) {
                m.put("icon", icon);
            }
            return m;
        }
    }

    @Override
    public Notification notify(String title, Resource icon, String details, ActionListener onClick, Priority priority, Category category) {
        final Impl n;
        synchronized (items) {
            n = new Impl(nextId++, title, icon, details, onClick, priority == null ? Priority.NORMAL : priority,
                    category == null ? Category.INFO : category);
            items.add(0, n);
            while (items.size() > MAX) {
                items.remove(items.size() - 1);
            }
        }
        forEachHost(new HostTask() {
            @Override
            public void run(NotificationHost h) {
                h.show(n, true);
            }
        });
        return n;
    }

    void remove(final Impl n) {
        synchronized (items) {
            if (!items.remove(n)) {
                return;
            }
        }
        forEachHost(new HostTask() {
            @Override
            public void run(NotificationHost h) {
                h.hide(n.id);
            }
        });
    }

    Impl find(int id) {
        synchronized (items) {
            for (Impl i : items) {
                if (i.id == id) {
                    return i;
                }
            }
        }
        return null;
    }

    @Override
    public List<Notification> getNotifications() {
        synchronized (items) {
            return new ArrayList<Notification>(items);
        }
    }

    @Override
    public void clearAll() {
        synchronized (items) {
            items.clear();
        }
        forEachHost(new HostTask() {
            @Override
            public void run(NotificationHost h) {
                h.clearAll();
            }
        });
    }

    List<Impl> snapshot() {
        synchronized (items) {
            return new ArrayList<Impl>(items);
        }
    }

    @Override
    public void attach(final UI ui) {
        if (hosts.containsKey(ui)) {
            return;
        }
        final NotificationHost host = new NotificationHost(this);
        hosts.put(ui, host);
        ui.allowWhileModal(host);
        ui.getRoot().add(host, "overlay");
        ui.addToLookup(host);
        ui.addCloseListener(new Runnable() {
            @Override
            public void run() {
                hosts.remove(ui);
            }
        });
    }

    /** Runs the click handler of a notification on the tab that clicked. */
    void clicked(int id, NotificationHost source) {
        Impl n = find(id);
        if (n != null && n.onClick != null) {
            try {
                n.onClick.actionPerformed(new ActionEvent(source, source.getLookup()));
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Notification click handler failed", e);
            }
        }
    }

    private interface HostTask {
        void run(NotificationHost h);
    }

    private void forEachHost(final HostTask task) {
        List<Map.Entry<UI, NotificationHost>> all;
        synchronized (hosts) {
            all = new ArrayList<Map.Entry<UI, NotificationHost>>(hosts.entrySet());
        }
        for (final Map.Entry<UI, NotificationHost> e : all) {
            final UI ui = e.getKey();
            if (ui.getState() == UI.State.CLOSED) {
                continue;
            }
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    task.run(e.getValue());
                }
            };
            if (session.isSessionThread() && UI.current() == ui) {
                r.run();
            } else {
                ui.access(r);
            }
        }
    }
}
