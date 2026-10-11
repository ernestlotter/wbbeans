package org.webbeans.help;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.TreeView;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Node;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.HelpCtx;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Link;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.TextField;
import org.webbeans.windows.Mode;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * The Help window (its own {@code help} mode on the right): a bar with Contents, Back, a search field and
 * "open in new tab"; below it either the table of contents (a {@link TreeView} over {@link HelpNode}s, or the
 * search results) or the current page (breadcrumbs + {@link HelpViewer}). Opened by {@link HelpDisplayer} for
 * F1, Help ▸ Help Contents and the Help buttons of dialogs.
 */
@Mode.Registration(name = "help", side = Mode.Side.RIGHT, defaultSize = 420, position = 300, displayName = "#CTL_Mode_help")
@TopComponent.Registration(mode = "help", openAtStartup = false, position = 100)
@TopComponent.Description(preferredID = "HelpTC", iconBase = "org/webbeans/help/help.svg", persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        helpId = "platform")
@Messages({"CTL_HelpModule=WebBeans Help", "CTL_Mode_help=Help", "CTL_HelpTC=Help", "CTL_Contents=Contents", "CTL_Back=Back",
    "PH_Search=Search help", "CTL_OpenInTab=Open in new tab", "MSG_NoHelp=No help pages are installed.",
    "MSG_NoTopic=No help page for \"{0}\"", "MSG_NoResults=Nothing found for \"{0}\"", "LBL_Results=Results for \"{0}\""})
