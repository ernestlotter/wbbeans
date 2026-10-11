# WebBeans — Standard Component Library (detailed design v0.3)

Part of roadmap step 2 (shipped in `webbeans-ui`, renderers under `org/webbeans/ui/client/renderers/`), completed in **step 11** with the remaining inputs, containers, lists, the table, uploads and downloads, and extended in **step 12** with the tier-2/3 components (§2a–§4c). **v0.3 marks what is built and how it differs from the v0.1 plan** (§9); `docs/component-guide.md` is the user-facing reference. JDK 8. Depends on the component model and protocol in `design/sessions-and-delta-protocol.md`.

## 1. Foundations

### 1.1 Property model

```java
public final class Property<T> {
    public static <T> Property<T> of(String name, Class<T> type, T defaultValue);
    public Property<T> clientOwned();     // browser is the source of truth; server writes are sent, client changes are not echoed
    public String getName(); public Class<T> getType(); public T getDefault(); public boolean isClientOwned();
}
public final class EventType<E extends Event> {
    public static <E extends Event> EventType<E> of(String name, BiFunction<Component, Map<String,Object>, E> factory);
    public EventType<E> lazy(int debounceMs);     // coalesced on the client
}
```

Every component declares its properties as `public static final Property<…>` constants (the names below are those constants). Generic access `get/set` plus conventional accessors. Only values that were set travel in the `create` op — renderers treat a missing property as its default. `Component.resend(property)` re-sends the current value unchanged (used to overrule a rejected client edit).

### 1.2 Value types on the wire

| Java | JSON |
|---|---|
| `Size` (`px(n)`, `percent(n)`, `em(n)`, `auto()`, `fill()`) | `"200px"`, `"100%"`, `"auto"`, `"fill"` |
| `Resource` | `{"$res": "module/path"}` or `{"$blob": hash}` |
| `Item` | `{"v": key, "l": label, "i": icon?, "d": description?, "e": false?}` — domain objects are mapped to string keys by the component's key function (default `toString`, `Items<T>` helper), so the client never sees domain objects |
| `LocalDate`/`LocalDateTime`/`LocalTime` | ISO-8601 string (no zone) |
| `Component` reference | `{"$ref": id}` |
| `Cell` (table) | `{"t": text, "i": icon?, "h": html?, "s": style?, "tt": tooltip?}` |
| enum | the constant name; labels and icons are looked up server-side through `EnumLabels` (`Labeled` → registered function → bundle key `<SimpleName>.<CONSTANT>` → prettified name) and sent as `Item`/`Cell`/choice fields |

### 1.3 Common base

`Component` properties: `VISIBLE`, `ENABLED`, `TOOLTIP`, `STYLE_NAME`, `WIDTH`, `HEIGHT`, `ID_FOR_TEST`.
Common events: `CLICK` (`ClickEvent`), `CONTEXT_MENU`, `FOCUS`, `BLUR`. Calls: `focus` (per component), `scrollIntoView` (ScrollPane).

### 1.4 Input components: `ValueComponent<T>`

```java
public abstract class ValueComponent<T> extends Component {
    public static final Property<Boolean> READ_ONLY, REQUIRED;
    public static final Property<String> ERROR;            // server-owned; set by validation, shown inline
    public T getValue(); public ValueComponent<T> setValue(T v);
    public ValueComponent<T> setValidator(Validator<T> v); // the application's rule, after REQUIRED and the subclass range
    public Registration addChangeListener(Listener<ChangeEvent<T>> l);
    public boolean validate(); public boolean isValid();   // validate now (Form uses it before a submit)
    protected abstract T fromClient(Object raw);           // throw to reject
    protected String validateRange(T v);                   // subclass rule hook (min/max)
    protected boolean isEmpty(T v);                        // for REQUIRED
}
```

Protocol: the client sends `change {value}` on commit; the server parses, validates (`REQUIRED` → `ERR_Required`, the subclass range, then the `Validator`), stores the value, sets `ERROR` (or clears it) and fires `ChangeEvent` (`getValue`, `isValid`, `getOldValue`). If parsing fails (bad number/date, unknown key), `ERROR` is set and the last valid value is **re-sent** (`resend`) so the browser shows it again; the client lets the user re-commit the same value to clear the error. Validation texts come from the platform bundle (`org/webbeans/ui/Bundle.properties`, English and German).

