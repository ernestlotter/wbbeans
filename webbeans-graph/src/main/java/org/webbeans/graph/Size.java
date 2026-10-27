package org.webbeans.graph;

/** An immutable width/height. */
public final class Size {

    public final double width;
    public final double height;

    public Size(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public static Size of(double w, double h) {
        return new Size(w, h);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Size && ((Size) o).width == width && ((Size) o).height == height;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(width).hashCode() * 31 + Double.valueOf(height).hashCode();
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
