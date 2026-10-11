package org.webbeans.workbench.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.webbeans.actions.Actions;
import org.webbeans.test.HeadlessUI;
import org.webbeans.windows.TopComponent;

/** Role filtering: administrators get the Server Status window and its action; plain users do not even see the entries. */
public class AdminTest {

    @Test
    public void adminSeesTheStatusWindowAndSessions() throws Exception {
        try (HeadlessUI h = HeadlessUI.builder().user("alice", "user", "admin").start()) {
            TopComponent tc = h.open("ServerStatusTC");
            assertTrue(tc.isOpened());
            ServerStatusTopComponent status = (ServerStatusTopComponent) tc;
            assertEquals("headless: at least our own session", 1, status.getExplorerManager().getRootContext().getChildren().getNodes().length);
            String summary = ((org.webbeans.ui.components.Label) h.byTestId("sessions-summary")).get(org.webbeans.ui.components.Label.TEXT);
            assertTrue(summary, summary.startsWith("1 sessions, 1 tabs"));
            assertNotNull("end-session action registered for admins", Actions.forID("Admin", "org.webbeans.workbench.admin.EndSessionAction"));
        }
    }

    @Test
    public void plainUsersDoNotGetTheWindow() throws Exception {
        try (final HeadlessUI h = HeadlessUI.builder().user("bob", "user").start()) {
            final boolean[] seen = new boolean[3];
            h.run(new Runnable() {          // registry reads are filtered by the session's roles on the session executor
                @Override
                public void run() {
                    seen[0] = h.wm().findTopComponent("ServerStatusTC") != null;
                    seen[1] = Actions.forID("Admin", "org.webbeans.workbench.admin.EndSessionAction") != null;
                    for (org.webbeans.actions.Action a : Actions.forPath("Menu/Window")) {
                        if (a != null && "Server Status".equals(a.getValue(org.webbeans.actions.Action.NAME))) {
                            seen[2] = true;
                        }
                    }
                }
            });
            assertTrue("no window for bob", !seen[0]);
            assertTrue("no end-session action for bob", !seen[1]);
            assertTrue("no Window ▸ Server Status for bob", !seen[2]);
        }
    }
}
