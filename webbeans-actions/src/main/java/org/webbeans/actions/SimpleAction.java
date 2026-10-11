package org.webbeans.actions;

import org.webbeans.core.registry.Entry;

/** An always-enabled action delegating to an {@link ActionListener} (created lazily when registered). */
final class SimpleAction extends RegistryAction {

    private final java.util.function.Supplier<ActionListener> delegate;
    private ActionListener instance;

    SimpleAction(java.util.function.Supplier<ActionListener> delegate, Entry entry) {
        super(entry);
        this.delegate = delegate;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        if (!isEnabled()) {
            return;
        }
        run(new Runnable() {
            @Override
            public void run() {
                if (instance == null) {
                    instance = delegate.get();
                }
                instance.actionPerformed(e);
            }
        }, e);
    }
}
