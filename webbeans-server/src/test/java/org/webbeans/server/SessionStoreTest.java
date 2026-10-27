package org.webbeans.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.AuthenticationEvent;
import org.webbeans.auth.Principal;
import org.webbeans.auth.spi.SessionStore;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.lookup.WbDefaultLookup;

/** Two instances sharing a {@link FileSessionStore}: a session made on one is adopted by the other; logout, expiry and shutdown semantics. */
public class SessionStoreTest {

    private Path dir;
    private FileSessionStore store;
    private SessionManager a;
    private SessionManager b;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-sessions");
        store = new FileSessionStore(dir.resolve("shared"));
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(new DataDir(dir.resolve("data"))));
        a = new SessionManager(new ServerConfig().nodeId("node-a").sessionStore(store).sessionIdleTimeout(Duration.ofSeconds(2)));
        b = new SessionManager(new ServerConfig().nodeId("node-b").sessionStore(store));
    }

    @After
    public void tearDown() throws Exception {
        a.shutdown();
        b.shutdown();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(new java.util.function.Consumer<Path>() {
            @Override
            public void accept(Path p) {
                p.toFile().delete();
            }
        });
    }

    private static Authentication alice() {
        Principal p = Principal.builder("alice").name("alice").displayName("Alice Admin").email("a@example.org").roles("admin", "user")
                .attribute("dept", "ops").build().withProvider("local");
        return new Authentication(p, Instant.now(), null, Collections.singleton("password"), Collections.<String, Object>singletonMap("t", "x"));
    }

    @Test
    public void sessionCreatedOnOneNodeIsAdoptedByAnotherWithTheSameIdentity() throws Exception {
        HttpSession s = a.create(alice(), Locale.GERMAN, "10.0.0.7");
        SessionStore.StoredSession st = store.get(s.getId());
        assertNotNull(st);
        assertEquals("node-a", st.getNodeId());
        assertEquals(s.getCsrfToken(), st.getCsrfToken());
        assertTrue(st.getExpiresAt().isAfter(Instant.now()));

        assertNull("unknown to b in memory", bInMemory(s.getId()));
        HttpSession adopted = b.get(s.getId());
        assertNotNull(adopted);
        assertEquals(s.getId(), adopted.getId());
        assertEquals(s.getCsrfToken(), adopted.getCsrfToken());
        assertEquals("alice", adopted.getPrincipal().getName());
        assertEquals("Alice Admin", adopted.getPrincipal().getDisplayName());
        assertEquals("a@example.org", adopted.getPrincipal().getEmail());
        assertTrue(adopted.getPrincipal().hasRole("admin"));
        assertEquals("ops", adopted.getPrincipal().getAttributes().get("dept"));
        assertEquals("local", adopted.getPrincipal().getProviderId());
        assertEquals(Locale.GERMAN, adopted.getLocale());
        assertEquals("10.0.0.7", adopted.getRemoteAddress());
        assertEquals(Collections.singleton("password"), adopted.getAuthentication().getMethods());
        assertEquals("x", adopted.getAuthentication().getSecrets().get("t"));
        assertSame("adopted once, then in memory", adopted, b.get(s.getId()));
        assertEquals(1, store.list().size());

        // logout on b removes it everywhere
        b.invalidate(adopted, AuthenticationEvent.LogoutReason.USER);
        assertNull(store.get(s.getId()));
        assertNull("a still holds it in memory but the store is gone", store.get(s.getId()));
        assertNotNull(a.get(s.getId()));   // a's own memory: the sweeper would drop it when the policy says so
    }

    @Test
    public void shutdownKeepsSessionsForTheOtherNodesAndExpiryDropsThem() throws Exception {
        HttpSession s = a.create(alice(), Locale.ENGLISH, "10.0.0.8");
        a.shutdown();
        assertNotNull("a stopping node leaves its sessions in the store", store.get(s.getId()));
        assertNotNull(b.get(s.getId()));
        a = new SessionManager(new ServerConfig().nodeId("node-a").sessionStore(store).sessionIdleTimeout(Duration.ofSeconds(2)));

        // a session whose idle timeout passed is expired in the store (a's 2 s policy wrote the expiry)
        HttpSession t = a.create(alice(), Locale.ENGLISH, "10.0.0.9");
        Thread.sleep(2300);
        assertNull("expired for the store", store.get(t.getId()));
        assertNull(b.get(t.getId()));

        // bad ids never reach the file system
        assertNull(b.get("../etc/passwd"));
        assertNull(b.get("short"));
    }

    private HttpSession bInMemory(String id) {
        for (HttpSession s : b.getSessions()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }
}
