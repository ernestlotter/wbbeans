package org.webbeans.workbench.viewer3d;

import java.util.function.BiConsumer;

import org.openide.util.Lookup;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.actions.Actions;
import org.webbeans.actions.MenuItem;
import org.webbeans.actions.Presenter;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.options.WbPreferences;
import org.webbeans.scene3d.Scene3D;
import org.webbeans.scene3d.Scene3DTopComponent;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;
import org.webbeans.workbench.core.api.Log;

/**
 * Viewer actions: Snapshot (context over {@link Scene3D}, also in the viewport toolbar), Blades +/- (change the
 * model, the scene follows), and View ▸ Spin rotor (a checkbox backed by a user preference).
 */
@Messages({"CTL_Snapshot=Snapshot (1280×720)", "MSG_Snapshot=Snapshot: {0} bytes", "MSG_SnapshotFailed=Snapshot failed: {0}",
    "CTL_MoreBlades=More blades", "CTL_FewerBlades=Fewer blades", "CTL_Spin=Spin rotor", "MSG_Blades={0} blades", "CTL_ViewerToolbar=Viewer"})
@org.webbeans.core.registry.FolderRegistration(path = "Toolbars/Viewer", displayName = "#CTL_ViewerToolbar", position = 400)
public final class ViewerActions {

    private ViewerActions() {
    }

    @ActionID(category = "Viewer", id = "org.webbeans.workbench.viewer3d.SnapshotAction")
    @ActionRegistration(displayName = "#CTL_Snapshot", iconBase = "org/webbeans/workbench/viewer3d/icons/snapshot.svg")
    @ActionReferences({
        @ActionReference(path = Scene3DTopComponent.ACTIONS_FOLDER, position = 100),
        @ActionReference(path = "Menu/Tools", position = 300)})
    public static final class SnapshotAction implements ActionListener {
        private final Scene3D scene;

        public SnapshotAction(Scene3D scene) {
            this.scene = scene;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final UI ui = e.getUI();
            scene.snapshot(1280, 720).whenComplete(new BiConsumer<byte[], Throwable>() {
                @Override
                public void accept(byte[] png, Throwable error) {
                    String msg = error == null ? Bundle.MSG_Snapshot(png.length) : Bundle.MSG_SnapshotFailed(String.valueOf(error.getMessage()));
                    NotificationDisplayer.getDefault(ui.getSession()).notify(Bundle.CTL_Snapshot(), msg,
                            error == null ? NotificationDisplayer.Category.INFO : NotificationDisplayer.Category.ERROR);
                    Log.info("viewer", msg);
                }
            });
        }
    }

    @ActionID(category = "Viewer", id = "org.webbeans.workbench.viewer3d.MoreBladesAction")
    @ActionRegistration(displayName = "#CTL_MoreBlades", iconBase = "org/webbeans/workbench/viewer3d/icons/plus.svg")
    @ActionReferences({@ActionReference(path = "Toolbars/Viewer", position = 100), @ActionReference(path = "Menu/Tools", position = 310)})
    public static final class MoreBladesAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            PartsModel m = PartsModel.get();
            m.setBladeCount(m.getBladeCount() + 1);
            Log.info("viewer", Bundle.MSG_Blades(m.getBladeCount()));
        }
    }

    @ActionID(category = "Viewer", id = "org.webbeans.workbench.viewer3d.FewerBladesAction")
    @ActionRegistration(displayName = "#CTL_FewerBlades", iconBase = "org/webbeans/workbench/viewer3d/icons/minus.svg")
    @ActionReferences({@ActionReference(path = "Toolbars/Viewer", position = 110), @ActionReference(path = "Menu/Tools", position = 320, separatorAfter = 350)})
    public static final class FewerBladesAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            PartsModel m = PartsModel.get();
            m.setBladeCount(m.getBladeCount() - 1);
            Log.info("viewer", Bundle.MSG_Blades(m.getBladeCount()));
        }
    }

    /** View ▸ Spin rotor: a check item over a user preference; applied to the session's model when toggled and at login. */
    @ActionID(category = "View", id = "org.webbeans.workbench.viewer3d.SpinAction")
    @ActionRegistration(displayName = "#CTL_Spin", lazy = false)
    @ActionReference(path = "Menu/View", position = 100)
    public static final class SpinAction extends AbstractAction implements Presenter.Menu {
        public static final String PREF_KEY = "spin";

        public SpinAction() {
            super(Bundles.getMessage(ViewerActions.class, "CTL_Spin"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        }

        @Override
        public Component getMenuPresenter() {
            org.webbeans.actions.Action checkbox = Actions.checkbox(ViewerActions.class, PREF_KEY, true, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    PartsModel.get().setSpinning(WbPreferences.forModule(ViewerActions.class).getBoolean(PREF_KEY, true));
                }
            }, Bundles.getMessage(ViewerActions.class, "CTL_Spin"));
            MenuItem item = new MenuItem().setAction(checkbox, Lookup.EMPTY, false);
            item.setTestId("menu-spin");
            return item;
        }
    }
}
