package org.webbeans.explorer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.nodes.Clipboard;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.PasteType;
import org.webbeans.nodes.Transferable;
import org.webbeans.nodes.Transferables;
import org.webbeans.ui.UI;

/** Helpers that connect an {@link ExplorerManager} to the window's lookup and to the standard edit actions. */
public final class ExplorerUtils {

    private static final Logger LOG = Logger.getLogger(ExplorerUtils.class.getName());

    private ExplorerUtils() {
    }

    private static final class SelectionLookup extends ProxyLookup implements PropertyChangeListener {
        private final ExplorerManager em;
        private final Lookup fixed;

        SelectionLookup(ExplorerManager em, ActionMap map) {
            this.em = em;
            this.fixed = map == null ? Lookup.EMPTY : Lookups.singleton(map);
            em.addPropertyChangeListener(this);
            update();
        }

        private void update() {
            List<Lookup> l = new ArrayList<Lookup>();
            Node[] sel = em.getSelectedNodes();
            l.add(Lookups.fixed((Object[]) sel));
            for (Node n : sel) {
                l.add(n.getLookup());
            }
            l.add(fixed);
            setLookups(l.toArray(new Lookup[l.size()]));
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName())) {
                update();
            }
        }
    }

    /**
     * A lookup with the selected nodes, the contents of their lookups and the action map; associate it with the
     * window so the activated window's selection becomes the global context.
     */
    public static Lookup createLookup(ExplorerManager em, ActionMap map) {
        return new SelectionLookup(em, map);
    }

    /** Copies the selection to the session clipboard. */
    public static Action actionCopy(final ExplorerManager em) {
        return new AbstractAction(msg("CTL_Copy")) {
            @Override
            public boolean isEnabled() {
                Node[] sel = em.getSelectedNodes();
                if (sel.length == 0) {
                    return false;
                }
                for (Node n : sel) {
                    if (!n.canCopy()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                Clipboard.getDefault().setContents(transferable(em.getSelectedNodes(), false), false);
            }
        };
    }

    public static Action actionCut(final ExplorerManager em) {
        return new AbstractAction(msg("CTL_Cut")) {
            @Override
            public boolean isEnabled() {
                Node[] sel = em.getSelectedNodes();
                if (sel.length == 0) {
                    return false;
                }
                for (Node n : sel) {
                    if (!n.canCut()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                Clipboard.getDefault().setContents(transferable(em.getSelectedNodes(), true), true);
            }
        };
    }

    /** Pastes the clipboard into the single selected node (or the explored context). */
    public static Action actionPaste(final ExplorerManager em) {
        return new AbstractAction(msg("CTL_Paste")) {
            @Override
            public boolean isEnabled() {
                return pasteType(em) != null;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                PasteType pt = pasteType(em);
                if (pt != null) {
                    try {
                        Transferable after = pt.paste();
                        if (after != null) {
                            Clipboard.getDefault().setContents(after, false);
                        } else if (Clipboard.getDefault().isCut()) {
                            Clipboard.getDefault().setContents(null, false);
                        }
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, "Paste failed", ex);
                    }
                }
            }
        };
    }

    private static PasteType pasteType(ExplorerManager em) {
        Transferable t = Clipboard.getDefault().getContents();
        if (t == null) {
            return null;
        }
        Node[] sel = em.getSelectedNodes();
        Node target = sel.length == 1 ? sel[0] : em.getExploredContext();
        if (target == null) {
            return null;
        }
        PasteType[] types = target.getPasteTypes(t);
        return types.length == 0 ? null : types[0];
    }

    /** Destroys the selected nodes, after a confirmation dialog when {@code confirm} is set. */
    public static Action actionDelete(final ExplorerManager em, final boolean confirm) {
        return new AbstractAction(msg("CTL_Delete")) {
            @Override
            public boolean isEnabled() {
                Node[] sel = em.getSelectedNodes();
                if (sel.length == 0) {
                    return false;
                }
                for (Node n : sel) {
                    if (!n.canDestroy()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                deleteNodes(Arrays.asList(em.getSelectedNodes()), confirm);
            }
        };
    }

    /**
     * Destroys the nodes that allow it; with {@code confirm} a Yes/No dialog ({@code MSG_ConfirmDelete}) is
     * shown first in the current UI. Returns a future completed with true when the nodes were destroyed.
     */
    public static CompletableFuture<Boolean> deleteNodes(List<Node> nodes, boolean confirm) {
        final List<Node> targets = new ArrayList<Node>();
        for (Node n : nodes) {
            if (n.canDestroy()) {
                targets.add(n);
            }
        }
        final CompletableFuture<Boolean> done = new CompletableFuture<Boolean>();
        if (targets.isEmpty()) {
            done.complete(false);
            return done;
        }
        if (!confirm || UI.current() == null) {
            destroyAll(targets);
            done.complete(true);
            return done;
        }
        StringBuilder names = new StringBuilder();
        for (Node n : targets) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(n.getDisplayName());
        }
        NotifyDescriptor.Confirmation c = new NotifyDescriptor.Confirmation(
                Bundles.getMessage(Node.class, "MSG_ConfirmDelete", names.toString()), msg("CTL_Delete"), NotifyDescriptor.YES_NO_OPTION);
        DialogDisplayer.getDefault().notify(c).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object option) {
                if (option == NotifyDescriptor.YES_OPTION) {
                    destroyAll(targets);
                    done.complete(true);
                } else {
                    done.complete(false);
                }
            }
        });
        return done;
    }

    private static void destroyAll(List<Node> nodes) {
        for (Node n : nodes) {
            try {
                n.destroy();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Cannot delete " + n, ex);
            }
        }
    }


    /**
     * Puts the standard copy/cut/paste/delete actions of the manager into the map (or removes them), so the
     * global Edit menu and shortcuts ({@code Actions/Edit/*} callback actions) reach this explorer while its
     * window is active. Use the map in {@link #createLookup}.
     */
    public static void activateActions(ExplorerManager em, ActionMap map, boolean activate) {
        map.put(ActionMap.COPY, activate ? actionCopy(em) : null);
        map.put(ActionMap.CUT, activate ? actionCut(em) : null);
        map.put(ActionMap.PASTE, activate ? actionPaste(em) : null);
        map.put(ActionMap.DELETE, activate ? actionDelete(em, true) : null);
    }

    /** NetBeans signature; without a map there is nothing to wire (actions read the manager live). */
    public static void activateActions(ExplorerManager em, boolean activate) {
    }

    private static Transferable transferable(Node[] nodes, boolean cut) {
        List<Transferable> parts = new ArrayList<Transferable>();
        for (Node n : nodes) {
            try {
                parts.add(cut ? n.clipboardCut() : n.clipboardCopy());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Cannot copy " + n, e);
            }
        }
        return parts.size() == 1 ? parts.get(0) : Transferables.multi(parts.toArray(new Transferable[parts.size()]));
    }

    static String msg(String key) {
        return Bundles.getMessage(Node.class, key);
    }
}
