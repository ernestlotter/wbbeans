# WebBeans — Step 2 Implementation Notes (v0.1)

What was built for roadmap step 2 (UI runtime, delta protocol, authentication, HTTP server, launcher, demo), how it maps to `design/sessions-and-delta-protocol.md`, `design/standard-components.md` and `design/authentication.md`, and where the implementation deviates. Source: `webbeans-step2.zip` (Maven multi-module, JDK 8; 54 JUnit tests plus a Playwright browser check).

## 1. What exists

| Module / package | Classes | Design |
|---|---|---|
| `webbeans-auth` — `org.webbeans.auth` | `Authenticator` (+`getDefault()`), `Authentication`, `Principal`, credentials (`PasswordCredentials`, `TokenCredentials`, `HeaderCredentials`, `CallbackCredentials`, `ChallengeResponse`), `Challenge`, `LoginField`, `LoginMethod` (form / redirect / script), `AuthenticationResult`, `LoginOutcome`, `AuthContext` (+`SimpleAuthContext`), `AuthenticationEvent`, `LoginPageModel`, `Security` (`hasRole`/`checkRole`/`isAllowed`), `PasswordHash` (PBKDF2), `PasswordTool` | authentication.md §2–6 |
| `org.webbeans.auth.spi` | `AuthenticationProvider`, `RedirectAuthenticationProvider`, `TokenAuthenticationProvider`, `RoleMapper`, `Authorizer`, `SessionPolicy`, `AuthenticationListener`, `LoginThrottle`, `LoginPage`, `AuthHttpHandler` (+ `http.AuthHttpRequest/Response`) | authentication.md §3 |
| `org.webbeans.auth.impl` / `.providers` | `DefaultAuthenticator` (provider chain, throttle keys `ip:`/`user:`, pending challenges, role-mapper chain, listeners), `DefaultLoginThrottle`, `DefaultSessionPolicy`, `DefaultLoginPage`; `PropertiesAuthenticationProvider` (id `local`, `system/users.properties`), `HeaderAuthenticationProvider` | authentication.md §7 |
| `webbeans-ui` — `org.webbeans.ui` | `Component`, `Container`, `Property<T>` (`clientOwned`), `EventType<E>` (`lazy`), `Event`, `ClickEvent`, `ChangeEvent<T>`, `Listener`, `Registration`, `Validator`, `ValueComponent<T>`, `Resource`, `Size`, `JsonCodec`, `DeltaBuffer`, `DeltaHistory`, `Transport`, `UIProvider`, `SerialExecutor`, `UISession`, `UI` (+`Root`, `Timer`) | sessions-and-delta-protocol.md §1–5 |
| `org.webbeans.ui.components` | `Label`, `Html` (sanitised), `Link`, `Image`, `Button`, `TextField`, `TextArea`, `CheckBox`, `ComboBox<T>`, `Item`, `Panel` + `Layout` (Fill/Border/Flow/Grid/Form/Stack), `Spacer`, `ProgressBar`, `TabbedPane` | standard-components.md |
| `org/webbeans/ui/client/` (resources) | `wb-core.js` (node tree, delta application, event queue, long-poll transport, keyboard router, theme), `renderers/*.js` (one per component, registered through `@Renderer`), `wb.css` (`--wb-*` tokens, light/dark) | sessions-and-delta-protocol.md §6, standard-components.md §1 |
| `webbeans-server` — `org.webbeans.server` | `ServerConfig`, `WbServer` (JDK `HttpServer`), `SessionManager` + `HttpSession` (cookie sessions, sweeper), `PollTransport`, `Bundler`; `handlers.AppHandler` (`/`, `/wb/app-<hash>.js|css`, `/wb/res/*`), `handlers.AuthHandler` (`/login`, `/logout`, `/auth/*`), `handlers.PollHandler` (`/wb/poll`); `http.WbRequest`, `http.RequestAuthContext` | sessions-and-delta-protocol.md §7, authentication.md §8 |
| `webbeans-launcher` | `Main` (options, `Platform.start`, first-start `admin` account, shutdown hook) | architecture.md §9 |
| `webbeans-demo` | `DemoUIProvider` (`@ServiceProvider(service = UIProvider.class)`, `@Messages`, `Bundle_de.properties`), module manifest | — |

