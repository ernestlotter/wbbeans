package org.webbeans.charts;

/** Chart and series types. A chart has a default type; series may override it where the axes are compatible. */
public enum ChartType {
    LINE, AREA, BAR, PIE, DONUT, SCATTER, STEP;

    /** Whether the type draws on category/value axes (false for pie and donut). */
    public boolean isCartesian() {
        return this != PIE && this != DONUT;
    }
}
