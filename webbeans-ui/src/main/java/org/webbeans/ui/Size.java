package org.webbeans.ui;

import org.webbeans.core.json.JsonValue;

/** A CSS size for {@code WIDTH}/{@code HEIGHT}: {@code px(200)}, {@code percent(50)}, {@code auto()}, {@code fill()}. */
public final class Size implements JsonValue {

    private final String css;

    private Size(String css) {
        this.css = css;
    }

    public static Size px(int px) {
        return new Size(px + "px");
    }

    public static Size percent(int pct) {
        return new Size(pct + "%");
    }

    public static Size em(double em) {
        return new Size(em + "em");
    }

    public static Size auto() {
        return new Size("auto");
    }

    public static Size fill() {
        return new Size("fill");
    }

    @Override
    public Object toJson() {
        return css;
    }

    @Override
    public String toString() {
        return css;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Size && ((Size) o).css.equals(css);
    }

    @Override
    public int hashCode() {
        return css.hashCode();
    }
}
