package org.webbeans.map;

import java.util.List;

/** Geodesic helpers on the WGS-84 sphere (R = 6 371 008.8 m) and the Web Mercator projection the map uses. */
public final class Geo {

    public static final double EARTH_RADIUS = 6371008.8;
    /** Mercator's latitude limit; the map does not show beyond it. */
    public static final double MAX_LATITUDE = 85.05112878;

    private Geo() {
    }

    /** Great-circle distance in metres (haversine). */
    public static double distance(LatLng a, LatLng b) {
        double la1 = Math.toRadians(a.lat);
        double la2 = Math.toRadians(b.lat);
        double dLat = la2 - la1;
        double dLng = Math.toRadians(b.lng - a.lng);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Length of a path in metres. */
    public static double length(List<LatLng> path) {
        double sum = 0;
        for (int i = 1; i < path.size(); i++) {
            sum += distance(path.get(i - 1), path.get(i));
        }
        return sum;
    }

    /** Area of a ring in square metres (spherical excess approximation, fine below continental scale). */
    public static double area(List<LatLng> ring) {
        if (ring.size() < 3) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < ring.size(); i++) {
            LatLng p = ring.get(i);
            LatLng q = ring.get((i + 1) % ring.size());
            sum += Math.toRadians(q.lng - p.lng) * (2 + Math.sin(Math.toRadians(p.lat)) + Math.sin(Math.toRadians(q.lat)));
        }
        return Math.abs(sum * EARTH_RADIUS * EARTH_RADIUS / 2);
    }

    /** Initial bearing from a to b in degrees, 0 = north, clockwise. */
    public static double bearing(LatLng a, LatLng b) {
        double la1 = Math.toRadians(a.lat);
        double la2 = Math.toRadians(b.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double y = Math.sin(dLng) * Math.cos(la2);
        double x = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** The point at a distance and bearing from a start. */
    public static LatLng destination(LatLng from, double metres, double bearingDeg) {
        double d = metres / EARTH_RADIUS;
        double br = Math.toRadians(bearingDeg);
        double la1 = Math.toRadians(from.lat);
        double lo1 = Math.toRadians(from.lng);
        double la2 = Math.asin(Math.sin(la1) * Math.cos(d) + Math.cos(la1) * Math.sin(d) * Math.cos(br));
        double lo2 = lo1 + Math.atan2(Math.sin(br) * Math.sin(d) * Math.cos(la1), Math.cos(d) - Math.sin(la1) * Math.sin(la2));
        return new LatLng(Math.toDegrees(la2), Math.toDegrees(lo2));
    }

    /** Web Mercator: the point's pixel position in a world of {@code 256 · 2^zoom} pixels. */
    public static double[] project(LatLng p, double zoom) {
        double scale = 256 * Math.pow(2, zoom);
        double lat = Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, p.lat));
        double x = (p.lng + 180) / 360 * scale;
        double s = Math.sin(Math.toRadians(lat));
        double y = (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * scale;
        return new double[] {x, y};
    }

    public static LatLng unproject(double x, double y, double zoom) {
        double scale = 256 * Math.pow(2, zoom);
        double lng = x / scale * 360 - 180;
        double n = Math.PI - 2 * Math.PI * y / scale;
        double lat = Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
        return new LatLng(lat, lng);
    }

    /** Metres covered by one pixel at that latitude and zoom. */
    public static double metresPerPixel(double lat, double zoom) {
        return 40075016.686 * Math.cos(Math.toRadians(lat)) / (256 * Math.pow(2, zoom));
    }

    /** The largest zoom (fractional) at which the bounds fit in a viewport of the given pixel size. */
    public static double zoomToFit(LatLngBounds b, int widthPx, int heightPx, int paddingPx) {
        double[] sw = project(b.getSouthWest(), 0);
        double[] ne = project(b.getNorthEast(), 0);
        double w = Math.max(1e-9, Math.abs(ne[0] - sw[0]));
        double h = Math.max(1e-9, Math.abs(ne[1] - sw[1]));
        double zx = Math.log(Math.max(1, widthPx - 2 * paddingPx) / w) / Math.log(2);
        double zy = Math.log(Math.max(1, heightPx - 2 * paddingPx) / h) / Math.log(2);
        return Math.min(zx, zy);
    }
}
