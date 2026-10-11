package org.webbeans.explorer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.webbeans.nodes.Node;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.ComboBox;

/** NetBeans' {@code ChoiceView}: the children of the explored context in a combo box; choosing one selects it. */
public class NodeComboBox extends ComboBox<Node> {

    private ExplorerManager explicitManager;
    private ExplorerManager em;
    private boolean syncing;
    private final PropertyChangeListener emListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String p = evt.getPropertyName();
            if (ExplorerManager.PROP_EXPLORED_CONTEXT.equals(p) || ExplorerManager.PROP_ROOT_CONTEXT.equals(p)) {
                reload();
            } else if (ExplorerManager.PROP_SELECTED_NODES.equals(p) && !syncing) {
                Node[] sel = em.getSelectedNodes();
                setValue(sel.length > 0 ? sel[0] : null);
            }
        }
    };

    public NodeComboBox() {
        setKeyFunction(new Function<Node, String>() {
            @Override
            public String apply(Node n) {
                return String.valueOf(System.identityHashCode(n));
            }
        });
        addChangeListener(new Listener<ChangeEvent<Node>>() {
            @Override
            public void onEvent(ChangeEvent<Node> e) {
                if (em != null && e.getValue() != null) {
                    syncing = true;
                    try {
                        em.setSelectedNodes(new Node[] {e.getValue()});
                    } catch (PropertyVetoException ex) {
                        Node[] sel = em.getSelectedNodes();
                        setValue(sel.length > 0 ? sel[0] : null);
                    } finally {
                        syncing = false;
                    }
                }
            }
        });
    }

    public NodeComboBox setExplorerManager(ExplorerManager m) {
        explicitManager = m;
        if (isAttached()) {
            bind();
        }
        return this;
    }

    @Override
    protected void attached() {
        bind();
    }

    @Override
    protected void detached() {
        if (em != null) {
            em.removePropertyChangeListener(emListener);
            em = null;
        }
    }

    private void bind() {
        if (em != null) {
            em.removePropertyChangeListener(emListener);
        }
        em = explicitManager != null ? explicitManager : ExplorerManager.find(this);
        if (em != null) {
            em.addPropertyChangeListener(emListener);
        }
        reload();
    }

    private void reload() {
        Node ctx = em == null ? null : em.getExploredContext();
        List<Node> items = ctx == null ? java.util.Collections.<Node>emptyList() : Arrays.asList(ctx.getChildren().getNodes());
        setItems(items, new Function<Node, String>() {
            @Override
            public String apply(Node n) {
                return n.getDisplayName();
            }
        }, new Function<Node, Resource>() {
            @Override
            public Resource apply(Node n) {
                return n.getIcon();
            }
        });
        Node[] sel = em == null ? new Node[0] : em.getSelectedNodes();
        setValue(sel.length > 0 && items.contains(sel[0]) ? sel[0] : null);
    }
}
