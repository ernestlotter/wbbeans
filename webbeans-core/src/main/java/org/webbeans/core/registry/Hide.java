package org.webbeans.core.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Masks registry entries contributed by other modules (the NetBeans {@code _hidden} file):
 * <pre>&#64;Hide({"Menu/Tools/org-webbeans-ide-OldAction", "Toolbars/Debug"})</pre>
 * May be placed on any class or on {@code package-info.java}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface Hide {
    /** Entry or folder paths to hide. */
    String[] value();
}
