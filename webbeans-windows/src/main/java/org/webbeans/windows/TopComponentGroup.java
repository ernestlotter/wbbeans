package org.webbeans.windows;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A named set of components opened and closed together (NetBeans' "debugging" group). Declared with
 * {@link Registration} on any class: {@code Windows/Groups/<name>} with the component ids.
 */
public interface TopComponentGroup {

    String getName();

    /** The component ids of the group. */
    List<String> getTopComponentIds();

    /** Opens every member that is not open (members the user closed since the last open are still opened). */
    void open();

    /** Closes every member that this group opened. */
    void close();

    boolean isOpened();

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface Registration {
        String name();

        /** preferredIDs of the members. */
        String[] topComponents();
    }
}
