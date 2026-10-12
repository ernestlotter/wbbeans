package org.webbeans.modules;

import java.util.List;

import org.openide.util.lookup.Lookups;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.core.i18n.Messages;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;

/** One module known to the module system: code name, version, state and origin (read-only properties). */
@Messages({"PROP_CodeName=Code name", "PROP_Version=Version", "PROP_State=State", "PROP_Location=Location", "PROP_Problem=Problem",
    "STATE_Enabled=Enabled", "STATE_Disabled=Disabled", "STATE_Problem=Not enabled", "LOC_ClassPath=class path"})
public final class ModuleNode extends AbstractNode {

    public static final String ACTIONS_FOLDER = "Modules/Actions";

    private final ModuleInfo module;

    public ModuleNode(ModuleInfo module) {
        super(Children.LEAF, Lookups.singleton(module));
        this.module = module;
        setName(module.getCodeName());
        setDisplayName(module.getDisplayName());
        setIconBase(module.isEnabled() ? "org/webbeans/modules/icons/module.svg" : "org/webbeans/modules/icons/disable.svg");
        setShortDescription(module.getCodeName() + " " + module.getVersion());
    }

    public ModuleInfo getModule() {
        return module;
    }

    /** Re-reads the state (after an enable/disable). */
    void refresh() {
        setDisplayName(module.getDisplayName());
        setIconBase(module.isEnabled() ? "org/webbeans/modules/icons/module.svg" : "org/webbeans/modules/icons/disable.svg");
        firePropertyChange("state", null, null);
        firePropertyChange("problem", null, null);
    }

    static String state(ModuleInfo m) {
        if (m.isEnabled()) {
            return Bundle.STATE_Enabled();
        }
        return m.getProblem() != null ? Bundle.STATE_Problem() : Bundle.STATE_Disabled();
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> l = Actions.forPath(ACTIONS_FOLDER);
        return l.toArray(new Action[l.size()]);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.ReadOnly<String>("codeName", String.class, Bundle.PROP_CodeName(), null) {
            @Override
            public String getValue() {
                return module.getCodeName();
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("version", String.class, Bundle.PROP_Version(), null) {
            @Override
            public String getValue() {
                return module.getVersion().toString();
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("state", String.class, Bundle.PROP_State(), null) {
            @Override
            public String getValue() {
                return state(module);
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("location", String.class, Bundle.PROP_Location(), null) {
            @Override
            public String getValue() {
                return module.getLocation() == null ? Bundle.LOC_ClassPath() : module.getLocation().getPath();
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("problem", String.class, Bundle.PROP_Problem(), null) {
            @Override
            public String getValue() {
                return module.getProblem() == null ? "" : module.getProblem().toString();
            }
        });
        sheet.put(set);
        return sheet;
    }
}
