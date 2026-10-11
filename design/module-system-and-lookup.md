# WebBeans — Module System, Registry and Lookup (detailed design v0.3)

Roadmap step 1. Everything here is JDK 8. Packages: `org.webbeans.core.modules`, `org.webbeans.core.registry`, `org.webbeans.core.lookup`, `org.webbeans.core.i18n`, `org.webbeans.core.files`.

**v0.3 changes** (implementation feedback, see `impl/step1-core-notes.md`): `@Messages` is WebBeans' own annotation with session-locale `Bundle` classes (§7); `RegistryLookup.forPath` replaces routing `Lookups.forPath` through the registry (§5); builder API names (`supplier`, `separatorAt`), later-wins merge, properties-file overlay (§4); processor extension SPI (§6).
**v0.2 changes**: Lookup and i18n are taken from the NetBeans Platform libraries instead of being re-implemented (§5, §7). The XML layer filesystem is dropped; registrations are annotation-only and land in a path-keyed `Registry` (§4).

## 1. Module descriptor

A module is a jar. All metadata is in `META-INF/MANIFEST.MF`:

| Attribute | Required | Meaning |
|---|---|---|
| `WebBeans-Module` | yes | Code name base, Java-package style, e.g. `org.example.accounts` |
| `WebBeans-Module-Version` | yes | Specification version, dotted integers `1.2.0` |
| `WebBeans-Module-Dependencies` | no | Comma-separated `codename [> ver | = ver]` |
| `WebBeans-Module-Public-Packages` | no | Comma-separated packages; `org.example.api.*` for subpackages; `-` for none (default: none) |
| `WebBeans-Module-Install` | no | FQCN of `ModuleInstall` subclass |
| `WebBeans-Module-Display-Name` | no | Human name; `#KEY` refers to the module's `Bundle.properties` |
| `WebBeans-Module-Autoload` | no | `true`: enabled only if another module depends on it |
| `WebBeans-Module-Eager` | no | `true`: enabled as soon as all dependencies are satisfiable |
| `WebBeans-Module-Requires` / `-Provides` | no | Capability tokens |
| `WebBeans-Module-Class-Path` | no | Wrapped library jars inside the module (`lib/foo.jar`, space separated) |

Version matching: `>` means "at least" (same major, `compareTo >= 0`); `=` means exact. A major-version bump is incompatible, as in NetBeans.

```java
public final class ModuleInfo {                          // ModuleInfo.fromManifest(Manifest, File)
    public String getCodeName();
    public SpecificationVersion getVersion();           // WebBeans' own (NetBeans' is in org-openide-modules)
    public List<Dependency> getDependencies();          // MODULE and REQUIRES entries
    public Set<String> getPublicPackages();  public boolean isPublicPackage(String pkg);
    public boolean isEnabled();  public ClassLoader getClassLoader();  public Problem getProblem();
    public Map<String, String> getAttributes();  public File getLocation();
}
public final class Dependency {
    public enum Comparison { AT_LEAST, EXACT, ANY }
    public enum Type { MODULE, REQUIRES }
    public String getName(); public Comparison getComparison(); public SpecificationVersion getVersion();
    public boolean isSatisfiedBy(SpecificationVersion actual);
}
```

## 2. Module manager and lifecycle

```java
public abstract class ModuleSystem {
    public static ModuleSystem getDefault();            // Lookup.getDefault().lookup(ModuleSystem.class)
    public abstract Collection<ModuleInfo> getModules();
    public abstract List<ModuleInfo> getEnabledModules();   // dependency order
    public abstract ModuleInfo find(String codeName);
    public abstract void enable(Collection<ModuleInfo> modules);   // batch + its transitive dependencies
    public abstract void disable(Collection<ModuleInfo> modules);  // IllegalArgumentException if depended upon
    public abstract SystemClassLoader getSystemClassLoader();
    public abstract boolean closing();  public abstract void close();
    public abstract void addModuleListener(ModuleListener l);
}
```

**Discovery** (`DefaultModuleSystem.discover(dir)`, `discoverClasspath()`): jars on the platform class path whose manifest has `WebBeans-Module` (development convenience; visibility rules are not enforced for them because the platform loader already sees them) and every `*.jar` in `--modules` directories (default `modules/`). Duplicate code names: higher version wins, the other is reported as `DUPLICATE`.

