package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.text.LineDiff;

/**
 * Shows the difference between two texts, side by side or inline, with line numbers, optional syntax
 * highlighting (the {@link CodeEditor.Language} tokenizers) and collapsible unchanged runs ({@link #setContext}).
 * The diff is computed on the server ({@link LineDiff}) and sent as the edit script, so the client never sees
 * more than what it displays. Read-only; {@link #LINE_CLICK} reports clicks when listened.
 */
@Renderer(id = "wb.DiffView", resource = "org/webbeans/ui/client/renderers/diffview.js", css = "org/webbeans/ui/client/renderers/diffview.css")
public class DiffView extends Component {

    public enum Layout { SIDE_BY_SIDE, INLINE }

    /** A line was clicked: {@code side} ("left"/"right"), {@code line} (1-based), {@code op} ("=", "-", "+"). */
    public static final class LineEvent extends Event {
        public LineEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getSide() {
            return getString("side");
        }

        public int getLine() {
            return getInt("line", -1);
        }

        public String getOp() {
            return getString("op");
        }
    }

    public static final Property<Layout> LAYOUT = Property.of("layout", Layout.class, Layout.SIDE_BY_SIDE);
    public static final Property<String> LEFT_TITLE = Property.of("leftTitle", String.class, null);
    public static final Property<String> RIGHT_TITLE = Property.of("rightTitle", String.class, null);
    public static final Property<CodeEditor.Language> LANGUAGE = Property.of("language", CodeEditor.Language.class, CodeEditor.Language.PLAIN);
    /** Unchanged lines to keep around each change; {@code null} shows everything. */
    public static final Property<Integer> CONTEXT = Property.of("context", Integer.class, null);
    public static final Property<Boolean> WRAP = Property.of("wrap", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> LINE_NUMBERS = Property.of("lineNumbers", Boolean.class, Boolean.TRUE);
    /** The edit script the client renders (derived from the texts). */
    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> OPS = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("ops", List.class, new ArrayList<Map<String, Object>>());

    public static final EventType<LineEvent> LINE_CLICK = EventType.of("line", LineEvent::new);

    private String left = "";
    private String right = "";
    private List<LineDiff.Op> ops = new ArrayList<LineDiff.Op>();
    private int added;
    private int removed;

    public DiffView() {
        super("wb.DiffView");
    }

    public DiffView(String left, String right) {
        this();
        setTexts(left, right);
    }

    public DiffView setTexts(String left, String right) {
        checkThread();
        this.left = left == null ? "" : left;
        this.right = right == null ? "" : right;
        recompute();
        return this;
    }

    public DiffView setLeft(String text) {
        return setTexts(text, right);
    }

    public DiffView setRight(String text) {
        return setTexts(left, text);
    }

    public String getLeft() {
        return left;
    }

    public String getRight() {
        return right;
    }

    /** The edit script of the current texts. */
    public List<LineDiff.Op> getOps() {
        return ops;
    }

    public int getAddedCount() {
        return added;
    }

    public int getRemovedCount() {
        return removed;
    }

    public boolean isIdentical() {
        return added == 0 && removed == 0;
    }

    /** The current diff in unified format ({@code @@} hunks) with the given context. */
    public String toUnified(int context) {
        return LineDiff.unified(ops, context);
    }

    private void recompute() {
        ops = LineDiff.diff(left, right);
        added = 0;
        removed = 0;
        for (LineDiff.Op op : ops) {
            if (op.type == LineDiff.Type.INSERT) {
                added++;
            } else if (op.type == LineDiff.Type.DELETE) {
                removed++;
            }
        }
        set(OPS, encode());
    }

    private List<Map<String, Object>> encode() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Integer ctx = get(CONTEXT);
        int n = ops.size();
        int i = 0;
        while (i < n) {
            LineDiff.Op op = ops.get(i);
            if (ctx != null && op.type == LineDiff.Type.EQUAL) {
                // find the run of equal lines and collapse its middle beyond the context
                int j = i;
                while (j < n && ops.get(j).type == LineDiff.Type.EQUAL) {
                    j++;
                }
                int keepBefore = i == 0 ? 0 : ctx;      // context after the previous change
                int keepAfter = j == n ? 0 : ctx;       // context before the next change
                if (j - i > keepBefore + keepAfter + 1) {
                    for (int k = i; k < i + keepBefore; k++) {
                        out.add(line(ops.get(k)));
                    }
                    List<Map<String, Object>> hidden = new ArrayList<Map<String, Object>>();
                    for (int k = i + keepBefore; k < j - keepAfter; k++) {
                        hidden.add(line(ops.get(k)));
                    }
                    Map<String, Object> fold = new LinkedHashMap<String, Object>();
                    fold.put("op", "~");
                    fold.put("lines", hidden);
                    out.add(fold);
                    for (int k = j - keepAfter; k < j; k++) {
                        out.add(line(ops.get(k)));
                    }
                    i = j;
                    continue;
                }
            }
            out.add(line(op));
            i++;
        }
        return out;
    }

    private static Map<String, Object> line(LineDiff.Op op) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("op", op.type == LineDiff.Type.EQUAL ? "=" : op.type == LineDiff.Type.DELETE ? "-" : "+");
        if (op.a >= 0) {
            m.put("a", op.a + 1);
        }
        if (op.b >= 0) {
            m.put("b", op.b + 1);
        }
        m.put("t", op.text);
        return m;
    }

    public DiffView setLayout(Layout l) {
        set(LAYOUT, l);
        return this;
    }

    public DiffView setTitles(String leftTitle, String rightTitle) {
        set(LEFT_TITLE, leftTitle);
        set(RIGHT_TITLE, rightTitle);
        return this;
    }

    public DiffView setLanguage(CodeEditor.Language l) {
        set(LANGUAGE, l);
        return this;
    }

    /** Collapses unchanged runs to {@code lines} of context around each change ({@code null}: show all). */
    public DiffView setContext(Integer lines) {
        set(CONTEXT, lines);
        set(OPS, encode());
        return this;
    }

    public DiffView setWrap(boolean b) {
        set(WRAP, b);
        return this;
    }

    public DiffView setLineNumbers(boolean b) {
        set(LINE_NUMBERS, b);
        return this;
    }

    public Registration addLineClickListener(Listener<LineEvent> l) {
        return addListener(LINE_CLICK, l);
    }
}
