package org.webbeans.ui.table;

import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Html;

/**
 * A pre-rendered table cell: text with an optional icon and style, or sanitised HTML. A column's value function
 * may return one of these instead of a plain value when a cell needs more than text.
 */
public final class Cell implements JsonValue {

    private final String text;
    private final Resource icon;
    private final String html;
    private final String style;
    private final String tooltip;

    private Cell(String text, Resource icon, String html, String style, String tooltip) {
        this.text = text;
        this.icon = icon;
        this.html = html;
        this.style = style;
        this.tooltip = tooltip;
    }

    public static Cell of(String text) {
        return new Cell(text, null, null, null, null);
    }

    public static Cell of(String text, Resource icon) {
        return new Cell(text, icon, null, null, null);
    }

    /** HTML content, sanitised like {@link Html}. */
    public static Cell html(String html) {
        return new Cell(null, null, Html.sanitize(html), null, null);
    }

    public Cell icon(Resource r) {
        return new Cell(text, r, html, style, tooltip);
    }

    /** Extra CSS class(es) on the cell. */
    public Cell style(String css) {
        return new Cell(text, icon, html, css, tooltip);
    }

    public Cell tooltip(String t) {
        return new Cell(text, icon, html, style, t);
    }

    public String getText() {
        return text;
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (text != null) {
            m.put("t", text);
        }
        if (icon != null) {
            m.put("i", icon);
        }
        if (html != null) {
            m.put("h", html);
        }
        if (style != null) {
            m.put("s", style);
        }
        if (tooltip != null) {
            m.put("tt", tooltip);
        }
        return m;
    }
}
