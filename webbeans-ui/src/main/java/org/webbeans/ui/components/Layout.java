package org.webbeans.ui.components;

import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.ui.Component;
import org.webbeans.ui.Size;

/** How a {@link Panel} arranges its children. Constraints are given when adding a child. */
public abstract class Layout implements JsonValue {

    public enum Orientation { HORIZONTAL, VERTICAL }

    public enum Align { START, CENTER, END, STRETCH }

    abstract String type();

    protected Map<String, Object> params() {
        return new LinkedHashMap<String, Object>();
    }

    @Override
    public final Object toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("t", type());
        m.putAll(params());
        return m;
    }

    /** One child fills the panel. */
    public static final class Fill extends Layout {
        @Override
        String type() {
            return "fill";
        }
    }

    /** NORTH/SOUTH/EAST/WEST/CENTER constraints. */
    public static final class Border extends Layout {
        public static final String NORTH = "north", SOUTH = "south", EAST = "east", WEST = "west", CENTER = "center";

        @Override
        String type() {
            return "border";
        }
    }

    /** Children in a row or column with a gap; a {@link Spacer} takes the remaining space. */
    public static final class Flow extends Layout {
        private final Orientation orientation;
        private final int gap;
        private Align align = Align.START;
        private boolean wrap;

        public Flow(Orientation orientation, int gap) {
            this.orientation = orientation;
            this.gap = gap;
        }

        public Flow align(Align a) {
            this.align = a;
            return this;
        }

        public Flow wrap(boolean w) {
            this.wrap = w;
            return this;
        }

        @Override
        String type() {
            return "flow";
        }

        @Override
        protected Map<String, Object> params() {
            Map<String, Object> m = super.params();
            m.put("o", orientation == Orientation.VERTICAL ? "v" : "h");
            m.put("gap", gap);
            m.put("align", align.name().toLowerCase());
            m.put("wrap", wrap);
            return m;
        }
    }

    /** A grid; children fill row-major or use {@link #cell(int, int, int, int)} constraints. */
    public static final class Grid extends Layout {
        private final int rows;
        private final int cols;
        private final int hgap;
        private final int vgap;
        private Size[] columnWidths;
        private Size[] rowHeights;

        public Grid(int rows, int cols, int hgap, int vgap) {
            this.rows = rows;
            this.cols = cols;
            this.hgap = hgap;
            this.vgap = vgap;
        }

        public Grid columnWidths(Size... widths) {
            this.columnWidths = widths;
            return this;
        }

        /** Row heights ({@code Size.fill()} = share the remaining height); default: rows size to their content. */
        public Grid rowHeights(Size... heights) {
            this.rowHeights = heights;
            return this;
        }

        /** Every row and column shares the available space equally (dashboards). */
        public Grid fill() {
            Size[] w = new Size[cols];
            Size[] h = new Size[rows];
            java.util.Arrays.fill(w, Size.fill());
            java.util.Arrays.fill(h, Size.fill());
            return columnWidths(w).rowHeights(h);
        }

        public static Map<String, Object> cell(int row, int col, int rowSpan, int colSpan) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("row", row);
            m.put("col", col);
            m.put("rs", rowSpan);
            m.put("cs", colSpan);
            return m;
        }

        @Override
        String type() {
            return "grid";
        }

        @Override
        protected Map<String, Object> params() {
            Map<String, Object> m = super.params();
            m.put("rows", rows);
            m.put("cols", cols);
            m.put("hgap", hgap);
            m.put("vgap", vgap);
            if (columnWidths != null) {
                m.put("widths", columnWidths);
            }
            if (rowHeights != null) {
                m.put("heights", rowHeights);
            }
            return m;
        }
    }

    /** Two-column label/field form; use {@link #row(String, Component)} and {@link #section(String)} constraints. */
    public static final class Form extends Layout {
        private Size labelWidth;

        public Form labelWidth(Size w) {
            this.labelWidth = w;
            return this;
        }

        /** The constraint for a form row. */
        public static Map<String, Object> row(String label) {
            return row(label, null);
        }

        public static Map<String, Object> row(String label, String hint) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("label", label == null ? "" : label);
            if (hint != null) {
                m.put("hint", hint);
            }
            return m;
        }

        public static Map<String, Object> section(String title) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("section", title);
            return m;
        }

        @Override
        String type() {
            return "form";
        }

        @Override
        protected Map<String, Object> params() {
            Map<String, Object> m = super.params();
            if (labelWidth != null) {
                m.put("labelWidth", labelWidth);
            }
            return m;
        }
    }

    /** One child visible at a time ({@link Panel#setSelected}). */
    public static final class Stack extends Layout {
        @Override
        String type() {
            return "stack";
        }
    }
}
