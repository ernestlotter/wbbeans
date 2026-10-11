package org.webbeans.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The data of a {@link Chart}: ordered series and, for a category x-axis, the category names. Points on a
 * category axis name their category; unknown names are appended to the list.
 */
public final class ChartModel implements Series.Sink {

    /** The chart: merges mutations into the next {@code data} call. */
    interface Sink {
        void categoriesChanged();

        void seriesStyle(Series s);

        void seriesSet(Series s);

        void seriesAppend(Series s, List<Point> added);

        void seriesUpdate(Series s, Object key, Point p);

        void seriesRemovePoint(Series s, Object key);

        void seriesRemoved(String id);
    }

    private final Sink sink;
    private final Map<String, Series> series = new LinkedHashMap<String, Series>();
    private final List<String> categories = new ArrayList<String>();

    ChartModel(Sink sink) {
        this.sink = sink;
    }

    /** Adds (or replaces) a series; {@code type} null = the chart's default. */
    public Series addSeries(String id, String displayName, ChartType type) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("series id");
        }
        Series s = new Series(id, displayName, type, this);
        series.put(id, s);
        sink.seriesSet(s);
        return s;
    }

    public Series addSeries(String id, String displayName) {
        return addSeries(id, displayName, null);
    }

    public Series getSeries(String id) {
        return series.get(id);
    }

    public List<Series> getSeries() {
        return Collections.unmodifiableList(new ArrayList<Series>(series.values()));
    }

    public void removeSeries(String id) {
        if (series.remove(id) != null) {
            sink.seriesRemoved(id);
        }
    }

    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public void setCategories(List<String> names) {
        categories.clear();
        categories.addAll(names);
        sink.categoriesChanged();
    }

    public void clear() {
        for (String id : new ArrayList<String>(series.keySet())) {
            removeSeries(id);
        }
        if (!categories.isEmpty()) {
            setCategories(Collections.<String>emptyList());
        }
    }

    /** The x value sent for a point: its number, or its category's index (appending unknown categories). */
    double resolveX(Point p) {
        if (p.getCategory() == null) {
            return p.getX();
        }
        int i = categories.indexOf(p.getCategory());
        if (i < 0) {
            categories.add(p.getCategory());
            sink.categoriesChanged();
            i = categories.size() - 1;
        }
        return i;
    }

    // ---- Series.Sink ----------------------------------------------------------------------------------------

    @Override
    public void styleChanged(Series s) {
        sink.seriesStyle(s);
    }

    @Override
    public void pointsSet(Series s) {
        sink.seriesSet(s);
    }

    @Override
    public void pointsAppended(Series s, List<Point> added) {
        sink.seriesAppend(s, added);
    }

    @Override
    public void pointUpdated(Series s, Object key, Point p) {
        sink.seriesUpdate(s, key, p);
    }

    @Override
    public void pointRemoved(Series s, Object key) {
        sink.seriesRemovePoint(s, key);
    }
}
