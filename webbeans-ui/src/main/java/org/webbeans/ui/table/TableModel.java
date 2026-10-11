package org.webbeans.ui.table;

import java.util.List;

/**
 * The rows behind a {@link Table}. The table asks for pages ({@link #load}) as the user scrolls, sorts or
 * filters, and listens for changes. {@link ListTableModel} holds the rows in memory; {@link LazyTableModel}
 * fetches them in the background from wherever they live.
 */
public interface TableModel<T> {

    /** Receives a page: the total row count for the query, the page's offset and its rows. */
    interface Callback<T> {
        void loaded(int total, int offset, List<T> rows);
    }

    /** Notified by the model; implemented by the table. */
    interface Listener<T> {
        /** The row set changed (rows added, removed, re-sorted, or everything): the table re-requests its pages. */
        void reset();

        /** One row's values changed; it keeps its key and position. */
        void updated(T row);
    }

    /**
     * Delivers the rows {@code offset .. offset+limit} for the query to the callback — synchronously, or later on
     * the session executor. Called on the session executor.
     */
    void load(Query<T> query, int offset, int limit, Callback<T> callback);

    void addListener(Listener<T> l);

    void removeListener(Listener<T> l);
}
