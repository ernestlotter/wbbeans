package org.webbeans.actions;

import org.webbeans.core.registry.Entry;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/** The action behind {@code @TopComponent.OpenActionRegistration}: finds or creates the window and activates it. */
final class OpenTopComponentAction extends RegistryAction {

    private final String topComponentId;

    OpenTopComponentAction(Entry entry) {
        super(entry);
        this.topComponentId = entry.getString("topComponent");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        WindowManager wm = e.getUI() != null ? e.getUI().getLookup().lookup(WindowManager.class) : WindowManager.getDefault();
        if (wm == null) {
            return;
        }
        TopComponent tc = wm.findTopComponent(topComponentId);
        if (tc != null) {
            tc.requestActive();
        }
    }
}
