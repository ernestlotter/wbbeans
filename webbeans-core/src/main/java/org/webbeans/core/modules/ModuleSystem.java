package org.webbeans.core.modules;

import java.util.Collection;
import java.util.List;

import org.openide.util.Lookup;

/**
 * The module manager: discovers module jars, resolves dependencies, creates class loaders and drives the
 * {@link ModuleInstall} lifecycle. One instance per process, published in {@code Lookup.getDefault()}.
 */
public abstract class ModuleSystem {

    /** The process-wide module system, or null before the platform has started. */
    public static ModuleSystem getDefault() {
        return Lookup.getDefault().lookup(ModuleSystem.class);
    }

    /** All known modules: enabled, disabled with a problem, and autoload modules nothing wants. */
    public abstract Collection<ModuleInfo> getModules();

    /** Enabled modules in dependency order. */
    public abstract List<ModuleInfo> getEnabledModules();

    public abstract ModuleInfo find(String codeName);

    /**
     * Resolves and enables the given (known) modules together with everything they need. Modules that cannot be
     * enabled are reported through {@link ModuleListener#moduleDisabled} and {@link ModuleInfo#getProblem()}.
     */
    public abstract void enable(Collection<ModuleInfo> modules);

    /**
     * Disables the given modules. Only modules no other enabled module depends on may be disabled.
     *
     * @throws IllegalArgumentException if an enabled module outside the batch depends on one of them
     */
    public abstract void disable(Collection<ModuleInfo> modules);

    /** The loader that sees the platform and every enabled module. */
    public abstract SystemClassLoader getSystemClassLoader();

    /** Runs {@code closing()} on every enabled module (reverse order); returns false if one vetoed. */
    public abstract boolean closing();

    /** Runs {@code close()} on every enabled module in reverse order. */
    public abstract void close();

    public abstract void addModuleListener(ModuleListener l);

    public abstract void removeModuleListener(ModuleListener l);
}
