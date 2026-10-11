package org.webbeans.workbench.admin;

import java.util.ArrayList;
import java.util.List;

import org.openide.util.Lookup;
import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.OutlineView;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;
import org.webbeans.ui.SessionDirectory;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.Mode;
import org.webbeans.windows.TopComponent;

/**
 * Tools ▸ Server Status (administrators only — the registry entry carries {@code roles = "admin"}, so other users
 * never receive the window or its menu item): the live sessions from the {@link SessionDirectory} service as an
 * {@link OutlineView} with property columns, refreshed every two seconds; the popup can end a session.
 */
@Mode.Registration(name = "admin", side = Mode.Side.BOTTOM, defaultSize = 180, position = 200)
@TopComponent.Registration(mode = "admin", openAtStartup = false, position = 100, roles = {"admin"})
@TopComponent.Description(preferredID = "ServerStatusTC", iconBase = "org/webbeans/workbench/admin/icons/server.svg", persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED, helpId = "admin")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenServerStatus", position = 950)
@Messages({"CTL_AdminModule=Workbench Administration", "CTL_ServerStatusTC=Server Status", "CTL_OpenServerStatus=Server Status",
    "COL_Session=Session", "LBL_Summary={0} sessions, {1} tabs, JVM {2} MB used"})
public final class ServerStatusTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final OutlineView view = new OutlineView(Bundle.COL_Session());
    private final Label summary = new Label();
    private final SessionsChildren children = new SessionsChildren();
    private UI.Timer timer;

    public ServerStatusTopComponent() {
        setDisplayName(Bundle.CTL_ServerStatusTC());
        setLayout(new Layout.Border());
        ExplorerUtils.activateActions(em, map, true);
        associateLookup(ExplorerUtils.createLookup(em, map));
        view.setPropertyColumns("user", Bundle.PROP_User(), "address", Bundle.PROP_Address(), "tabs", Bundle.PROP_Tabs(), "activity", Bundle.PROP_Activity(), "locale", Bundle.PROP_Locale());
        view.setRootVisible(false);
        view.setTestId("sessions");
        summary.setTestId("sessions-summary");
        summary.setStyleName("wb-status-summary");
        AbstractNode root = new AbstractNode(children);
        root.setName("sessions");
        em.setRootContext(root);
        add(summary, Layout.Border.NORTH);
        add(view, Layout.Border.CENTER);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_ServerStatusTC());
    }

    @Override
    protected void componentOpened() {
        refresh();
        timer = getUI().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 2000, 2000);
    }

    @Override
    protected void componentClosed() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    void refresh() {
        SessionDirectory dir = Lookup.getDefault().lookup(SessionDirectory.class);
        List<UISession> sessions = dir == null ? new ArrayList<UISession>() : new ArrayList<UISession>(dir.getSessions());
        if (dir == null || sessions.isEmpty()) {
            sessions.add(getUI().getSession()); // headless: at least ourselves
        }
        children.update(sessions);
        int tabs = 0;
        for (UISession s : sessions) {
            tabs += s.getUIs().size();
        }
        Runtime rt = Runtime.getRuntime();
        summary.setText(Bundle.LBL_Summary(sessions.size(), tabs, (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)));
    }

    static final class SessionsChildren extends Children.Keys<UISession> {
        void update(List<UISession> sessions) {
            setKeys(sessions);
            for (Node n : getNodes()) {
                if (n instanceof SessionNode) {
                    ((SessionNode) n).refresh();
                }
            }
        }

        @Override
        protected Node[] createNodes(UISession key) {
            return new Node[] {new SessionNode(key)};
        }
    }
}
