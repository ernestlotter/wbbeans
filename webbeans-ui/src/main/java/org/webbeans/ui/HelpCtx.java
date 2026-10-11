package org.webbeans.ui;

import org.openide.util.Lookup;

/**
 * A help context: the id of a help topic, attached to windows ({@link Provider}), dialogs and options pages.
 * {@link #display()} hands it to the {@link Displayer} service (the help module), which opens the topic in
 * the Help window of the current UI. {@link #DEFAULT_HELP} means "no specific topic": the help home page.
 */
public final class HelpCtx {

    /** Something that knows its help topic (a {@code TopComponent}, a dialog descriptor...). */
    public interface Provider {
        HelpCtx getHelpCtx();
    }

    /** Shows help; registered in the default lookup by the help module. */
    public interface Displayer {
        /** @return false when there is no help system or the topic is unknown */
        boolean display(HelpCtx ctx);
    }

    public static final HelpCtx DEFAULT_HELP = new HelpCtx("");

    private final String id;

    public HelpCtx(String id) {
        this.id = id == null ? "" : id;
    }

    /** The topic id ({@code "files.editor"}), empty for {@link #DEFAULT_HELP}. */
    public String getHelpID() {
        return id;
    }

    public boolean isDefault() {
        return id.isEmpty();
    }

    /** Opens this topic with the {@link Displayer} service; false if none is installed. */
    public boolean display() {
        Displayer d = Lookup.getDefault().lookup(Displayer.class);
        return d != null && d.display(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HelpCtx && ((HelpCtx) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "HelpCtx[" + id + "]";
    }
}
