package org.webbeans.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.impl.DefaultAuthenticator;
import org.webbeans.auth.impl.DefaultLoginPage;
import org.webbeans.auth.providers.HeaderAuthenticationProvider;
import org.webbeans.auth.providers.PropertiesAuthenticationProvider;
import org.webbeans.auth.spi.AuthenticationListener;
import org.webbeans.auth.spi.AuthenticationProvider;
import org.webbeans.auth.spi.RoleMapper;
import org.webbeans.core.lookup.WbDefaultLookup;

public class AuthenticatorTest {

    private Path users;
    private final List<String> events = new ArrayList<String>();

    /** A home-grown provider with a second factor. */
    static final class AcmeProvider implements AuthenticationProvider {
        public String getId() { return "acme"; }
        public String getDisplayName() { return "Acme"; }
        public boolean supports(Class<? extends Credentials> t) { return PasswordCredentials.class.isAssignableFrom(t) || ChallengeResponse.class.isAssignableFrom(t); }
        public LoginMethod getLoginMethod() { return LoginMethod.form(); }
        public AuthenticationResult authenticate(Credentials c, AuthContext ctx) {
            if (c instanceof ChallengeResponse) {
                ChallengeResponse r = (ChallengeResponse) c;
                String user = (String) ctx.getFlowState().get("user");
                return "123456".equals(r.get("code")) ? AuthenticationResult.success(Principal.builder(user).roles("staff").build()).method("mfa:totp")
                        : AuthenticationResult.failure("#ERR_Code");
            }
            PasswordCredentials pc = (PasswordCredentials) c;
            if (!pc.getUsername().startsWith("acme-")) {
                return AuthenticationResult.notMine();
            }
            if (!new String(pc.getPassword()).equals("secret")) {
                return AuthenticationResult.failure("#ERR_Login");
            }
            ctx.getFlowState().put("user", pc.getUsername());
            return AuthenticationResult.challenge(Challenge.of("totp", "#T_Code", LoginField.text("code", "#LBL_Code")));
        }
    }

    @Before
    public void setUp() throws Exception {
        users = Files.createTempDirectory("wb-auth").resolve("users.properties");
        PropertiesAuthenticationProvider.putAccount(users, "alice", "alice-pw".toCharArray(), "Alice", "user", "admin");
        PropertiesAuthenticationProvider.putAccount(users, "bob", "bob-pw".toCharArray(), null, "user");
        RoleMapper mapper = new RoleMapper() {
            public Set<String> mapRoles(Principal p, AuthenticationProvider src) {
                Set<String> r = new HashSet<String>(p.getRoles());
                if (r.contains("staff")) { r.add("accountant"); }   // group → role
                return r;
            }
        };
        AuthenticationListener listener = new AuthenticationListener() {
            public void loginSucceeded(AuthenticationEvent e) { events.add("ok:" + e.getPrincipal().getId() + "@" + e.getProviderId()); }
            public void loginFailed(AuthenticationEvent e) { events.add("fail:" + e.getAttemptedUser()); }
            public void loggedOut(AuthenticationEvent e) { events.add("out:" + e.getPrincipal().getId() + ":" + e.getLogoutReason()); }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(new DefaultAuthenticator(), new AcmeProvider(),
                new PropertiesAuthenticationProvider(users), new HeaderAuthenticationProvider(), mapper, listener, new DefaultLoginPage()));
    }

