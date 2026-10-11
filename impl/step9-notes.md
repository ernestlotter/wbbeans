# WebBeans — Step 9 Implementation Notes (v0.1): the Help System

What was built for the help system (the last unimplemented item of the original roadmap, `design/actions-menus-dialogs.md` §10, now v0.3 "as built"), the platform changes it needed, and where it deviates from the one-paragraph design. Source: `webbeans-step9.zip` (137 JUnit tests: 128 before + 9 `HelpTest`, plus one new `TutorialTest`; browser checks `workbench/e2e/help.js` and the six existing scripts).

## 1. What exists

### API in the platform core modules

| Module | Classes | Notes |
|---|---|---|
| `webbeans-ui` | `org.webbeans.ui.HelpCtx` (`DEFAULT_HELP`, `getHelpID`, `isDefault`, `display()`), `HelpCtx.Provider`, `HelpCtx.Displayer` (a `Lookup.getDefault()` service) | NetBeans' `HelpCtx` lives in `org-openide-util-ui`, which the platform does not use, so it is WebBeans' own; the same shape |
| `webbeans-ui` | `PageProvider` (`getPrefix()`, `render(session, basePath, path, query)`) | a module-provided full HTML page outside the single-page app; keeps `webbeans-server` independent of the help module |
| `webbeans-ui` | `Html.sanitize` allow-list extended: `img` (`src` same-origin / https / inline data), `id`, `alt`, `title`, `kbd`, `dl/dt/dd`, `h5/h6`, `div`, `caption`; `help:` hrefs; `Link.setText` | help pages are rendered through the same sanitiser as `Html` |
| `webbeans-windows` | `TopComponent implements HelpCtx.Provider`: `getHelpCtx()` (explicit `setHelpCtx`, else `@TopComponent.Description(helpId)`, else default) | `Description` is runtime-retained, so no processor change was needed |
| `webbeans-actions` | `NotifyDescriptor.get/setHelpCtx` (`PROP_HELP_CTX`), `DialogDescriptor(…, HelpCtx, listener)`, `DialogComponent.HELP` slot, the dialog displayer's Help button (test id `dialog-help`; added/removed when the context changes; `F1` inside the dialog presses it client-side), `DefaultOptionsDisplayer` sets the dialog's `HelpCtx` from the selected category's `getHelpId()` | `CTL_Help` in the dialogs bundle (en/de) |
| `webbeans-actions-processor` | `HelpAnnotationHandler`: `@HelpPage`/`@HelpPages` → `Help/Pages/<id>` (attrs `displayName`, `resource`, `parent`, `keywords`; position, roles); on classes and packages | registered in the processor's services file |
| `webbeans-server` | `AppHandler` routes any path under a `PageProvider` prefix: session required (login redirect with `return`), rendered inside `session.access` (10 s wait), 404 for null | |
| `webbeans-nodes` | `NodeView` expansion memory: expanded nodes (user toggles, `expandNode`, `reveal`) are remembered and re-expanded after every `rebuild` (root change, reconnect `resync`, re-attach), in dependency order, forgetting nodes no longer under the root; `TreeView.expandNode/collapseNode` delegate to `expand/collapse` | found while making the Help window's table of contents survive a restore; benefits every tree |

### The help module `webbeans-help` (platform jar, depends on `webbeans-nodes`)

| Class | Role |
|---|---|
| `HelpPage` (+ `HelpPages`) | `@Repeatable` registration: `id`, `title` (`#key`), `resource` (`.md` or `.html`), `parent`, `position`, `keywords` (`#key`), `roles`; on classes or `package-info.java` |
| `HelpSet` / `HelpSet.Topic` | the table of contents of the current session from `Help/Pages` (role-filtered and localised by the registry): roots, home (first root), `find`, `byResource`, `getAll`, `search(query, locale)` (title/keyword hits before text hits) |
| `Markdown` | a small converter: ATX headings with slug ids, paragraphs, fenced code (with language class), inline code, bold/italic/strike, links, images, auto-links, nested ordered/unordered lists (runs of one kind), block quotes, rules, pipe tables, raw HTML blocks and inline tags; everything else escaped |
| `HelpContent` | loads `resource_<lang>.ext` before `resource.ext`, converts Markdown, resolves relative links (`other.md` → `help:<id>` of the page registered with that resource, `img.png` → `wb/res/<path>`; `#fragment` kept) and sanitises; `text()` for search; a missing resource renders a note |
| `HelpViewer` (`wb.HelpViewer`, `help.js` + `help.css`) | sanitised HTML with help typography; `help:`/`#` clicks → `navigate` events, other links open a new tab; `scrollTo(anchor)` |
| `HelpNode` | TOC nodes over topics (`Children.Keys`, flat for search results), book/page icons |
| `HelpTopComponent` (`HelpTC`) | its own `help` mode (right, 420 px), `PERSISTENCE_ONLY_OPENED`, `helpId = "platform"`; bar: Contents, Back, search (change + debounced input), *Open in new tab* (`wb/help/<id>`); a `Layout.Stack` of the TOC (`TreeView`, fully expanded; "no pages"/"no results" note) and the page (breadcrumbs + viewer); `show(HelpCtx)` (topic + `#anchor`, home for the default context, contents + status text for an unknown id), `showContents`, history, `localeChanged` rebuilds and re-renders |
| `HelpDisplayer` | the `HelpCtx.Displayer` service: finds/opens `HelpTC` in the current UI |
| `HelpAction.HelpContentsAction` / `ContextHelpAction` | Help ▸ Help Contents (position 10, separator after) shows the TOC; `Shortcuts/F1` shows the active window's `getHelpCtx()` or the home page (the Help window itself counts as "no context") |
| `HelpPageProvider` | `GET /wb/help/<id>` (empty id = home): a complete document with a `<nav>` TOC (current page marked), breadcrumbs and the article; `help:` links → page links; `wb/res/` → absolute; light/dark CSS; null (404) for unknown or forbidden ids |
| `package-info` + `platform.md` / `platform_de.md` | the platform's own page "Getting around" (windows, menus, Properties, Options, help), position 9000 |

