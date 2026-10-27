package org.webbeans.graph;

import java.util.Arrays;
import java.util.List;

/** An immutable scene coordinate. */
public final class Point {

    public final double x;
    public final double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Point of(double x, double y) {
        return new Point(x, y);
    }

    List<Double> toJson() {
        return Arrays.asList(x, y);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Point && ((Point) o).x == x && ((Point) o).y == y;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(x).hashCode() * 31 + Double.valueOf(y).hashCode();
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
