package org.webbeans.server.handlers;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openide.util.Lookup;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.AuthenticationEvent;
import org.webbeans.auth.Authenticator;
import org.webbeans.auth.CallbackCredentials;
import org.webbeans.auth.Challenge;
import org.webbeans.auth.ChallengeResponse;
import org.webbeans.auth.Credentials;
import org.webbeans.auth.LoginField;
import org.webbeans.auth.LoginMethod;
import org.webbeans.auth.LoginOutcome;
import org.webbeans.auth.LoginPageModel;
import org.webbeans.auth.PasswordCredentials;
import org.webbeans.auth.Principal;
import org.webbeans.auth.TokenCredentials;
import org.webbeans.auth.http.AuthHttpRequest;
import org.webbeans.auth.http.AuthHttpResponse;
import org.webbeans.auth.impl.DefaultLoginPage;
import org.webbeans.auth.spi.AuthHttpHandler;
import org.webbeans.auth.spi.AuthenticationProvider;
import org.webbeans.auth.spi.LoginPage;
import org.webbeans.auth.spi.RedirectAuthenticationProvider;
import org.webbeans.core.json.Json;
import org.webbeans.server.HttpSession;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.SessionManager;
import org.webbeans.server.http.RequestAuthContext;
import org.webbeans.server.http.WbRequest;

/**
 * {@code /login}, {@code /logout} and {@code /auth/*}: the HTTP face of the pluggable authentication API.
 * <ul>
 * <li>{@code GET /login} renders the {@link LoginPage}; {@code POST /login} handles a form provider.</li>
 * <li>{@code POST /auth/challenge} answers a multi-step challenge.</li>
 * <li>{@code POST /auth/token} signs in with a bearer token (JSON body or {@code Authorization} header).</li>
 * <li>{@code GET /auth/<provider>/start} and {@code /auth/<provider>/callback} run a redirect (OAuth) flow.</li>
 * <li>{@code GET /auth/me} describes the current principal; {@code POST /logout} ends the session.</li>
 * <li>{@code /auth/<prefix>/...} is delegated to the {@link AuthHttpHandler} with that prefix.</li>
 * </ul>
 */
