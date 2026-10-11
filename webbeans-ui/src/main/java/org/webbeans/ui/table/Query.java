package org.webbeans.ui.table;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * What a {@link Table} asks its {@link TableModel} for: the sort (column id and direction, plus the comparator
 * the table built from that column), and the filter text (plus the matcher the table built over the visible
 * columns). In-memory models use the comparator and matcher; a model over a database translates the column id
 * and the text into its own query.
 */
public final class Query<T> {

    private final String sortColumn;
    private final boolean ascending;
    private final String filter;
    private final Comparator<T> comparator;
    private final Predicate<T> matcher;

    public Query(String sortColumn, boolean ascending, String filter, Comparator<T> comparator, Predicate<T> matcher) {
        this.sortColumn = sortColumn;
        this.ascending = ascending;
        this.filter = filter == null || filter.trim().isEmpty() ? null : filter.trim();
        this.comparator = comparator;
        this.matcher = matcher;
    }

    /** The id of the sort column, or null for the model's natural order. */
    public String getSortColumn() {
        return sortColumn;
    }

    public boolean isAscending() {
        return ascending;
    }

    /** The filter text, or null. */
    public String getFilter() {
        return filter;
    }

    /** Orders rows as requested (null when unsorted). */
    public Comparator<T> getComparator() {
        return comparator;
    }

    /** Accepts the rows matching the filter (null when unfiltered). */
    public Predicate<T> getMatcher() {
        return matcher;
    }

    @Override
    public String toString() {
        return "Query[sort=" + sortColumn + (sortColumn == null ? "" : ascending ? " asc" : " desc") + ", filter=" + filter + "]";
    }
}
