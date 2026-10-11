package org.webbeans.dialogs;

import java.util.concurrent.CompletableFuture;

import org.webbeans.ui.UI;

/**
 * Shows dialogs in the current UI. Never blocking: {@link #notify} returns a future completed on the session
 * executor with the chosen option, so continuations ({@code future.thenAccept(...)}, not {@code thenAcceptAsync})
 * run where components may be touched.
 *
 * <pre>
 * DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation("Delete it?", "Delete", NotifyDescriptor.YES_NO_OPTION))
 *     .thenAccept(option -&gt; { if (option == NotifyDescriptor.YES_OPTION) { ... } });
 * </pre>
 */
public abstract class DialogDisplayer {

    /** The displayer of the current UI (one per UI, created on first use). */
    public static DialogDisplayer getDefault() {
        UI ui = UI.current();
        if (ui == null) {
            throw new IllegalStateException("DialogDisplayer.getDefault() needs a current UI (use ui.access)");
        }
        return getDefault(ui);
    }

    public static DialogDisplayer getDefault(UI ui) {
        DialogDisplayer d = ui.getLookup().lookup(DialogDisplayer.class);
        if (d == null) {
            d = new DefaultDialogDisplayer(ui);
            ui.addToLookup(d);
        }
        return d;
    }

    /** Shows the dialog; the future completes with the chosen option or {@link NotifyDescriptor#CLOSED_OPTION}. */
    public abstract CompletableFuture<Object> notify(NotifyDescriptor descriptor);

    /** Creates the dialog without showing it. */
    public abstract Dialog createDialog(DialogDescriptor descriptor);
}
