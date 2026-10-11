# WebBeans — Step 1 Implementation Notes (v0.1)

What was built for roadmap step 1 (`webbeans-core`, `webbeans-processor`), how it maps to `design/module-system-and-lookup.md`, and where the implementation had to deviate. Source: `webbeans-step1.zip` (Maven multi-module, JDK 8, 35 JUnit tests).

## 1. What exists

| Package | Classes | Design § |
|---|---|---|
| `org.webbeans.core.modules` | `ModuleInfo` (manifest parser + runtime state), `Dependency`, `SpecificationVersion`, `Problem`, `Resolver` (+`Resolution`), `ModuleClassLoader`, `SystemClassLoader`, `ModuleInstall`, `ModuleListener`, `ModuleSystem`, `DefaultModuleSystem` | §1–3 |
| `org.webbeans.core.registry` | `Registry`, `Folder`, `Entry` (+`EntryImpl`), `RegistrationBuilder`, `RegistrationProvider`, `RegistryContext`, `UserOverlay`, annotations `@Registration`(`.Reference`), `@Registrations`, `@Hide`, `@Renderer` | §4 |
| `org.webbeans.core.lookup` | `WbDefaultLookup` (installed via `META-INF/services/org.openide.util.Lookup`), `RegistryLookup` (+ `forPath`) | §5 |
| `org.webbeans.core.i18n` | `LocaleContext` (thread-local session locale), `Bundles`, `@Messages` | §7 |
| `org.webbeans.core.files` | `DataDir`, `UserFiles` (atomic writes) | arch. §6 |
| `org.webbeans.core` | `Platform` (bootstrap: discover → enable → registry rebuild → default lookup) | §8 |
| `org.webbeans.processor` | `WbProcessor`, `RegistrationsWriter`, `MessagesSupport`; SPI `org.webbeans.processor.spi.AnnotationHandler`, `RegistrationModel`, `Literals`; handlers for `@Registration`, `@Hide`, `@Renderer` | §6 |

Tests: resolver (ordering, missing/mismatched/cyclic deps, duplicates, autoload/eager, requires tokens), module system with real jars built at test time (lifecycle order, public-package enforcement with helpful messages, `META-INF/services` visibility, `validate()` veto, wrapped library jars, enable/disable), registry (ordering, later-wins + hide, references + separators, singleton instances, `instanceOf` laziness, roles, overlay incl. file round-trip, `#KEY` localisation), platform integration (default Lookup publishes platform singletons, module `@ServiceProvider`s and `Services` entries; `RegistryLookup.forPath`; role views; data dir layout), processor (class + method factories, references/separators, `@Messages` → `Bundle` class and merged `Bundle.properties` honouring the session locale, module package from option or common prefix, `@Hide`/`@Renderer`, error reporting at the element, nothing generated when nothing is annotated).

## 2. Deviations from the design (and why)

1. **`@Messages` is WebBeans' own (`org.webbeans.core.i18n.Messages`), not NetBeans'.** NetBeans' generated `Bundle` methods call `NbBundle.getMessage`, which is hard-wired to `Locale.getDefault()` and therefore cannot be per session. WebBeans' processor generates the same `Bundle` class shape, but the methods call `Bundles.getMessage(Bundle.class, key, args)`, which uses `LocaleContext.get()` (set per task by the session executor in step 2) and delegates bundle lookup — branding, locale chain, caching — to `NbBundle.getBundle(name, locale, loader)`. Design §7's "set the thread's default locale" idea was not viable: `Locale.setDefault` is JVM-wide. Developer-facing change: `import org.webbeans.core.i18n.Messages` instead of `org.openide.util.NbBundle.Messages`; `Bundle.KEY(...)` usage is identical. Direct `NbBundle.getMessage` still works (JVM default locale).
2. **`Lookups.forPath` is not routed through the registry.** NetBeans seals `NamedServicesProvider` to its own subclasses (the constructor throws for unknown class names), so `RegistryLookup.forPath("Services/Session")` is the WebBeans equivalent (cached per path, lazy instantiation, `instanceOf` filtering without instantiating). NetBeans' `Lookups.forPath` keeps its `META-INF/namedservices` semantics.
3. **`RegistrationBuilder.supplier(Supplier)` instead of a second `factory` overload** — a `Foo::new` method reference would be ambiguous between `Supplier<?>` and `Function<Entry,?>` for classes with several constructors. Generated code uses `.supplier(org.example.SaveAction::new)` / `.factory(Factories::make)` (for `(Entry)` factory methods). Separators get stable names (`separator-<origin>-<position>`) via `separatorAt(path)`.
4. **Conflicting paths: later module wins** (dependency order), logged as a warning — rather than "higher position wins".
5. **Overlay file is `config/registry.properties`** (`<path>=hidden` / `<path>=<position>`), not JSON, to keep core free of a JSON dependency; Jackson arrives with step 2.
6. **`SpecificationVersion` is WebBeans' own** (NetBeans' lives in `org-openide-modules`, which is not a dependency).
7. **Class loading delegation is platform-first** for every class, not only for listed prefixes: modules cannot shadow platform classes (Jetty, Jackson, NetBeans util), and classpath modules in development mode load through the platform loader consistently.
8. **The processor is a framework**: handlers for `@ActionRegistration`, `@TopComponent.Registration`, `@Mode.Registration`, etc. are not in step 1 — each platform API module (steps 3 and 5) ships an `AnnotationHandler` on the processor path. Handlers work on annotation mirrors by name, so they need no compile dependency on the annotation classes. The generated class is `<module>.generated.WbRegistrations`, where `<module>` is `-Awebbeans.module=<code name>` (the Maven plugin will pass it) or, by default, the longest common package of the annotated elements.

## 3. Notes for step 2

- `RegistryContext.run(ctx, task)` and `LocaleContext.run(locale, task)` are what the session executor wraps each task in; `RegistryContext.of(roles, locale, overlay)` builds the per-session context from the `Principal` and `UserOverlay.load(userFiles.registryOverlayFile())`.
- `Platform.start(Config)` is what the launcher calls before Jetty; `Platform.refresh()` after `modulesLoaded`/`modulesDisabled` re-targets `Lookup.getDefault()`.
- `Registry.getDefault().folder("Renderers")` lists `@Renderer` registrations (`id`, `resource`, `css`, `component`) for the bundler.
- `Entry.newInstance(type)` is for per-session instantiation of `Services/Session` entries.

## 4. Build

`mvn verify` (Maven Central). During development in a sandbox without Central, `build.sh` compiles with `javac --release 8` against NetBeans 12.6 util/util-lookup built from the Apache NetBeans sources and JUnit 4.13.2 built from source; all class files are major version 52.
