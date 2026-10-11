package org.webbeans.core.registry;

import java.util.List;
import java.util.Map;

/**
 * A folder view of the {@link Registry} for the current {@link RegistryContext}: entries hidden by a module,
 * not allowed for the user's roles, or hidden by the user's overlay are absent; order follows positions
 * (overlay applied), ties broken by name.
 */
public interface Folder {

    /** Full path, e.g. {@code Menu/File}; empty string for the root. */
    String getPath();

    String getName();

    /** The localised {@code displayName} attribute of the folder, or its name. */
    String getDisplayName();

    /** Folder attributes (localised like entry attributes); empty if the folder was never registered explicitly. */
    Map<String, Object> getAttributes();

    /** Ordering among sibling folders (overlay applied); {@link Integer#MAX_VALUE} when unspecified. */
    int getPosition();

    /** Whether anything is registered at or below this path. */
    boolean exists();

    /** Direct sub-folders, ordered. */
    List<Folder> getFolders();

    /** Direct entries, ordered and filtered for the current context. */
    List<Entry> getEntries();

    /** The entry with the given name in this folder, or null (also null when filtered out for the context). */
    Entry getEntry(String name);

    /** Instances of all entries (references followed, separators skipped) that are of the given type, in order. */
    <T> List<T> instances(Class<T> type);
}
