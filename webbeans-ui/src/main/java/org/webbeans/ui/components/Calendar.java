package org.webbeans.ui.components;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.JsonValue;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/**
 * A month / week calendar showing keyed {@link Entry entries} (all-day or timed, spanning days, coloured).
 * The user navigates (the server is told through {@link #RANGE} so it can load the entries of the visible
 * period), clicks days and entries, and drags entries to other days ({@link #MOVE}, applied by the listener —
 * the calendar itself does not change the entry). Month and day names follow the session locale.
 */
@Renderer(id = "wb.Calendar", resource = "org/webbeans/ui/client/renderers/calendar.js", css = "org/webbeans/ui/client/renderers/calendar.css")
public class Calendar extends Component {

    public enum View { MONTH, WEEK }

    /** A calendar entry; immutable, build with the fluent setters and {@link Calendar#setEntries}. */
    public static final class Entry implements JsonValue {
        private final String key;
        private final String title;
        private final LocalDate start;
        private final LocalDate end;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String color;
        private final Resource icon;
        private final String tooltip;
        private final boolean readOnly;

        public Entry(String key, String title, LocalDate date) {
            this(key, title, date, date, null, null, null, null, null, false);
        }

        private Entry(String key, String title, LocalDate start, LocalDate end, LocalTime startTime, LocalTime endTime, String color,
                Resource icon, String tooltip, boolean readOnly) {
            if (key == null || title == null || start == null) {
                throw new IllegalArgumentException("key, title and start are required");
            }
            this.key = key;
            this.title = title;
            this.start = start;
            this.end = end == null || end.isBefore(start) ? start : end;
            this.startTime = startTime;
            this.endTime = endTime;
            this.color = color;
            this.icon = icon;
            this.tooltip = tooltip;
            this.readOnly = readOnly;
        }

        /** Spans up to {@code end} (inclusive). */
        public Entry until(LocalDate end) {
            return new Entry(key, title, start, end, startTime, endTime, color, icon, tooltip, readOnly);
        }

        /** A timed entry (otherwise all-day). */
        public Entry at(LocalTime from, LocalTime to) {
            return new Entry(key, title, start, end, from, to, color, icon, tooltip, readOnly);
        }

        /** A CSS colour (e.g. {@code #c62828} or {@code var(--wb-accent)}). */
        public Entry color(String css) {
            return new Entry(key, title, start, end, startTime, endTime, css, icon, tooltip, readOnly);
        }

        public Entry icon(Resource r) {
            return new Entry(key, title, start, end, startTime, endTime, color, r, tooltip, readOnly);
        }

        public Entry tooltip(String t) {
            return new Entry(key, title, start, end, startTime, endTime, color, icon, t, readOnly);
        }