**Resolution** (`Resolver`, pure function → `Resolution { enabled (ordered), problems, notWanted }`): duplicates; Tarjan SCC cycle detection (`CYCLE`); the largest set closed under `MODULE` dependencies and `REQUIRES` tokens (fixed point; `MISSING_DEPENDENCY`, `VERSION_MISMATCH`, `MISSING_REQUIRES`, `DEPENDENCY_DISABLED`); autoload modules only if an enabled module depends on them (otherwise `notWanted`, no problem), eager modules whenever satisfiable; Kahn topological sort with ties by code name. Problems are reported via `ModuleListener.moduleDisabled(info, Problem)` and `ModuleInfo.getProblem()` — never thrown.

**Lifecycle**, in topological order:

```java
public class ModuleInstall {
    public void validate() throws IllegalStateException {}  // veto enable before classes are used → INSTALL_FAILED
    public void restored() {}                                // after the whole batch is loaded and the registry rebuilt
    public boolean closing() { return true; }                // may veto shutdown
    public void close() {}
}
```

`enable(batch)`: resolve → per module: create class loader, instantiate `ModuleInstall`, `validate()` → `ModuleListener.modulesLoaded(batch)` (the platform rebuilds the `Registry` and re-targets `Lookup.getDefault()` here) → `restored()` each → `modulesEnabled`. `disable` is the reverse (`closing()`, `close()`, loader closed) and only for modules nothing enabled depends on. Hot reload is out of scope for v1.

## 3. Class loading

```
                 PlatformClassLoader   (webbeans-core, webbeans-ui, NetBeans util/util-lookup, jetty, jackson …)
                        │
      ┌─────────────────┼──────────────────┐
 ModuleClassLoader   ModuleClassLoader   ModuleClassLoader   (one per enabled module)
   org.a (api)   ◄──── org.b depends on org.a
```

`ModuleClassLoader extends URLClassLoader` (parallel capable):

