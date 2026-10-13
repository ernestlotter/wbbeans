# WebBeans — Step 15 Implementation Notes (v0.1): `@Messages` never overwrites `Bundle.properties`

Question that triggered it: *"could the framework not overwrite existing Bundle.properties files and rather
merge them?"* — asked after setting up a NetBeans Ant project, whose build passes no `-sourcepath` (so the
processor could not find the source `Bundle.properties` to merge) and copies the resources of `src` into
`build/classes` *after* javac (so the copy would overwrite whatever the processor had written). Maven happens
to do both the other way round, which is why the old scheme worked there and nowhere else.

## 1. What changed

| Where | Before | After |
|---|---|---|
| Processor (`MessagesSupport`) | reads the package's source `Bundle.properties` through `SOURCE_PATH`, writes a **merged** `Bundle.properties` into the class output | writes `<pkg>/Bundle-generated.properties` (header, the `#` comments, keys sorted; no timestamps) and **never** writes `Bundle.properties` |
| Duplicate-key check | only when `SOURCE_PATH` found the file | best effort in three places: source path; the directory of the annotated class's source file (javac `Trees`, called reflectively — works without `-sourcepath`, skipped silently under other compilers); a `Bundle.properties` already copied to the class output (ignored when it is a stale merged file of the old processor). The error says the hand-written value would win at run time. |
| `org.webbeans.core.i18n.Bundles` | `NbBundle.getBundle(name, locale, loader)` | its own lookup: layers `Bundle<suffix>.properties` for the branding × locale chain (`Bundles.suffixes`, same order as NetBeans' `LocaleIterator` minus the JVM-default-locale fallback), then `Bundle-generated.properties`; first layer with the key wins; all copies of a file on the class path are read (first wins); UTF-8 if valid, else ISO-8859-1; cached per loader/name/locale/branding; `MissingResourceException` only when a package has no file at all. New: `find` (null instead of a log line), `getBranding`/`setBranding` (shared with `NbBundle`, used by the launcher), `clearCache`. |
| `DefaultLoginPage.resolve` | provider bundle → platform bundle via `resolve` (logged a warning per request when the provider had no bundle) | `Bundles.find` for the provider bundle, silent |
| `build.sh` | comment claimed the processor overwrites copied bundles | resources are still copied first (so the processor can check them) |

## 2. Why a second file rather than a smarter merge

Any scheme in which the processor writes `Bundle.properties` depends on the build: Maven copies resources
before javac (processor output wins), NetBeans' Ant projects after it (copy wins, `@Messages` keys lost),
Gradle into another directory (both end up in the jar, order-dependent). The processor also cannot always see
the source file (`SOURCE_PATH` is empty in Ant and Gradle builds). Writing to a file name that no build copies
and that `ResourceBundle` conventions never look at removes every such interaction: the developer's files are
copied by the build, the processor's file is written by javac, and the runtime reads both. `Bundle-generated`
contains a hyphen precisely so that it can never be mistaken for a locale or branding variant.

Why not keep `NbBundle` for the lookup: when a package has no base `Bundle.properties`, `NbBundle` stops
looking at the properties chain, tries to load `<pkg>.Bundle` as a `ResourceBundle` *class* — finds our
generated `Bundle` class, logs a `ClassCastException` warning — and ignores `Bundle_de.properties` entirely.
With the processor no longer producing a base file, that would have broken every translation of `@Messages`
keys (the Workbench's `Bundle_de.properties` files, for example). Owning the lookup is ~100 lines and also
drops the server-locale fallback, which is wrong for a multi-user server.

## 3. Resolution order, by example

Package `org.acme`, branding `acme`, user locale `de_DE`: `Bundle_acme_de_DE` → `Bundle_acme_de` →
`Bundle_acme` → `Bundle_de_DE` → `Bundle_de` → `Bundle` → `Bundle-generated`. A translator adds
`CTL_SayHello=Hallo sagen` to `Bundle_de.properties` whether `CTL_SayHello` is hand-written or in
`@Messages`; an application brands a platform string in `Bundle_acme.properties` the same way.

## 4. Tests

- `BundlesTest` (core, new): hand-written wins over generated, union of keys, translation of a generated key,
  locale file without a base file, UTF-8 next to ISO-8859-1 files, split package over two roots, branding
  overlay on both kinds of key, `suffixes`, missing key/bundle behaviour.
- `WbProcessorTest`: the Maven-shaped compile checks `Bundle-generated.properties` and that `Bundle.properties`
  is untouched; new `messagesWithoutSourcePathOrEarlyResourceCopy` compiles NetBeans-Ant-style (no
  `-sourcepath`, resources copied afterwards) and reads messages through `Bundle.KEY()` and `#KEY`; new
  `keyInBothBundleAndMessagesIsAnErrorEvenWithoutSourcePath` exercises the `Trees` lookup.
- All JUnit suites green (157 tests); the Workbench browser suite (branding, German switch, every `@Messages` label) green
  against the rebuilt application jar.

## 5. For a NetBeans Ant project

Nothing to configure beyond the processor path (`javac.processorpath` with the three processor jars,
`webbeans-core` and `org-openide-util-lookup`; `annotation.processing.processor.options=-Awebbeans.module=<pkg>`).
A hand-written `src/<pkg>/Bundle.properties` is copied by the IDE's build after javac, the processor's
`build/classes/<pkg>/Bundle-generated.properties` sits next to it, and both are in the jar.