Tests: `UITest` (tree → `init`, property/event round trip, delta merging, history replay vs. full resync, timers, strict threading, client-owned values), `AuthenticatorTest` (providers, challenges, throttling, role mapping, header SSO), `WbServerTest` (the raw HTTP protocol end to end: login form + CSRF, bootstrap page, bundle, `hello`/`ev`/`poll`/`bye`, duplicate events, replay by sequence, 410 for unknown UIs, logout with token, resource path hardening), plus the step-1 suites. `webbeans-demo/e2e/e2e.js` drives a real Chromium through login, validation, events, server timers, tabs, locale switch, a second tab, a forced reconnect with replay, and logout of both tabs.

## 2. Transport: JDK server + long polling now, Jetty later

The user chose to start with the JDK's built-in `com.sun.net.httpserver` (no Maven Central in the sandbox) and move to Jetty 9.4 + WebSocket afterwards. Everything above the transport is unaffected by that move:

- `org.webbeans.ui.Transport` (`send(json)`, `close()`) is all the `UI` knows. The server binds one `PollTransport` per `hello`; a WebSocket transport implements the same two methods.
- The client runtime isolates the transport in one object (`wb.transport`: `hello`, `loop`, `sendEvents`, `fail`, `bye`); the WebSocket version replaces that object only.
- Wire protocol over long polling (`POST /wb/poll`, JSON `{uiId, t, ...}`, header `X-WB-Token`):
  - `hello {lastSeq}` → `{messages:[init | replayed deltas]}`; binds a fresh transport, closing the previous one.
  - `ev {ack, events:[{s,id,type,data}]}` → `{messages:[deltas produced by the handlers]}`.
  - `poll {ack}` → waits up to `pollWait` (25 s) for server-initiated messages, else `{messages:[]}`.
  - `bye` (sent with `sendBeacon` on `pagehide`) → 204, UI disposed.
  - 401 not logged in, 410 UI/session gone (client goes to `/login`), 409 "hello first" / transport superseded (client reconnects), 403 bad token.
- Ordering: at most one `ev` request is in flight per tab; further events are batched into the next request. Downstream messages may arrive out of order across the `ev` and `poll` responses, so the client holds a `pending` map keyed by `seq` and applies in order. Server-side, `UISession.access()` completes its future only after the deltas have been flushed to the transport, so the HTTP thread that waited for the handlers sees their output.
- Lifetimes: `SessionManager` sweeps every 10 s — idle timeout / max lifetime from `SessionPolicy` (else `ServerConfig`), provider `revalidate` on its interval, and UIs whose tab stopped polling for `uiGracePeriod` (2 min) are closed. A reconnect inside the grace period replays from `DeltaHistory` (64 messages / 2 MB), otherwise a fresh `init` is sent.

## 3. HTTP endpoints (server)

| Path | Purpose |
|---|---|
| `GET /` | bootstrap page: creates a `UI` in the caller's session, `window.WB = {uiId, token, locale, base, poll, user, dev}`; redirects to `/login?return=` without a session |
| `GET /login`, `POST /login` | `LoginPage` from the lookup (default self-contained HTML); form providers post `provider`, `csrf` (double-submit cookie `WBCSRF`), `return`, fields |
| `POST /auth/challenge` | answers a multi-step challenge (`challenge` id + fields) |
| `POST /auth/token` | JSON `{scheme, token}` or `Authorization` header → `TokenCredentials`; replies `{ok, redirect}` |
| `GET /auth/<id>/start`, `/auth/<id>/callback` | `RedirectAuthenticationProvider` flows; flow state kept server-side under the `WBFLOW` cookie (10 min) |
| `GET /auth/me` | principal as JSON (401 without session) |
| `/auth/<prefix>/...` | delegated to the `AuthHttpHandler` with that prefix; `establishSession()` creates the cookie session |
| `POST /logout` | needs the session token (`X-WB-Token` header or `csrf` field); `UI.logout()` posts it from a tab |
| `POST /wb/poll` | the transport (above) |
| `GET /wb/app-<hash>.js|css` | the bundle: `wb-core.js` + every `Renderers/*` resource (+ `wb.css` + renderer css), immutable cache by content hash; `--dev` rebuilds per page load |
| `GET /wb/res/<path>` | class-path resources through the system class loader; `.class`, `.properties`, `META-INF` and `..` refused |

