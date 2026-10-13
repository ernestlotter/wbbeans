# WebBeans — Class Path, Registry and Lookup (detailed design v0.5)

Roadmap step 1, revised in step 14. Everything here is JDK 8. Packages: `org.webbeans.core` (`Platform`, `Installer`), `org.webbeans.core.registry`, `org.webbeans.core.lookup`, `org.webbeans.core.i18n`, `org.webbeans.core.files`.

**v0.5 changes** (step 15): bundle lookup is WebBeans' own and the processor no longer writes `Bundle.properties` — `@Messages` defaults go to `Bundle-generated.properties`, merged at run time under the hand-written files (§7).

**v0.4 changes** (step 14): the module system is gone — no descriptors, resolver, module class loaders, enable/disable or run-time installation; the application is the class path and §1–3 now describe that. `ModuleInstall` became `Installer`, found through `META-INF/services`. `Entry.getModule()` became `Entry.getClassLoader()`.

**v0.3 changes** (implementation feedback, see `impl/step1-core-notes.md`): `@Messages` is WebBeans' own annotation with session-locale `Bundle` classes (§7); `RegistryLookup.forPath` replaces routing `Lookups.forPath` through the registry (§5); builder API names (`supplier`, `separatorAt`), later-wins merge, properties-file overlay (§4); processor extension SPI (§6).
**v0.2 changes**: Lookup and i18n are taken from the NetBeans Platform libraries instead of being re-implemented (§5, §7). The XML layer filesystem is dropped; registrations are annotation-only and land in a path-keyed `Registry` (§4).

## 1–3. The application is the class path (v0.4, replaces the module descriptor, manager and class loading of v0.1–0.3)

There is no module system. The application is whatever is on the JVM class path: the platform jars plus the
application's jars (ordinary Maven artifacts, or one merged jar). Every jar takes part through its
`META-INF/services` files, which the annotation processor and NetBeans' `@ServiceProvider` processor write at
compile time:

| Service file | Written by | Read by |
|---|---|---|
| `org.webbeans.core.registry.RegistrationProvider` | `webbeans-processor` (one generated `<name>.generated.WbRegistrations` per jar, from the annotations) | `Registry.rebuild(classLoader)` — all providers, in class-path order |
| `<service interface>` | `@ServiceProvider` | `Lookup.getDefault()` (`Lookups.metaInfServices(classLoader)`) |
| `org.webbeans.core.Installer` | `@ServiceProvider(service = Installer.class)` | `Platform.start` (`restored()` in Lookup order) and `shutdown` (`closing()`/`close()` in reverse) |

What this gives up, deliberately: isolation (every class sees every other class), declared dependencies and
versions (Maven's job), enable/disable and installation at run time (replace jars and restart), public-package
enforcement. What it keeps: everything above the class loader — the registry with ordering, masking, roles,
locale and the per-user overlay; the Lookup; the annotations and the processor; the lifecycle hooks.

```java
public class Installer {
    public void restored() {}                 // registry and Lookup complete: seed data, start jobs
    public boolean closing() { return true; } // may veto shutdown
    public void close() {}                    // stop what restored() started
}
```

Conflicts: two jars registering the same registry path — the later jar on the class path wins and a warning
names both providers; `@Hide` masks entries on purpose. The `-Awebbeans.module=<name>` processor option only
chooses the package of the generated registrations class, so that two jars never generate the same class.

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
- `RegistrationBuilder` is public: hand-written providers and `Installer` code may register programmatically; `folder(path)` sets folder attributes/position.
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

Bundle files follow NetBeans' conventions — `Bundle.properties`, `Bundle_<locale>.properties`, branding overlays `Bundle_<brand>[_<locale>].properties`, the same suffix chain (`Bundles.suffixes`) — but the lookup is WebBeans' own (`org.webbeans.core.i18n.Bundles`, v0.5), for two reasons NetBeans' `NbBundle` cannot serve: a locale per user (`NbBundle.getMessage` and NetBeans' generated `Bundle` classes are bound to `Locale.getDefault()`, which is JVM-wide), and a two-file merge that keeps the processor out of the developer's `Bundle.properties`. Hence:

