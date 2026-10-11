package org.webbeans.nodes;

import java.io.IOException;

/** One way of pasting a transferable into a node ("Paste", "Paste as link"...). */
public abstract class PasteType {

    /** Performs the paste; returns what should go to the clipboard afterwards (null = unchanged). */
    public abstract Transferable paste() throws IOException;

    /** Localised name for a Paste submenu; null for the plain "Paste". */
    public String getName() {
        return null;
    }
}
