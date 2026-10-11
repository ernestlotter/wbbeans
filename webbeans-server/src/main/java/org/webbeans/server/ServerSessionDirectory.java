package org.webbeans.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.webbeans.auth.AuthenticationEvent;
import org.webbeans.ui.SessionDirectory;
import org.webbeans.ui.UISession;

/** {@link SessionDirectory} over the running server's {@link SessionManager}. */
/* registered in META-INF/services (the server module builds without annotation processing) */
public final class ServerSessionDirectory implements SessionDirectory {

    private static volatile SessionManager manager;

    static void register(SessionManager m) {
        manager = m;
    }

    @Override
    public Collection<UISession> getSessions() {
        SessionManager m = manager;
        List<UISession> out = new ArrayList<UISession>();
        if (m != null) {
            for (HttpSession s : m.getSessions()) {
                if (s.isValid()) {
                    out.add(s.getUISession());
                }
            }
        }
        return out;
    }

    @Override
    public boolean invalidate(String sessionId) {
        SessionManager m = manager;
        if (m == null) {
            return false;
        }
        for (HttpSession s : m.getSessions()) {
            if (s.getUISession().getId().equals(sessionId)) {
                m.invalidate(s, AuthenticationEvent.LogoutReason.ADMIN);
                return true;
            }
        }
        return false;
    }
}