### 1.5 Validation and forms

`Form` (`org.webbeans.ui`) is a helper, not a component: `add(fields…)`, `bind(field, consumer)`, `enable(button)` (enabled ⇔ `isValid()`), `isValid()` (untouched fields count as valid unless required and empty), `validate()` (every field now, errors shown), `write()`, `reset()`, `setReadOnly`, `firstInvalid()`, `addChangeHandler`. `DialogDescriptor.setValid` is typically wired to `form.isValid()` from a change handler.

## 2. Basic components (as built)

| Component | Renderer | Properties (server-owned unless marked C) | Events | Calls |
|---|---|---|---|---|
| `Label` | `wb.Label` | `TEXT`, `HTML`, `ICON`, `WRAP` | `CLICK` | — |
| `Button` | `wb.Button` | `TEXT`, `ICON`, `KIND`, `DEFAULT_BUTTON`, `BUSY` | `CLICK` | `click`, `focus` |
| `TextField` | `wb.TextField` | `VALUE` (C), `PLACEHOLDER`, `MAX_LENGTH`, `PASSWORD`, `PREFIX_ICON`, `CLEAR_BUTTON` | change, `INPUT` (lazy 300), `KEY` | `focus`, `select` |
| `TextArea` | `wb.TextArea` | as `TextField` + `ROWS`, `AUTO_GROW`, `MONOSPACE`, `WRAP` | same | `scrollToLine` |
| `NumberField` | `wb.NumberField` | `VALUE` (C, `Double`), `MIN`, `MAX`, `STEP`, `DECIMALS`, `GROUPING`, `SPINNER`, `PREFIX`, `SUFFIX`, `PLACEHOLDER`, `FORMAT` (derived: `{decimal, grouping, maxFrac, minFrac}` for the session locale, compiled on attach / `compileFormat()`) | change | `focus`, `select` |
| `CheckBox` | `wb.CheckBox` | `TEXT`, `SELECTED` (C), `INDETERMINATE` | change | — |
| `RadioGroup<T>` | `wb.RadioGroup` | `ITEMS`, `VALUE` (C, key), `ORIENTATION` | change | `focus` |
| `ComboBox<T>` | `wb.ComboBox` | `ITEMS`, `VALUE` (C, key), `FILTERABLE`, `PLACEHOLDER`, `ALLOW_EMPTY` | change | `focus` |
| `DatePicker` / `DateTimePicker` | `wb.DatePicker` / `wb.DateTimePicker` (one renderer file) | `VALUE` (C, ISO), `MIN`, `MAX`, `CLEAR_BUTTON`; `SECONDS` | change | `focus` |
| `Slider` | `wb.Slider` | `VALUE` (C, `Double`), `MIN`, `MAX`, `STEP`, `TICKS`, `SHOW_VALUE`, `ORIENTATION` | change (release), `INPUT` (lazy 50, while dragging) | `focus` |
| `Link` | `wb.Link` | `TEXT`, `HREF`, `NEW_TAB` | `CLICK` (when `HREF` null) | — |
| `Image` | `wb.Image` | `SOURCE`, `ALT`, `FIT` | `CLICK` | — |
| `ProgressBar` | `wb.ProgressBar` | `VALUE`, `INDETERMINATE`, `TEXT` | — | — |
| `Html` | `wb.Html` | `HTML` (sanitised) | `CLICK` | — |
| `Upload` | `wb.Upload` | `ACCEPT`, `MULTIPLE`, `MAX_SIZE`, `DROP_ZONE`, `TEXT`, `HINT`, `SHOW_LIST` | `UploadEvent` (server-side, from `/wb/upload`), `PROGRESS` (lazy 250), `FAILED`, `FINISHED` | `open`, `clear` |
| `Spacer` | `wb.Spacer` | — | — | — |

