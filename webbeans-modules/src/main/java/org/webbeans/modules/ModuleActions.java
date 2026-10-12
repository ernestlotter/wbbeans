package org.webbeans.modules;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.Platform;
import org.webbeans.core.i18n.Messages;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.notifications.NotificationDisplayer;

/** The actions of the Modules window and the module node popup (administrators). */
@Messages({"CTL_ShowModules=Modules...", "CTL_EnableModule=Enable", "CTL_DisableModule=Disable", "CTL_UninstallModule=Uninstall",
    "MSG_Enabled={0} enabled", "MSG_NotEnabled={0} could not be enabled: {1}", "MSG_Disabled={0} disabled ({1} modules)",
    "MSG_ConfirmUninstall=Uninstall {0}? Its jar is deleted from the install directory; modules depending on it are disabled.",
    "MSG_Uninstalled={0} uninstalled", "MSG_Failed=Module operation failed: {0}", "TITLE_Modules=Modules"})
public final class ModuleActions {

    private static final Logger LOG = Logger.getLogger(ModuleActions.class.getName());

    private ModuleActions() {
    }

    static Platform platform() {
        Platform p = Platform.getCurrent();
        if (p == null) {
            throw new IllegalStateException("No running platform");
        }
        return p;
    }

    static void info(String text) {
        NotificationDisplayer.getDefault().notify(Bundle.TITLE_Modules(), text, NotificationDisplayer.Category.INFO);
    }

    static void failed(Exception e) {
        LOG.log(Level.WARNING, "Module operation failed", e);
        NotificationDisplayer.getDefault().notify(Bundle.TITLE_Modules(), Bundle.MSG_Failed(e.getMessage()), NotificationDisplayer.Category.ERROR);
    }

    /** Tools ▸ Modules… opens the window. */
    @ActionID(category = "Modules", id = "org.webbeans.modules.ShowModulesAction")
    @ActionRegistration(displayName = "#CTL_ShowModules", iconBase = "org/webbeans/modules/icons/module.svg", roles = {"admin"})
    @ActionReference(path = "Menu/Tools", position = 1900)
    public static final class ShowModulesAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            ModulesTopComponent.show();
        }
    }

    /** Enables the selected modules (and what they need). */
    @ActionID(category = "Modules", id = "org.webbeans.modules.EnableModuleAction")
    @ActionRegistration(displayName = "#CTL_EnableModule", iconBase = "org/webbeans/modules/icons/enable.svg", roles = {"admin"})
    @ActionReference(path = ModuleNode.ACTIONS_FOLDER, position = 100)
    public static final class EnableModuleAction implements ActionListener {
        private final List<ModuleInfo> modules;

        public EnableModuleAction(List<ModuleInfo> modules) {
            this.modules = modules;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (ModuleInfo m : modules) {
                if (m.isEnabled()) {
                    continue;
                }
                try {
                    ModuleInfo r = platform().enableModule(m.getCodeName());
                    info(r.isEnabled() ? Bundle.MSG_Enabled(r.getCodeName()) : Bundle.MSG_NotEnabled(r.getCodeName(), r.getProblem()));
                } catch (RuntimeException ex) {
                    failed(ex);
                }
            }
        }
    }

    /** Disables the selected modules together with the modules depending on them. */
    @ActionID(category = "Modules", id = "org.webbeans.modules.DisableModuleAction")
    @ActionRegistration(displayName = "#CTL_DisableModule", iconBase = "org/webbeans/modules/icons/disable.svg", roles = {"admin"})
    @ActionReference(path = ModuleNode.ACTIONS_FOLDER, position = 200)
    public static final class DisableModuleAction implements ActionListener {
        private final List<ModuleInfo> modules;

        public DisableModuleAction(List<ModuleInfo> modules) {
            this.modules = modules;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (ModuleInfo m : modules) {
                if (!m.isEnabled()) {
                    continue;
                }
                try {
                    List<ModuleInfo> off = platform().disableModule(m.getCodeName());
                    info(Bundle.MSG_Disabled(m.getCodeName(), off.size()));
                } catch (RuntimeException ex) {
                    failed(ex);
                }
            }
        }
    }

    /** Uninstalls a module installed at run time after a confirmation. */
    @ActionID(category = "Modules", id = "org.webbeans.modules.UninstallModuleAction")
    @ActionRegistration(displayName = "#CTL_UninstallModule", iconBase = "org/webbeans/modules/icons/uninstall.svg", roles = {"admin"})
    @ActionReference(path = ModuleNode.ACTIONS_FOLDER, position = 300, separatorBefore = 250)
    public static final class UninstallModuleAction implements ActionListener {
        private final ModuleInfo module;

        public UninstallModuleAction(ModuleInfo module) {
            this.module = module;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(Bundle.MSG_ConfirmUninstall(module.getCodeName()),
                    NotifyDescriptor.YES_NO_OPTION)).thenAccept(new Consumer<Object>() {
                @Override
                public void accept(Object r) {
                    if (r != NotifyDescriptor.YES_OPTION) {
                        return;
                    }
                    try {
                        platform().uninstallModule(module.getCodeName());
                        info(Bundle.MSG_Uninstalled(module.getCodeName()));
                    } catch (IOException ex) {
                        failed(ex);
                    } catch (RuntimeException ex) {
                        failed(ex);
                    }
                }
            });
        }
    }
}
