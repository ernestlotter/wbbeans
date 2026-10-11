package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.ValueComponent;
import org.webbeans.ui.text.Markdown;

/**
 * A Markdown editor: a formatting toolbar (headings, bold, italic, code, link, lists, quote) over a text area,
 * with a live preview rendered on the server ({@link Markdown#toHtml} + {@link Html#sanitize}) — the same
 * renderer the help pages use, so what the user sees is what a help page or an {@link Html} component would show.
 * The value is the Markdown source. {@link #getHtml()} is the rendered, sanitised HTML of the current value.
 */
@Renderer(id = "wb.MarkdownEditor", resource = "org/webbeans/ui/client/renderers/markdowneditor.js", css = "org/webbeans/ui/client/renderers/markdowneditor.css")
public class MarkdownEditor extends ValueComponent<String> {

    public enum Preview { SPLIT, OFF, ONLY }

    public static final Property<String> VALUE = Property.of("value", String.class, "").clientOwned();
    public static final Property<Preview> PREVIEW = Property.of("preview", Preview.class, Preview.SPLIT);
    /** The rendered preview; derived from the value on every (debounced) input. */
    public static final Property<String> PREVIEW_HTML = Property.of("previewHtml", String.class, "");
    public static final Property<Boolean> TOOLBAR = Property.of("toolbar", Boolean.class, Boolean.TRUE);
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    public static final Property<Integer> ROWS = Property.of("rows", Integer.class, null);

    public static final EventType<TextField.InputEvent> INPUT = EventType.of("input", TextField.InputEvent::new).lazy(400);

    public MarkdownEditor() {
        super("wb.MarkdownEditor", VALUE);
        addListener(INPUT, new Listener<TextField.InputEvent>() {
            @Override
            public void onEvent(TextField.InputEvent e) {
                // keep the value and the preview current while typing
                setFromClient(VALUE, e.getValue());
                set(PREVIEW_HTML, render(e.getValue()));
            }
        });
    }

    public MarkdownEditor(String markdown) {
        this();
        setValue(markdown);
    }

    @Override
    protected String fromClient(Object raw) {
        String v = raw == null ? "" : raw.toString();
        set(PREVIEW_HTML, render(v));
        return v;
    }

    @Override
    public MarkdownEditor setValue(String v) {
        super.setValue(v);
        set(PREVIEW_HTML, render(v));
        return this;
    }

    static String render(String markdown) {
        return markdown == null || markdown.isEmpty() ? "" : Html.sanitize(Markdown.toHtml(markdown));
    }

    /** The current value rendered to sanitised HTML. */
    public String getHtml() {
        return render(getValue());
    }

    public MarkdownEditor setPreview(Preview p) {
        set(PREVIEW, p);
        return this;
    }

    public MarkdownEditor setToolbar(boolean b) {
        set(TOOLBAR, b);
        return this;
    }

    public MarkdownEditor setPlaceholder(String p) {
        set(PLACEHOLDER, p);
        return this;
    }

    public MarkdownEditor setRows(Integer rows) {
        set(ROWS, rows);
        return this;
    }

    public void focus() {
        call("focus");
    }

    /** Inserts text at the caret (replacing the selection). */
    public void insertText(String text) {
        call("insert", text);
    }

    public Registration addInputListener(Listener<TextField.InputEvent> l) {
        return addListener(INPUT, l);
    }

    @Override
    protected void onEvent(Event event) {
        // the INPUT listener above does the work; change is handled by ValueComponent
    }
}
