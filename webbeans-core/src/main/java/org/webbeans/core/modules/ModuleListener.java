package org.webbeans.core.modules;

import java.util.EventListener;
import java.util.List;

/** Notified about module lifecycle events. Methods are called on the thread performing the change. */
public interface ModuleListener extends EventListener {

    /**
     * A batch of modules has class loaders and passed {@code validate()}, but {@code restored()} has not run yet.
     * The registry and the default Lookup rebuild themselves here so that {@code restored()} sees them complete.
     */
    void modulesLoaded(List<ModuleInfo> modules);

    /** A batch of modules has been enabled, in dependency order, and their {@code restored()} hooks have run. */
    void modulesEnabled(List<ModuleInfo> modules);

    /** A batch of modules has been disabled. */
    void modulesDisabled(List<ModuleInfo> modules);

    /** A module could not be enabled. */
    void moduleDisabled(ModuleInfo module, Problem problem);

    /** Convenience base class with empty implementations. */
    class Adapter implements ModuleListener {
        @Override
        public void modulesLoaded(List<ModuleInfo> modules) {
        }

        @Override
        public void modulesEnabled(List<ModuleInfo> modules) {
        }

        @Override
        public void modulesDisabled(List<ModuleInfo> modules) {
        }

        @Override
        public void moduleDisabled(ModuleInfo module, Problem problem) {
        }
    }
}
