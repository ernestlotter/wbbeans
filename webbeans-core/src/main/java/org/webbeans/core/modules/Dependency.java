package org.webbeans.core.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One entry of {@code WebBeans-Module-Dependencies} or {@code WebBeans-Module-Requires}.
 * <p>Module dependency syntax: {@code codename}, {@code codename > 1.2} (at least, same major)
 * or {@code codename = 1.2.0} (exact).
 */
public final class Dependency {

    public enum Type { MODULE, REQUIRES }

    public enum Comparison { ANY, AT_LEAST, EXACT }

    private final Type type;
    private final String name;
    private final Comparison comparison;
    private final SpecificationVersion version;

    public Dependency(Type type, String name, Comparison comparison, SpecificationVersion version) {
        if (type == null || name == null || comparison == null) {
            throw new NullPointerException();
        }
        if (comparison != Comparison.ANY && version == null) {
            throw new IllegalArgumentException("Version required for " + comparison);
        }
        this.type = type;
        this.name = name;
        this.comparison = comparison;
        this.version = version;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Comparison getComparison() {
        return comparison;
    }

    /** Null when {@link #getComparison()} is {@link Comparison#ANY}. */
    public SpecificationVersion getVersion() {
        return version;
    }

    /** Whether a module with the given version satisfies this (MODULE) dependency. */
    public boolean isSatisfiedBy(SpecificationVersion actual) {
        switch (comparison) {
            case ANY:
                return true;
            case EXACT:
                return actual.equals(version);
            case AT_LEAST:
                return actual.getMajor() == version.getMajor() && actual.compareTo(version) >= 0;
            default:
                throw new AssertionError(comparison);
        }
    }

    /** Parses the comma-separated manifest attribute value. */
    public static List<Dependency> parseModuleDependencies(String attr) {
        List<Dependency> result = new ArrayList<Dependency>();
        if (attr == null || attr.trim().isEmpty()) {
            return result;
        }
        for (String item : attr.split(",")) {
            String s = item.trim();
            if (s.isEmpty()) {
                continue;
            }
            int gt = s.indexOf('>');
            int eq = s.indexOf('=');
            if (gt >= 0) {
                result.add(new Dependency(Type.MODULE, s.substring(0, gt).trim(), Comparison.AT_LEAST,
                        new SpecificationVersion(s.substring(gt + 1).trim())));
            } else if (eq >= 0) {
                result.add(new Dependency(Type.MODULE, s.substring(0, eq).trim(), Comparison.EXACT,
                        new SpecificationVersion(s.substring(eq + 1).trim())));
            } else {
                result.add(new Dependency(Type.MODULE, s, Comparison.ANY, null));
            }
        }
        return result;
    }

    /** Parses the comma-separated {@code WebBeans-Module-Requires} token list. */
    public static List<Dependency> parseRequires(String attr) {
        if (attr == null || attr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Dependency> result = new ArrayList<Dependency>();
        for (String item : attr.split(",")) {
            String s = item.trim();
            if (!s.isEmpty()) {
                result.add(new Dependency(Type.REQUIRES, s, Comparison.ANY, null));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        switch (comparison) {
            case AT_LEAST:
                return name + " > " + version;
            case EXACT:
                return name + " = " + version;
            default:
                return name;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Dependency)) {
            return false;
        }
        Dependency d = (Dependency) o;
        return type == d.type && name.equals(d.name) && comparison == d.comparison
                && (version == null ? d.version == null : version.equals(d.version));
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + comparison.hashCode();
    }
}
