package org.webbeans.actions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Places a registered action in a menu ({@code Menu/<path>}), a toolbar ({@code Toolbars/<name>}), the shortcut
 * map ({@code path = "Shortcuts", name = "D-S"}) or a popup folder that nodes read with
 * {@link Actions#forPath} (e.g. {@code Planets/Actions}).
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ActionReferences.class)
public @interface ActionReference {

    String path();

    int position() default Integer.MAX_VALUE;

    /** Position of a separator before the entry; -1 = none. */
    int separatorBefore() default -1;

    /** Position of a separator after the entry; -1 = none. */
    int separatorAfter() default -1;

    /**
     * Entry name. For {@code Shortcuts} the key in NetBeans syntax ({@code D-S} = Ctrl/Cmd+S, {@code O-F4} = Alt+F4,
     * {@code DS-Z} = Ctrl+Shift+Z, {@code C-} = Ctrl always); otherwise defaults to the action's entry name.
     */
    String name() default "";
}
