package org.webbeans.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.RequestProcessor;
import org.webbeans.ui.UISession;

/** {@link Children.Keys} backed by a {@link ChildFactory}; asynchronous key computation on a shared request processor. */
final class FactoryChildren<T> extends Children.Keys<T> {

    private static final Logger LOG = Logger.getLogger(FactoryChildren.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("Node Children", 4, true);

    private final ChildFactory<T> factory;
    private final boolean asynchronous;
    private Node waitNode;
    private boolean waiting;
    private int generation;

    FactoryChildren(ChildFactory<T> factory, boolean asynchronous) {
        this.factory = factory;
        this.asynchronous = asynchronous;
        factory.bind(this);
    }

    @Override
    protected Node[] createNodes(T key) {
        return factory.createNodesForKey(key);
    }

    @Override
    protected void addNotify() {
        refresh(!asynchronous);
    }

    @Override
    public Node[] getNodes(boolean optimalResult) {
        if (optimalResult && asynchronous) {
            refresh(true);
        }
        return getNodes();
    }

    void refresh(boolean immediate) {
        if (immediate || !asynchronous) {
            List<T> keys = computeAll();
            setKeysInternal(keys);
            return;
        }
        final int gen = ++generation;
        final UISession session = UISession.current();
        if (!waiting && getNode() != null) {
            waiting = true;
            waitNode = factory.createWaitNode();
            if (waitNode != null && getNodesCountRaw() == 0) {
                showWait();
            }
        }
        RP.post(new Runnable() {
            @Override
            public void run() {
                final List<T> keys = computeAll();
                Runnable apply = new Runnable() {
                    @Override
                    public void run() {
                        if (gen == generation) {
                            waiting = false;
                            setKeysInternal(keys);
                        }
                    }
                };
                if (session != null) {
                    session.access(apply);
                } else {
                    apply.run();
                }
            }
        });
    }

    private int getNodesCountRaw() {
        return nodes().size();
    }

    private final List<Node> waitList = new ArrayList<Node>();

    private void showWait() {
        // the wait node is shown as a temporary child until the first batch arrives
        if (waitNode != null && waitList.isEmpty()) {
            waitList.add(waitNode);
            nodes().add(waitNode);
            nodesAdded(Collections.singletonList(waitNode), new int[] {0});
        }
    }

    private void setKeysInternal(List<T> keys) {
        if (!waitList.isEmpty()) {
            Node w = waitList.remove(0);
            int i = nodes().indexOf(w);
            if (i >= 0) {
                nodes().remove(i);
                nodesRemoved(Collections.singletonList(w), new int[] {i});
            }
        }
        setKeys(keys);
    }

    private List<T> computeAll() {
        List<T> keys = new ArrayList<T>();
        try {
            int guard = 0;
            while (!factory.createKeys(keys) && guard++ < 10000) {
                // batching: keep calling until done
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "createKeys failed for " + getNode(), e);
        }
        return keys;
    }
}