    @After
    public void tearDown() {
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    private static SimpleAuthContext ctx() {
        return new SimpleAuthContext("127.0.0.1", "test", Locale.ENGLISH);
    }

    @Test
    public void passwordLoginThroughPropertiesProviderWithRolesAndEvents() {
        Authenticator auth = Authenticator.getDefault();
        assertNotNull(auth);
        LoginOutcome o = auth.login(new PasswordCredentials("Alice", "alice-pw"), ctx());
        assertEquals(LoginOutcome.Kind.AUTHENTICATED, o.getKind());
        Principal p = o.getAuthentication().getPrincipal();
        assertEquals("alice", p.getId());
        assertEquals("Alice", p.getDisplayName());
        assertEquals("local", p.getProviderId());
        assertTrue(p.hasRole("admin"));
        assertEquals(new HashSet<String>(Arrays.asList("user", "admin")), p.getRoles());
        assertTrue(o.getAuthentication().getMethods().contains("password"));
        assertEquals(Arrays.asList("ok:alice@local"), events);

        LoginOutcome bad = auth.login(new PasswordCredentials("alice", "nope"), ctx());
        assertEquals(LoginOutcome.Kind.FAILURE, bad.getKind());
        assertEquals("#ERR_Login", bad.getMessage());
        LoginOutcome unknown = auth.login(new PasswordCredentials("nobody", "x"), ctx());
        assertEquals(LoginOutcome.Kind.FAILURE, unknown.getKind());
        assertEquals("#ERR_Login", unknown.getMessage());
        auth.logout(o.getAuthentication(), ctx(), AuthenticationEvent.LogoutReason.USER);
        assertEquals("out:alice:USER", events.get(events.size() - 1));
    }

    @Test
    public void providerChainChallengeAndRoleMapping() {
        Authenticator auth = Authenticator.getDefault();
        SimpleAuthContext c = ctx();
        LoginOutcome o = auth.login(new PasswordCredentials("acme-carol", "secret"), c);
        assertEquals(LoginOutcome.Kind.CHALLENGE, o.getKind());
        assertEquals("totp", o.getChallenge().getType());
        java.util.Map<String, String> wrong = new java.util.HashMap<String, String>(); wrong.put("code", "000000");
        LoginOutcome w = auth.answer(new ChallengeResponse(o.getChallenge().getId(), wrong), ctx());
        assertEquals(LoginOutcome.Kind.FAILURE, w.getKind());
        // the challenge is single-use
        java.util.Map<String, String> right = new java.util.HashMap<String, String>(); right.put("code", "123456");
        assertEquals(LoginOutcome.Kind.FAILURE, auth.answer(new ChallengeResponse(o.getChallenge().getId(), right), ctx()).getKind());
        LoginOutcome o2 = auth.login(new PasswordCredentials("acme-carol", "secret"), ctx());
        LoginOutcome done = auth.answer(new ChallengeResponse(o2.getChallenge().getId(), right), ctx());
        assertEquals(LoginOutcome.Kind.AUTHENTICATED, done.getKind());
        Principal p = done.getAuthentication().getPrincipal();
        assertEquals("acme", p.getProviderId());
        assertEquals(new HashSet<String>(Arrays.asList("staff", "accountant")), p.getRoles());   // RoleMapper added accountant
        assertTrue(done.getAuthentication().getMethods().contains("mfa:totp"));
    }

    @Test
    public void headerProviderOnlyFromTrustedProxy() {
        Authenticator auth = Authenticator.getDefault();
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("X-Remote-User", "dave"); h.put("X-Remote-Groups", "ops, admin");
        assertEquals(LoginOutcome.Kind.FAILURE, auth.login(new HeaderCredentials(h, "1.2.3.4", false), ctx()).getKind());
        LoginOutcome o = auth.login(new HeaderCredentials(h, "10.0.0.5", true), ctx());
        assertEquals(LoginOutcome.Kind.AUTHENTICATED, o.getKind());
        assertEquals(new HashSet<String>(Arrays.asList("ops", "admin")), o.getAuthentication().getPrincipal().getRoles());
        assertEquals("header", o.getAuthentication().getPrincipal().getProviderId());
    }

    @Test
    public void throttleBlocksAfterRepeatedFailures() {
        Authenticator auth = Authenticator.getDefault();
        for (int i = 0; i < 10; i++) {
            assertEquals("#ERR_Login", auth.login(new PasswordCredentials("bob", "wrong"), ctx()).getMessage());
        }
        assertEquals("#ERR_Throttled", auth.login(new PasswordCredentials("bob", "bob-pw"), ctx()).getMessage());
        assertEquals("#ERR_Throttled", auth.login(new PasswordCredentials("alice", "alice-pw"), new SimpleAuthContext("127.0.0.1", "t", Locale.ENGLISH)).getMessage());
        // another address, another user: fine
        assertEquals(LoginOutcome.Kind.AUTHENTICATED, auth.login(new PasswordCredentials("alice", "alice-pw"), new SimpleAuthContext("10.1.1.1", "t", Locale.ENGLISH)).getKind());
    }

    @Test
    public void passwordHashRoundTrip() {
        String h = PasswordHash.hash("s3cret".toCharArray(), 2000);
        assertTrue(h.startsWith("pbkdf2$2000$"));
        assertTrue(PasswordHash.verify("s3cret".toCharArray(), h));
        assertFalse(PasswordHash.verify("s3cret ".toCharArray(), h));
        assertFalse(PasswordHash.verify("x".toCharArray(), "garbage"));
    }

    @Test
    public void loginPageRendersMethodsAndLocalisedLabels() {
        Authenticator auth = Authenticator.getDefault();
        LoginPageModel m = new LoginPageModel(auth.getLoginProviders(), null, DefaultLoginPage.msg(Locale.GERMAN, "#ERR_Login"), Locale.GERMAN, "tok", "/", "Workbench", "");
        String html = new DefaultLoginPage().render(m);
        assertTrue(html, html.contains("Benutzername"));
        assertTrue(html, html.contains("Unbekannter Benutzer"));
        assertTrue(html, html.contains("name=\"csrf\" value=\"tok\""));
        assertTrue(html, html.contains("name=\"provider\" value=\"acme\""));
        assertTrue(html, html.contains("name=\"provider\" value=\"local\""));
        assertFalse("header provider has no login method", html.contains("value=\"header\""));
        Challenge c = Challenge.of("totp", "#T_Code", LoginField.text("code", "Code"));
        String ch = new DefaultLoginPage().render(new LoginPageModel(auth.getLoginProviders(), c, null, Locale.ENGLISH, "tok", "/", "Workbench", ""));
        assertTrue(ch, ch.contains("/auth/challenge") && ch.contains(c.getId()) && ch.contains("name=\"code\""));
    }
}
