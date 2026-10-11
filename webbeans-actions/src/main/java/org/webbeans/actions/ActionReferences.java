package org.webbeans.actions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container of several {@link ActionReference}s. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ActionReferences {

    ActionReference[] value();
}
