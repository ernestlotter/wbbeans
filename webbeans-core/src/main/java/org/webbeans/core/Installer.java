package org.webbeans.core;

/**
 * Start-up and shutdown hooks of an application or library jar. Register a subclass through
 * {@code META-INF/services/org.webbeans.core.Installer} ({@code @ServiceProvider(service = Installer.class)});
 * the platform runs {@link #restored()} on every installer once the registry and the default Lookup are
 * complete, in Lookup order, and {@link #closing()}/{@link #close()} in reverse order on shutdown.
 */
public class Installer {

    /** Called after the registry and {@code Lookup.getDefault()} are complete: seed data, start background jobs. */
    public void restored() {
    }

    /** Called on shutdown; return false to veto it. */
    public boolean closing() {
        return true;
    }

    /** Called on shutdown after every installer agreed to close: stop what {@link #restored()} started. */
    public void close() {
    }
}
