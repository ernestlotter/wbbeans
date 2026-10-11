package org.webbeans.ui;

import org.webbeans.ui.components.Html;

/**
 * A value that knows how to present itself: enums (and other domain values) implementing this interface are
 * shown by their {@link #getDisplayName() display name} wherever the platform renders them — property editors
 * and sheets, outline and table cells, combo boxes, radio groups — instead of {@code name()}/{@code toString()}.
 * {@link EnumLabels} is the lookup that falls back to a bundle key or a prettified constant name for enums that
 * do not implement it.
 */
public interface Labeled {

    /** The localised, human-readable name (call the generated {@code Bundle} for i18n). */
    String getDisplayName();

    /** An icon shown next to the name where the renderer supports it; null for none. */
    default Resource getIcon() {
        return null;
    }

    /** A longer description (tooltip); null for none. Plain text, see {@link Html#sanitize} for rich text elsewhere. */
    default String getDescription() {
        return null;
    }
}
