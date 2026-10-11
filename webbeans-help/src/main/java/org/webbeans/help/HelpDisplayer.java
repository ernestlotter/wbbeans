package org.webbeans.help;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.ui.HelpCtx;
import org.webbeans.ui.UI;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/**
 * The {@link HelpCtx.Displayer} service: opens the Help window of the current UI on the topic. Call on the
 * session thread (actions, dialog buttons and listeners already are).
 */
@ServiceProvider(service = HelpCtx.Displayer.class)
public final class HelpDisplayer implements HelpCtx.Displayer {

    public static final String TC_ID = "HelpTC";

    @Override
    public boolean display(HelpCtx ctx) {
        HelpTopComponent help = helpWindow(UI.current());
        return help != null && help.show(ctx);
    }

    /** The Help window of a UI, opened and brought to the front; null without a window manager. */
    static HelpTopComponent helpWindow(UI ui) {
        if (ui == null) {
            return null;
        }
        WindowManager wm = ui.getLookup().lookup(WindowManager.class);
        if (wm == null) {
            return null;
        }
        TopComponent tc = wm.findTopComponent(TC_ID);
        if (!(tc instanceof HelpTopComponent)) {
            return null;
        }
        if (!tc.isOpened()) {
            tc.open();
        }
        tc.requestVisible();
        return (HelpTopComponent) tc;
    }
}
