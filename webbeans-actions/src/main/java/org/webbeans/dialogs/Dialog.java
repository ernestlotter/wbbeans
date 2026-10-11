package org.webbeans.dialogs;

import java.util.concurrent.CompletableFuture;

/** A created dialog ({@link DialogDisplayer#createDialog}): show, hide and dispose it; the future gives the final choice. */
public interface Dialog {

    void show();

    void hide();

    /** Hides and completes the result with the descriptor's current value. */
    void dispose();

    void setTitle(String title);

    String getTitle();

    boolean isVisible();

    /** Completed, on the session executor, with the chosen option when the dialog closes. */
    CompletableFuture<Object> getResult();
}
