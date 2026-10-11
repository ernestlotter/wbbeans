package org.webbeans.workbench.admin;

import java.text.DateFormat;
import java.util.Date;

import org.openide.util.lookup.Lookups;
import org.webbeans.core.i18n.Messages;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;
import org.webbeans.ui.SessionDirectory;
import org.webbeans.ui.UISession;

/** One live session: user, address, tabs, created / last activity (read-only properties; the popup can end it). */
@Messages({"PROP_User=User", "PROP_Address=Address", "PROP_Tabs=Tabs", "PROP_Created=Signed in", "PROP_Activity=Last activity", "PROP_Locale=Locale"})
public final class SessionNode extends AbstractNode {

    public static final String ACTIONS_FOLDER = "Sessions/Actions";

    private final UISession session;

    public SessionNode(UISession session) {
        super(Children.LEAF, Lookups.singleton(session));
        this.session = session;
        setName(session.getId());
        setDisplayName(session.getPrincipal().getDisplayName() + " (" + session.getPrincipal().getName() + ")");
        setIconBase("org/webbeans/workbench/admin/icons/user.svg");
    }

    public UISession getSession() {
        return session;
    }

    @Override
    public org.webbeans.actions.Action[] getActions(boolean context) {
        java.util.List<org.webbeans.actions.Action> l = org.webbeans.actions.Actions.forPath(ACTIONS_FOLDER);
        return l.toArray(new org.webbeans.actions.Action[l.size()]);
    }

    void refresh() {
        firePropertyChange("tabs", null, null);
        firePropertyChange("activity", null, null);
    }

    private String time(java.time.Instant i) {
        UISession s = UISession.current();
        return DateFormat.getTimeInstance(DateFormat.MEDIUM, s != null ? s.getLocale() : java.util.Locale.getDefault()).format(new Date(i.toEpochMilli()));
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.ReadOnly<String>("user", String.class, Bundle.PROP_User(), null) {
            @Override
            public String getValue() {
                return session.getPrincipal().getName();
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("address", String.class, Bundle.PROP_Address(), null) {
            @Override
            public String getValue() {
                Object a = session.getAttribute(SessionDirectory.REMOTE_ADDRESS);
                return a == null ? "" : a.toString();
            }
        });
        set.put(new PropertySupport.ReadOnly<Integer>("tabs", Integer.class, Bundle.PROP_Tabs(), null) {
            @Override
            public Integer getValue() {
                return session.getUIs().size();
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("created", String.class, Bundle.PROP_Created(), null) {
            @Override
            public String getValue() {
                return time(session.getCreated());
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("activity", String.class, Bundle.PROP_Activity(), null) {
            @Override
            public String getValue() {
                return time(session.getLastActivity());
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("locale", String.class, Bundle.PROP_Locale(), null) {
            @Override
            public String getValue() {
                return session.getLocale().toString();
            }
        });
        sheet.put(set);
        return sheet;
    }
}
