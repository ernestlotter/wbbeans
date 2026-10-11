# WebBeans — Sessions, Transport and Delta Protocol (detailed design v0.3)

Roadmap step 2, transport replaced in step 10. JDK 8. **v0.3 (implemented):** the HTTP front end is an embedded **Jetty 9.4** server; a tab talks to its UI over a **WebSocket** (`/wb/ws`) and falls back to **long polling** (`/wb/poll`) when no WebSocket can be opened. The message protocol (§6) is the same on both transports; the UI runtime sees only the `Transport` interface. Packages: `org.webbeans.auth` (see `design/authentication.md`), `org.webbeans.ui` (component model, sessions, delta buffer), `org.webbeans.server` (Jetty, servlets, transports), client `org/webbeans/ui/client/wb-core.js`. Implementation notes: `impl/step2-notes.md` (sessions, protocol), `impl/step10-notes.md` (Jetty, WebSocket).

## 1. HTTP surface (`webbeans-server`)

One `ServletContextHandler` (no servlet sessions — WebBeans has its own) at `ServerConfig.contextPath`:

| Path | Servlet | Purpose |
|---|---|---|
| `GET /` | `AppHandler` | Bootstrap page. No session → 303 `/login?return=…`. Otherwise mints a `uiId`, creates the `UI` in the session and returns a page with inline `window.WB = {uiId, token, locale, base, poll, ws, user, dev}` and the bundle links (`ws` is `null` when the server runs with `--no-websocket`) |
| `GET/POST /login`, `POST /logout`, `/auth/*` | `AuthHandler` | `LoginPage` from the lookup (plain HTML, nothing is rendered before auth), CSRF double-submit cookie, challenge / token / redirect flows, provider `AuthHttpHandler`s, `/auth/me` (authentication.md §8) |
| `GET /wb/ws?ui=<uiId>&token=<token>` | `WebSocketHandler` | The WebSocket transport (§6): one socket per tab, upgrade authenticated by the session cookie **and** the session token |
| `POST /wb/poll` | `PollHandler` | The long-poll transport, the fallback (§6) |
| `GET /wb/app-<hash>.js`, `/wb/app-<hash>.css` | `AppHandler` | Generated client bundle (`Bundler`: `wb-core.js` + every `Renderers/*` resource, `wb.css` + renderer css), immutable cache headers keyed by content hash; `--dev` rebuilds per page load |
| `GET /wb/res/<path>` | `AppHandler` | Icons, fonts, renderer assets from module jars through the system class loader (`.class`, `.properties`, `META-INF`, `..` refused) |
| `GET /wb/blob/<sha256>` | `AppHandler` | Content-addressed payloads from the `BlobStore` (3D geometry, textures), session required |
| `GET <prefix>/…` | `AppHandler` | A `PageProvider` page (the help module's `/wb/help/<id>`), session required, rendered on the session executor |

`WbServer` builds the Jetty `Server` (a `QueuedThreadPool` named `wb-http`, `--http-threads`, one `ServerConnector` on `host:port`, no `Server` header) and exposes it through `getJetty()` so an application can add its own handlers or connectors before `start()`. Jetty logs through `java.util.logging` like the rest of the platform. Session tasks run on a separate fixed `backendThreads` pool (§4). Cookies: `WBSESSION` (HttpOnly, SameSite=Lax, Secure behind HTTPS/`--secure-cookies`), `WBCSRF` (pre-login), `WBFLOW` (redirect flows). Idle timeout 30 min default (`--session-timeout`), max lifetime 12 h, UI grace period 2 min (all overridable by a `SessionPolicy` in the lookup).

## 2. Authentication

Superseded by `design/authentication.md` (pluggable `AuthenticationProvider` SPI, `Authenticator`, `Security`). The server's `SessionManager` creates the `HttpSession` + `UISession` pair on `LoginOutcome.AUTHENTICATED`, runs the policy's revalidation, and calls `Authenticator.logout(..., reason)` on timeout / logout / shutdown / concurrent-session replacement.

## 3. Sessions and UIs

```java
public final class UISession implements Lookup.Provider {
    public static UISession current();                // thread-local, set while a task runs on the executor
    public String getId(); public Principal getPrincipal(); public Authentication getAuthentication();
    public Locale getLocale(); public void setLocale(Locale l);   // publishes LocaleChangeEvent
    public Lookup getLookup();                        // session content + Services/Session instances + Lookup.getDefault()
    public UserFiles getUserFiles(); public UserOverlay getOverlay();
    public Collection<UI> getUIs(); public UI getUI(String uiId); public UI createUI(String uiId);
    public Future<?> access(Runnable r);              // run on the session executor; completes after the flush
    public <T> T getAttribute(Class<T> key); public <T> void setAttribute(Class<T> key, T v);
    public void publish(Object event);                // session-wide event bus (all UIs)
    public <E> Registration subscribe(Class<E> type, Consumer<E> l);
    public void close();                              // disposes all UIs (on the session thread)
}
public final class UI implements Lookup.Provider {
    public static UI current();
    public String getId(); public UISession getSession();
    public Root getRoot();                            // the wb.Root component: TITLE, THEME, KEYMAP; "content"/"overlay" constraints
    public void setContent(Component c); public void setTitle(String t);
    public void navigate(String url); public void reload(); public void logout();
    public Future<?> access(Runnable r);              // = session.access with this UI current
    public Timer schedule(Runnable r, long delayMs); public Timer scheduleAtFixedRate(Runnable r, long initial, long period);
    public State getState();                          // INIT, ATTACHED, DETACHED, CLOSED
    public void connect(Transport t, long lastSeq); public void disconnect(Transport t);   // transport side
    public void handleEvents(List<Object> events);    // transport side, on the session executor
    public void addCloseListener(Runnable r); public void close();
}
public interface UIProvider { void init(UI ui); }     // Lookup.getDefault(); builds the root tree for a new tab
public interface Transport { void send(String json); void close(); }
```

Lifecycle of a `UI`:

1. `GET /` mints `uiId`, `session.createUI(uiId)` registers `UI(INIT)`; the page loads the bundle.
2. The client opens the WebSocket (or, failing that, posts to `/wb/poll`) and says `hello` with `lastSeq = -1`; the server binds the transport, runs `UIProvider.init(ui)` on the session executor and answers with `init` (full tree). State → `ATTACHED`.
3. Connection lost → the client reconnects with backoff. Server side a closed WebSocket puts the UI into `DETACHED` at once (deltas keep accumulating in the `DeltaHistory`); a silent long-poll transport is noticed by the sweeper. Either way a reconnect inside `uiGracePeriod` resumes from the history (or gets a fresh `init` when the gap is too large); after the grace period the UI is closed.
4. Tab closed: `pagehide` sends `bye` (a text frame, or `sendBeacon` to `/wb/poll`); the UI is closed at once. Otherwise by the sweeper.

A `UISession` closes when the HTTP session expires (idle / max lifetime), on logout from any tab (`UI.logout()` posts `/logout`; the other tabs' sockets are closed with code 4410 / their next poll gets 410, and they go to the login page), when revalidation fails, or on shutdown.

## 4. Threading

- **One logical thread per session**: `SerialExecutor` wraps the server's fixed backend pool. Tasks for one session run strictly one at a time, in submission order; N sessions do not cost N threads.
- Every task runs with `UISession.current()` / `UI.current()` set, `LocaleContext` and `RegistryContext` installed (session locale, roles, overlay), and the thread's context class loader set to the `SystemClassLoader`.
- Client events, `access()` tasks and timers are all submitted to the same executor; **all** component mutation must happen there. Setters, `add`/`remove`, `addListener` and `call` check this (`UISession.setStrictThreading`, on by default) and throw `IllegalStateException` off-thread; reads do not check.
- Never block the session thread on the browser: dialogs return futures; long work goes to `RequestProcessor` and posts back with `ui.access`.
- After every task the executor flushes every UI of the session (`UI.flush()` → one `delta` per UI with pending ops) **before** completing the task's future. Over a WebSocket the delta is written asynchronously from the session thread (`RemoteEndpoint.sendString(json, callback)` — never blocking); over long polling an HTTP thread waiting for the task hands it back in the response.
- Jetty's threads (`wb-http`) only parse frames and requests and submit tasks; a WebSocket text frame never runs application code on a Jetty thread.
- `UI.Timer` (`ui.schedule`, `ui.scheduleAtFixedRate`) for server-driven refresh; cancelled automatically when the UI closes.

## 5. Component model and delta buffer

```java
public abstract class Component implements Lookup.Provider {
    public final int getId();                         // UI-unique, assigned on attach
    public final UI getUI();                          // null while detached
    public final <T> T get(Property<T> p);
    public final <T> Component set(Property<T> p, T value);   // no-op if equal; else records delta (encodeProperty hook)
    public final <E extends Event> Registration addListener(EventType<E> type, Listener<E> l);   // records listen
    public final void call(String method, Object... args);     // client-side method invocation; queued until attached
    public Component setVisible/setEnabled/setTooltip/setStyleName/setWidth/setHeight/setTestId(...);
    protected void attached() {}  protected void detached() {}  protected void onEvent(Event e) {}
}
public final class Property<T> { static of(name, type, default); Property<T> clientOwned(); }
public final class EventType<E extends Event> { static of(name, factory); EventType<E> lazy(ms); }
public abstract class ValueComponent<T> extends Component { getValue/setValue, setValidator, setRequired, setReadOnly, addChangeListener; ERROR }
```

- **Attach**: adding a component to an attached container assigns ids to the subtree and records `create` ops parent-first (`{op:"create", id, type, parent, index, c: constraint, props, events}`); removing records one `remove` of the subtree root.
- `DeltaBuffer` (per UI) merge rules: `set` after `create` in the same flush merges into the `create`; `create` followed by `remove` cancels both; repeated `set` keeps the last value; `call`s stay in order and are emitted after structural ops; `listen` ops are merged into a pending `create`.
- **Events** are only forwarded for listened-to types (`events:[...]` on create / `listen` op, with `{type, lazy}` specs); the server drops upstream events with no listener or for unknown components.
- **Client-owned properties** (`TextField.VALUE`, `CheckBox.SELECTED`, `TabbedPane.SELECTED`, …): client-originated `change` events update the server value through `setFromClient` without echoing a `set`; a server `set` always wins and is sent. `ValueComponent` runs the `Validator` and sends `ERROR`.
- Value encoding (`org.webbeans.core.json.Json`): primitives, `String`, enums (name), `List`/`Map`, objects implementing `JsonValue` (`Resource` → `{"$res": path}`, `Layout`), component refs `{"$ref": id}`. `ComboBox` encodes its value as the item key (`encodeProperty`).

## 6. Wire protocol

The same messages travel over both transports. **Downstream** (server → client):

| Type | Shape | Notes |
|---|---|---|
| `init` | `{t:"init", seq, uiId, locale, ops:[create…]}` | Full tree; client discards any existing DOM |
| `delta` | `{t:"delta", seq, ops:[…]}` | One per flush |
| `nav` | `{t:"nav", url}` / `{t:"nav", reload:true}` | reserved (`wb.Root` calls `navigate`/`reload`/`logout` are used instead) |
| `err` | `{t:"err", code, message, fatal}` | Fatal → client shows overlay and stops |
| `ack` | `{t:"ack", ev}` | WebSocket only: the events up to client seq `ev` were handled (long polling acknowledges in the reply) |
| `pong` | `{t:"pong"}` | WebSocket only: answer to a keep-alive `ping` |
| `gone` | `{t:"gone"}` | WebSocket only: the UI or session no longer exists; the socket is then closed with code 4410 and the client goes to the login page |

Ops: `{op:"create", id, type, parent, index, c, props, events}`, `{op:"set", id, props}` (`null` = reset to default), `{op:"remove", id}`, `{op:"call", id, m, args}`, `{op:"listen", id, events}`.

**Upstream** (client → server):

| Type | Shape | Notes |
|---|---|---|
| `hello` | `{t:"hello", lastSeq}` | First message on every (re)connect; binds the transport to the UI; answered with `init` or the replayed deltas |
| `ev` | `{t:"ev", events:[{s, id, type, data}]}` | `s` = client event seq; the handlers' deltas follow |
| `ping` | `{t:"ping"}` | WebSocket only, every 30 s; touches the session and is answered with `pong` |
| `poll` | `{t:"poll", ack}` | Long polling only: held up to `pollWait` (25 s) for server-initiated messages, then answered empty; the client re-issues it immediately |
| `bye` | `{t:"bye"}` | Tab closing: the UI is closed at once |

### 6.1 WebSocket (`/wb/ws`)

- The client opens `ws(s)://<host><base>/wb/ws?ui=<uiId>&token=<token>` right after the page loads. The upgrade carries the browser's cookies; `WebSocketHandler` (a Jetty `WebSocketServlet` with a `WebSocketCreator`) looks up the `HttpSession` from `WBSESSION`, checks the **token** (the per-session CSRF token the page embedded in `window.WB`) and the UI, and refuses with HTTP 401 / 403 / 410 before any socket exists. The token is what stops a page on another origin from opening a socket with the browser's ambient cookies.
- A successful upgrade creates a `WebSocketTransport` (a `WebSocketListener` **and** the UI's `Transport`). Every text frame is one JSON message; every downstream message is one text frame — no batching envelope. Binary frames are refused.
- `hello` binds the transport (`HttpSession.replaceTransport` closes a previous one for the same tab, e.g. after a reload that the server has not noticed yet) and calls `ui.connect(transport, lastSeq)` on the session executor; `ev` calls `ui.handleEvents` and then sends `ack`; `bye` closes the UI. All three detect a closed UI/session and answer `gone`.
- The socket closing (browser gone, network, idle timeout) calls `ui.disconnect(transport)` on the session executor if the transport is still the tab's current one → state `DETACHED`, `detachedAt` set; the sweeper closes the UI after `uiGracePeriod`. The UI closing (`UI.close()`, logout, session end) closes the socket with code 1000 (`closed`); `gone` uses 4410.
- Keep-alive: the client sends `ping` every 30 s and expects traffic within 90 s, otherwise it drops the socket and reconnects (a half-open connection after a network change); the server's WebSocket idle timeout is 5 min (`ServerConfig.webSocketIdleTimeout`), far above the ping interval. Max text frame 1 MB (`maxPollBody`).
- Reconnect: the client reopens the socket with exponential backoff (0.5 s → 30 s) and says `hello` with its last applied `seq`; the server replays from the `DeltaHistory` or re-initialises. Un-acked events are re-queued after the `hello`; the server deduplicates by `s`.
- Why no JSR-356: Jetty's native API (`WebSocketServlet`/`WebSocketListener`) gives the creator step for authentication, async writes with callbacks and policy per servlet without a `ServerContainer`; it also keeps `javax.websocket` off the class path.

### 6.2 Long polling (`/wb/poll`), the fallback

Every request is `POST /wb/poll` with header `X-WB-Token: <session token>` and a JSON body `{uiId, t, ...}`; every reply is `{"messages": [...]}` (downstream messages, possibly empty). Status codes: 401 not logged in, 403 bad token, 410 UI or session gone (client stops and loads `/login`), 409 "hello first" / transport superseded (client reconnects).

- `hello` binds a `PollTransport` (a queue with a wait) and answers with `init` or the replay; `ev` answers with the deltas the handlers produced (that reply is the acknowledgement); `poll` waits up to `pollWait` for server-initiated messages; `bye` is a `sendBeacon` with the token in the query string.
- `seq` is monotonic per UI; the client applies strictly in order and holds out-of-order messages in a `pending` map (an `ev` reply and a `poll` reply may cross on the wire). At most one `ev` request is in flight per tab; events raised meanwhile are batched into the next one — this keeps `s` arriving in order, which the server's dedupe relies on. (Over a WebSocket the single ordered socket makes both unnecessary: events go out at once.)
- A new `hello` replaces the UI's transport; a `poll` waiting on the old one returns 409. The client waits for an in-flight poll to come back before reconnecting so the two never race.
- The sweeper closes a UI whose poll transport has been silent for `uiGracePeriod` + twice `pollWait`.

### 6.3 Choosing the transport (client)

`wb.transport` is a facade over two implementations with the same interface (`hello`, `resync`, `sendEvents`, `bye`, `reconnect`, `drop`); `wb.transport.kind` is `'ws'` or `'poll'`:

1. `window.WB.ws` is `null` (server started with `--no-websocket`) or the browser has no `WebSocket` → long polling.
2. Otherwise the first attempt opens the WebSocket. If it closes **before** it ever opened (a proxy or load balancer that does not forward the upgrade, a blocked port) the client switches to long polling for the life of the page and says so on the console.
3. Once a WebSocket has worked, later failures reconnect the WebSocket (with backoff); a proxy that worked once is assumed to keep working.

`wb.transport.simulateOutage(ms)` drops the connection and refuses to reconnect for `ms` — the hook the browser checks use to exercise reconnect and replay on either transport.

## 7. Security

- Session token (`WB.token`, per HTTP session) checked on every `/wb/poll`, on the WebSocket upgrade (query parameter — the browser cannot set headers on a WebSocket handshake) and on `/logout`; pre-login forms use a double-submit CSRF cookie.
- Every upstream event is validated: component exists in this UI, event type is listened; payload is read through typed accessors (`Event.getString/getInt/...`) and client-owned values go through the component's `fromClient` conversion and `Validator`.
- Role checks happen server-side on actions; unauthorised registry entries are never sent (step 1 §4.4). `Security.currentPrincipal()` resolves through `UISession.current()`.
- Request body / text frame limit 1 MB (`maxPollBody`); pages carry a CSP (`default-src 'self'`, which covers same-origin `ws:`/`wss:` connections in current browsers), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`; Jetty sends no `Server` header.
- No component state is trusted from the client except declared client-owned properties.
- Behind a TLS-terminating proxy the socket is `wss://` from the browser's point of view and plain HTTP between the proxy and Jetty; the proxy must forward the `Upgrade`/`Connection` headers (nginx: `proxy_http_version 1.1; proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade";` with a `proxy_read_timeout` above the 30 s ping interval). `--trusted-proxy` makes `X-Forwarded-For`/`-Proto` believed.

## 8. Client runtime (`wb-core.js`)

Plain ES2017, no framework, no build step: the bundle is the concatenation of `wb-core.js` and the renderer files in registry order. Global `wb` exposes: `register(id, impl)`, `h(tag, cls, text)`, `res(value)`, `textInput(tag)` (shared text-field factory), `sendEvent`, `transport`, `cfg`, `base`, `keymap`, `on/fire` (`'message'`, `'state'`), `nodes`.

| Part | Responsibility |
|---|---|
| transport | WebSocket with `hello` on (re)connect, immediate `ev` frames, `ack` bookkeeping, 30 s `ping`; long-poll fallback with one `ev` in flight and a `poll` loop; exponential backoff reconnect (0.5 s → 30 s) on both; `bye` on `pagehide`; `document.documentElement.dataset.wbState` = connecting / connected / reconnecting / gone, `#wb-conn` banner |
| tree / apply | `Map<id, Node>` (`{id, type, el, props, listens, lazy, renderer, parent, children, constraint}`), ops applied in order, `afterUpdate` per touched node |
| events | `ctx.emit(type, data)` → checked against `listens`; lazy events debounced per `(id, type)`; batched into one `ev` per tick; `s` counter + unacked queue |
| common props | `visible`, `enabled`, `tooltip`, `styleName`, `testId` (→ `data-test`), `width`, `height` applied for every component |
| keys | Global shortcut router driven by `wb.Root.KEYMAP` |
| theme | `--wb-*` custom properties, `data-theme` from `wb.Root.THEME`, `prefers-color-scheme` |

Renderer contract (`wb.register('wb.Button', {...})`):

```js
{
  create(ctx)                            // ctx = {node, emit(type, data)}; returns the root element
  update(el, props, changed, ctx)        // changed = Set of property names; called after create with all props
  insertChild(el, childEl, index, ctx, childNode)   // containers only (childNode.constraint carries the layout hint)
  removeChild(el, childEl, ctx, childNode)
  call(el, method, args, ctx)            // server-invoked
  afterUpdate(el, props, ctx), destroy(el, ctx)      // optional
}
```

Bootstrap: the page contains `window.WB` and `<script src="/wb/app-<hash>.js">`; on `DOMContentLoaded` the core mounts into `#wb-root`, picks the transport and says `hello`.

## 9. Server classes

`WbServer` (Jetty wiring, `getJetty()`, `Security.PrincipalSource`, bundle invalidation on registry change) · `ServerConfig` · `SessionManager` / `HttpSession` (cookie session ↔ `UISession`, one `ServerTransport` per tab, sweeper: idle / max lifetime / revalidation / abandoned UIs) · `ServerTransport` (`Transport` + `isClosed`, `idleMillis`) · `WebSocketTransport` (`WebSocketListener` + transport) · `PollTransport` (queue with a wait) · `Bundler` · `handlers.AppHandler`, `AuthHandler`, `PollHandler` (`HttpServlet`s), `WebSocketHandler` (`WebSocketServlet`) · `http.WbRequest` (wraps `HttpServletRequest`/`Response`: path below the context, cookies, parameters, response helpers with the security headers — the only servlet-aware type the handlers use), `http.RequestAuthContext`.

## 10. Testing

- `UITest` runs `UI`/`UISession` with a recording `Transport` and a direct executor: tree → `init`, delta merge rules, replay vs. `init` fallback, timers, strict threading.
- `WbServerTest` starts the real Jetty server on port 0 and speaks the raw protocol: the long-poll round trip with `HttpURLConnection`, and the WebSocket round trip with Jetty's `WebSocketClient` (refused upgrades without cookie / with a bad token, `hello` → `init`, `ev` → `ack` + `delta`, `ping`/`pong`, a second socket replacing the first with replay, `bye` → 410).
- `webbeans-demo/e2e/transport.js` (Playwright) checks the browser side: `kind === 'ws'`, acknowledgements, an outage with replay over a second socket, the fallback to long polling when the WebSocket never connects, and long polling from the start without a `WebSocket` API. The other e2e scripts drop the socket mid-way (`simulateOutage`) to exercise reconnect and resync.

## 11. Open points

- Push notifications when no `UI` is attached — a `NotificationDisplayer` concern (step 5 delivers them to attached UIs).
- Binary blob frames over the socket (today blobs are fetched with `GET /wb/blob/<sha256>`), `POST /wb/upload`, per-connection send-queue flow control (detach a tab whose socket cannot drain), `GzipHandler` for the bundle — all additions inside `webbeans-server`.
- HTTP/2 and TLS termination inside Jetty (`--https`): Jetty 9.4 supports both; today TLS is the proxy's job.

## 12. Change log

- **v0.3 (step 10)** — Jetty 9.4 replaces the JDK `HttpServer`; WebSocket transport with long-poll fallback; `ack`/`ping`/`pong`/`gone` messages; `ServerTransport`, `WebSocketTransport`, `WebSocketHandler`; `WbRequest` on the Servlet API; `--no-websocket`, `--http-threads`; `WbServer.getJetty()`.
- **v0.2 (step 2)** — long polling on the JDK `HttpServer`, the delta protocol, sessions.
