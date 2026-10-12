package org.webbeans.core.modules;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Descriptor and runtime state of one module. The descriptor part is parsed from the jar manifest
 * ({@link #fromManifest}); the runtime part (enabled, class loader, problem) is maintained by the
 * {@link ModuleSystem}.
 *
 * <p>Manifest attributes:
 * <pre>
 * WebBeans-Module: org.example.accounts            (required: code name base)
 * WebBeans-Module-Version: 1.2.0                   (required: specification version)
 * WebBeans-Module-Dependencies: org.a &gt; 1.0, org.b = 2.1.0, org.c
 * WebBeans-Module-Public-Packages: org.example.accounts.api.*, org.example.accounts.spi   (default: none; "-" = none)
 * WebBeans-Module-Install: org.example.accounts.Installer
 * WebBeans-Module-Display-Name: Accounts                (or #KEY into the module's Bundle.properties)
 * WebBeans-Module-Autoload: true
 * WebBeans-Module-Eager: true
 * WebBeans-Module-Requires: token1, token2
 * WebBeans-Module-Provides: token3
 * WebBeans-Module-Class-Path: lib/foo.jar lib/bar.jar  (space separated, inside the module jar)
 * </pre>
 */
public final class ModuleInfo {

    public static final String ATTR_MODULE = "WebBeans-Module";
    public static final String ATTR_VERSION = "WebBeans-Module-Version";
    public static final String ATTR_DEPENDENCIES = "WebBeans-Module-Dependencies";
    public static final String ATTR_PUBLIC_PACKAGES = "WebBeans-Module-Public-Packages";
    public static final String ATTR_INSTALL = "WebBeans-Module-Install";
    public static final String ATTR_DISPLAY_NAME = "WebBeans-Module-Display-Name";
    public static final String ATTR_AUTOLOAD = "WebBeans-Module-Autoload";
    public static final String ATTR_EAGER = "WebBeans-Module-Eager";
    public static final String ATTR_REQUIRES = "WebBeans-Module-Requires";
    public static final String ATTR_PROVIDES = "WebBeans-Module-Provides";
    public static final String ATTR_CLASS_PATH = "WebBeans-Module-Class-Path";

    // descriptor
    private final String codeName;
    private final SpecificationVersion version;
    private final List<Dependency> dependencies;
    private final Set<String> provides;
    private final Set<String> publicPackages;
    private final String installClass;
    private final String displayName;
    private final boolean autoload;
    private final boolean eager;
    private final List<String> classPath;
    private final Map<String, String> attributes;
    private final File location;

    // runtime
    private volatile boolean enabled;
    private volatile ClassLoader classLoader;
    private volatile Problem problem;
    private volatile ModuleInstall install;

    private ModuleInfo(String codeName, SpecificationVersion version, List<Dependency> dependencies, Set<String> provides,
            Set<String> publicPackages, String installClass, String displayName, boolean autoload, boolean eager,
            List<String> classPath, Map<String, String> attributes, File location) {
        this.codeName = codeName;
        this.version = version;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.provides = Collections.unmodifiableSet(provides);
        this.publicPackages = Collections.unmodifiableSet(publicPackages);
        this.installClass = installClass;
        this.displayName = displayName;
        this.autoload = autoload;
        this.eager = eager;
        this.classPath = Collections.unmodifiableList(classPath);
        this.attributes = Collections.unmodifiableMap(attributes);
        this.location = location;
    }

    /**
     * Parses a module manifest.
     *
     * @param manifest the jar manifest
     * @param location the jar file (may be null for classpath/in-memory modules)
     * @return the descriptor, or null if the manifest has no {@code WebBeans-Module} attribute
     * @throws IllegalArgumentException for a malformed module manifest
     */
    public static ModuleInfo fromManifest(Manifest manifest, File location) {
        Attributes a = manifest.getMainAttributes();
        String codeName = a.getValue(ATTR_MODULE);
        if (codeName == null) {
            return null;
        }
        codeName = codeName.trim();
        if (!codeName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid module code name '" + codeName + "'");
        }
        String v = a.getValue(ATTR_VERSION);
        if (v == null) {
            throw new IllegalArgumentException("Module " + codeName + ": missing " + ATTR_VERSION);
        }
        SpecificationVersion version = new SpecificationVersion(v);
        List<Dependency> deps = new ArrayList<Dependency>();
        deps.addAll(Dependency.parseModuleDependencies(a.getValue(ATTR_DEPENDENCIES)));
        deps.addAll(Dependency.parseRequires(a.getValue(ATTR_REQUIRES)));
        for (Dependency d : deps) {
            if (d.getType() == Dependency.Type.MODULE && d.getName().equals(codeName)) {
                throw new IllegalArgumentException("Module " + codeName + " depends on itself");
            }
        }
        Set<String> provides = new LinkedHashSet<String>();
        String p = a.getValue(ATTR_PROVIDES);
        if (p != null) {
            for (String s : p.split(",")) {
                if (!s.trim().isEmpty()) {
                    provides.add(s.trim());
                }
            }
        }
        Set<String> pub = new LinkedHashSet<String>();
        String pp = a.getValue(ATTR_PUBLIC_PACKAGES);
        if (pp != null && !pp.trim().equals("-")) {
            for (String s : pp.split(",")) {
                if (!s.trim().isEmpty()) {
                    pub.add(s.trim());
                }
            }
        }
        List<String> cp = new ArrayList<String>();
        String cpAttr = a.getValue(ATTR_CLASS_PATH);
        if (cpAttr != null) {
            for (String s : cpAttr.trim().split("\\s+")) {
                if (!s.isEmpty()) {
                    cp.add(s);
                }
            }
        }
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        for (Map.Entry<Object, Object> e : a.entrySet()) {
            attrs.put(e.getKey().toString(), e.getValue().toString());
        }
        String install = a.getValue(ATTR_INSTALL);
        return new ModuleInfo(codeName, version, deps, provides, pub,
                install == null ? null : install.trim(),
                a.getValue(ATTR_DISPLAY_NAME),
                Boolean.parseBoolean(a.getValue(ATTR_AUTOLOAD)),
                Boolean.parseBoolean(a.getValue(ATTR_EAGER)),
                cp, attrs, location);
    }

    public String getCodeName() {
        return codeName;
    }

    public SpecificationVersion getVersion() {
        return version;
    }

    /** Module and requires dependencies, in manifest order. */
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public Set<String> getProvides() {
        return provides;
    }

    /** Package names, possibly ending in {@code .*} for a subtree. Empty = nothing exported. */
    public Set<String> getPublicPackages() {
        return publicPackages;
    }

    /** Whether a class in the given package may be loaded by dependent modules. */
    public boolean isPublicPackage(String pkg) {
        for (String p : publicPackages) {
            if (p.endsWith(".*")) {
                String prefix = p.substring(0, p.length() - 2);
                if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) {
                    return true;
                }
            } else if (p.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    public String getInstallClass() {
        return installClass;
    }

    /** Raw display name attribute; may be a {@code #KEY} bundle reference. Null if absent. */
    public String getDisplayNameAttribute() {
        return displayName;
    }

    /**
     * The display name for the session's locale: the attribute, a {@code #KEY} resolved against
     * {@code <codeName>.Bundle} while the module is enabled, else the code name.
     */
    public String getDisplayName() {
        if (displayName == null || displayName.isEmpty()) {
            return codeName;
        }
        if (!displayName.startsWith("#")) {
            return displayName;
        }
        ClassLoader loader = classLoader;
        if (loader == null) {
            return codeName;
        }
        try {
            java.util.ResourceBundle b = org.webbeans.core.i18n.Bundles.getBundle(loader, codeName + ".Bundle",
                    org.webbeans.core.i18n.LocaleContext.get());
            String key = displayName.substring(1);
            return b.containsKey(key) ? b.getString(key) : codeName;
        } catch (java.util.MissingResourceException e) {
            return codeName;
        }
    }

    public boolean isAutoload() {
        return autoload;
    }

    public boolean isEager() {
        return eager;
    }

    /** Wrapped library jars relative to the module jar root. */
    public List<String> getClassPath() {
        return classPath;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    /** The jar file, or null for classpath modules. */
    public File getLocation() {
        return location;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The module's class loader while enabled, else null. */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /** Why the module is disabled, or null. */
    public Problem getProblem() {
        return problem;
    }

    ModuleInstall getInstall() {
        return install;
    }

    void setEnabled(boolean enabled, ClassLoader loader, ModuleInstall install) {
        this.enabled = enabled;
        this.classLoader = loader;
        this.install = install;
        if (enabled) {
            this.problem = null;
        }
    }

    void setProblem(Problem problem) {
        this.problem = problem;
    }

    @Override
    public String toString() {
        return codeName + "/" + version;
    }
}