### Content

- **Workbench**: every module registers its pages in `package-info.java`, all nested under the core's `workbench` overview (position 0 = the home page): `workbench`, `workbench.output`, `workbench.options`, `files`, `files.editor`, `dashboard`, `dashboard.options`, `viewer`, `viewer.parts`, `admin` (roles admin). The overview links to the other modules' pages by relative file path across module jars. `helpId` on every window; `WorkbenchOptions`/`DashboardOptions.getHelpId()`; the About dialog carries `HelpCtx("workbench")`. German titles/keywords in the bundles; `workbench_de.md` as a localised page.
- **Tutorial**: `tutorial` page + `helpId` on the Hello and Tasks windows; `TutorialTest.f1OpensTheTutorialHelpPage`.
- **Demo**: `demo` page.

### Tests

`HelpTest` (`webbeans-help`, `HeadlessUI`, with its own `@HelpPage`s and windows in the test sources — the processor runs on the test tree with `-Awebbeans.module=org.webbeans.help.test`): the Markdown subset, localised variants, the TOC from the registry with roles (alice-as-admin sees `test.secret`, bob does not), `display` → window + page with rewritten links / missing page note / unknown id / default context, F1 vs. Help Contents, the dialog Help button appearing and disappearing with the context, search (flat results, keywords, back to the TOC), the stand-alone page (TOC links with the context path, `help:` links rewritten, 404), and re-expansion of the TOC after a restore from a saved layout. `workbench/e2e/help.js` (headless Chromium against the application jar): Help Contents → TOC → home page, a `help:` link, Back, F1 in the 3D viewer, search, the Options dialog's Help button following the category, About's Help button and `F1` in a dialog, the stand-alone page, German variant after a locale switch, bob's filtered TOC and a 404 for `/wb/help/admin`.

## 2. Shape as implemented

- **Pages are resources, not a help set file**: a module ships `.md`/`.html` files and declares each one with `@HelpPage`; the registry is the map and the TOC. Cross-module nesting (`parent` naming another module's id) and cross-module links (by resource path) work because the registry and the class path are shared.
- **Two entry points, not one**: Help ▸ Help Contents opens the table of contents; `F1` is context-sensitive. A first version made the menu item context-sensitive too (NetBeans' behaviour); showing the TOC from the menu turned out to be what the e2e expected and what the name says.
- **Rendering on the server, sanitised**: Markdown becomes HTML on the server and goes through the `Html` allow-list; the viewer's `innerHTML` only ever sees sanitised markup. Inline HTML in Markdown (`<kbd>`) passes the converter and is filtered by the sanitiser, so a page can use any allowed tag.
- **Relative URLs**: resources are emitted as `wb/res/<path>` (relative to the application, so a `--context /app` deployment works); the stand-alone page makes them absolute with the base path.
- **Expansion memory in `NodeView`** rather than a help-window hack: the first attempts (`componentShowing`, `attached` + `beforeFlush`) failed on restore because the view is rebuilt on connect (`resync`) and only the root was re-expanded; and a second subtle bug — re-expanding in `IdentityHashMap` order — made it work only every other run until the re-expansion became order-independent (repeat passes until no progress).

## 3. Deviations from the design paragraph

1. `HelpCtx` is `org.webbeans.ui.HelpCtx`, not NetBeans' (`org-openide-util-ui` is not on the platform's dependency list).
2. Help ids on actions are not implemented (the design said "dialogs, TCs and actions"); a dialog opened by an action carries the context instead.
3. The stand-alone page is `/wb/help/<id>` (under the `/wb/` prefix like every platform endpoint) rather than `/help/<id>`.
4. No search index: `HelpSet.search` renders page texts on demand (fine for tens of pages; an index can come with a bigger help set).

## 4. Notes for what follows

- The remaining roadmap items are the Maven plugin/archetype and the Jetty/WebSocket front end; `PageProvider` is the hook the latter has to keep.
- Options sub-categories could reuse the Help window's TOC/page split.
