# Step 11 — Tier-1 UI components (implementation notes)

Closes the gap between `design/standard-components.md` and what `webbeans-ui` shipped: the remaining form inputs, the two missing containers, lists and trees of plain objects, a virtualised **Table**, file **uploads** and **downloads**, and a **Form** helper. Design (as built): `design/standard-components.md` v0.2; reference: `docs/component-guide.md` §0–§3; tutorial: `docs/user-guide.md` §2.4, §3.1–3.2, §6a.

## 1. What was added

| Area | Server (`org.webbeans.ui…`) | Client (`client/renderers/`) |
|---|---|---|
| Inputs | `NumberField` (Double; locale format spec `FORMAT` compiled on attach; `MIN/MAX` validated; spinner; prefix/suffix), `Slider` (`INPUT` lazy 50 while dragging), `RadioGroup<T>` (keyed via the new `Items<T>` helper), `DatePicker` (`LocalDate`) / `DateTimePicker` (`LocalDateTime`) — ISO on the wire, native pickers | `numberfield.js` (format/parse with the server's separators, ArrowUp/Down and wheel stepping), `slider.js`, `radiogroup.js`, `datepicker.js` (both pickers) |
| Validation | `ValueComponent`: `validateValue` = `REQUIRED` (`isEmpty` hook) → `validateRange` (subclass) → `Validator`; `validate()`, `isValid()`; a parse failure now **re-sends** the last valid value (`Component.resend`) instead of a no-op `set`; messages from a new platform bundle `org/webbeans/ui/Bundle(_de).properties` | text inputs, number and date fields re-emit `change` on commit while an error is showing, so re-committing the restored value clears it |
| Form | `org.webbeans.ui.Form`: `add`, `bind`, `enable(button)`, `isValid` (untouched required-and-empty → invalid but no error shown), `validate`, `write`, `reset`, `setReadOnly`, `firstInvalid`, `addChangeHandler` | — |
| Containers | `SplitPane` (positional children, `POSITION`/`COLLAPSED` client-owned, `CHANGE` lazy 200), `ScrollPane` (`SCROLL_X/Y` client-owned, `SCROLL` lazy 150, `scrollTo`, `scrollToBottom`, `scrollIntoView`) | `splitpane.js` (mouse drag, keyboard, double-click collapse), `scrollpane.js` |
| Lists | `ListBox<T>` (`SELECTED` keys client-owned, `MULTI`, `CHECKBOXES`, `SELECTION`/`ACTIVATE`), `Tree<T>` + `TreeModel<T>` (roots as a property, children pushed by `call("children", key, nodes)` on first expansion, `EXPANDED`/`SELECTED` client-owned, `resync()` re-sends expanded children) | `listbox.js`, `tree.js` (keyboard, ranges, ARIA roles) |
| Table | package `org.webbeans.ui.table`: `Table<T>`, `Column<T>`, `Cell`, `Query<T>`, `TableModel<T>`, `ListTableModel<T>`, `LazyTableModel<T>` (background `RequestProcessor`, delivery via `ui.access`, generation counter against stale pages) | `table.js`: virtualised body (spacer + absolutely positioned rows), page requests with lookahead and in-flight dedupe, header sort indicators and column resizing, filter box, selection (click/Shift/Ctrl/keyboard/Ctrl+A), inline editor (dblclick/F2, Enter/Escape), `editError` marks kept across the re-render, `rowMenu` on right-click |
| Files | `Upload` + `UploadedFile` (`received()` fires `UploadEvent` on the session executor, deletes the temp file unless retained/moved); `DownloadService` (one-time and reusable tokens in a per-UI attribute map; `UI.getAttribute/setAttribute` added) | `upload.js` (XHR per file, raw body, progress rows, drop zone), `root.js` `download` call (hidden `<a download>`) |
| Server | `UploadHandler` servlet at `/wb/upload` (cookie + `X-WB-Token`, `ui`/`c`/`name`/`size` params, streams to `<datadir>/cache/uploads/`, 413 over `ServerConfig.maxUploadSize`, component `MAX_SIZE` checked on the session thread, file names stripped of directories/control characters); `/wb/download/<token>` in `AppHandler` (session required, searches the session's UIs, RFC 6266/5987 `Content-Disposition`, streamed; `WbRequest.markResponded`); launcher `--max-upload 256m` (`Main.parseSize`) | — |
| Demo | `GalleryTopComponent` (*Window ▸ Components*): the inputs under a `Form`, CSV `offer()` and a `link()`, an `Upload` that shows the first line of a dropped text file, a `Tree` → `ListBox` in a `ScrollPane` inside a collapsible `SplitPane`; `OrdersTopComponent` (*Window ▸ Orders*): 10 000 generated orders in a `Table` with sort, filter, multi-selection, inline edit with a rejected value, a row popup (`Actions.actionsToPopup`), keyed updates (*Mark paid*), add/delete | — |

CSS: `wb.css` gained the number/date boxes, spinner, slider, radio list, split/scroll/list/tree, table and upload styles — all on the existing `--wb-*` variables, dark theme included.

## 2. Decisions

- **Locale formatting without a pattern language on the wire.** `NumberField` sends `{decimal, grouping, maxFrac, minFrac}` from `DecimalFormatSymbols` of the session locale; the browser formats on blur and parses by stripping the grouping separator and swapping the decimal one. `setPattern("#,##0.00")` is kept as sugar (fraction digits + grouping flag). Dates use the native inputs: ISO-8601 both ways, the browser's own locale for display, the session locale for server-side messages (`DatePicker.format`).
- **Parse failures re-send the old value.** The v0.1 text said "VALUE is reset with an explicit set", but `Component.set` is a no-op when the value did not change — the server's copy never changed. `Component.resend(property)` writes a `set` delta unconditionally; the client clears the error when the same value is committed again (the only way out otherwise would have been to change the value).
- **`REQUIRED` is enforced by the platform.** Previously only a flag for the renderer; now `validateValue` checks `isEmpty` first, so `Form` can reason about untouched fields: an empty required field makes the form invalid (Save disabled) without painting an error until the user touches it or `form.validate()` is called.
- **Keyed items, one helper.** `Items<T>` (key/label/icon/description functions, key ↔ object maps) backs `RadioGroup` and `ListBox`; `ComboBox` keeps its original code (same semantics) to avoid churn.
- **Table: pages, not rows; models own order.** The client's cache is keyed by index and asks for page-aligned ranges; the server never pushes unrequested rows, so a 10 000-row list costs ~22 rendered rows and one 100-row page per scroll stop. Sorting/filtering happen in the model with a comparator and matcher the table derives from the columns, which keeps `LazyTableModel` free to translate `Query` into SQL. Model changes: `update(row)` → keyed re-render (position unchanged, even if the sort key changed — a `refresh()` re-sorts); `add/remove/set` → `reset` (the client re-requests what it shows). A generation counter discards pages that were in flight across a reset. Columns are published just before the next flush (`UI.beforeFlush`), so chained `.width().align()` calls after `addColumn` are included in the same `create`.
- **Inline edit rejection travels as a call.** The column editor throws `IllegalArgumentException`; the table sends `editError(key, col, message)` and re-sends the row. The client remembers the mark for three seconds across the re-render.
- **Uploads are raw single-request bodies.** No multipart parsing and no chunking: the browser sends one `POST` per file with the file as the body (`Content-Type` = the file's MIME type) — streamed to disk by the servlet, bounded by two limits, and finished on the session executor so the application's handler can touch components. Progress comes from the browser (`xhr.upload`), not from the server.
- **Downloads are tokens, not blobs.** `DownloadService.offer` registers content under a random token in the UI and tells the root renderer to click a hidden `<a download>`; the app servlet streams it with `Content-Disposition: attachment` and consumes the token. `link()` registers a producer for the life of the tab (the Link's `href` is the relative URL, so `--context` works).
- **Renderers treat missing properties as defaults** — only set properties travel in `create` (an old rule that bit the first version of `upload.js`'s drop zone).

## 3. Tests

- JUnit: `ComponentsTest` (6) and `TableTest` (4) in `webbeans-ui`; `WbServerTest.uploadAndDownload` in `webbeans-server`. Total 150 tests green with `./build.sh`.
- Browser: `webbeans-demo/e2e/components.js` (new, 10 steps); all earlier scripts still pass (`windows`, `actions`, `nodes`, `charts`, `scene3d`, `transport`, `tutorial`, `workbench`, `help`).

## 4. Pitfalls met

- `@SafeVarargs` on a constructor trips `-Xlint:varargs -Werror`; `RadioGroup` takes a `Collection` instead.
- `Bundles.getMessage(SomeComponent.class, key)` looks for a bundle in *that* class's package — the shared messages live in `org/webbeans/ui/Bundle.properties`, so every lookup goes through `ValueComponent.class`.
- JDK 21 formats `2:30 PM` with a narrow no-break space (U+202F); the e2e regex uses `\s`.
- Tree rows include the expander glyph in their text; Playwright filters match on the `.wb-text` child.
- The `Table` status in the demo is recomputed after add/delete, not only on selection events (server-side selection changes do not produce client events).

## 5. Left for later

Frozen columns (the flag travels, the renderer ignores it); persisted column widths in TC state; `Table.setContextActions` sugar in the actions module; paged `ListBox`/`ComboBox` items for very long lists; multipart/chunked uploads for files above a few hundred MB; a date picker with a server-controlled format and week start (today the browser's); `Themes/<name>` registry.
