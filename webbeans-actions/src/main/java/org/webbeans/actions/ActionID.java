package org.webbeans.actions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies an action: the registry entry is {@code Actions/<category>/<id with dots as dashes>} and
 * {@link Actions#forID} finds it. Always paired with {@link ActionRegistration}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ActionID {

    /** Grouping folder, e.g. {@code File}, {@code Edit}, {@code Planets}. */
    String category();

    /** Unique id, by convention the fully qualified class name. */
    String id();
}
