package org.webbeans.nodes;

import java.util.Set;

/** Data moved by clipboard or drag and drop, keyed by flavor name ({@link Transferables#FLAVOR_NODES} etc.). */
public interface Transferable {

    Set<String> getFlavors();

    boolean isDataFlavorSupported(String flavor);

    /** The data of a flavor as the given type, or null. */
    <T> T getData(String flavor, Class<T> type);
}