public final class HelpTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager em = new ExplorerManager();
    private final TreeView tree = new TreeView();
    private final HelpViewer viewer = new HelpViewer();
    private final Label crumbs = new Label();
    private final Label emptyNote = new Label();
    private final Panel stack = new Panel(new Layout.Stack());
    private final Panel page = new Panel(new Layout.Border());
    private final Button contents = new Button(Bundle.CTL_Contents());
    private final Button back = new Button("");
    private final TextField search = new TextField();
    private final Link openInTab = new Link(Bundle.CTL_OpenInTab(), "wb/help/");
    private final Deque<HelpSet.Topic> history = new ArrayDeque<HelpSet.Topic>();
    private HelpSet set;
    private HelpSet.Topic current;
    private String query = "";
    private boolean syncing;

    public HelpTopComponent() {
        setDisplayName(Bundle.CTL_HelpTC());
        setLayout(new Layout.Border());

        Panel bar = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 6));
        bar.setStyleName("wb-help-bar");
        contents.setIcon(Resource.of("org/webbeans/help/book.svg")).setTestId("help-contents");
        contents.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                showContents();
            }
        });
        back.setIcon(Resource.of("org/webbeans/help/back.svg")).setTooltip(Bundle.CTL_Back()).setTestId("help-back");
        back.setEnabled(false);
        back.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                goBack();
            }
        });
        search.setPlaceholder(Bundle.PH_Search()).setClearButton(true).setTestId("help-search");
        search.addChangeListener(new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                search(e.getValue());
            }
        });
        search.addInputListener(new Listener<TextField.InputEvent>() {
            @Override
            public void onEvent(TextField.InputEvent e) {
                search(e.getValue());
            }
        });
        openInTab.setTestId("help-open-tab");
        bar.add(contents);
        bar.add(back);
        bar.add(search);
        bar.add(openInTab);
        add(bar, Layout.Border.NORTH);

        tree.setRootVisible(false);
        tree.setTestId("help-toc");
        em.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName()) && !syncing) {
                    Node[] sel = em.getSelectedNodes();
                    HelpSet.Topic t = sel.length > 0 ? sel[0].getLookup().lookup(HelpSet.Topic.class) : null;
                    if (t != null) {
                        showTopic(t, null, true);
                    }
                }
            }
        });
        Panel toc = new Panel(new Layout.Border());
        emptyNote.setStyleName("wb-help-crumbs").setTestId("help-note");
        toc.add(emptyNote, Layout.Border.NORTH);
        toc.add(tree, Layout.Border.CENTER);

        crumbs.setStyleName("wb-help-crumbs").setTestId("help-crumbs");
        viewer.setTestId("help-page");
        viewer.addNavigateListener(new Listener<HelpViewer.NavigateEvent>() {
            @Override
            public void onEvent(HelpViewer.NavigateEvent e) {
                navigate(e.getHref());
            }
        });
        page.setStyleName("wb-help-page");
        page.add(crumbs, Layout.Border.NORTH);
        page.add(viewer, Layout.Border.CENTER);

        stack.add(toc);
        stack.add(page);
        stack.setSelected(toc);
        add(stack, Layout.Border.CENTER);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void componentOpened() {
        reload();
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_HelpTC());
        contents.setText(Bundle.CTL_Contents());
        back.setTooltip(Bundle.CTL_Back());
        search.setPlaceholder(Bundle.PH_Search());
        openInTab.setText(Bundle.CTL_OpenInTab());
        reload();
    }

    /** Rebuilds the table of contents from the registry and re-renders the current page. */
    private void reload() {
        set = HelpSet.current();
        buildTree();
        if (current != null) {
            HelpSet.Topic again = set.find(current.getId());
            if (again != null) {
                render(again);
            } else {
                showContents();
            }
        }
    }

    private void buildTree() {
        List<HelpSet.Topic> topics;
        boolean flat = !query.isEmpty();
        if (flat) {
            topics = set.search(query, getUI().getSession().getLocale());
            emptyNote.setText(topics.isEmpty() ? Bundle.MSG_NoResults(query) : Bundle.LBL_Results(query));
            emptyNote.setVisible(true);
        } else {
            topics = set.getRoots();
            emptyNote.setText(Bundle.MSG_NoHelp());
            emptyNote.setVisible(topics.isEmpty());
        }
        AbstractNode root = new AbstractNode(new HelpNode.TopicChildren(topics, flat));
        root.setName("help");
        syncing = true;
        try {
            em.setRootContext(root);
        } finally {
            syncing = false;
        }
        if (!flat) {
            expandAll(root);          // the view remembers expansions across rebuilds and reconnects
        }
    }


    private void expandAll(Node n) {
        for (Node c : n.getChildren().getNodes()) {
            if (!c.isLeaf()) {
                tree.expandNode(c);
                expandAll(c);
            }
        }
    }

    // ---- API used by the displayer and the action -------------------------------------------------------------

    /** Shows the topic of a help context: its page, or the home page / contents for the default context. */
    public boolean show(HelpCtx ctx) {
        if (set == null) {
            set = HelpSet.current();
            buildTree();
        }
        if (ctx == null || ctx.isDefault()) {
            HelpSet.Topic home = set.getHome();
            if (home == null) {
                showContents();
            } else {
                showTopic(home, null, true);
            }
            return true;
        }
        String id = ctx.getHelpID(), anchor = null;
        int hash = id.indexOf('#');
        if (hash >= 0) {
            anchor = id.substring(hash + 1);
            id = id.substring(0, hash);
        }
        HelpSet.Topic t = set.find(id);
        if (t == null) {
            StatusDisplayer.getDefault().setStatusText(Bundle.MSG_NoTopic(id));
            showContents();
            return false;
        }
        showTopic(t, anchor, true);
        return true;
    }

    /** The table of contents (or the search results). */
    public void showContents() {
        stack.setSelected(stack.getChildren().get(0));
    }

    /** Whether the table of contents (or the search results) is showing rather than a page. */
    public boolean isShowingContents() {
        return stack.get(Panel.SELECTED) != page;
    }

    public HelpSet.Topic getCurrentTopic() {
        return current;
    }

    /** The current page's HTML (tests). */
    public String getPageHtml() {
        return viewer.get(HelpViewer.HTML);
    }

    private void showTopic(HelpSet.Topic t, String anchor, boolean remember) {
        if (remember && current != null && current != t) {
            history.push(current);
            back.setEnabled(true);
        }
        render(t);
        if (anchor != null) {
            viewer.scrollTo(anchor);
        }
    }

    private void render(HelpSet.Topic t) {
        current = t;
        viewer.setHtml(HelpContent.html(set, t, getUI().getSession().getLocale()));
        StringBuilder sb = new StringBuilder();
        for (HelpSet.Topic p : t.getPath()) {
            if (sb.length() > 0) {
                sb.append(" › ");
            }
            sb.append(p.getTitle());
        }
        crumbs.setText(sb.toString());
        openInTab.setHref("wb/help/" + t.getId());
        stack.setSelected(page);
        Node n = HelpNode.find(em.getRootContext(), t);
        syncing = true;
        try {
            em.setSelectedNodes(n == null ? new Node[0] : new Node[] {n});
        } catch (PropertyVetoException e) {
            // keep the old selection
        } finally {
            syncing = false;
        }
    }

    private void goBack() {
        if (history.isEmpty()) {
            return;
        }
        HelpSet.Topic t = history.pop();
        back.setEnabled(!history.isEmpty());
        HelpSet.Topic again = set.find(t.getId());
        if (again != null) {
            showTopic(again, null, false);
        }
    }

    private void navigate(String href) {
        if (href.startsWith("#")) {
            viewer.scrollTo(href.substring(1));
        } else if (href.startsWith("help:")) {
            show(new HelpCtx(href.substring("help:".length())));
        }
    }

    private void search(String q) {
        String nq = q == null ? "" : q.trim();
        if (nq.equals(query)) {
            return;
        }
        query = nq;
        buildTree();
        showContents();
    }
}