| `ToggleButton` | `wb.ToggleButton` (`widgets.js`) | `SELECTED` (C, the value), `TEXT`, `ICON`, `SELECTED_ICON` | change, `CLICK` | `focus` |
| `ButtonGroup<T>` | `wb.ButtonGroup` | `ITEMS`, `VALUE` (C, key), `ICONS_ONLY`, `DESELECTABLE` | change | `focus` |
| `SplitButton` | `wb.SplitButton` | `TEXT`, `ICON`, `KIND`, `ITEMS` (`"-"` = separator), `MENU_ONLY` | `CLICK`, `SELECT {key}` | `focus` |
| `ColorPicker` | `wb.ColorPicker` | `VALUE` (C, `#rrggbb`), `PALETTE`, `ALLOW_CUSTOM`, `SHOW_TEXT`, `CLEARABLE` | change | `focus` |
| `TimePicker` | `wb.TimePicker` | `VALUE` (C, `LocalTime` ISO), `MIN`, `MAX`, `STEP_MINUTES` | change | `focus` |
| `PasswordField` | `wb.PasswordField` (extends the text input factory) | `TextField` + `REVEAL_BUTTON`, `STRENGTH_METER`, `strengthLabels` (localised, derived) | as `TextField` | as `TextField` |
| `TagInput` | `wb.TagInput` | `VALUE` (C, `List<String>`), `SUGGESTIONS`, `RESTRICT`, `PLACEHOLDER`, `MAX_TAGS`, `LOWER_CASE` | change | `focus` |
| `Badge` | `wb.Badge` | `TEXT`, `KIND`, `PILL`, `DOT` | `CLICK` | — |
| `Chip` | `wb.Chip` | `TEXT`, `ICON`, `CLOSABLE`, `SELECTABLE`, `SELECTED` (C), `COLOR` | `CLOSE`, `SELECT {value}`, `CLICK` | — |
| `Avatar` | `wb.Avatar` | `NAME`, `IMAGE`, `SIZE`, `COLOR`, `STATUS`, `SQUARE` | `CLICK` | — |
| `EmptyState` | `wb.EmptyState` | `ICON`, `TITLE`, `TEXT`, `ACTION_TEXT` | `CLICK` (the action button) | — |
| `Breadcrumb` | `wb.Breadcrumb` | `ITEMS`, `SEPARATOR`, `MAX_ITEMS` | `SELECT {key, index}` | — |
| `Stepper` | `wb.Stepper` | `STEPS` (`{title, desc?}`), `CURRENT`, `ERROR_STEP`, `ORIENTATION`, `CLICKABLE`, `FREE` | `SELECT {index}` | — |
| `ImageViewer` | `wb.ImageViewer` (`viewers.js`) | `SOURCE` (`Resource` or URL), `ALT`, `TOOLBAR`, `CHECKERBOARD`, `ZOOM` (C), `ROTATION` | — | `fit` |
| `PdfViewer` | `wb.PdfViewer` | `URL` (a `DownloadService` URL or any), `FILE_NAME`, `TOOLBAR`, `PAGE` | — | — |
Notes:

- `HTML` properties are sanitised server-side (`Html.sanitize`; see the component guide for the allow-list).
- `NumberField` compiles its format from the session locale and `DECIMALS`/`GROUPING` rather than from a `DecimalFormat` pattern on the wire (`setPattern` reads fraction digits and grouping from a pattern); the browser formats on blur and parses with the same separators. `MIN`/`MAX` are validated on the server (`ERR_Min`/`ERR_Max`).
- Dates use the browser's native `date` / `datetime-local` inputs (locale display is the browser's); the server validates `MIN`/`MAX` and formats dates for messages with `DatePicker.format` / `DateTimePicker.format` (session locale).
- `Upload`: the browser POSTs each file **raw** to `/wb/upload?ui=&c=&name=&size=` with `X-WB-Token` and the file's MIME type as `Content-Type` — no multipart and no chunking (one request per file, streamed to disk; limits: the component's `MAX_SIZE` and `ServerConfig.maxUploadSize` / `--max-upload`, 256 MB default → 413). The servlet stores the body under `<datadir>/cache/uploads/` and runs `Upload.received(UploadedFile)` on the session executor; the temp file is deleted when the listeners return unless `retain()`/`moveTo()`.

## 3. Layouts and containers

