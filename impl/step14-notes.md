# WebBeans — Step 14 Implementation Notes (v0.1): the class path is the application

The module system of step 1 (and the run-time module loading of step 13) was removed: there are no module
descriptors, no dependency resolution, no per-jar class loaders, no enable/disable and no installation while
running. Whatever is on the JVM class path is the application. Source: repository `main`; 165 JUnit tests
(the module-system, resolver, live-module and Modules-window tests are gone; `PlatformTest` rewritten); all
browser suites green against the new layout.

## 1. What changed

| Where | Before | After |
|---|---|---|
| `org.webbeans.core.modules` | `ModuleSystem`, `DefaultModuleSystem`, `ModuleInfo`, `ModuleClassLoader`, `SystemClassLoader`, `Resolver`, `Dependency`, `SpecificationVersion`, `Problem`, `ModuleListener`, `ModuleInstall` | deleted |
| `org.webbeans.core.Installer` | `ModuleInstall` named in the manifest (`validate`, `restored`, `closing`, `close`) | a plain class found through `META-INF/services/org.webbeans.core.Installer` (`@ServiceProvider(service = Installer.class)`); `restored()` in Lookup order after the Lookup is complete, `closing()`/`close()` in reverse at shutdown; no `validate()` |
| `Platform` | discover (`--modules` dirs, class path, embedded jars) → resolve → enable; `installModule`/`uninstallModule`/`enableModule`/`disableModule`; `ModuleWatcher` | `Config.dataDir`, `Config.classLoader`; `start`: `Registry.rebuild(classLoader)` → `Lookup.getDefault()` = `metaInfServices(classLoader)` + singletons + `RegistryLookup("Services")` → installers; `refresh()`, `shutdown(force)`, `getInstallers()` |
| `Registry` | `rebuild(ModuleSystem, platformLoader)` with per-module provider ownership | `rebuild(ClassLoader)` over every `RegistrationProvider` the loader sees, in class-path order; conflicts logged with the provider class names |
| `Entry` | `getModule()` | `getClassLoader()` (what `Actions` and the `Bundler` use to load classes and resources) |
| `RegistrationBuilder` | `(ModuleInfo, ClassLoader)` | `(String origin, ClassLoader)` |
| `UISession` | `RegistryChangedEvent` | removed (the registry does not change after start-up); `ActionsDecorator` and `WindowManagerImpl` lost their handlers, `WbServer` only invalidates the bundle on a registry change |
| Launcher | `--modules`, embedded `WEBBEANS-INF/modules`, `--no-watch-modules` | none of them: `java -cp 'lib/*' org.webbeans.launcher.Main --datadir data` |
| Packaging | `tools/fatjar.py --modules`, `maven-dependency-plugin` + `exec` writing `WEBBEANS-INF` | the application jars are plain dependencies merged by `maven-shade-plugin` / `fatjar.py --lib`; `build/dist/lib` holds platform, demo and tutorial together |
| Manifests | `WebBeans-Module*` attributes in demo, tutorial, Workbench, `examples/hello` | deleted (no custom manifests at all) |
| `webbeans-modules` | the Modules window | deleted |

The annotation processor's `-Awebbeans.module=<name>` option stays: it only names the package of the generated
`WbRegistrations` class, so two jars never generate the same class.

## 2. Why

Isolation and versioned dependencies between jars of one application solved a problem this platform's users do
not have: an application is built as one Maven reactor and deployed as one class path or one merged jar. The
module layer cost a manifest per jar, a second dependency declaration next to Maven's, class-visibility rules
that surfaced as `ClassNotFoundException`s, and a packaging format (`WEBBEANS-INF`) of its own. Removing it
leaves the registry, the Lookup, the annotations and the lifecycle hooks — the parts that carry the NetBeans
development style — unchanged for application code apart from two mechanical edits: `ModuleInstall` →
`Installer` + `@ServiceProvider`, and manifests deleted.

## 3. What is lost, and the substitute

- Run-time installation/upgrade of jars (step 13): replace the jar and restart.
- A jar failing to load no longer disables just that jar: a bad `RegistrationProvider` is logged and skipped
  (`Registry.collect`), an exception in `Installer.restored()` is logged and the start continues.
- Version checks between jars: Maven's.
- `Entry.getModule()` for "who registered this": `Entry.getClassLoader()`, and the provider class name in the
  conflict log.

## 4. Tests

`PlatformTest` builds two jars at test time (`TestJars`, the former `TestModules` without manifests), puts them
in a `URLClassLoader` and starts the platform on it: services from both routes, the installer's `restored()`
seeing a complete Lookup, registry entries with bundles in two locales, role views, data-dir layout, a vetoed
and a forced shutdown. Browser: the demo and tutorial e2e suites from `build/dist/lib`, the Workbench suites
from the merged jar (its installer seeds `alice`/`bob`), `examples/hello` as a plain jar on the class path.
