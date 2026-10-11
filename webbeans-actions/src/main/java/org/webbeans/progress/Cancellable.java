package org.webbeans.progress;

/** Something the user may cancel from the progress UI; return false when cancelling was not possible. */
public interface Cancellable {

    boolean cancel();
}
