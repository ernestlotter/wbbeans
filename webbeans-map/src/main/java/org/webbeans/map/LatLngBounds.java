package org.webbeans.map;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** A latitude/longitude box; immutable. */
public final class LatLngBounds {

    public final double south;
    public final double west;
    public final double north;
    public final double east;

    public LatLngBounds(double south, double west, double north, double east) {
        this.south = Math.min(south, north);
        this.north = Math.max(south, north);
        this.west = Math.min(west, east);
        this.east = Math.max(west, east);
    }

    public LatLngBounds(LatLng a, LatLng b) {
        this(a.lat, a.lng, b.lat, b.lng);
    }

    /** The smallest box around the points; null for none. */
    public static LatLngBounds of(Collection<LatLng> points) {
        LatLngBounds b = null;
        for (LatLng p : points) {
            b = b == null ? new LatLngBounds(p, p) : b.extend(p);
        }
        return b;
    }

    public LatLngBounds extend(LatLng p) {
        return new LatLngBounds(Math.min(south, p.lat), Math.min(west, p.lng), Math.max(north, p.lat), Math.max(east, p.lng));
    }

    public LatLngBounds extend(LatLngBounds o) {
        return new LatLngBounds(Math.min(south, o.south), Math.min(west, o.west), Math.max(north, o.north), Math.max(east, o.east));
    }

    /** Grown by a fraction of the width and height on every side (0.1 = 10 %). */
    public LatLngBounds pad(double fraction) {
        double dh = (north - south) * fraction;
        double dw = (east - west) * fraction;
        return new LatLngBounds(south - dh, west - dw, north + dh, east + dw);
    }

    public LatLng getCenter() {
        return new LatLng((south + north) / 2, (west + east) / 2);
    }

    public LatLng getSouthWest() {
        return new LatLng(south, west);
    }

    public LatLng getNorthEast() {
        return new LatLng(north, east);
    }

    public boolean contains(LatLng p) {
        return p.lat >= south && p.lat <= north && p.lng >= west && p.lng <= east;
    }

    public boolean intersects(LatLngBounds o) {
        return o.north >= south && o.south <= north && o.east >= west && o.west <= east;
    }

    List<Double> toJson() {
        return Arrays.asList(south, west, north, east);
    }

    static LatLngBounds fromJson(Object o) {
        if (o instanceof List && ((List<?>) o).size() >= 4) {
            List<?> l = (List<?>) o;
            double[] v = new double[4];
            for (int i = 0; i < 4; i++) {
                if (!(l.get(i) instanceof Number)) {
                    return null;
                }
                v[i] = ((Number) l.get(i)).doubleValue();
            }
            return new LatLngBounds(v[0], v[1], v[2], v[3]);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LatLngBounds && ((LatLngBounds) o).south == south && ((LatLngBounds) o).west == west
                && ((LatLngBounds) o).north == north && ((LatLngBounds) o).east == east;
    }

    @Override
    public int hashCode() {
        return toJson().hashCode();
    }

    @Override
    public String toString() {
        return "[" + south + "," + west + " .. " + north + "," + east + "]";
    }
}
