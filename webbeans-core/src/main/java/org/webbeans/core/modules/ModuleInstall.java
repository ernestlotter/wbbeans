package org.webbeans.core.modules;

/**
 * Lifecycle hooks of a module. Name the subclass in the {@code WebBeans-Module-Install} manifest attribute.
 * All methods are invoked in module dependency order (reverse order for shutdown).
 */
public class ModuleInstall {

    /**
     * Called before any other class of the module is used, with the module's class loader in place.
     * Throwing {@link IllegalStateException} vetoes enabling the module (and everything depending on it).
     */
    public void validate() throws IllegalStateException {
    }

    /** Called after every module of the batch has been loaded and registered. */
    public void restored() {
    }

    /** Called on shutdown; return false to veto. */
    public boolean closing() {
        return true;
    }

    /** Called on shutdown after every module agreed to close. */
    public void close() {
    }
}
