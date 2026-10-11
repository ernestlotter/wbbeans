package org.webbeans.core.modules;

import java.util.Arrays;

/**
 * A dotted-integer specification version such as {@code 1.2.0}.
 * Compares numerically, segment by segment; missing trailing segments count as zero,
 * so {@code 1.2} equals {@code 1.2.0}.
 */
public final class SpecificationVersion implements Comparable<SpecificationVersion> {

    private final int[] segments;
    private final String text;

    public SpecificationVersion(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty version");
        }
        String[] parts = text.trim().split("\\.");
        int[] segs = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                segs[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid version '" + text + "': segment '" + parts[i] + "' is not a number");
            }
            if (segs[i] < 0) {
                throw new IllegalArgumentException("Invalid version '" + text + "': negative segment");
            }
        }
        this.segments = segs;
        this.text = text.trim();
    }

    /** The first segment, used for compatibility checks: a major bump is incompatible. */
    public int getMajor() {
        return segments[0];
    }

    public int[] getSegments() {
        return segments.clone();
    }

    @Override
    public int compareTo(SpecificationVersion o) {
        int n = Math.max(segments.length, o.segments.length);
        for (int i = 0; i < n; i++) {
            int a = i < segments.length ? segments[i] : 0;
            int b = i < o.segments.length ? o.segments[i] : 0;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SpecificationVersion && compareTo((SpecificationVersion) obj) == 0;
    }

    @Override
    public int hashCode() {
        // normalise trailing zeros so equal versions hash equally
        int end = segments.length;
        while (end > 1 && segments[end - 1] == 0) {
            end--;
        }
        return Arrays.hashCode(Arrays.copyOf(segments, end));
    }

    @Override
    public String toString() {
        return text;
    }
}
