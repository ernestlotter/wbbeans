package org.webbeans.modules;

import java.io.IOException;
import java.io.InputStream;

import org.webbeans.core.i18n.Messages;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.dialogs.Dialog;
import org.webbeans.dialogs.DialogDescriptor;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.ui.Listener;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.Upload;

/** "Install module…": an upload field for a module jar; the jar is installed as soon as it has arrived. */
@Messages({"TITLE_Install=Install module", "LBL_InstallHint=Choose or drop a module jar (a jar with a WebBeans-Module manifest). "
    + "A module with the same code name is replaced; modules depending on it are re-enabled on the new version.",
    "LBL_UploadJar=Choose jar...", "MSG_Installed=Installed {0} {1}", "MSG_InstalledProblem=Installed {0} {1}, but it could not be enabled: {2}",
    "MSG_InstallFailed=Not installed: {0}", "CTL_Close=Close"})
final class InstallModuleDialog {

    private InstallModuleDialog() {
    }

    static void show() {
        Panel content = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 8));
        content.add(new Label(Bundle.LBL_InstallHint()));
        final Upload upload = new Upload(Bundle.LBL_UploadJar()).setAccept(".jar").setMultiple(false).setShowList(true);
        upload.setTestId("install-upload");
        content.add(upload);
        final Label result = new Label();
        result.setTestId("install-result");
        content.add(result);
        content.setTestId("install-dialog");
        final DialogDescriptor d = new DialogDescriptor(content, Bundle.TITLE_Install(), true, new Object[] {Bundle.CTL_Close()},
                Bundle.CTL_Close(), DialogDescriptor.BOTTOM_ALIGN, null);
        d.setMessageType(NotifyDescriptor.PLAIN_MESSAGE);
        final Dialog dialog = DialogDisplayer.getDefault().createDialog(d);
        upload.addUploadListener(new Listener<Upload.UploadEvent>() {
            @Override
            public void onEvent(Upload.UploadEvent e) {
                try {
                    InputStream in = e.getFile().open();
                    try {
                        ModuleInfo m = ModuleActions.platform().installModule(in, e.getFile().getName());
                        String text = m.isEnabled() ? Bundle.MSG_Installed(m.getCodeName(), m.getVersion())
                                : Bundle.MSG_InstalledProblem(m.getCodeName(), m.getVersion(), m.getProblem());
                        result.setText(text);
                        ModuleActions.info(text);
                    } finally {
                        in.close();
                    }
                } catch (IOException ex) {
                    result.setText(Bundle.MSG_InstallFailed(ex.getMessage()));
                } catch (RuntimeException ex) {
                    result.setText(Bundle.MSG_InstallFailed(ex.getMessage()));
                }
            }
        });
        dialog.show();
    }
}