- `loadClass(name)`: (1) already loaded, (2) the platform (parent) — platform-first for everything, so modules cannot shadow platform classes, (3) own jar + wrapped libraries (extracted to `<datadir>/cache/modules/<module>-<version>/`), (4) each declared dependency's loader **restricted to that module's public packages** (re-export through a dependency's own dependencies is allowed when the package is public there); otherwise `ClassNotFoundException` whose message names the fix ("the class is in module X — add a dependency on it" / "module X does not export package Y").
- `getResource(s)`: same order; `META-INF/` resources of dependencies are **never** filtered so `ServiceLoader` and NetBeans' `MetaInfServicesLookup` work across modules; other resources follow the public-package rule.
- `SystemClassLoader` (platform, then every enabled module that owns the class) is published in `Lookup.getDefault()` as `ClassLoader.class` and is what `Lookups.metaInfServices` runs over, so `Lookup.getDefault()` sees every module's `@ServiceProvider`s. The same `Class` object results whether a class is loaded through the system loader or through a dependent module.

## 4. Registry (replaces `layer.xml`)

### 4.1 Rationale

NetBeans' layer filesystem gives three things worth keeping: **path-keyed registration** (`Menu/File`, `Toolbars/Main`, `Windows/Modes`), **ordering and masking across modules** (`position`, `_hidden`), and a **per-user overlay** (customised menus). It also brings XML, a virtual filesystem API and reflective `instanceCreate` plumbing we do not need. WebBeans keeps the first three and drops the rest: registrations are declared only with annotations; the processor emits a generated Java class per module; nothing is hand-written and nothing is parsed at runtime.

### 4.2 API

```java
public final class Registry {                        // shared; immutable between rebuilds
    public static Registry getDefault();             // from Lookup.getDefault(); an empty registry before the platform starts
    public Folder folder(String path);               // "Menu/File" — a view evaluated against RegistryContext.current()
    public Entry entry(String path);                 // null if absent or not visible to the current context
    public <T> List<T> instances(String path, Class<T> type);
    public void rebuild(ModuleSystem modules, ClassLoader platform);   // collects RegistrationProviders of the platform + enabled modules
    public void addChangeListener(ChangeListener l); // fires on rebuild
}
public interface Folder {
    String getPath(); String getName(); String getDisplayName(); Map<String,Object> getAttributes(); boolean exists();
    List<Folder> getFolders();                       // ordered by position (overlay applied), then name
    List<Entry> getEntries();                        // ordered; hidden, role-filtered and overlay-hidden entries absent
    Entry getEntry(String name);
    <T> List<T> instances(Class<T> type);            // follows references, skips separators, uses instanceOf to avoid instantiating
}
public interface Entry {
    enum Kind { INSTANCE, REFERENCE, SEPARATOR }
    String getPath(); String getName(); String getFolderPath(); Kind getKind(); int getPosition();   // overlay applied
    ModuleInfo getModule(); Set<String> getRoles(); Class<?> getInstanceOf();
    Map<String, Object> getAttributes();             // "#KEY" resolved for the context locale; references inherit their target's
    String getString(String attr); String getDisplayName();
    Entry getTarget();                               // the referenced entry (null if dangling) / this
    <T> T getInstance(Class<T> type);                // singleton per entry, null on failure (logged) or type mismatch
    <T> T newInstance(Class<T> type);                // fresh instance (per-session services)
}
public abstract class RegistryContext {              // thread-local; the session executor installs one per task
    public static RegistryContext current();  public static void run(RegistryContext c, Runnable task);
    public static RegistryContext of(Set<String> roles /* null = all */, Locale locale, UserOverlay overlay);
    public static final RegistryContext ALL;         // default outside sessions
}
```

### 4.3 Generated registration

The annotation processor emits, per module, `<module>.generated.WbRegistrations` implementing `RegistrationProvider` and lists it in `META-INF/services/org.webbeans.core.registry.RegistrationProvider`:

```java
public final class WbRegistrations implements RegistrationProvider {
    public void register(RegistrationBuilder b) {
        b.instance("Actions/File/org-example-accounts-SaveAction")
            .supplier(org.example.accounts.SaveAction::new)         // method ref, no reflection
            .instanceOf(java.lang.Runnable.class)
            .bundle("org.example.accounts.Bundle")                   // resolves #KEY attributes
            .attr("displayName", "#CTL_Save")
            .attr("async", Boolean.TRUE)
            .position(300)
            .roles("admin");
        b.reference("Menu/File/org-example-accounts-SaveAction", "Actions/File/org-example-accounts-SaveAction").position(300);
        b.separatorAt("Menu/File/separator-org-example-accounts-SaveAction-350").position(350);
        b.hide("Menu/File/org-webbeans-ide-OldAction");             // masks another module's entry (or a whole folder)
    }
}
```

- Factories: `supplier(Supplier)`, `factory(Function<Entry,?>)` (static methods taking the `Entry`), `value(Object)`. `instanceOf` lets `instances(type)` filter without instantiating.
- `RegistrationBuilder` is public: hand-written providers and `ModuleInstall` code may register programmatically; `folder(path)` sets folder attributes/position.
- Merge: providers are applied platform first, then modules in dependency order; an equal path from a later module replaces the earlier one with a warning (a dependent overriding its dependency); `hide` masks regardless of order. Position ties are sorted by name.

### 4.4 Sessions, roles, locale, user overlay

- The merged tree is shared and immutable; `Folder`/`Entry` are live views evaluated against `RegistryContext.current()`: `roles` filter (an entry with roles is visible iff the user has one of them; references to filtered targets disappear too), `#KEY` attributes resolved through the registering module's loader and bundle for the context locale, and the user's `UserOverlay` (hide / reorder).
- The overlay is `users/<user>/config/registry.properties` (`<path>=hidden` or `<path>=<position>`), written by UI customisation (hide a toolbar button, reorder a menu). It can only hide or reorder, never add.
- There is no writable "config file system" and no `FileObject` API. Modules that need per-user files use `UserFiles` (architecture §6).

## 5. Lookup — NetBeans `org-openide-util-lookup`

**Decision**: depend on `org.netbeans.api:org-openide-util-lookup` (Apache 2.0, zero dependencies) rather than re-implement: `Lookup`, `Lookup.Result`/`Template`/`Item`, `Lookups` (`fixed`, `singleton`, `proxy`, `exclude`, `metaInfServices`), `AbstractLookup` + `InstanceContent`, `ProxyLookup`, `@ServiceProvider`(s) with its annotation processor and the `#position` / `#-masking` `META-INF/services` syntax. Companion `org-openide-util` supplies `NbBundle`, `RequestProcessor`, `WeakListeners`, `ChangeSupport`, `Mutex`, `Parameters`.

Version: pin `RELEASE126` (NetBeans 12.6, the last release whose platform targets JDK 8; verified: the jars built from the 12.6 sources compile with `--release 8`).

Integration points:

- `Lookup.getDefault()` is pluggable in NetBeans through `META-INF/services/org.openide.util.Lookup`. `webbeans-core` installs `WbDefaultLookup`, a `ProxyLookup` that is also a `Lookup.Provider` so the platform can re-target it (`setContent`) once modules are loaded:

```
Lookup.getDefault()  = ProxyLookup(
      Lookups.metaInfServices(SystemClassLoader),   // every module's @ServiceProvider
      Lookups.fixed(Platform, ModuleSystem, Registry, SystemClassLoader, DataDir),
      RegistryLookup(registry, "Services"))         // registry-declared services

UISession.getLookup() = ProxyLookup(
      InstanceContent(Principal, Locale, UISession, UserFiles),
      sessionServices,                              // registry "Services/Session" entries, Entry.newInstance per session
      Lookup.getDefault())

UI.getLookup()        = ProxyLookup(InstanceContent(UI, WindowManager), session lookup)

Utilities.actionsGlobalContext() (per UI) = ProxyLookup(activeTopComponent.getLookup()), retargeted on activation
```

- `RegistryLookup.forPath("Services/Foo")` is the WebBeans counterpart of `Lookups.forPath`: a `Lookup` over a registry folder (cached per path, lazy `AbstractLookup.Pair`s, `instanceOf` filtering without instantiation, unfiltered `ALL` view since a Lookup cannot vary per session). NetBeans' own `Lookups.forPath` is left alone: its `NamedServicesProvider` SPI is sealed to NetBeans classes, so it keeps resolving `META-INF/namedservices/`.
- Before the platform starts, `Lookup.getDefault()` is `Lookups.metaInfServices` over the core's loader, so tests and tools see class-path `@ServiceProvider`s as usual; `Platform.shutdown` restores that.
- Threading contract is NetBeans': results are weakly held, listeners fire on the mutating thread, so UI-bound listeners hop with `ui.access(...)`.
- Not adopted: `org-openide-filesystems` (no layers), `org-openide-nodes`/`org-openide-explorer` (Swing-bound; own Nodes API in step 4), `org-openide-modules` (own module system; own `SpecificationVersion`).

## 6. Annotations and the processor (`webbeans-processor`)

All WebBeans annotations are `@Retention(SOURCE)`. `@ServiceProvider` is NetBeans' and handled by NetBeans' processor; `@Messages` is WebBeans' (§7). The processor (`org.webbeans.processor.WbProcessor`, registered in `META-INF/services/javax.annotation.processing.Processor`) is a small framework:

- Core handlers: `@Registration` / `@Registrations` on classes (public no-arg constructor) and public static methods (no parameter or one `Entry`), with `path`, `name` (default `org-example-Foo`), `position`, `type` (= `instanceOf`), `attrs` (`key=value`; `true`/`false` → `Boolean`, integers → `Integer`, `#KEY` → bundle key), `roles`, and `references` (`@Registration.Reference(path, position, separatorBefore/After)`); `@Hide({paths})` on classes or `package-info`; `@Renderer(id, resource, css)` → `Renderers/<id>` whose instance is the component class.
- Extension SPI `org.webbeans.processor.spi.AnnotationHandler` (`getSupportedAnnotationTypes()`, `handle(Element, AnnotationMirror, RegistrationModel, ProcessingEnvironment)`), discovered through `META-INF/services` on the processor path. Platform API modules ship handlers for their own annotations — handlers read annotation mirrors by name, so they need no compile dependency on the annotation classes:

| Annotation (owner) | Registration |
|---|---|
| `@ActionID` + `@ActionRegistration`, `@ActionReference(s)` (api.actions, step 5) | `Actions/<category>/<id>` instance with `Actions.context` / `alwaysEnabled` factory; references (+ separators) |
| `@TopComponent.Registration` + `.Description`, `.OpenActionRegistration` (api.windows, step 3) | `Windows/Components/<id>`, `Windows/Modes/<mode>/<id>`, open action |
| `@Mode.Registration` (api.windows) | `Windows/Modes/<name>` |
| `@NodeFactory.Registration`, `@OptionsPanelController.*Registration`, `@StatusBarCell.Registration` … | corresponding entries |

- `RegistrationModel` collects `EntrySpec`s (path, kind, factory expression, attrs as Java expressions, bundle, position, roles, origin) and messages; it reports errors at the annotated element (not public, no no-arg constructor, duplicate path, malformed attribute, bad message key). The writer emits `<module>.generated.WbRegistrations` in the last round (module = `-Awebbeans.module=<code name>`, passed by the Maven plugin; default: the longest common package of the annotated elements) plus the `META-INF/services` line; nothing is written when nothing is registered or when errors occurred.

## 7. i18n — session locale over NetBeans bundles

`org-openide-util`'s `NbBundle.getBundle(name, locale, loader)` does the work — bundle lookup through the module loader, branding (`Bundle_<brand>.properties`), the locale suffix chain, caching. What NetBeans cannot do is a locale per user: `NbBundle.getMessage` and NetBeans' generated `Bundle` classes are bound to `Locale.getDefault()`, which is JVM-wide. Hence:

- `LocaleContext` — a thread-local locale (`get()` falls back to `Locale.getDefault()`); the session executor (step 2) runs every task inside `LocaleContext.run(session.getLocale(), …)`.
- `Bundles.getMessage(Class, key, args…)` — `NbBundle.getBundle` for `LocaleContext.get()` plus `MessageFormat`; `Bundles.resolve(loader, bundle, "#KEY", locale)` for registry attributes.
- `@org.webbeans.core.i18n.Messages({"CTL_Save=Save", "MSG_N={0} items"})` — same syntax as NetBeans'; the processor generates the package-private `Bundle` class (`Bundle.CTL_Save()`, `Bundle.MSG_N(Object)`) calling `Bundles.getMessage`, and merges the keys with the package's source `Bundle.properties` into the class output (reproducibly, no timestamps). Translations go into `Bundle_<locale>.properties` as usual. The only visible difference from NetBeans is the import.
- Registry `#KEY` attributes and `WebBeans-Module-Display-Name` resolve through `Bundles.resolve` with the context locale at view time, so display names are localised per session; a missing key is logged and shown as `#KEY`.

## 8. Startup sequence (`Platform.start(Config)`)

1. `DataDir` (`users/`, `system/`, `cache/`) from `--datadir`.
2. `DefaultModuleSystem` with the platform loader, platform tokens (`org.webbeans.ui` + configured) and `cache/modules` for wrapped jars; `discoverClasspath()` (optional) and `discover(dir)` for each `--modules` directory.
3. `enableAll()` → class loaders, `validate()`; on `modulesLoaded`: `Registry.rebuild` and `WbDefaultLookup.setContent(…)` (§5); `restored()` each.
4. The launcher (step 2) then builds the client bundle from `Renderers/` entries and starts Jetty.

## 9. Testing (implemented)

- Resolver: ordering and ties, missing dependency propagation, version matching (`>`/`=`/major), cycles, duplicates, autoload/eager, requires tokens from platform and modules.
- Module system with jars built at test time (sources compiled through `javax.tools`): lifecycle order, listener events, public-package enforcement and the explanatory messages, `META-INF/services` visibility, identical `Class` through system and module loaders, `validate()` veto and dependent disabling, wrapped library extraction, disable rules, subset enabling.
- Registry: ordering, later-wins and `hide` (entry and folder), references/separators, singleton and failed instances, `instanceOf` laziness, roles per context, overlay (hide/reorder, file round-trip), `#KEY` with two locales, change events.
- Platform: default Lookup publishes platform singletons, module services (both routes), `RegistryLookup.forPath`, role views, data dir layout, shutdown restores the plain Lookup.
- Processor (`javax.tools` compilation with the processor on the path): class and method factories, references and separators, attribute typing, roles, `@Messages` → `Bundle` class and merged properties honouring the session locale, module package from option or common prefix, `@Hide`, `@Renderer`, error reporting, no output when nothing is annotated.

## 10. Deviations from NetBeans (deliberate)

- No layer filesystem / `FileObject` API; registration is annotation-only and compiled to Java.
- Role filtering and locale resolution are first-class in the registry because views are per session.
- Own `@Messages` for the same reason (session locale); NetBeans' `NbBundle.getMessage` remains usable with the JVM default locale.
- No module auto-update in v1; modules are files in `modules/` and a restart.
- `Lookup.getDefault()` is immutable after startup; dynamic services belong in the session lookup.
