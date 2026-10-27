# WebBeans — Step 18 Implementation Notes (v0.1): session store and load balancing

Question: "what is the best way to load-balance the server side; can multiple instances run on different
machines with users distributed between them?" The answer — sticky per session, shared data dir, and a way to
keep users signed in across a failover — is written up in `design/authentication.md` §14; the missing piece,
a shared session store, was built here (§8a).

## 1. What was built

| Piece | Content |
|---|---|
| `SessionStore` SPI (`org.webbeans.auth.spi`) | `put / get / touch / remove / list` over `StoredSession` (id, CSRF token, `Authentication` incl. secrets, locale, remote address, node id, created / lastActivity / expiresAt; `toJson` / `fromJson`) |
| `SessionManager` | `create` → `put`; `get(unknown cookie)` → adopt from the store (same id and CSRF token, new `UISession`), logged with the creating node; sweeper `touch`es active sessions (≤ 1 write / 10 s / session); `invalidate` → `remove` except on `SHUTDOWN`; store failures logged, never fatal; the store comes from `ServerConfig.sessionStore` or the Lookup |
| `FileSessionStore` (`webbeans-server`) | the example: one JSON file per session in a shared directory, atomic writes, tolerant reads, expiry honoured on read, pruning once a minute, strict id validation |
| `ServerConfig` | `sessionStore(...)`, `nodeId(...)` (default: host name) |
| Launcher | `--session-store DIR`, `--node-id NAME` |
| `GET /wb/health` | `{"status":"ok","node":…,"sessions":n}` without a session, `Cache-Control: no-store` — for balancers and probes |
| Docs | `design/authentication.md` v0.2 (§8a store, §14 load balancing with HAProxy/nginx/Kubernetes shapes), developer guide §15 (flags, summary), design index |

## 2. Decisions

- **Store the authentication, not the UI.** Moving a live component tree between JVMs would need every
  component to be serialisable and every application model to be shareable; the honest contract is "you stay
  signed in and get a fresh tab", which is what users of any server-side-UI framework get. Layouts come back
  from the shared data dir.
- **Same session id after adoption.** The cookie keeps working without a redirect dance; the CSRF token
  travels with the session so forms in flight still validate.
- **Shutdown keeps sessions in the store** so rolling restarts are invisible; every other end removes them.
- **Expiry is written by the manager** (`expiresAt` = min of idle, max lifetime and the authentication's own
  expiry) so a dumb store (files, a key/value TTL) can enforce it without knowing the policy.
- **Secrets are stored.** An OIDC refresh token that did not survive the failover would make the adopted
  session useless at the next revalidation; the directory must be private to the server user (documented).

## 3. Verified

- `SessionStoreTest`: two `SessionManager`s over one `FileSessionStore` — create on A, adopt on B with the full
  principal (roles, attributes, provider, e-mail), locale, address, methods and secrets; logout on B removes
  the file; shutdown of A keeps it; a 2 s idle policy expires it for the store; bad ids never reach the file
  system. 175 JUnit tests green.
- Manual, with the demo: `--session-store … --node-id demo-1`, login → one JSON file; restart as `demo-2` →
  the old cookie answers `200` and the log reads "adopted from the session store (created on demo-1)";
  `/wb/health` reports the node and session count.