Cookies are `HttpOnly; SameSite=Lax` (+`Secure` behind HTTPS or `--secure-cookies`); responses carry `Cache-Control: no-store`, `X-Content-Type-Options`, a CSP (`default-src 'self'`) and `X-Frame-Options: DENY` on pages. `X-Forwarded-For` is honoured only from `--trusted-proxy` addresses.

## 4. Deviations from the design (and why)

1. **Long polling instead of WebSocket, JDK server instead of Jetty** — by decision (sandbox without Central); the design's §7 Jetty/WebSocket text now describes the next iteration. `ServerConfig` already carries what Jetty needs (host, port, context path, proxies, cookie flags).
2. **No Jackson** — `org.webbeans.core.json.Json` (a small parser/writer with typed accessors) serves the protocol; it stays JDK-only and keeps modules free of a JSON dependency. Objects implementing `JsonValue` (`Resource`, `Layout`) serialise themselves; `Component` references become `{"$ref": id}`.
3. **`UI.Root` is a component** (`wb.Root` renderer: title, theme, keymap, `content`/`overlay` constraints) rather than special-cased messages; `UI.navigate/reload/logout` are calls on it.
4. **Form credentials are mapped generically**: a `LoginMethod.form()` with a text and a password field becomes `PasswordCredentials` (field names are taken from the method, so providers can rename them). Other form shapes need a provider-specific `AuthHttpHandler`; a `FormCredentials` type can be added if a provider needs it.
5. **Registrations are written per round**: the processor now writes `WbRegistrations`, `WbRegistrations2`, … at the end of each round that contributed annotations and the `META-INF/services` file in the final round — javac warns (fatal with `-Werror`) when a type is created in the last round. `Bundle.properties` lives next to the sources (NetBeans convention) because the processor merges `@Messages` keys into it through `SOURCE_PATH`; Maven copies `src/main/java/**/*.properties` as resources first.
6. **`Component.get()` does not check the thread** (reads from tests/other threads are harmless); every mutation does when strict threading is on.
7. **`UISession` takes its backend `Executor`** from the server's fixed pool (`--backend-threads`, default 2×cores) rather than a `RequestProcessor`; `SerialExecutor` guarantees one task per session at a time and `isCurrentThread()` for the threading check.

## 5. Notes for step 3 (window system)

- `UI.getRoot()` accepts children with constraint `"overlay"` for dialogs/toasts; `UI.Root.KEYMAP` is the keyboard-router table the window system fills from action registrations.
- `UISession.publish/subscribe` is the session bus; `LocaleChangeEvent` is already used by the demo to re-text components — the window system should offer a `Localizable` hook so components re-read their bundles automatically.
- `UISession.getOverlay()` / `UserFiles.layoutFile(name)` are where per-tab window layouts go (`layout.json` per named layout).
- The bundler reads `Renderers/*` from the registry; window-system renderers register the same way and nothing in the server changes.
- Jetty: implement `WebSocketTransport implements Transport`, a `WebSocketServlet` doing what `PollHandler` does (hello/ev on the socket; no poll needed), reuse `SessionManager`, `AuthHandler` and `AppHandler` behind servlet adapters (`WbRequest` is the only JDK-specific type), and swap `wb.transport` in the client.
