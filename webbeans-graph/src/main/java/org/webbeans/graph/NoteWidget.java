package org.webbeans.graph;

import java.util.Map;

/**
 * A sticky note: free text with a folded corner, anywhere on the scene, optionally attached to another widget
 * with a dashed connector that follows it. Notes are not part of the object model (no node or edge object);
 * the scene keys them by id.
 */
public class NoteWidget extends Widget {

    private String text;
    private Point location = new Point(0, 0);
    private double width = 160;
    private Widget attachedTo;
    private boolean movable = true;
    private boolean textEditable;
    private double fontSize;

    NoteWidget(String id) {
        super(id);
    }

    public String getText() {
        return text;
    }

    /** The note's text; line breaks are kept, long lines wrap to the width. */
    public NoteWidget setText(String t) {
        text = t;
        changed();
        return this;
    }

    public Point getLocation() {
        return location;
    }

    /** Top-left corner. */
    public NoteWidget setLocation(Point p) {
        location = p == null ? new Point(0, 0) : p;
        changed();
        return this;
    }

    public NoteWidget setLocation(double x, double y) {
        return setLocation(new Point(x, y));
    }

    void moved(Point p) {
        location = p;
    }

    public double getWidth() {
        return width;
    }

    /** The width; the height follows the text. */
    public NoteWidget setWidth(double w) {
        width = Math.max(40, w);
        changed();
        return this;
    }

    public Widget getAttachedTo() {
        return attachedTo;
    }

    /** Draws a dashed connector to the widget (a node, pin or edge); null detaches. */
    public NoteWidget attachTo(Widget w) {
        attachedTo = w;
        changed();
        return this;
    }

    public boolean isMovable() {
        return movable;
    }

    public NoteWidget setMovable(boolean b) {
        movable = b;
        changed();
        return this;
    }

    /** Double-click edits the text in place; the result arrives as {@link GraphScene#RENAME}. */
    public NoteWidget setTextEditable(boolean b) {
        textEditable = b;
        changed();
        return this;
    }

    public boolean isTextEditable() {
        return textEditable;
    }

    /** Font size in pixels; 0 = theme default. */
    public NoteWidget setFontSize(double px) {
        fontSize = Math.max(0, px);
        changed();
        return this;
    }

    @Override
    public NoteWidget setVisible(boolean b) {
        super.setVisible(b);
        return this;
    }

    @Override
    public NoteWidget setOpacity(double o) {
        super.setOpacity(o);
        return this;
    }

    @Override
    public NoteWidget setStyleName(String css) {
        super.setStyleName(css);
        return this;
    }

    @Override
    public NoteWidget setTooltip(String t) {
        super.setTooltip(t);
        return this;
    }

    @Override
    public NoteWidget setForeground(String css) {
        super.setForeground(css);
        return this;
    }

    /** The paper colour; null = the theme's note yellow. */
    @Override
    public NoteWidget setBackground(String css) {
        super.setBackground(css);
        return this;
    }

    @Override
    public NoteWidget setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        return this;
    }

    @Override
    void writeJson(Map<String, Object> m) {
        put(m, "tx", text);
        m.put("x", location.x);
        m.put("y", location.y);
        m.put("w", width);
        if (attachedTo != null) {
            m.put("to", attachedTo.getId());
        }
        if (!movable) {
            m.put("mv", false);
        }
        if (textEditable) {
            m.put("ed", true);
        }
        if (fontSize > 0) {
            m.put("fs", fontSize);
        }
    }
}
