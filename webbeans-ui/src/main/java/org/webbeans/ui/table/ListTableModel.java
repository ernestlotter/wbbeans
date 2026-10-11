package org.webbeans.ui.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An in-memory {@link TableModel}: sorts and filters with the query's comparator and matcher. Changes
 * ({@link #add}, {@link #remove}, {@link #set}) reset the table; {@link #update} refreshes one row in place.
 */
public class ListTableModel<T> implements TableModel<T> {

    private final List<T> items = new ArrayList<T>();
    private final List<Listener<T>> listeners = new ArrayList<Listener<T>>();

    public ListTableModel() {
    }

    public ListTableModel(Collection<? extends T> items) {
        this.items.addAll(items);
    }

    @Override
    public void load(Query<T> query, int offset, int limit, Callback<T> callback) {
        List<T> rows = items;
        if (query.getMatcher() != null) {
            rows = new ArrayList<T>();
            for (T t : items) {
                if (query.getMatcher().test(t)) {
                    rows.add(t);
                }
            }
        }
        if (query.getComparator() != null) {
            if (rows == items) {
                rows = new ArrayList<T>(items);
            }
            Collections.sort(rows, query.getComparator());
        }
        int from = Math.min(offset, rows.size());
        int to = Math.min(rows.size(), from + limit);
        callback.loaded(rows.size(), from, new ArrayList<T>(rows.subList(from, to)));
    }

    /** The backing list (unmodifiable); change it through the methods below so the table hears about it. */
    public List<T> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public void set(Collection<? extends T> rows) {
        items.clear();
        items.addAll(rows);
        fireReset();
    }

    public void add(T row) {
        items.add(row);
        fireReset();
    }

    public void add(int index, T row) {
        items.add(index, row);
        fireReset();
    }

    public void addAll(Collection<? extends T> rows) {
        items.addAll(rows);
        fireReset();
    }

    public boolean remove(T row) {
        boolean removed = items.remove(row);
        if (removed) {
            fireReset();
        }
        return removed;
    }

    public void removeAll(Collection<? extends T> rows) {
        if (items.removeAll(rows)) {
            fireReset();
        }
    }

    public void clear() {
        items.clear();
        fireReset();
    }

    /** The row's values changed (same object, or an equal replacement): the table re-renders it in place. */
    public void update(T row) {
        int i = items.indexOf(row);
        if (i >= 0) {
            items.set(i, row);
            for (Listener<T> l : new ArrayList<Listener<T>>(listeners)) {
                l.updated(row);
            }
        }
    }

    /** Replaces one row by another (a copy with changes); the table re-renders it in place. */
    public void replace(T oldRow, T newRow) {
        int i = items.indexOf(oldRow);
        if (i >= 0) {
            items.set(i, newRow);
            for (Listener<T> l : new ArrayList<Listener<T>>(listeners)) {
                l.updated(newRow);
            }
        }
    }

    /** Tells the table the rows were changed behind its back (re-sort, re-filter, re-count). */
    public void refresh() {
        fireReset();
    }

    protected void fireReset() {
        for (Listener<T> l : new ArrayList<Listener<T>>(listeners)) {
            l.reset();
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
