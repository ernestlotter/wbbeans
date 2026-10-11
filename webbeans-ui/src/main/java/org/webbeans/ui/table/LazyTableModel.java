package org.webbeans.ui.table;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.RequestProcessor;
import org.webbeans.ui.UI;

/**
 * A {@link TableModel} whose rows live elsewhere (a database, a service): {@link #count} and {@link #fetch} run
 * on a background {@link RequestProcessor}, never on the session thread, and the page is handed to the table
 * through {@code ui.access}. Implement the two methods; call {@link #refresh} when the data changed.
 */
public abstract class LazyTableModel<T> implements TableModel<T> {

    private static final Logger LOG = Logger.getLogger(LazyTableModel.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("wb-table", 4, true);

    private final List<Listener<T>> listeners = new ArrayList<Listener<T>>();
    private int generation;

    /** The number of rows matching the query. Background thread. */
    protected abstract int count(Query<T> query);

    /** The rows {@code offset .. offset+limit} in query order. Background thread. */
    protected abstract List<T> fetch(Query<T> query, int offset, int limit);

    @Override
    public void load(final Query<T> query, final int offset, final int limit, final Callback<T> callback) {
        final UI ui = UI.current();
        final int gen = generation;
        RP.post(new Runnable() {
            @Override
            public void run() {
                final int total;
                final List<T> rows;
                try {
                    total = count(query);
                    rows = fetch(query, offset, limit);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Table fetch failed", e);
                    return;
                }
                if (gen != generation) {
                    return; // refreshed meanwhile; the table has asked again
                }
                Runnable deliver = new Runnable() {
                    @Override
                    public void run() {
                        callback.loaded(total, offset, rows);
                    }
                };
                if (ui != null) {
                    ui.access(deliver);
                } else {
                    deliver.run();
                }
            }
        });
    }

    /** The data changed: pending fetches are discarded and the table re-requests its pages. */
    public void refresh() {
        generation++;
        for (Listener<T> l : new ArrayList<Listener<T>>(listeners)) {
            l.reset();
        }
    }

    /** One row changed in place (the table re-renders it if it is showing it). */
    public void updated(T row) {
        for (Listener<T> l : new ArrayList<Listener<T>>(listeners)) {
            l.updated(row);
        }
    }

    @Override
    public void addListener(Listener<T> l) {
        listeners.add(l);
    }

    @Override
    public void removeListener(Listener<T> l) {
        listeners.remove(l);
    }
}