```java
public abstract class Layout implements JsonValue { /* {"t": "border"|"flow"|"grid"|"form"|"stack"|"fill", …} */ }
Layout.Fill · Layout.Border (NORTH, SOUTH, EAST, WEST, CENTER) · Layout.Flow(orientation, gap).align().wrap()
Layout.Grid(rows, cols, hgap, vgap).fill().columnWidths().rowHeights(); Grid.cell(row, col, rowSpan, colSpan)
Layout.Form().labelWidth(); Form.row(label[, hint]), Form.section(title) · Layout.Stack (Panel.setSelected)
```

| Container | Renderer | Properties | Events | Calls |
|---|---|---|---|---|
| `Panel` | `wb.Panel` | `LAYOUT`, `PADDING`, `BORDER`, `TITLE`, `SCROLLABLE`, `SELECTED` (stack), `BUSY`, `BUSY_TEXT` (overlay) | `CLICK`, `CONTEXT_MENU` | — |
| `SplitPane` | `wb.SplitPane` | `ORIENTATION`, `POSITION` (C, px of the first pane), `MIN_FIRST`, `MIN_SECOND`, `COLLAPSIBLE`, `COLLAPSED` (C) | `CHANGE` (lazy 200: `position`, `collapsed`) | — |
| `TabbedPane` | `wb.TabbedPane` | tabs from child constraints `{title, icon, closable}`, `SELECTED` (C, index) | `SELECTION`, `TAB_CLOSE` | — |
| `ScrollPane` | `wb.ScrollPane` | `SCROLL_X`/`SCROLL_Y` (C), `HORIZONTAL`/`VERTICAL` policies | `SCROLL` (lazy 150: `x`, `y`, `atBottom`) | `scrollTo`, `scrollToBottom`, `scrollIntoView(childId)` |
| `Card` | `wb.Card` (extends the panel renderer lazily) | `Panel` + `CARD_TITLE`, `SUBTITLE`, `ICON`, `IMAGE`, `CLICKABLE`, `CARD_SELECTED`, `ELEVATED` | `CLICK` | — |
| `Accordion` | `wb.Accordion` | sections from child constraints (`Section {title, icon?, badge?}`), `EXPANDED` (C, indexes), `MULTIPLE` | `TOGGLE {expanded}` | — |
| `Popover` | `wb.Popover` | `ANCHOR` (`$ref`), `OPEN` (C), `PLACEMENT`, `CLOSE_ON_OUTSIDE`, `ARROW`, `PADDING`; children render in a fixed popup | `CLOSE` | — |
| `Toolbar` | `wb.Toolbar` (actions module) | generated from the registry | — | — |

Child constraints are sent in the `create` op as `"c"`; a child's constraint lives with the child. `SplitPane` children are positional (first = left/top). The window system's mode splitters are the dock renderer's own, not `SplitPane`.

## 4. Data components (as built)

### 4.1 `Table<T>` — `wb.Table` (`org.webbeans.ui.table`)

```java
public class Table<T> extends Component {
    public Column<T> addColumn(String title, Function<T, ?> value);  public Column<T> addColumn(String id, String title, Function<T, ?> value);
    public Table<T> setModel(TableModel<T> model);  public Table<T> setItems(Collection<? extends T> rows);   // ListTableModel
    public Table<T> setKeyFunction(Function<T, String> key);        // default toString
    public Table<T> setSelectionMode(SelectionMode m);  public List<T> getSelected();  public Table<T> setSelected(Collection<? extends T> rows);
    public Table<T> setSort(String columnId, boolean asc);  public Table<T> setFilter(String text);  public Table<T> setShowFilter(boolean b);
    public Table<T> setRowStyle(Function<T, String> css);  public void refresh();  public void updateRow(T row);  public int getTotal();
    public Registration addSelectionListener(...); addActivateListener(...); addRowMenuListener(...); addCellEditListener(...); addSortListener(...);
}
public final class Column<T> { title, width(px), minWidth, align, sortable, resizable, hidden, frozen, format(fn), comparator, editable(BiConsumer<T,String>), styleName }
public interface TableModel<T> { void load(Query<T> q, int offset, int limit, Callback<T> cb); addListener/removeListener(Listener<T>: reset(), updated(row)) }
public class ListTableModel<T> implements TableModel<T> { set, add, add(i, row), addAll, remove, removeAll, clear, update(row), replace(old, new), refresh }
public abstract class LazyTableModel<T> implements TableModel<T> { protected abstract int count(Query<T>); protected abstract List<T> fetch(Query<T>, int, int); refresh(); updated(row) }
public final class Query<T> { sortColumn, ascending, filter, comparator (built from the column), matcher (text over visible columns) }
```