        /** Not draggable. */
        public Entry readOnly() {
            return new Entry(key, title, start, end, startTime, endTime, color, icon, tooltip, true);
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public LocalDate getStart() {
            return start;
        }

        public LocalDate getEnd() {
            return end;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public String getColor() {
            return color;
        }

        public boolean isAllDay() {
            return startTime == null;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("k", key);
            m.put("t", title);
            m.put("s", start.toString());
            if (!end.equals(start)) {
                m.put("e", end.toString());
            }
            if (startTime != null) {
                m.put("st", startTime.toString());
            }
            if (endTime != null) {
                m.put("et", endTime.toString());
            }
            if (color != null) {
                m.put("c", color);
            }
            if (icon != null) {
                m.put("i", icon);
            }
            if (tooltip != null) {
                m.put("tt", tooltip);
            }
            if (readOnly) {
                m.put("ro", Boolean.TRUE);
            }
            return m;
        }
    }

    /** A day was clicked: {@link #getDate()}. */
    public static class DayEvent extends Event {
        public DayEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public LocalDate getDate() {
            String d = getString("date");
            return d == null ? null : LocalDate.parse(d);
        }
    }

    /** An entry was clicked or activated: {@link #getEntry()}. */
    public static class EntryEvent extends DayEvent {
        public EntryEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        public Entry getEntry() {
            return ((Calendar) getSource()).getEntry(getKey());
        }
    }

    /** An entry was dragged to another day: {@link #getDate()} is the new start; the end shifts by the same amount. */
    public static class MoveEvent extends EntryEvent {
        public MoveEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        /** The entry with its dates shifted to the drop target (convenience for listeners that accept the move). */
        public Entry getMoved() {
            Entry e = getEntry();
            LocalDate to = getDate();
            if (e == null || to == null) {
                return e;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(e.start, to);
            return new Entry(e.key, e.title, to, e.end.plusDays(days), e.startTime, e.endTime, e.color, e.icon, e.tooltip, e.readOnly);
        }
    }

    /** The visible period changed: {@link #getFrom()} .. {@link #getTo()} (inclusive). */
    public static class RangeEvent extends Event {
        public RangeEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public LocalDate getFrom() {
            return LocalDate.parse(getString("from"));
        }

        public LocalDate getTo() {
            return LocalDate.parse(getString("to"));
        }
    }

    public static final Property<View> VIEW = Property.of("view", View.class, View.MONTH).clientOwned();
    /** The date the view is positioned on (any day of the shown month/week). */
    public static final Property<LocalDate> DATE = Property.of("date", LocalDate.class, null).clientOwned();
    public static final Property<LocalDate> SELECTED = Property.of("selected", LocalDate.class, null).clientOwned();
    @SuppressWarnings("unchecked")
    public static final Property<List<Entry>> ENTRIES = (Property<List<Entry>>) (Property<?>) Property.of("entries", List.class, new ArrayList<Entry>());
    public static final Property<Integer> FIRST_DAY = Property.of("firstDay", Integer.class, Integer.valueOf(DayOfWeek.MONDAY.getValue()));
    public static final Property<Boolean> WEEK_NUMBERS = Property.of("weekNumbers", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> TOOLBAR = Property.of("toolbar", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> DRAGGABLE = Property.of("draggable", Boolean.class, Boolean.TRUE);
    public static final Property<Integer> DAY_START_HOUR = Property.of("dayStart", Integer.class, Integer.valueOf(7));
    public static final Property<Integer> DAY_END_HOUR = Property.of("dayEnd", Integer.class, Integer.valueOf(20));

    public static final EventType<DayEvent> DAY_CLICK = EventType.of("day", DayEvent::new);
    public static final EventType<DayEvent> DAY_ACTIVATE = EventType.of("dayActivate", DayEvent::new);
    public static final EventType<EntryEvent> ENTRY_CLICK = EventType.of("entry", EntryEvent::new);
    public static final EventType<EntryEvent> ENTRY_ACTIVATE = EventType.of("entryActivate", EntryEvent::new);
    public static final EventType<MoveEvent> MOVE = EventType.of("move", MoveEvent::new);
    public static final EventType<RangeEvent> RANGE = EventType.of("range", RangeEvent::new);
    static final EventType<Event> NAV = EventType.of("nav", Event::new);

    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    public Calendar() {
        super("wb.Calendar");
        set(DATE, LocalDate.now());
        addListener(NAV, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String d = e.getString("date");
                if (d != null) {
                    setFromClient(DATE, LocalDate.parse(d));
                }
                String v = e.getString("view");
                if (v != null) {
                    setFromClient(VIEW, View.valueOf(v));
                }
                String s = e.getString("selected");
                setFromClient(SELECTED, s == null ? null : LocalDate.parse(s));
            }
        });
    }

    // ---- entries ------------------------------------------------------------------------------------------------

    public Calendar setEntries(Collection<Entry> list) {
        entries.clear();
        for (Entry e : list) {
            entries.put(e.key, e);
        }
        publish();
        return this;
    }

    public Calendar addEntry(Entry e) {
        entries.put(e.key, e);
        publish();
        return this;
    }

    public Calendar removeEntry(String key) {
        if (entries.remove(key) != null) {
            publish();
        }
        return this;
    }

    public Calendar clearEntries() {
        entries.clear();
        publish();
        return this;
    }

    public Entry getEntry(String key) {
        return key == null ? null : entries.get(key);
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(entries.values()));
    }

    private void publish() {
        set(ENTRIES, new ArrayList<Entry>(entries.values()));
    }

    // ---- navigation ---------------------------------------------------------------------------------------------

    public Calendar setDate(LocalDate d) {
        set(DATE, d);
        return this;
    }

    public LocalDate getDate() {
        return get(DATE);
    }

    public Calendar setMonth(YearMonth m) {
        return setDate(m.atDay(1));
    }

    public Calendar setView(View v) {
        set(VIEW, v);
        return this;
    }

    public View getView() {
        return get(VIEW);
    }

    public Calendar setSelected(LocalDate d) {
        set(SELECTED, d);
        return this;
    }

    public LocalDate getSelected() {
        return get(SELECTED);
    }

    public Calendar setFirstDayOfWeek(DayOfWeek d) {
        set(FIRST_DAY, d.getValue());
        return this;
    }

    public Calendar setWeekNumbers(boolean b) {
        set(WEEK_NUMBERS, b);
        return this;
    }

    public Calendar setToolbar(boolean b) {
        set(TOOLBAR, b);
        return this;
    }

    public Calendar setDraggable(boolean b) {
        set(DRAGGABLE, b);
        return this;
    }

    /** The hours the week view shows (default 7–20). */
    public Calendar setDayHours(int from, int to) {
        set(DAY_START_HOUR, from);
        set(DAY_END_HOUR, to);
        return this;
    }

    public Registration addDayClickListener(Listener<DayEvent> l) {
        return addListener(DAY_CLICK, l);
    }

    public Registration addDayActivateListener(Listener<DayEvent> l) {
        return addListener(DAY_ACTIVATE, l);
    }

    public Registration addEntryClickListener(Listener<EntryEvent> l) {
        return addListener(ENTRY_CLICK, l);
    }

    public Registration addEntryActivateListener(Listener<EntryEvent> l) {
        return addListener(ENTRY_ACTIVATE, l);
    }

    public Registration addMoveListener(Listener<MoveEvent> l) {
        return addListener(MOVE, l);
    }

    public Registration addRangeListener(Listener<RangeEvent> l) {
        return addListener(RANGE, l);
    }
}
