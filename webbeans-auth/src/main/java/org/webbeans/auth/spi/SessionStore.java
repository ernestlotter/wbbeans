package org.webbeans.auth.spi;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;

/**
 * Shared storage of <em>authenticated</em> sessions across server instances. Without one, a browser's session
 * cookie is only known to the instance that issued it: when that instance goes away (restart, failover to
 * another node of a load-balanced deployment), the user sees the login page. With a store, any instance that
 * receives an unknown but stored cookie <em>adopts</em> the session — same id, same CSRF token, same
 * authentication — and builds a fresh UI for it; the user keeps their login (not their open windows: UI state
 * lives in the JVM that built it). Register one with {@code @ServiceProvider(service = SessionStore.class)}
 * or {@code ServerConfig.sessionStore(...)}; {@code FileSessionStore} in the server module keeps one JSON file
 * per session in a shared directory.
 *
 * <p>Calls come from the HTTP threads and the session sweeper, concurrently; implementations must be thread
 * safe. Failures are logged and the instance carries on with its own memory, so a store outage degrades to
 * "re-login after failover", never to "cannot sign in".
 */
public interface SessionStore {

    /** Records a new session (or replaces a stored one with the same id). */
    void put(StoredSession session) throws IOException;

    /** The stored session, or null when unknown or past {@link StoredSession#getExpiresAt()}. */
    StoredSession get(String sessionId) throws IOException;

    /** Updates the activity and expiry stamps of a stored session (called at most every few seconds per session). */
    void touch(String sessionId, Instant lastActivity, Instant expiresAt) throws IOException;

    /** Forgets a session (logout, timeout, admin). A node shutting down does <em>not</em> remove its sessions. */
    void remove(String sessionId) throws IOException;

    /** Every stored, unexpired session (for the concurrent-sessions policy and admin views); may be empty if unsupported. */
    Collection<StoredSession> list() throws IOException;

    /** What survives an instance: the identity, the cookie's id and CSRF token, locale, timestamps, the node that made it. */
    final class StoredSession {
        private final String id;
        private final String csrfToken;
        private final Authentication authentication;
        private final Locale locale;
        private final String remoteAddress;
        private final String nodeId;
        private final Instant created;
        private final Instant lastActivity;
        private final Instant expiresAt;

        public StoredSession(String id, String csrfToken, Authentication authentication, Locale locale, String remoteAddress, String nodeId,
                Instant created, Instant lastActivity, Instant expiresAt) {
            this.id = id;
            this.csrfToken = csrfToken;
            this.authentication = authentication;
            this.locale = locale;
            this.remoteAddress = remoteAddress;
            this.nodeId = nodeId;
            this.created = created;
            this.lastActivity = lastActivity;
            this.expiresAt = expiresAt;
        }

        public String getId() {
            return id;
        }

        public String getCsrfToken() {
            return csrfToken;
        }

        public Authentication getAuthentication() {
            return authentication;
        }

        public Locale getLocale() {
            return locale;
        }

        public String getRemoteAddress() {
            return remoteAddress;
        }

        /** The instance that created the session ({@code ServerConfig.nodeId}). */
        public String getNodeId() {
            return nodeId;
        }

        public Instant getCreated() {
            return created;
        }

        public Instant getLastActivity() {
            return lastActivity;
        }

        /** When the session is dead whatever happens (idle timeout or maximum lifetime, whichever comes first). */
        public Instant getExpiresAt() {
            return expiresAt;
        }

        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }

        public StoredSession touched(Instant activity, Instant expires) {
            return new StoredSession(id, csrfToken, authentication, locale, remoteAddress, nodeId, created, activity, expires);
        }

        /** A JSON-friendly map (strings, numbers, lists, maps) — what {@code FileSessionStore} writes. Secrets are kept. */
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("csrf", csrfToken);
            Principal p = authentication.getPrincipal();
            Map<String, Object> pm = new LinkedHashMap<String, Object>();
            pm.put("id", p.getId());
            pm.put("name", p.getName());
            pm.put("displayName", p.getDisplayName());
            pm.put("email", p.getEmail());
            pm.put("roles", new java.util.ArrayList<String>(p.getRoles()));
            pm.put("attributes", new LinkedHashMap<String, String>(p.getAttributes()));
            pm.put("provider", p.getProviderId());
            m.put("principal", pm);
            Map<String, Object> am = new LinkedHashMap<String, Object>();
            am.put("time", authentication.getTime().toString());
            am.put("expiresAt", authentication.getExpiresAt() == null ? null : authentication.getExpiresAt().toString());
            am.put("methods", new java.util.ArrayList<String>(authentication.getMethods()));
            am.put("secrets", new LinkedHashMap<String, Object>(authentication.getSecrets()));
            m.put("authentication", am);
            m.put("locale", locale == null ? null : locale.toLanguageTag());
            m.put("remoteAddress", remoteAddress);
            m.put("node", nodeId);
            m.put("created", created.toString());
            m.put("lastActivity", lastActivity.toString());
            m.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
            return m;
        }

        @SuppressWarnings("unchecked")
        public static StoredSession fromJson(Map<String, Object> m) {
            Map<String, Object> pm = (Map<String, Object>) m.get("principal");
            Principal.Builder b = Principal.builder(str(pm.get("id")));
            if (pm.get("name") != null) {
                b.name(str(pm.get("name")));
            }
            if (pm.get("displayName") != null) {
                b.displayName(str(pm.get("displayName")));
            }
            if (pm.get("email") != null) {
                b.email(str(pm.get("email")));
            }
            if (pm.get("roles") instanceof List) {
                for (Object r : (List<Object>) pm.get("roles")) {
                    b.role(str(r));
                }
            }
            if (pm.get("attributes") instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) pm.get("attributes")).entrySet()) {
                    b.attribute(e.getKey(), str(e.getValue()));
                }
            }
            Principal p = b.build();
            if (pm.get("provider") != null) {
                p = p.withProvider(str(pm.get("provider")));
            }
            Map<String, Object> am = (Map<String, Object>) m.get("authentication");
            Set<String> methods = new LinkedHashSet<String>();
            if (am.get("methods") instanceof List) {
                for (Object o : (List<Object>) am.get("methods")) {
                    methods.add(str(o));
                }
            }
            Map<String, Object> secrets = am.get("secrets") instanceof Map ? (Map<String, Object>) am.get("secrets") : Collections.<String, Object>emptyMap();
            Authentication auth = new Authentication(p, instant(am.get("time")), instant(am.get("expiresAt")), methods, secrets);
            return new StoredSession(str(m.get("id")), str(m.get("csrf")), auth,
                    m.get("locale") == null ? null : Locale.forLanguageTag(str(m.get("locale"))), str(m.get("remoteAddress")), str(m.get("node")),
                    instant(m.get("created")), instant(m.get("lastActivity")), instant(m.get("expiresAt")));
        }

        private static String str(Object o) {
            return o == null ? null : o.toString();
        }

        private static Instant instant(Object o) {
            return o == null ? null : Instant.parse(o.toString());
        }
    }
}
