package org.webbeans.workbench.viewer3d;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;

import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.TreeView;
import org.webbeans.nodes.Node;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/**
 * The Parts window: a {@link TreeView} over the same {@link PartsRootNode} the viewer uses. Its selection goes to
 * the model, so the 3D scene highlights the part; picks in the scene come back the same way.
 */
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 200)
@TopComponent.Description(preferredID = "PartsTC", iconBase = "org/webbeans/workbench/viewer3d/icons/turbine.svg", helpId = "viewer.parts")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenParts", position = 400)
@Messages({"CTL_PartsTC=Parts", "CTL_OpenParts=Parts"})
public final class PartsTopComponent extends TopComponent implements ExplorerManager.Provider, PartsModel.Listener {

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final TreeView tree = new TreeView();
    private PartsModel model;
    private boolean syncing;

    public PartsTopComponent() {
        setDisplayName(Bundle.CTL_PartsTC());
        setLayout(new Layout.Border());
        ExplorerUtils.activateActions(em, map, true);
        associateLookup(ExplorerUtils.createLookup(em, map));
        tree.setRootVisible(true);
        tree.setTestId("parts");
        add(tree, Layout.Border.CENTER);
        em.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName()) && !syncing && model != null) {
                    Node[] sel = em.getSelectedNodes();
                    model.setSelected(sel.length > 0 ? sel[0].getLookup().lookup(Part.class) : null);
                }
            }
        });
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_PartsTC());
    }

    @Override
    protected void componentOpened() {
        model = PartsModel.get();
        if (em.getRootContext() == null) {
            em.setRootContext(new PartsRootNode(model));
        }
        model.addListener(this);
    }

    @Override
    protected void componentClosed() {
        if (model != null) {
            model.removeListener(this);
        }
    }

    @Override
    public void partsChanged() {
    }

    @Override
    public void partChanged(Part p) {
    }

    @Override
    public void selectionChanged(Part selected) {
        Node n = selected == null ? null : PartsRootNode.find(em.getRootContext(), selected);
        syncing = true;
        try {
            em.setSelectedNodes(n == null ? new Node[0] : new Node[] {n});
        } catch (PropertyVetoException e) {
            // keep
        } finally {
            syncing = false;
        }
    }
}