Wire: `COLUMNS` (list of `{id, title, width?, minWidth?, align?, sortable?, resizable?, hidden?, frozen?, editable?, style?}`), `SELECTION_MODE`, `SELECTED` (C, keys), `SORT` (`{col, asc}`), `FILTER` (C text), `SHOW_FILTER`, `FILTER_PLACEHOLDER`, `SHOW_HEADER`, `PAGE_SIZE` (100), `ROW_HEIGHT` (28), `STRIPED`, `EMPTY_TEXT`. Rows travel as calls:

```json
{ "op":"call", "id":55, "m":"rows", "args":[{ "total": 12840, "offset": 400, "rows": [ {"k":"o-1021", "c":["1021","ACME","1,250.00",{"t":"Paid","i":{"$res":"…/ok.svg"},"s":"status-paid"}], "s":"row-paid"} ] }] }
{ "op":"call", "id":55, "m":"rows", "args":[{ "reset": true }] }            // client drops its cache and re-requests the visible pages
{ "op":"call", "id":55, "m":"update", "args":[[ {"k":"o-1019", "c":[…]} ]] }  // keyed re-render in place
{ "op":"call", "id":55, "m":"editError", "args":["o-1019", "customer", "Name needed"] }
```

- Cells are rendered server-side to strings (numbers/dates in the session locale, booleans as ✓, or the column's `format`) or `Cell` objects, so the client never sees `T`. Sorting and filtering happen in the model: the table hands the `Query` a comparator (column `comparator` or natural order of the values) and a matcher (case-insensitive text over the visible columns); `LazyTableModel` translates the column id and the text into its own query and runs `count`/`fetch` on a `RequestProcessor`, delivering through `ui.access`.
- Paging: the virtualised renderer requests `page {offset, limit}` (page-aligned, with a few rows of lookahead) as the viewport moves; the server deduplicates in-flight offsets and ignores pages from before a reset (generation counter). `ListTableModel` changes: `update(row)` → keyed `update`; `add/remove/set` → `reset` (the client re-requests). `resync()` after a full `init` clears the server cache; the client starts empty and requests again.
- Selection is client-owned keys (`select {keys}`); `RowEvent` for `activate {key}` (double-click/Enter) and `rowMenu {key, x, y}` (right-click, the row is selected first — the application shows a `Popup`); `CellEditEvent` (`edit {key, col, value}`: the column's editor runs first, an `IllegalArgumentException` becomes `editError`, the row is re-sent either way); `sort {col, asc}` events set `SORT` and reset; `filter {text}` (lazy 300) sets `FILTER` and resets. Column widths dragged by the user stay client-side (not persisted yet).

### 4.2 `ListBox<T>` — `wb.ListBox`
`ITEMS`, `SELECTED` (C, keys), `MULTI`, `CHECKBOXES`, `VISIBLE_ROWS`, `EMPTY_TEXT`; events `SELECTION` (`select {keys}`), `ACTIVATE` (`activate {key}`). Not paged (lists are expected to be small; use `Table` for large sets).

### 4.3 `Tree<T>` + `TreeModel<T>` — `wb.Tree`
`TreeModel<T> { List<T> getChildren(T parent /* null = roots */); String getLabel(T); Resource getIcon(T); boolean isLeaf(T); String getKey(T); }`. Props `ROOTS` (list of `{k, l, i?, leaf?}`), `EXPANDED` (C, keys), `SELECTED` (C, keys), `MULTI`, `EMPTY_TEXT`. Events `EXPAND` (`expand {key, expanded}` → children fetched once and pushed with `call("children", key, nodes)`), `SELECTION`, `ACTIVATE`. `refresh()` / `refresh(node)` re-read; `resync()` re-sends the children of expanded nodes after an `init`. For actions, properties, DnD, use `TreeView` (step 4).

### 4.4 `TreeTable<T>` + `TreeTableModel<T>` — `wb.TreeTable`
A `Table` subclass (`Table(String rendererId)`, renderer extends `wb.Table` lazily on the client). `TreeTableModel<T> { List<? extends T> getRoots(); List<? extends T> getChildren(T); boolean isLeaf(T); default String getKey(T); }`. An internal `TableModel` flattens the expanded part per query — siblings sorted with the query's comparator, a filter keeping nodes that match or have matching descendants (their subtree shown expanded) — and caches the result until the expansion or the query changes. Rows carry `d` (depth), `x` (expandable), `e` (expanded), `p` (parent key) through `Table.decorateRow`; the client adds indent and expander to the first visible cell. Events `TOGGLE {key}` (client → expand/collapse → reset), `EXPAND` (server-fired, `getNode()`, `isExpanded()`). Keyboard Right/Left expand, collapse, go to parent.

### 4.5 `PropertyGrid<T>` — `wb.PropertyGrid`
`PROPERTIES` (`[{id, label, cat?, desc?, kind: text|number|bool|choice|date, ro?, choices?: [{k, l, i?}]}]`, republished per flush), `VALUES` (`{id: {t, i?, h?, s?, tt?, v?, k?}}`), `SHOW_DESCRIPTION`, `SHOW_CATEGORIES`, `EMPTY_TEXT`. Event `edit {id, value}` → `Prop.parse` (String, Boolean, Integer/Long/Short/Float/Double/BigDecimal, LocalDate, choices by key, enums by name, or a `parser`) → setter → `refresh()` → server-fired `CHANGE {id}` (`getOldValue/getNewValue/getBean`); a throwing setter → `call("editError", id, message)` + `resend(VALUES)`.

### 4.6 Text — `wb.CodeEditor`, `wb.MarkdownEditor`, `wb.DiffView`, `wb.OutputView`
- `CodeEditor`: `VALUE` (C), `LANGUAGE`, `LINE_NUMBERS`, `WRAP`, `TAB_SIZE`, `HIGHLIGHT_CURRENT_LINE`, `PLACEHOLDER`, `ANNOTATIONS` (`[{line, kind, message?}]`); events change, `INPUT` (lazy 300), `CURSOR` (lazy 200, `{line, col}`), `GUTTER_CLICK {line}`, `KEY`; calls `goToLine`, `focus`, `insert`, `select`. The renderer is a `<pre>` token layer under a transparent `<textarea>` with regex tokenizers per language (`wb.highlightCode(text, lang)` is exported for the other renderers).
- `MarkdownEditor`: `VALUE` (C), `PREVIEW` (`SPLIT|OFF|ONLY`), `PREVIEW_HTML` (derived: `Html.sanitize(Markdown.toHtml(value))` on every debounced `INPUT` and on `setValue`), `TOOLBAR`, `PLACEHOLDER`, `ROWS`; calls `focus`, `insert`.
- `DiffView`: `LAYOUT`, `LEFT_TITLE`, `RIGHT_TITLE`, `LANGUAGE`, `CONTEXT`, `WRAP`, `LINE_NUMBERS`, `OPS` (`[{op: "="|"-"|"+", a?, b?, t} | {op: "~", lines: [...]}]` from `LineDiff` with folds applied on the server); event `LINE_CLICK {side, line, op}`.
- `OutputView`: `MAX_LINES`, `FOLLOW` (C), `WRAP`, `TIMESTAMPS`, `ANSI`; calls `append([{t, s?, ts, link?}])` (one per flush via `beforeFlush`), `set([...])` (resync), `clear`, `scrollToEnd`; events `LINK {link, url?, line}`, `follow {value}`. Ring buffer on both sides.

### 4.7 Boards — `wb.Calendar`, `wb.Kanban`, `wb.Diagram`, `wb.Palette`
- `Calendar`: `VIEW` (C), `DATE` (C), `SELECTED` (C), `ENTRIES` (`[{k, t, s, e?, st?, et?, c?, i?, tt?, ro?}]`), `FIRST_DAY`, `WEEK_NUMBERS`, `TOOLBAR`, `DRAGGABLE`, `DAY_START_HOUR`, `DAY_END_HOUR`; events `nav {date, view, selected}` (internal), `RANGE {from, to}`, `DAY_CLICK`/`DAY_ACTIVATE {date}`, `ENTRY_CLICK`/`ENTRY_ACTIVATE {key, date}`, `MOVE {key, date}` (reported only; `MoveEvent.getMoved()` is the shifted entry). Month and day names via `Intl` with `wb.locale`.
- `Kanban`: `COLUMNS` (`[{id, t, c?, limit?, add?, collapsed?}]`), `CARDS` (`[{k, t, col, s?, tags?, i?, c?, tt?, ro?}]`), `SELECTED` (C), `DRAGGABLE`; events `drop {key, column, index}` (applied: `moveCard`, then server-fired `MOVE {key, column, from, index}`), `CARD_CLICK`/`CARD_ACTIVATE`/`CARD_MENU {key, x?, y?}`, `ADD {column}`, `select {key}`.
- `Diagram`: `NODES` (`[{k, t, x, y, w, h, shape, c?, i?, tt?, s?, locked?}]`), `EDGES` (`[{k, from, to, t?, ls?, arrow?, c?, s?}]`), `SELECTED` (C, node keys), `GRID`, `SHOW_GRID`, `CONNECTABLE`, `DROP_TARGET`, `MINIMAP` (reserved); events `move {nodes: [{key, x, y}]}` (applied, then `NODE_MOVE` per node), `select {nodes, edges}`, `NODE_ACTIVATE`/`NODE_MENU {key}`, `EDGE_ACTIVATE {key}`, `CONNECT {from, to}`, `DROP {item, x, y}`, `DELETE {nodes, edges}`; calls `fit`, `zoom`, `scrollTo`. SVG with a transformed world group; pan/zoom are client state only.
- `Palette`: `CATEGORIES` (`[{name, items: [Item]}]`), `SHOW_LABELS`, `SHOW_FILTER`, `FILTER` (C); events `SELECT {key}`, `filter {text}` (lazy 200). Drag data: `text/x-wb-palette` = item key.

### 4.8 Quick Search — `wb.QuickSearch` (actions module, `org.webbeans.quicksearch`)
`RESULTS` (`[{cat, items: [{i, t, d?, icon?}]}]`), `PLACEHOLDER`, `NO_RESULTS`, `OPEN` (C); events `query {text}` (lazy 150 → the `QuickSearch/Providers` registry instances), `run {index}`, `close`; call `open`. One instance per UI, in the root's overlay slot, `allowWhileModal`.

## 5. Downloads (as built)

```java
public final class DownloadService {                       // org.webbeans.ui; per-UI registry in a UI attribute
    public interface Content { String getFileName(); String getContentType(); long getLength(); InputStream open(); }
    public static Content content(byte[] bytes, String fileName, String mime);  public static Content content(Path file, String fileName, String mime);
    public static void offer(byte[] bytes, String fileName, String mime);       // one-time token; root call "download" → the browser saves
    public static void offer(Content c);
    public static Link link(Supplier<Content> producer, String text);           // reusable token for the life of the tab
    public static String url(Supplier<Content> producer);
    public static Content take(UI ui, String token);                           // server side: resolves (and consumes single-use) tokens
}
```

`GET /wb/download/<token>` (app servlet) needs the session cookie, finds the token in one of the session's tabs, streams the content with `Content-Disposition: attachment; filename="ascii"; filename*=UTF-8''…` and `Cache-Control: private, no-store`. One-time tokens expire after 5 minutes; nothing travels through the control channel.

## 6. Accessibility and keyboard

Renderers set ARIA roles (`button`, `tab`, `grid`/`row`/`gridcell`/`columnheader`, `tree`/`treeitem`/`group`, `listbox`/`option`, `radiogroup`, `separator`, `dialog`) and support the usual keys per role: arrows, Home/End, Page Up/Down in the table, Space/Enter activation, F2 to edit a table cell, Shift/Ctrl ranges in lists, trees and tables, arrow keys on a split divider, Esc closes popups. Focus is restored after a dialog closes.

## 7. Theming

Every renderer uses only `--wb-*` CSS variables for colour, spacing and typography; the platform ships `light` and `dark` themes. Applications override variables in their own CSS (registered through a renderer's `css` attribute) — a `Themes/<name>` registry folder is not built yet.

## 8. Testing

- `ComponentsTest` (webbeans-ui): wire encoding and client events of `NumberField` (locale format spec, range, parse failure → resend), `Form` (required/untouched logic, validate/write/reset), `DatePicker`, `Slider` (clamping, `INPUT`), `RadioGroup` (unknown key), `ListBox` (keys ↔ objects, dropped selection), `SplitPane`, `Tree` (lazy children, no refetch, replay on `init`), `Upload.received` (temp file deleted), `DownloadService` (one-time vs. reusable tokens).
- `TableTest`: columns on the wire, paging and totals, server-side sort/filter, keyed `update` vs. `reset`, selection/activate/row-menu/edit events including a rejected edit, `LazyTableModel` on the background thread.
- `WbServerTest.uploadAndDownload`: raw `POST /wb/upload` (token required, path-stripped file name) → `UploadEvent` on the session thread → delta; a download offered by a button → `GET /wb/download/<token>` with the RFC 6266 header, single use.
- Browser: `webbeans-demo/e2e/components.js` drives the demo's *Components* and *Orders* windows (formatting and validation, downloads, a dropped file, tree → list → split pane, a 10 000-row table with scrolling, sort, filter, selection, keyboard, inline edit, row menu, keyed updates, add/delete, reconnect).

## 9. Change log

- **v0.3 (step 12, as built)** — tier-2/3 components: `ToggleButton`, `ButtonGroup`, `SplitButton`, `ColorPicker`, `TimePicker`, `PasswordField`, `TagInput`, `Badge`, `Chip`, `Avatar`, `EmptyState`, `Breadcrumb`, `Stepper`, `Card`, `Accordion`, `Popover`, `Panel.BUSY`; `TreeTable`/`TreeTableModel`, `PropertyGrid`; `CodeEditor`, `MarkdownEditor`, `DiffView` + `LineDiff`, `OutputView`; `Calendar`, `Kanban`, `Diagram` + `Palette`, `ImageViewer`, `PdfViewer`; `QuickSearch` + `QuickSearchProvider` (actions module); `Labeled`/`EnumLabels` and enum-aware property editors (`PropertyEditors.registerEnum`, `Choice` icons, `Property.setRenderer`/`getValueAsCell`); `Markdown` moved to `org.webbeans.ui.text`; `Component.fireEvent`, `Table.decorateRow`, `Table(String rendererId)`, `wb.locale`, `wb.placePopup`, utility CSS classes (`wb-muted`, `wb-strong`, `wb-ok`, `wb-warn`, `wb-danger`). Not built: a map component (no vendored tile library offline — `Scene3D`'s on-demand loading pattern would carry one), rich-text (WYSIWYG) editing beyond Markdown, a diagram minimap (the flag is reserved), `Diagram` edge routing (straight lines only), `Calendar` resizing of timed entries.
- **v0.2 (step 11, as built)** — `NumberField`, `Slider`, `RadioGroup`, `DatePicker`/`DateTimePicker`, `Upload` + `/wb/upload`, `Form`; `SplitPane`, `ScrollPane`; `Table` with `Column`/`Cell`/`Query`/`TableModel`/`ListTableModel`/`LazyTableModel`; `ListBox`, `Tree`/`TreeModel`; `DownloadService` + `/wb/download/<token>`; `ValueComponent.validate()`/`isValid()`/`validateRange`/`isEmpty`, `REQUIRED` enforced by the platform, `Component.resend`, `UI` attributes, platform bundle for validation texts (en/de). Differences from v0.1: `NumberField` sends locale separators rather than a compiled `DecimalFormat` pattern; dates are native inputs; uploads are raw single-request bodies, not multipart/chunked; `Table` has no `frozen` rendering yet (the flag travels), context actions are left to the application (`addRowMenuListener` + `Popup`), column widths are not persisted; `ListBox` is not paged; `Tree.ROOT`/`ROOT_VISIBLE` became `ROOTS`; themes are plain CSS, no `Themes/` folder.
- **v0.1** — the specification.
