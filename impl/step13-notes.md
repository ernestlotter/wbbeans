# WebBeans — Step 13 Implementation Notes (v0.1): HTTPS and modules at run time

Two platform additions made after the roadmap proper: TLS termination inside Jetty, and modules that come and go
while the application runs. Source: repository `main` (`ff98d3e` HTTPS, the live-module commit after it);
171 JUnit tests (`HttpsTest` +3, `LiveModulesTest` +3, `ModulesTest` +2); all browser suites green.

## 1. HTTPS

| Where | What |
|---|---|
| `ServerConfig` | `https(port, keyStore, password)`, `keyManagerPassword`, `keyStoreType` (PKCS12/JKS by extension), `httpEnabled`, `httpsRedirect`, `hsts` |
| `WbServer` | an `https` connector (`SslContextFactory.Server` + `SecureRequestCustomizer(false)` — no SNI host check, the browser judges the certificate) next to or instead of the `http` one; `SecuredRedirectHandler` in front of the context when redirecting; `getPort()`/`getHttpsPort()` find connectors by name; the start-up log names both URLs |
| `WbRequest` | `isHttps()` is true for TLS connections too (Secure cookies); `Strict-Transport-Security: max-age=31536000` on HTTPS responses |
| `SelfSignedCertificate` | PKCS12 key store with a self-signed RSA certificate (CN=localhost, SANs localhost + 127.0.0.1) through the JDK's `keytool`; the launcher's fallback and the tests' fixture |
| launcher | `--https-port`, `--keystore`, `--keystore-password` (or `WEBBEANS_KEYSTORE_PASSWORD`), `--key-password`, `--keystore-type`, `--https-redirect`, `--no-http`, `--no-hsts`; `--https-port` alone generates `<datadir>/system/selfsigned.p12` with a warning |

The browser picks `wss://` by itself, so the WebSocket transport needs nothing. A TLS-terminating proxy remains
the alternative (`--trusted-proxy`, `--secure-cookies`). Not done: HTTP/2 (ALPN on JDK 8), ACME renewal.

## 2. Modules at run time (withdrawn in step 14)

> Everything in this section was removed again in step 14 together with the module system itself (`impl/step14-notes.md`): the application is now the class path and a change of code is a restart. The section stays as the record of what was built and verified.

### Platform (`webbeans-core`)

- `Platform.getInstallDir()` = `<datadir>/modules`, scanned at start like a `--modules` directory.
- `installModule(Path)` / `installModule(InputStream, name)`: `describe` the jar (manifest), `retire` the running
  module of that code name (disable it with its transitive dependents — `DefaultModuleSystem.dependents` — and
  forget the install-dir versions), copy to `<codeName>.jar` (skipped when the jar already is that file), `adopt`
  (register the descriptor), `enable` the new module plus the dependents in one batch. The result's
  `getProblem()` says why it did not enable.
- `uninstallModule(codeName)`: only for modules whose location is the install directory; `retire`, `remove` from
  the module system, delete the jar, `refresh()`.
- `enableModule` / `disableModule` (dependents go along); `moduleOf(Class)`; `ModuleInfo.getDisplayName()` resolves
  `#KEY` against `<codeName>.Bundle` while enabled.
- `DefaultModuleSystem.remove`, `findByLocation`, `dependents`.
- `ModuleWatcher`: a `WatchService` on the install directory; events are coalesced until the directory has been
  quiet for a second (a jar still being copied is not read half-way); a created/modified jar → `installModule`,
  a deleted one → `uninstallModule` by file name; the platform's own copies (`installModule` from elsewhere into
  the directory) are recognised and skipped (`consumeSelfWrite`). Started by the launcher unless
  `--no-watch-modules`.

### Sessions

- `UISession.RegistryChangedEvent(clientChanged)`: `WbServer`'s registry listener rebuilds the client bundle,
  compares the hash, and publishes the event to every live session (on their executors).
- `ActionsDecorator`: menu bar, toolbars and shortcuts rebuilt; a HIGH-priority notification "Application
  updated — click to reload" when the bundle changed.
- `WindowManagerImpl.modulesChanged()`: new modes loaded; windows whose `Windows/Components/<id>` entry vanished
  are force-closed and forgotten (and *not* recorded as closed by the user, so they return with the module);
  `openAtStartup` windows of new modules are opened (unless the user closed them in this layout earlier); the
  layout is saved.
- Everything is role-filtered as usual: the event handler runs on the session executor with the session's
  `RegistryContext`.

### The window (`webbeans-modules`, platform jar)

`ModulesTopComponent` (`ModulesTC`, mode `editor`, `roles = admin`; *Tools ▸ Modules…* and the Window menu):
an `OutlineView` of `ModuleNode`s (version, state, location, problem; `Modules/Actions` popup), a toolbar with
*Install module…* (`InstallModuleDialog`: an `Upload` accepting `.jar`, installed on arrival, result shown in the
dialog and as a notification), *Refresh*, and the context actions Enable / Disable / Uninstall (confirmation).
The window follows `ModuleListener` events and `RegistryChangedEvent`.

### Tests

`LiveModulesTest` (core): install → dependent → upgrade in place with the dependent re-enabled → disable with
dependents → enable again → uninstall (jar deleted, dependents disabled but known) → not-a-module rejected;
installed modules survive a restart; the watcher installs a dropped jar, upgrades from a differently named jar
(one module, not two) and uninstalls on delete. `ModulesTest` (modules, `HeadlessUI` over a real `Platform`,
module jar compiled at test time): install → node, start-up window opened, Tools item present; disable → window
closed, item gone, node Disabled; enable → back; upgrade → one node with the new version; uninstall → node gone;
plain users get neither window nor actions. Browser: the demo with the Modules window, `examples/hello`
uploaded through the dialog → row, *Tools ▸ Say Hello*, its dialog; the jar deleted from the directory →
item and row gone, no page errors; all existing e2e suites unchanged.

## 3. Limits

- A module's classes stay loaded while other code holds its objects; `ModuleInstall.close()` has to stop what
  `restored()` started. There is no leak detection.
- The client bundle is per page: new renderers need the reload the notification offers.
- Replacing a module from a `--modules` directory (not the install directory) with the same code name works by
  installing into the install directory; the resolver picks the higher version among duplicates, so a *lower*
  version cannot be installed over one still present in `--modules`.
- Uninstall is refused for modules outside the install directory (class-path or `--modules` modules).
