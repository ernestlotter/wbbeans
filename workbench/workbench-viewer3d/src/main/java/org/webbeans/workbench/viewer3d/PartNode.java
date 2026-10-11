package org.webbeans.workbench.viewer3d;

import java.util.ArrayList;
import java.util.List;

import org.openide.util.lookup.Lookups;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.core.i18n.Messages;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;

/** A part as a node: name (rename), kind, colour (editable hex), position and the live temperature of sensors. */
@Messages({"PROP_Name=Name", "HINT_Name=Part name", "PROP_Kind=Kind", "HINT_Kind=What the part is", "PROP_Color=Colour", "HINT_Color=Hex colour, e.g. #d9822b",
    "PROP_Position=Position", "HINT_Position=x, y, z in the assembly", "PROP_Temperature=Temperature", "HINT_Temperature=Live reading in °C",
    "PROP_Status=Status", "HINT_Status=OK below 85 °C", "VAL_Ok=OK", "VAL_Hot=HOT", "ERR_Color=Use #rrggbb"})
public final class PartNode extends AbstractNode {

    /** Registry folder of the popup actions. */
    public static final String ACTIONS_FOLDER = "Parts/Actions";

    private final Part part;

    public PartNode(Part part) {
        super(Children.LEAF, Lookups.singleton(part));
        this.part = part;
        setName(part.getName());
        setIconBase("org/webbeans/workbench/viewer3d/icons/" + part.getKind().name().toLowerCase() + ".svg");
        setShortDescription(part.getKind().name());
        setCanRename(true);
    }

    public Part getPart() {
        return part;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> all = new ArrayList<Action>(Actions.forPath(ACTIONS_FOLDER));
        return all.toArray(new Action[all.size()]);
    }

    @Override
    public void setName(String n) {
        if (!part.getName().equals(n)) {
            PartsModel.get().rename(part, n);
        }
        super.setName(n);
    }

    /** The model changed the part: refresh name and properties. */
    void changed() {
        if (!part.getName().equals(getName())) {
            super.setName(part.getName());
        }
        firePropertyChange("temperature", null, null);
        firePropertyChange("status", null, null);
        firePropertyChange("color", null, null);
    }

    @Override
    public String getHtmlDisplayName() {
        return part.getKind() == Part.Kind.SENSOR && !part.isOk() ? "<font color='#b43b3b'>" + part.getName() + "</font>" : null;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.Name(this, Bundle.PROP_Name(), Bundle.HINT_Name()));
        set.put(new PropertySupport.ReadOnly<Part.Kind>("kind", Part.Kind.class, Bundle.PROP_Kind(), Bundle.HINT_Kind()) {
            @Override
            public Part.Kind getValue() {
                return part.getKind();
            }
        });
        set.put(new PropertySupport.ReadWrite<String>("color", String.class, Bundle.PROP_Color(), Bundle.HINT_Color()) {
            @Override
            public String getValue() {
                return String.format("#%06x", part.getColor());
            }

            @Override
            public void setValue(String v) {
                if (v == null || !v.matches("#[0-9a-fA-F]{6}")) {
                    throw new IllegalArgumentException(Bundle.ERR_Color());
                }
                PartsModel.get().recolor(part, Integer.parseInt(v.substring(1), 16));
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("position", String.class, Bundle.PROP_Position(), Bundle.HINT_Position()) {
            @Override
            public String getValue() {
                return part.getPosition().x + ", " + part.getPosition().y + ", " + part.getPosition().z;
            }
        });
        if (part.getKind() == Part.Kind.SENSOR) {
            set.put(new PropertySupport.ReadOnly<Double>("temperature", Double.class, Bundle.PROP_Temperature(), Bundle.HINT_Temperature()) {
                @Override
                public Double getValue() {
                    return part.getTemperature();
                }
            });
            set.put(new PropertySupport.ReadOnly<String>("status", String.class, Bundle.PROP_Status(), Bundle.HINT_Status()) {
                @Override
                public String getValue() {
                    return part.isOk() ? Bundle.VAL_Ok() : Bundle.VAL_Hot();
                }
            });
        }
        sheet.put(set);
        return sheet;
    }
}
