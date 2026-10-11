package org.webbeans.windows;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ships a window layout with a module: the JSON written by {@link WindowManager#saveLayout} (or found in the
 * user's {@code layout.json}) as a class-path resource. It is used for users who have no saved layout yet —
 * for the given role (see {@link WindowManager#setRole}; {@code default} is the initial role). Registered under
 * {@code Windows/Layouts/<role>}.
 *
 * <pre>&#64;LayoutRegistration(role = "default", resource = "com/acme/app/layouts/default.json")</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface LayoutRegistration {

    String role() default "default";

    /** Class-path resource with the layout JSON. */
    String resource();

    /** Several registrations on one class. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface Registrations {
        LayoutRegistration[] value();
    }
}
