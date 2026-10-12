package org.webbeans.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.openide.util.Lookup;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionMap;
import org.webbeans.actions.ActionPresenters;
import org.webbeans.actions.Actions;
import org.webbeans.actions.Separator;
import org.webbeans.actions.Toolbar;
import org.webbeans.core.Platform;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.Messages;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.core.modules.ModuleListener;
import org.webbeans.core.modules.ModuleSystem;
import org.webbeans.core.modules.Problem;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.OutlineView;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/**
 * Tools ▸ Modules (administrators only): every module the module system knows, with its version, state and
 * origin; a toolbar to install a jar, enable, disable or uninstall the selected ones. Follows the module system
 * live, so a jar dropped into the install directory shows up by itself. The registry entries carry
 * {@code roles = "admin"}: other users get neither the window nor its menu items.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 900, roles = {"admin"})
@TopComponent.Description(preferredID = "ModulesTC", iconBase = "org/webbeans/modules/icons/module.svg",
    persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED, helpId = "platform")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenModules", position = 960)
@Messages({"CTL_ModulesTC=Modules", "CTL_OpenModules=Modules", "COL_Module=Module", "CTL_Install=Install module...",
    "CTL_Refresh=Refresh", "LBL_ModulesSummary={0} modules, {1} enabled", "LBL_InstallDir=Install directory: {0}"})
public final class ModulesTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final OutlineView view = new OutlineView(Bundle.COL_Module());
    private final Toolbar toolbar = new Toolbar("modules");
    private final Label summary = new Label();
    private final ModulesChildren children = new ModulesChildren();
    private ModuleListener listener;
    private org.webbeans.ui.Registration registryChanges;

    public ModulesTopComponent() {
        setDisplayName(Bundle.CTL_ModulesTC());
        setLayout(new Layout.Border());
        ExplorerUtils.activateActions(em, map, true);
        associateLookup(ExplorerUtils.createLookup(em, map));
        view.setPropertyColumns("version", Bundle.PROP_Version(), "state", Bundle.PROP_State(), "location", Bundle.PROP_Location(),
                "problem", Bundle.PROP_Problem());
        view.setRootVisible(false);
        view.setTestId("modules");
        summary.setTestId("modules-summary");
        summary.setStyleName("wb-status-summary");
        AbstractNode root = new AbstractNode(children);
        root.setName("modules");
        em.setRootContext(root);
        add(toolbar, Layout.Border.NORTH);
        add(view, Layout.Border.CENTER);
        add(summary, Layout.Border.SOUTH);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void attached() {
        super.attached();
        if (toolbar.getChildCount() == 0) {
            buildToolbar();
        }
    }

    private void buildToolbar() {
        Lookup ctx = getLookup();
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Install", "org/webbeans/modules/icons/install.svg", new Runnable() {
            @Override
            public void run() {
                InstallModuleDialog.show();
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Refresh", "org/webbeans/modules/icons/refresh.svg", new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }), ctx));
        boolean first = true;
        for (Action a : Actions.forPath(ModuleNode.ACTIONS_FOLDER)) {
            if (a == null) {
                continue;
            }
            if (first) {
                toolbar.add(new Separator());
                first = false;
            }
            toolbar.add(ActionPresenters.toolbarButton(a, ctx));
        }
    }

    private static Action simple(String key, String icon, final Runnable r) {
        AbstractAction a = new AbstractAction(Bundles.getMessage(ModulesTopComponent.class, key), Resource.of(icon)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, Bundles.getMessage(ModulesTopComponent.class, key));
        return a;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_ModulesTC());
    }

    @Override
    protected void componentOpened() {
        refresh();
        final UISession session = getUI().getSession();
        listener = new ModuleListener.Adapter() {
            @Override
            public void modulesEnabled(List<ModuleInfo> modules) {
                later();
            }

            @Override
            public void modulesDisabled(List<ModuleInfo> modules) {
                later();
            }

            @Override
            public void moduleDisabled(ModuleInfo module, Problem problem) {
                later();
            }

            private void later() {
                session.access(new Runnable() {
                    @Override
                    public void run() {
                        if (isOpened()) {
                            refresh();
                        }
                    }
                });
            }
        };
        ModuleSystem.getDefault().addModuleListener(listener);
        // uninstall and install rebuild the registry without a module event of their own
        registryChanges = session.subscribe(UISession.RegistryChangedEvent.class, new Consumer<UISession.RegistryChangedEvent>() {
            @Override
            public void accept(UISession.RegistryChangedEvent e) {
                if (isOpened()) {
                    refresh();
                }
            }
        });
    }

    @Override
    protected void componentClosed() {
        if (listener != null) {
            ModuleSystem.getDefault().removeModuleListener(listener);
            listener = null;
        }
        if (registryChanges != null) {
            registryChanges.remove();
            registryChanges = null;
        }
    }

    void refresh() {
        List<ModuleInfo> all = new ArrayList<ModuleInfo>(ModuleSystem.getDefault().getModules());
        Collections.sort(all, new Comparator<ModuleInfo>() {
            @Override
            public int compare(ModuleInfo a, ModuleInfo b) {
                int c = a.getCodeName().compareTo(b.getCodeName());
                return c != 0 ? c : b.getVersion().compareTo(a.getVersion());
            }
        });
        int enabled = 0;
        for (ModuleInfo m : all) {
            if (m.isEnabled()) {
                enabled++;
            }
        }
        children.update(all);
        Platform p = Platform.getCurrent();
        summary.setText(Bundle.LBL_ModulesSummary(all.size(), enabled)
                + (p == null ? "" : " — " + Bundle.LBL_InstallDir(p.getInstallDir())));
    }

    static final class ModulesChildren extends Children.Keys<ModuleInfo> {
        void update(List<ModuleInfo> modules) {
            setKeys(modules);
            for (Node n : getNodes()) {
                if (n instanceof ModuleNode) {
                    ((ModuleNode) n).refresh();
                }
            }
        }

        @Override
        protected Node[] createNodes(ModuleInfo key) {
            return new Node[] {new ModuleNode(key)};
        }
    }

    /** Opens the window (Tools ▸ Modules). */
    static ModulesTopComponent show() {
        org.webbeans.windows.WindowManager wm = org.webbeans.windows.WindowManager.getDefault();
        TopComponent tc = wm.findTopComponent("ModulesTC");
        if (tc instanceof ModulesTopComponent) {
            tc.open();
            tc.requestActive();
            return (ModulesTopComponent) tc;
        }
        return null;
    }
}
