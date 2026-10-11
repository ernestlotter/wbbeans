package org.webbeans.core.registry;

import java.util.Map;
import java.util.Set;

import org.webbeans.core.modules.ModuleInfo;

/**
 * One registration in the {@link Registry}: an instance, a reference to an instance elsewhere, or a separator.
 * Attribute values and display strings are resolved for the current {@link RegistryContext} (locale).
 */
public interface Entry {

    enum Kind { INSTANCE, REFERENCE, SEPARATOR }

    /** Full path, e.g. {@code Menu/File/org-example-SaveAction}. */
    String getPath();

    /** Last path segment. */
    String getName();

    /** Parent folder path, e.g. {@code Menu/File}. */
    String getFolderPath();

    Kind getKind();

    /** Ordering within the folder; {@link Integer#MAX_VALUE} when unspecified. Effective value (user overlay applied). */
    int getPosition();

    /** The module that registered this entry, or null for platform/programmatic registrations. */
    ModuleInfo getModule();

    /** Roles allowed to see the entry; empty = everyone. */
    Set<String> getRoles();

    /**
     * Attributes with {@code #KEY} strings resolved through the registering module's bundle for the current locale.
     * Values are what the registration gave: {@code String}, {@code Integer}, {@code Boolean}, {@code Class},
     * {@code String[]}, or any object.
     */
    Map<String, Object> getAttributes();

    /** Convenience: a string attribute, or {@code null}. */
    String getString(String attribute);

    /** The localised {@code displayName} attribute, or the entry name if absent. */
    String getDisplayName();

    /** The declared type the instance is known to implement, or null if not declared. */
    Class<?> getInstanceOf();

    /** For a {@link Kind#REFERENCE}: the referenced entry, or null if it does not exist. Otherwise {@code this}. */
    Entry getTarget();

    /**
     * The (cached, singleton) instance, created on first call by the registered factory and following references.
     * Returns null for separators, for entries whose instance is not of the requested type, or if creation failed
     * (logged).
     */
    <T> T getInstance(Class<T> type);

    /** A fresh instance from the factory, never cached; null under the same conditions as {@link #getInstance}. */
    <T> T newInstance(Class<T> type);
}