@SuppressWarnings("serial") // servlets are nominally Serializable; these are never serialised
public final class AuthHandler extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AuthHandler.class.getName());
    public static final String SESSION_COOKIE = "WBSESSION";
    static final String CSRF_COOKIE = "WBCSRF";
    private static final String FLOW_COOKIE = "WBFLOW";
    private static final long FLOW_TTL_MS = 10 * 60 * 1000L;

    private final ServerConfig config;
    private final SessionManager sessions;
    private final Map<String, Flow> flows = new ConcurrentHashMap<String, Flow>();

    private static final class Flow {
        final Map<String, Object> state = new LinkedHashMap<String, Object>();
        final String returnUrl;
        final long created = System.currentTimeMillis();

        Flow(String returnUrl) {
            this.returnUrl = returnUrl;
        }
    }

    public AuthHandler(ServerConfig config, SessionManager sessions) {
        this.config = config;
        this.sessions = sessions;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WbRequest r = new WbRequest(req, resp, config);
        try {
            route(r);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Auth request failed: " + r.getMethod() + " " + r.getPath(), e);
            if (!r.isResponded()) {
                r.sendText(500, "Internal error");
            }
        } finally {
            if (!r.isResponded()) {
                r.sendText(404, "Not found");
            }
        }
    }

    private void route(WbRequest r) throws IOException {
        String path = r.getPath();
        String method = r.getMethod();
        if (path.equals("/login")) {
            if (method.equals("GET") || method.equals("HEAD")) {
                renderLogin(r, null, null);
            } else if (method.equals("POST")) {
                formLogin(r);
            } else {
                r.sendText(405, "Method not allowed");
            }
        } else if (path.equals("/logout")) {
            logout(r);
        } else if (path.equals("/auth/me")) {
            me(r);
        } else if (path.equals("/auth/challenge") && method.equals("POST")) {
            challenge(r);
        } else if (path.equals("/auth/token") && method.equals("POST")) {
            tokenLogin(r);
        } else if (path.startsWith("/auth/")) {
            String rest = path.substring("/auth/".length());
            int slash = rest.indexOf('/');
            String id = slash < 0 ? rest : rest.substring(0, slash);
            String action = slash < 0 ? "" : rest.substring(slash + 1);
            Authenticator auth = authenticator();
            AuthenticationProvider p = auth != null ? auth.getProvider(id) : null;
            if (p instanceof RedirectAuthenticationProvider && action.equals("start")) {
                redirectStart(r, (RedirectAuthenticationProvider) p);
            } else if (p instanceof RedirectAuthenticationProvider && action.equals("callback")) {
                redirectCallback(r, p);
            } else {
                for (AuthHttpHandler h : Lookup.getDefault().lookupAll(AuthHttpHandler.class)) {
                    if (id.equals(h.getPathPrefix())) {
                        h.handle(new RequestAdapter(r, "/" + action), new ResponseAdapter(r));
                        return;
                    }
                }
            }
        }
    }

    // ---- login page -----------------------------------------------------------------------------------------

    private static Authenticator authenticator() {
        return Authenticator.getDefault();
    }

    private static LoginPage loginPage() {
        LoginPage p = Lookup.getDefault().lookup(LoginPage.class);
        return p != null ? p : new DefaultLoginPage();
    }

    private String csrfCookie(WbRequest r) {
        String t = r.getCookie(CSRF_COOKIE);
        if (t == null || t.isEmpty() || !t.matches("[A-Za-z0-9_-]{16,64}")) {
            t = SessionManager.randomToken();
            r.setCookie(CSRF_COOKIE, t, -1, true);
        }
        return t;
    }

    private void renderLogin(WbRequest r, Challenge challenge, String errorKey) throws IOException {
        HttpSession existing = currentSession(r);
        String ret = WbRequest.safeReturnUrl(r.getParameter("return"));
        if (existing != null && challenge == null && errorKey == null) {
            r.redirect(config.getContextPath() + ret);
            return;
        }
        Authenticator auth = authenticator();
        List<AuthenticationProvider> providers = auth != null ? auth.getLoginProviders()
                : Collections.<AuthenticationProvider>emptyList();
        Locale locale = r.getLocale();
        String error = errorKey == null ? null : DefaultLoginPage.msg(locale, errorKey);
        LoginPageModel model = new LoginPageModel(providers, challenge, error, locale, csrfCookie(r), ret,
                config.getApplicationTitle(), config.getContextPath());
        r.sendHtml(challenge != null || error != null ? 200 : 200, loginPage().render(model));
    }

    private boolean csrfOk(WbRequest r) throws IOException {
        String form = r.getParameter("csrf");
        String cookie = r.getCookie(CSRF_COOKIE);
        return form != null && cookie != null && form.equals(cookie);
    }

    private void formLogin(WbRequest r) throws IOException {
        if (!csrfOk(r)) {
            renderLogin(r, null, "#ERR_Csrf");
            return;
        }
        Authenticator auth = authenticator();
        String providerId = r.getParameter("provider");
        AuthenticationProvider p = auth != null && providerId != null ? auth.getProvider(providerId) : null;
        if (p == null || p.getLoginMethod() == null || p.getLoginMethod().getKind() != LoginMethod.Kind.FORM) {
            renderLogin(r, null, "#ERR_Unavailable");
            return;
        }
        Credentials creds = formCredentials(p.getLoginMethod(), r.getForm());
        if (creds == null) {
            renderLogin(r, null, "#ERR_Unavailable");
            return;
        }
        LoginOutcome out = auth.login(creds, new RequestAuthContext(r, null, null));
        finish(r, out, r.getParameter("return"));
    }

    /**
     * Maps the posted fields onto credentials: a form with a text and a password field is a
     * {@link PasswordCredentials}; other forms are not supported by this server version.
     */
    static Credentials formCredentials(LoginMethod m, Map<String, String> form) {
        String user = null;
        String pw = null;
        for (LoginField f : m.getFields()) {
            if (f.getKind() == LoginField.Kind.TEXT && user == null) {
                user = form.get(f.getName());
            } else if (f.getKind() == LoginField.Kind.PASSWORD && pw == null) {
                pw = form.get(f.getName());
            }
        }
        if (user == null) {
            return null;
        }
        return new PasswordCredentials(user, pw == null ? "" : pw);
    }

    private void challenge(WbRequest r) throws IOException {
        if (!csrfOk(r)) {
            renderLogin(r, null, "#ERR_Csrf");
            return;
        }
        Authenticator auth = authenticator();
        String id = r.getParameter("challenge");
        if (auth == null || id == null) {
            renderLogin(r, null, "#ERR_ChallengeExpired");
            return;
        }
        Map<String, String> values = new LinkedHashMap<String, String>(r.getForm());
        values.remove("csrf");
        values.remove("challenge");
        values.remove("return");
        LoginOutcome out = auth.answer(new ChallengeResponse(id, values), new RequestAuthContext(r, null, null));
        finish(r, out, r.getParameter("return"));
    }

    /** Turns an outcome into a session + redirect, a challenge page, or an error page. */
    private void finish(WbRequest r, LoginOutcome out, String returnUrl) throws IOException {
        switch (out.getKind()) {
            case AUTHENTICATED:
                String target = establish(r, out.getAuthentication(), returnUrl);
                r.redirect(target);
                break;
            case CHALLENGE:
                renderLogin(r, out.getChallenge(), null);
                break;
            default:
                renderLogin(r, null, out.getMessage() != null ? out.getMessage() : "#ERR_Login");
        }
    }

    /** Creates the session, sets the cookie, and returns the absolute-path URL to continue at. */
    String establish(WbRequest r, Authentication a, String returnUrl) throws IOException {
        HttpSession s = sessions.create(a, r.getLocale(), r.getRemoteAddress());
        r.setCookie(SESSION_COOKIE, s.getId(), -1, true);
        r.setCookie(CSRF_COOKIE, "", 0, true);
        return config.getContextPath() + WbRequest.safeReturnUrl(returnUrl);
    }

    // ---- token ------------------------------------------------------------------------------------------------

    private void tokenLogin(WbRequest r) throws IOException {
        Authenticator auth = authenticator();
        String scheme = null;
        String token = null;
        String ct = r.getHeader("Content-Type");
        if (ct != null && ct.startsWith("application/json")) {
            Map<String, Object> body = Json.parseObject(r.getBodyText());
            scheme = Json.string(body, "scheme");
            token = Json.string(body, "token");
        }
        String authz = r.getHeader("Authorization");
        if (token == null && authz != null && authz.contains(" ")) {
            int sp = authz.indexOf(' ');
            scheme = authz.substring(0, sp);
            token = authz.substring(sp + 1).trim();
        }
        if (auth == null || token == null || token.isEmpty()) {
            r.sendJson(400, "{\"ok\":false,\"error\":\"token required\"}");
            return;
        }
        LoginOutcome out = auth.login(new TokenCredentials(scheme == null ? "Bearer" : scheme, token),
                new RequestAuthContext(r, null, null));
        if (out.getKind() == LoginOutcome.Kind.AUTHENTICATED) {
            String target = establish(r, out.getAuthentication(), r.getParameter("return"));
            r.sendJson(200, "{\"ok\":true,\"redirect\":" + Json.write(target) + "}");
        } else {
            String msg = out.getMessage() != null ? DefaultLoginPage.msg(r.getLocale(), out.getMessage()) : "failed";
            r.sendJson(401, "{\"ok\":false,\"error\":" + Json.write(msg) + "}");
        }
    }

    // ---- redirect flows ---------------------------------------------------------------------------------------

    private String callbackUri(WbRequest r, String providerId) {
        String proto = r.isHttps() ? "https" : "http";
        String host = r.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = r.getHeader("Host");
        }
        if (host == null) {
            host = "localhost:" + config.getPort();
        }
        return proto + "://" + host + config.getContextPath() + "/auth/" + providerId + "/callback";
    }

    private void redirectStart(WbRequest r, RedirectAuthenticationProvider p) throws IOException {
        expireFlows();
        String flowId = SessionManager.randomToken();
        Flow flow = new Flow(WbRequest.safeReturnUrl(r.getParameter("return")));
        flows.put(flowId, flow);
        r.setCookie(FLOW_COOKIE, flowId, FLOW_TTL_MS / 1000, true);
        URI target = p.start(new RequestAuthContext(r, flow.state, null), URI.create(callbackUri(r, p.getId())));
        r.redirect(target.toString());
    }

    private void redirectCallback(WbRequest r, AuthenticationProvider p) throws IOException {
        expireFlows();
        String flowId = r.getCookie(FLOW_COOKIE);
        Flow flow = flowId == null ? null : flows.remove(flowId);
        r.setCookie(FLOW_COOKIE, "", 0, true);
        if (flow == null) {
            renderLogin(r, null, "#ERR_ChallengeExpired");
            return;
        }
        Authenticator auth = authenticator();
        Map<String, String> params = r.getParameters();
        CallbackCredentials creds = new CallbackCredentials(p.getId(), params, params.get("state"), callbackUri(r, p.getId()));
        LoginOutcome out = auth.login(creds, new RequestAuthContext(r, flow.state, null));
        finish(r, out, flow.returnUrl);
    }

    private void expireFlows() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Flow> e : new ArrayList<Map.Entry<String, Flow>>(flows.entrySet())) {
            if (now - e.getValue().created > FLOW_TTL_MS) {
                flows.remove(e.getKey());
            }
        }
    }

    // ---- session endpoints ------------------------------------------------------------------------------------

    /** The valid session of the request's cookie, or null. */
    /** The session table (for the health endpoint and admin views). */
    public SessionManager getSessionManager() {
        return sessions;
    }

    public HttpSession currentSession(WbRequest r) {
        return sessions.get(r.getCookie(SESSION_COOKIE));
    }

    private void logout(WbRequest r) throws IOException {
        HttpSession s = currentSession(r);
        if (s != null) {
            String token = r.getHeader("X-WB-Token");
            if (token == null) {
                token = r.getParameter("csrf");
            }
            if (!s.getCsrfToken().equals(token)) {
                r.sendText(403, "Bad token");
                return;
            }
            sessions.invalidate(s, AuthenticationEvent.LogoutReason.USER);
        }
        r.setCookie(SESSION_COOKIE, "", 0, true);
        if ("application/json".equals(r.getHeader("Accept"))) {
            r.sendJson(200, "{\"ok\":true}");
        } else {
            r.redirect(config.getContextPath() + "/login");
        }
    }

    private void me(WbRequest r) throws IOException {
        HttpSession s = currentSession(r);
        if (s == null) {
            r.sendJson(401, "{\"authenticated\":false}");
            return;
        }
        Principal p = s.getPrincipal();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("authenticated", Boolean.TRUE);
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("displayName", p.getDisplayName());
        m.put("email", p.getEmail());
        m.put("roles", new ArrayList<String>(p.getRoles()));
        m.put("provider", p.getProviderId());
        m.put("locale", s.getLocale().toLanguageTag());
        r.sendJson(200, Json.write(m));
    }

    // ---- AuthHttpHandler adapters -----------------------------------------------------------------------------

    private static final class RequestAdapter implements AuthHttpRequest {
        private final WbRequest r;
        private final String path;

        RequestAdapter(WbRequest r, String path) {
            this.r = r;
            this.path = path;
        }

        @Override
        public String getMethod() {
            return r.getMethod();
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getHeader(String name) {
            return r.getHeader(name);
        }

        @Override
        public Map<String, String> getParameters() {
            try {
                return r.getParameters();
            } catch (IOException e) {
                return Collections.emptyMap();
            }
        }

        @Override
        public String getRemoteAddress() {
            return r.getRemoteAddress();
        }

        @Override
        public String getBody() {
            try {
                return r.getBodyText();
            } catch (IOException e) {
                return "";
            }
        }
    }

    private final class ResponseAdapter implements AuthHttpResponse {
        private final WbRequest r;

        ResponseAdapter(WbRequest r) {
            this.r = r;
        }

        @Override
        public void setHeader(String name, String value) {
            r.setHeader(name, value);
        }

        @Override
        public void send(int status, String contentType, String body) throws IOException {
            r.send(status, contentType, body);
        }

        @Override
        public void redirect(String location) throws IOException {
            r.redirect(location);
        }

        @Override
        public String establishSession(Authentication authentication) throws IOException {
            return establish(r, authentication, r.getParameter("return"));
        }
    }
}
