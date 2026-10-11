package org.webbeans.nodes;

import java.io.IOException;

/** Capability in a node's or window's lookup: the object has unsaved changes that {@link #save()} writes. */
public interface Savable {
    void save() throws IOException;
}
