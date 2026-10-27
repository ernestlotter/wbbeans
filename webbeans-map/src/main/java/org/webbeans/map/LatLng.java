package org.webbeans.map;

import java.util.Arrays;
import java.util.List;

/** A WGS-84 position in degrees; immutable. Longitudes are normalised to −180…180. */
public final class LatLng {

    public final double lat;
    public final double lng;

    public LatLng(double lat, double lng) {
        this.lat = Math.max(-90, Math.min(90, lat));
        double l = lng;
        while (l > 180) {
            l -= 360;
        }
        while (l < -180) {
            l += 360;
        }
        this.lng = l;
    }

    public static LatLng of(double lat, double lng) {
        return new LatLng(lat, lng);
    }

    List<Double> toJson() {
        return Arrays.asList(lat, lng);
    }

    static LatLng fromJson(Object o) {
        if (o instanceof List && ((List<?>) o).size() >= 2) {
            Object a = ((List<?>) o).get(0);
            Object b = ((List<?>) o).get(1);
            if (a instanceof Number && b instanceof Number) {
                return new LatLng(((Number) a).doubleValue(), ((Number) b).doubleValue());
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LatLng && ((LatLng) o).lat == lat && ((LatLng) o).lng == lng;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(lat).hashCode() * 31 + Double.valueOf(lng).hashCode();
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%.6f,%.6f", lat, lng);
    }
}