- `LocaleContext` — a thread-local locale (`get()` falls back to `Locale.getDefault()`); the session executor (step 2) runs every task inside `LocaleContext.run(session.getLocale(), …)`.
- `Bundles.getBundle(loader, name, locale)` — layers, most specific first: every `Bundle<suffix>.properties` of the branding × locale chain (`_brand_de_DE, _brand_de, _brand, _de_DE, _de, ""`), then the processor's `Bundle-generated.properties`; a key comes from the first layer that has it. Every copy of a file on the class path is read (split packages across jars: first wins). Files are decoded as UTF-8 when they are valid UTF-8, ISO-8859-1 otherwise. The JVM default locale is deliberately *not* a fallback (a user without a translation sees the base texts, not the server's language). Bundles are cached per loader, name, locale and branding. `Bundles.getMessage(Class, key, args…)` adds `LocaleContext.get()` and `MessageFormat`; `Bundles.resolve(loader, bundle, "#KEY", locale)` is for registry attributes (missing key → logged, returned as-is) and `Bundles.find` the silent variant. `Bundles.setBranding` shares the token with `NbBundle` (the launcher's `--branding`).
- `@org.webbeans.core.i18n.Messages({"CTL_Save=Save", "MSG_N={0} items"})` — same syntax as NetBeans'; the processor generates the package-private `Bundle` class (`Bundle.CTL_Save()`, `Bundle.MSG_N(Object)`) calling `Bundles.getMessage`, and writes the keys to `<pkg>/Bundle-generated.properties` in the class output (sorted, no timestamps). It never writes `Bundle.properties`: that file is the developer's, copied by the build like any resource — before javac (Maven), after it (NetBeans' Ant projects) or into another directory (Gradle), it makes no difference, and nothing can overwrite anything. A key declared both in `Bundle.properties` and in `@Messages` is a compile error; to find the hand-written file for that check the processor looks on the source path, next to the annotated class's source file (javac's `Trees`, used reflectively, so builds that pass no `-sourcepath` are covered) and in the class output. Translations go into `Bundle_<locale>.properties` as usual, for generated keys too. The only visible difference from NetBeans is the import.
- Registry `#KEY` attributes resolve through `Bundles.resolve` with the context locale at view time, so display names are localised per session; a missing key is logged and shown as `#KEY`.

## 8. Startup sequence (`Platform.start(Config)`)

1. `DataDir` (`users/`, `system/`, `cache/`) from `--datadir`.
2. `Registry.rebuild(classLoader)` over every `RegistrationProvider` on the class path; `WbDefaultLookup.setContent(metaInfServices(classLoader), platform singletons, RegistryLookup("Services"))` (§5).
3. `Installer.restored()` on every installer found in the Lookup, in order.
4. The launcher (step 2) then builds the client bundle from `Renderers/` entries and starts Jetty.

## 9. Testing (implemented)

- Registry: ordering, later-wins and `hide` (entry and folder), references/separators, singleton and failed instances, `instanceOf` laziness, roles per context, overlay (hide/reorder, file round-trip), `#KEY` with two locales, change events.
- Platform: jars built at test time (sources compiled through `javax.tools`, `TestJars`) in a `URLClassLoader`: default Lookup publishes platform singletons and the jars' services (both routes), installers run after the Lookup is complete and in reverse at shutdown (veto honoured unless forced), `RegistryLookup.forPath`, role views, data dir layout, shutdown restores the plain Lookup.
- Processor (`javax.tools` compilation with the processor on the path): class and method factories, references and separators, attribute typing, roles, `@Messages` → `Bundle` class and `Bundle-generated.properties` merged at run time with the hand-written files (with and without `-sourcepath`, with the resource copy before or after javac), duplicate-key error, module package from option or common prefix, `@Hide`, `@Renderer`, error reporting, no output when nothing is annotated.

## 10. Deviations from NetBeans (deliberate)

- No layer filesystem / `FileObject` API; registration is annotation-only and compiled to Java.
- Role filtering and locale resolution are first-class in the registry because views are per session.
- Own `@Messages` for the same reason (session locale); NetBeans' `NbBundle.getMessage` remains usable with the JVM default locale.
- No module system at all (v0.4): no manifests, dependencies, isolation, enable/disable or auto-update — the class path is the application and a change is a restart (the run-time module loading built in step 13 was removed with it).
- `Lookup.getDefault()` is fixed after start-up; services that come and go within a session belong in the session lookup.
