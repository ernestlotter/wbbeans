package org.webbeans.map;

import java.util.Map;

import org.webbeans.ui.components.Html;

/** A speech-bubble at a position with sanitised HTML; opened and closed by the server or by the user's ×. */
public class Popup extends Overlay {

    private LatLng position;
    private String html;
    private boolean open = true;
    private boolean closeButton = true;
    private int maxWidth = 300;

    Popup(String id, LatLng position, String html) {
        super(id);
        this.position = position;
        this.html = Html.sanitize(html == null ? "" : html);
    }

    public LatLng getPosition() {
        return position;
    }

    public Popup setPosition(LatLng p) {
        position = p;
        changed();
        return this;
    }

    /** The content; sanitised like the {@code Html} component (no scripts, no event handlers). */
    public Popup setHtml(String h) {
        html = Html.sanitize(h == null ? "" : h);
        changed();
        return this;
    }

    public Popup setText(String text) {
        html = Html.sanitize(text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>"));
        changed();
        return this;
    }

    public Popup setOpen(boolean b) {
        open = b;
        changed();
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    void closedByUser() {
        open = false;
    }

    public Popup setCloseButton(boolean b) {
        closeButton = b;
        changed();
        return this;
    }

    public Popup setMaxWidth(int px) {
        maxWidth = px;
        changed();
        return this;
    }

    @Override
    String kind() {
        return "popup";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("p", position.toJson());
        m.put("html", html);
        if (!open) {
            m.put("open", false);
        }
        if (!closeButton) {
            m.put("close", false);
        }
        if (maxWidth != 300) {
            m.put("mw", maxWidth);
        }
    }
}
