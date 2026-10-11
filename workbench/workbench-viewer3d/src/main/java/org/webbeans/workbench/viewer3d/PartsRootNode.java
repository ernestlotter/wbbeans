package org.webbeans.workbench.viewer3d;

import java.util.ArrayList;
import java.util.List;

import org.webbeans.core.i18n.Messages;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;

/** The assembly as the root of the Parts tree; children follow the model (keys are the parts by identity). */
@Messages({"CTL_Assembly=Turbine"})
public final class PartsRootNode extends AbstractNode {

    public PartsRootNode(PartsModel model) {
        super(new PartChildren(model));
        setName("turbine");
        setDisplayName(Bundle.CTL_Assembly());
        setIconBase("org/webbeans/workbench/viewer3d/icons/turbine.svg");
    }

    /** The node of a part anywhere under a root, or null. */
    public static PartNode find(Node root, Part p) {
        for (Node n : root.getChildren().getNodes()) {
            if (n instanceof PartNode && ((PartNode) n).getPart() == p) {
                return (PartNode) n;
            }
        }
        return null;
    }

    static final class PartChildren extends Children.Keys<Part> implements PartsModel.Listener {
        private final PartsModel model;

        PartChildren(PartsModel model) {
            this.model = model;
        }

        @Override
        protected void addNotify() {
            model.addListener(this);
            setKeys(model.getParts());
        }

        @Override
        protected void removeNotify() {
            model.removeListener(this);
            super.removeNotify();
        }

        @Override
        protected Node[] createNodes(Part key) {
            return new Node[] {new PartNode(key)};
        }

        @Override
        public void partsChanged() {
            setKeys(new ArrayList<Part>(model.getParts()));
        }

        @Override
        public void partChanged(Part p) {
            for (Node n : getNodes()) {
                if (n instanceof PartNode && ((PartNode) n).getPart() == p) {
                    ((PartNode) n).changed();
                }
            }
        }

        @Override
        public void selectionChanged(Part selected) {
        }
    }
}
