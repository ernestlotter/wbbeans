package org.webbeans.ui.components;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.UI;

/**
 * An append-only log / console view (NetBeans' Output window): lines are appended from the server and streamed
 * to the browser in batches, styled per line ({@link Style}), with optional timestamps, ANSI colour support,
 * follow-tail (which the user can switch off by scrolling up) and hyperlinks — URLs in the text become links,
 * and a line appended with a {@link #append(String, Style, String) link id} becomes a clickable line that reports
 * {@link #LINK} to the server (like {@code OutputListener}). A ring buffer of {@link #MAX_LINES} lines is kept so a
 * reconnecting client gets the tail back.
 */
@Renderer(id = "wb.OutputView", resource = "org/webbeans/ui/client/renderers/outputview.js", css = "org/webbeans/ui/client/renderers/outputview.css")
public class OutputView extends Component {

    public enum Style { NORMAL, ERROR, WARNING, INFO, SUCCESS, DEBUG, HEADING }

    /** One output line. */
    public static final class Line implements JsonValue {
        private final String text;
        private final Style style;
        private final long time;
        private final String link;

        Line(String text, Style style, long time, String link) {
            this.text = text == null ? "" : text;
            this.style = style == null ? Style.NORMAL : style;
            this.time = time;
            this.link = link;
        }

        public String getText() {
            return text;
        }

        public Style getStyle() {
            return style;
        }

        public long getTime() {
            return time;
        }

        public String getLink() {
            return link;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("t", text);
            if (style != Style.NORMAL) {
                m.put("s", style.name().toLowerCase());
            }
            m.put("ts", time);
            if (link != null) {
                m.put("link", link);
            }
            return m;
        }
    }

    /** A hyperlink was clicked: {@link #getLink()} is the link id (for linked lines) or the URL. */
    public static final class LinkEvent extends Event {
        public LinkEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getLink() {
            return getString("link");
        }

        public int getLine() {
            return getInt("line", -1);
        }

        public boolean isUrl() {
            return getBoolean("url");
        }
    }

    public static final Property<Integer> MAX_LINES = Property.of("maxLines", Integer.class, Integer.valueOf(10000));
    /** Follow the tail; the client switches it off when the user scrolls up and on again at the bottom. */
    public static final Property<Boolean> FOLLOW = Property.of("follow", Boolean.class, Boolean.TRUE).clientOwned();
    public static final Property<Boolean> WRAP = Property.of("wrap", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> TIMESTAMPS = Property.of("timestamps", Boolean.class, Boolean.FALSE);
    /** Interpret ANSI SGR escape sequences (colours, bold) in the text. */
    public static final Property<Boolean> ANSI = Property.of("ansi", Boolean.class, Boolean.TRUE);

    public static final EventType<LinkEvent> LINK = EventType.of("link", LinkEvent::new);
    public static final EventType<Event> FOLLOW_CHANGED = EventType.of("follow", Event::new);

    private final Deque<Line> lines = new ArrayDeque<Line>();
    private final List<Line> pending = new ArrayList<Line>();
    private boolean flushScheduled;
    private boolean clearPending;
    private final Runnable flusher = new Runnable() {
        @Override
        public void run() {
            flushScheduled = false;
            if (clearPending) {
                clearPending = false;
                call("clear");
            }
            if (!pending.isEmpty()) {
                call("append", new ArrayList<Line>(pending));
                pending.clear();
            }
        }
    };

    public OutputView() {
        super("wb.OutputView");
        addListener(FOLLOW_CHANGED, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(FOLLOW, e.getBoolean("value"));
            }
        });
    }

    // ---- appending ---------------------------------------------------------------------------------------------

    public OutputView append(String text) {
        return append(text, Style.NORMAL, null);
    }

    public OutputView append(String text, Style style) {
        return append(text, style, null);
    }

    /**
     * Appends a line that acts as a hyperlink: clicking it fires {@link #LINK} with {@code link} as the id
     * (a file path, an element id — whatever the listener needs to navigate).
     */
    public OutputView append(String text, Style style, String link) {
        checkThread();
        if (text != null && text.indexOf('\n') >= 0) {
            for (String part : text.split("\r?\n", -1)) {
                add(new Line(part, style, System.currentTimeMillis(), link));
            }
        } else {
            add(new Line(text, style, System.currentTimeMillis(), link));
        }
        return this;
    }

    public OutputView appendAll(Collection<String> texts) {
        for (String t : texts) {
            append(t);
        }
        return this;
    }

    public OutputView error(String text) {
        return append(text, Style.ERROR);
    }

    public OutputView warning(String text) {
        return append(text, Style.WARNING);
    }

    public OutputView info(String text) {
        return append(text, Style.INFO);
    }

    public OutputView success(String text) {
        return append(text, Style.SUCCESS);
    }

    public OutputView heading(String text) {
        return append(text, Style.HEADING);
    }

    private void add(Line l) {
        lines.addLast(l);
        int max = Math.max(1, get(MAX_LINES));
        while (lines.size() > max) {
            lines.removeFirst();
        }
        pending.add(l);
        if (pending.size() > max) {
            pending.remove(0);
        }
        schedule();
    }

    public OutputView clear() {
        checkThread();
        lines.clear();
        pending.clear();
        clearPending = true;
        schedule();
        return this;
    }

    private void schedule() {
        if (flushScheduled) {
            return;
        }
        UI ui = getUI();
        if (ui != null) {
            flushScheduled = true;
            ui.beforeFlush(flusher);
        }
        // not attached yet: attached() flushes what accumulated
    }

    @Override
    protected void attached() {
        if (!pending.isEmpty() || clearPending) {
            flusher.run();
        }
    }

    @Override
    protected void resync() {
        // the client starts from the properties alone: send the whole buffer again
        pending.clear();
        clearPending = false;
        flushScheduled = false;
        if (!lines.isEmpty()) {
            call("set", new ArrayList<Line>(lines));
        }
    }

    // ---- state -------------------------------------------------------------------------------------------------

    /** The buffered lines (oldest first), at most {@link #MAX_LINES}. */
    public List<Line> getLines() {
        return Collections.unmodifiableList(new ArrayList<Line>(lines));
    }

    public int getLineCount() {
        return lines.size();
    }

    /** The buffered text, lines joined with {@code \n}. */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(l.text);
        }
        return sb.toString();
    }

    public OutputView setMaxLines(int n) {
        set(MAX_LINES, n);
        while (lines.size() > Math.max(1, n)) {
            lines.removeFirst();
        }
        return this;
    }

    public OutputView setFollow(boolean b) {
        set(FOLLOW, b);
        return this;
    }

    public boolean isFollow() {
        return Boolean.TRUE.equals(get(FOLLOW));
    }

    public OutputView setWrap(boolean b) {
        set(WRAP, b);
        return this;
    }

    public OutputView setTimestamps(boolean b) {
        set(TIMESTAMPS, b);
        return this;
    }

    public OutputView setAnsi(boolean b) {
        set(ANSI, b);
        return this;
    }

    /** Scrolls to the last line (and switches follow back on). */
    public void scrollToEnd() {
        set(FOLLOW, Boolean.TRUE);
        call("scrollToEnd");
    }

    public Registration addLinkListener(Listener<LinkEvent> l) {
        return addListener(LINK, l);
    }
}
