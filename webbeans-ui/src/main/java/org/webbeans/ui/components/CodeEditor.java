package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.ValueComponent;

/**
 * A source editor: line numbers, syntax highlighting for the common languages (done in the browser, no
 * dependency), a gutter with {@link Annotation annotations} (errors, warnings, marks) kept on the server, a
 * current-line highlight, {@link #goToLine}, tab/indent handling. The value is the whole text ({@code change}
 * on blur, {@link #INPUT} while typing, debounced); {@link #CURSOR} reports the caret (debounced) when listened.
 * For very large files prefer a read-only {@link OutputView} or paging — the whole text travels as one string.
 */
@Renderer(id = "wb.CodeEditor", resource = "org/webbeans/ui/client/renderers/codeeditor.js", css = "org/webbeans/ui/client/renderers/codeeditor.css")
public class CodeEditor extends ValueComponent<String> {

    /** Highlighting modes the client knows; anything else is shown as plain text. */
    public enum Language { PLAIN, JAVA, JAVASCRIPT, JSON, XML, HTML, CSS, PROPERTIES, MARKDOWN, SQL, SHELL, PYTHON, YAML }

    /** A gutter marker on one line. */
    public static final class Annotation implements JsonValue {
        public enum Kind { ERROR, WARNING, INFO, MARK, BOOKMARK }

        private final int line;
        private final Kind kind;
        private final String message;

        public Annotation(int line, Kind kind, String message) {
            this.line = line;
            this.kind = kind;
            this.message = message;
        }

        public int getLine() {
            return line;
        }

        public Kind getKind() {
            return kind;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("line", line);
            m.put("kind", kind.name().toLowerCase());
            if (message != null) {
                m.put("message", message);
            }
            return m;
        }
    }

    public static final Property<String> VALUE = Property.of("value", String.class, "").clientOwned();
    public static final Property<Language> LANGUAGE = Property.of("language", Language.class, Language.PLAIN);
    public static final Property<Boolean> LINE_NUMBERS = Property.of("lineNumbers", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> WRAP = Property.of("wrap", Boolean.class, Boolean.FALSE);
    public static final Property<Integer> TAB_SIZE = Property.of("tabSize", Integer.class, Integer.valueOf(4));
    public static final Property<Boolean> HIGHLIGHT_CURRENT_LINE = Property.of("highlightLine", Boolean.class, Boolean.TRUE);
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    @SuppressWarnings("unchecked")
    public static final Property<List<Annotation>> ANNOTATIONS = (Property<List<Annotation>>) (Property<?>) Property.of("annotations", List.class, new ArrayList<Annotation>());

    /** While typing (debounced 300 ms); {@code getValue()} is the text. */
    public static final EventType<TextField.InputEvent> INPUT = EventType.of("input", TextField.InputEvent::new).lazy(300);
    /** The caret moved (debounced 200 ms): {@code line}, {@code col} (1-based). */
    public static final EventType<Event> CURSOR = EventType.of("cursor", Event::new).lazy(200);
    /** A gutter annotation was clicked: {@code line}. */
    public static final EventType<Event> GUTTER_CLICK = EventType.of("gutter", Event::new);

    public CodeEditor() {
        super("wb.CodeEditor", VALUE);
    }

    public CodeEditor(Language language) {
        this();
        setLanguage(language);
    }

    @Override
    protected String fromClient(Object raw) {
        return raw == null ? "" : raw.toString();
    }

    @Override
    protected void onEvent(Event event) {
        if (event instanceof TextField.InputEvent) {
            setFromClient(VALUE, ((TextField.InputEvent) event).getValue());
        }
    }

    public CodeEditor setLanguage(Language l) {
        set(LANGUAGE, l);
        return this;
    }

    public CodeEditor setLineNumbers(boolean b) {
        set(LINE_NUMBERS, b);
        return this;
    }

    public CodeEditor setWrap(boolean b) {
        set(WRAP, b);
        return this;
    }

    public CodeEditor setTabSize(int n) {
        set(TAB_SIZE, n);
        return this;
    }

    public CodeEditor setHighlightCurrentLine(boolean b) {
        set(HIGHLIGHT_CURRENT_LINE, b);
        return this;
    }

    public CodeEditor setPlaceholder(String p) {
        set(PLACEHOLDER, p);
        return this;
    }

    /** Replaces the gutter annotations. */
    public CodeEditor setAnnotations(Collection<Annotation> annotations) {
        set(ANNOTATIONS, new ArrayList<Annotation>(annotations));
        return this;
    }

    public CodeEditor addAnnotation(Annotation a) {
        List<Annotation> l = new ArrayList<Annotation>(get(ANNOTATIONS));
        l.add(a);
        set(ANNOTATIONS, l);
        return this;
    }

    public List<Annotation> getAnnotations() {
        return java.util.Collections.unmodifiableList(get(ANNOTATIONS));
    }

    public CodeEditor clearAnnotations() {
        set(ANNOTATIONS, new ArrayList<Annotation>());
        return this;
    }

    /** Moves the caret to a line (1-based) and scrolls it into view. */
    public void goToLine(int line) {
        call("goToLine", line);
    }

    public void focus() {
        call("focus");
    }

    /** Inserts text at the caret (replacing the selection). The client reports the new value through {@code input}. */
    public void insertText(String text) {
        call("insert", text);
    }

    /** Selects a range of characters. */
    public void setSelection(int start, int end) {
        call("select", start, end);
    }

    /** The number of lines of the current value. */
    public int getLineCount() {
        String v = getValue();
        if (v == null || v.isEmpty()) {
            return 1;
        }
        int n = 1;
        for (int i = 0; i < v.length(); i++) {
            if (v.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    public Registration addInputListener(Listener<TextField.InputEvent> l) {
        return addListener(INPUT, l);
    }

    public Registration addCursorListener(Listener<Event> l) {
        return addListener(CURSOR, l);
    }

    public Registration addGutterClickListener(Listener<Event> l) {
        return addListener(GUTTER_CLICK, l);
    }
}
