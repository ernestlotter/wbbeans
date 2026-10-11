package org.webbeans.core.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a registry folder with attributes: what menus ({@code Menu/File}), toolbars ({@code Toolbars/Main})
 * and other named groups need beyond the entries placed in them. May be placed on any class or on
 * {@code package-info.java}.
 *
 * <pre>
 * &#64;FolderRegistration(path = "Menu/Planets", displayName = "#CTL_PlanetsMenu", position = 250)
 * </pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(FolderRegistrations.class)
public @interface FolderRegistration {

    String path();

    /** {@code #KEY} into the package's bundle, or a literal. Empty = the folder name. */
    String displayName() default "";

    /** Order among sibling folders. */
    int position() default Integer.MAX_VALUE;

    /** Further attributes as {@code key=value}, see {@link Registration#attrs()}. */
    String[] attrs() default {};
}
