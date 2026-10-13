package org.webbeans.demo;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.PropertySheet;
import org.webbeans.nodes.Node;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/**
 * The NetBeans "Properties" window: a {@link PropertySheet} showing the activated nodes
 * ({@code WindowManager.getActivatedNodesContext()}), plus a few window actions.
 */
@TopComponent.Registration(mode = "properties", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "PropertiesTC", iconBase = "org/webbeans/demo/icons/props.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenProperties", position = 500)
@Messages({
    "CTL_PropertiesTC=Properties",
    "CTL_OpenProperties=Properties",
    "LBL_Activated=Active window: {0}",
    "LBL_NoneActivated=No window active",
    "BTN_OpenForm=Open Form",
    "BTN_OpenAbout=Open About",
    "BTN_ResetLayout=Reset layout",
    "BTN_Maximize=Toggle maximize"
})
public final class PropertiesTopComponent extends TopComponent {

    private final Label activated = new Label(Bundle.LBL_NoneActivated());
    private final PropertySheet sheet = new PropertySheet();
    private PropertyChangeListener listener;
    private Lookup.Result<Node> nodesResult;
    private LookupListener nodesListener;

    public PropertiesTopComponent() {
        setDisplayName(Bundle.CTL_PropertiesTC());
        setLayout(new Layout.Border());
        activated.setTestId("activated");
        sheet.setTestId("sheet");
        add(activated, Layout.Border.NORTH);
        add(sheet, Layout.Border.CENTER);
        Panel buttons = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 6).wrap(true));
        buttons.setPadding(6);
        buttons.add(button(Bundle.BTN_OpenForm(), "open-form", new Runnable() {
            @Override
            public void run() {
                TopComponent tc = WindowManager.getDefault().findTopComponent("FormTC");
                if (tc != null) {
                    tc.requestActive();
                }
            }
        }));
        buttons.add(button(Bundle.BTN_OpenAbout(), "open-about", new Runnable() {
            @Override
            public void run() {
                TopComponent tc = WindowManager.getDefault().findTopComponent("AboutTC");
                if (tc != null) {
                    tc.requestActive();
                }
            }
        }));
        buttons.add(button(Bundle.BTN_Maximize(), "maximize", new Runnable() {
            @Override
            public void run() {
                WindowManager wm = WindowManager.getDefault();
                TopComponent tc = wm.getActivated();
                if (tc != null) {
                    wm.setMaximized(tc, !wm.isMaximized(tc));
                }
            }
        }));
        buttons.add(button(Bundle.BTN_ResetLayout(), "reset-layout", new Runnable() {
            @Override
            public void run() {
                WindowManager.getDefault().resetLayout();
            }
        }));
        add(buttons, Layout.Border.SOUTH);
    }

    private static Button button(String text, String testId, final Runnable r) {
        Button b = new Button(text);
        b.setTestId(testId);
        b.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                r.run();
            }
        });
        return b;
    }

    @Override
    protected void componentOpened() {
        final WindowManager wm = getWindowManager();
        listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (WindowManager.Registry.PROP_ACTIVATED.equals(e.getPropertyName())) {
                    refreshTitle(wm);
                }
            }
        };
        wm.getRegistry().addPropertyChangeListener(listener);
        // the activated nodes follow the last window that owns nodes (the explorer) and its selection — not this
        // window: clicking or editing in the sheet must not clear it
        nodesResult = wm.getActivatedNodesContext().lookupResult(Node.class);
        final UI ui = getUI();
        nodesListener = new LookupListener() {
            @Override
            public void resultChanged(LookupEvent ev) {
                final Collection<? extends Node> nodes = nodesResult.allInstances();
                ui.access(new Runnable() {
                    @Override
                    public void run() {
                        showNodes(nodes);
                    }
                });
            }
        };
        nodesResult.addLookupListener(nodesListener);
        refreshTitle(wm);
        showNodes(nodesResult.allInstances());
    }

    @Override
    protected void componentClosed() {
        getWindowManager().getRegistry().removePropertyChangeListener(listener);
        if (nodesResult != null) {
            nodesResult.removeLookupListener(nodesListener);
            nodesResult = null;
        }
    }

    private void showNodes(Collection<? extends Node> nodes) {
        List<Node> l = new ArrayList<Node>(nodes);
        sheet.setNodes(l.toArray(new Node[l.size()]));
    }

    private void refreshTitle(WindowManager wm) {
        TopComponent act = wm.getActivated();
        activated.setText(act == null ? Bundle.LBL_NoneActivated() : Bundle.LBL_Activated(act.getDisplayName()));
    }
}
