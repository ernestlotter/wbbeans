package org.webbeans.auth;

import java.util.Collection;

import org.openide.util.Lookup;
import org.webbeans.auth.spi.Authorizer;

/** Static helpers over the current principal (provided by the session layer through {@link PrincipalSource}). */
public final class Security {

    /** Implemented by the session layer to expose the principal of the current thread's session. */
    public interface PrincipalSource {
        Principal current();
    }

    private static volatile PrincipalSource source;

    private Security() {
    }

    public static void setPrincipalSource(PrincipalSource s) {
        source = s;
    }

    /** The current principal, or null outside a session. */
    public static Principal currentPrincipal() {
        PrincipalSource s = source;
        return s == null ? null : s.current();
    }

    public static boolean hasRole(String role) {
        Principal p = currentPrincipal();
        return p != null && p.hasRole(role);
    }

    public static void checkRole(String role) {
        if (!hasRole(role)) {
            throw new AccessDeniedException("Role " + role + " required");
        }
    }

    /** Asks every registered {@link Authorizer}; with none registered, falls back to {@code permission} being a role name. */
    public static boolean isAllowed(String permission, Object target) {
        Principal p = currentPrincipal();
        if (p == null) {
            return false;
        }
        Collection<? extends Authorizer> authorizers = Lookup.getDefault().lookupAll(Authorizer.class);
        if (authorizers.isEmpty()) {
            return p.hasRole(permission);
        }
        for (Authorizer a : authorizers) {
            if (!a.isAllowed(p, permission, target)) {
                return false;
            }
        }
        return true;
    }

    public static void checkAllowed(String permission, Object target) {
        if (!isAllowed(permission, target)) {
            throw new AccessDeniedException("Permission " + permission + " denied");
        }
    }
}
