package org.webbeans.core.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a class (public no-arg constructor) or a public static method (no parameters, or one {@link Entry}
 * parameter) as an instance entry in the {@link Registry}.
 *
 * <pre>
 * &#64;Registration(path = "Services/Session", type = CartService.class, position = 100)
 * public final class DefaultCartService implements CartService { ... }
 *
 * &#64;Registration(path = "Themes", name = "solarized", attrs = {"displayName=#CTL_Solarized", "css=org/example/solarized.css"})
 * public static Theme solarized() { ... }
 * </pre>
 *
 * Platform APIs ship their own annotations ({@code @ActionRegistration}, {@code @TopComponent.Registration}, ...)
 * that the processor compiles to the same kind of entries.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Registrations.class)
public @interface Registration {

    /** Folder path, e.g. {@code Services/Session}. */
    String path();

    /** Entry name; default: the fully qualified class name with dots replaced by dashes (plus {@code -method} for methods). */
    String name() default "";

    int position() default Integer.MAX_VALUE;

    /** The type callers request ({@link Entry#getInstanceOf()}); default: the annotated class / method return type. */
    Class<?> type() default Object.class;

    /**
     * Attributes as {@code key=value}. Values {@code true}/{@code false} become booleans, integers become
     * {@code Integer}, {@code #KEY} strings are resolved against this package's {@code Bundle.properties}.
     */
    String[] attrs() default {};

    /** Roles that may see the entry; empty = everyone. */
    String[] roles() default {};

    /** Additional reference entries pointing at this instance (menu items, toolbar buttons, ...). */
    Reference[] references() default {};

    /** A reference entry placed in another folder. */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface Reference {
        /** Folder path of the reference, e.g. {@code Menu/File}. */
        String path();

        int position() default Integer.MAX_VALUE;

        /** Position of a separator placed before the reference; -1 = none. */
        int separatorBefore() default -1;

        /** Position of a separator placed after the reference; -1 = none. */
        int separatorAfter() default -1;
    }
}
