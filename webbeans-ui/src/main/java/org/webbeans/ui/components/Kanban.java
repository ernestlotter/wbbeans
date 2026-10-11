package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Arrays;
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
 * A kanban board: named columns holding keyed {@link Card cards} that the user drags between and within
 * columns. A drop is applied to the board on the server (the card's column and position change) and reported
 * as {@link #MOVE}; a listener that disagrees calls {@link #moveCard} to put it back. Columns can show a
 * work-in-progress limit and an add button ({@link #ADD}).
 */
@Renderer(id = "wb.Kanban", resource = "org/webbeans/ui/client/renderers/kanban.js", css = "org/webbeans/ui/client/renderers/kanban.css")
public class Kanban extends Component {

    /** A column. */
    public static final class Column implements JsonValue {
        private final String id;
        private final String title;
        private String color;
        private Integer limit;
        private boolean addButton;
        private boolean collapsed;

        public Column(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Column color(String css) {
            color = css;
            return this;
        }

        /** A WIP limit: the count turns red beyond it (not enforced). */
        public Column limit(Integer n) {
            limit = n;
            return this;
        }

        public Column addButton(boolean b) {
            addButton = b;
            return this;
        }

        public Column collapsed(boolean b) {
            collapsed = b;
            return this;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("t", title);
            if (color != null) {
                m.put("c", color);
            }
            if (limit != null) {
                m.put("limit", limit);
            }
            if (addButton) {
                m.put("add", Boolean.TRUE);
            }
            if (collapsed) {
                m.put("collapsed", Boolean.TRUE);
            }
            return m;
        }
    }

    /** A card; immutable, built with the fluent methods. */
    public static final class Card implements JsonValue {
        private final String key;
        private final String title;
        private final String column;
        private final String subtitle;
        private final List<String> tags;
        private final Resource icon;
        private final String color;
        private final String tooltip;
        private final boolean readOnly;

        public Card(String key, String title, String column) {
            this(key, title, column, null, Collections.<String>emptyList(), null, null, null, false);
        }

        private Card(String key, String title, String column, String subtitle, List<String> tags, Resource icon, String color, String tooltip, boolean readOnly) {
            if (key == null || title == null || column == null) {
                throw new IllegalArgumentException("key, title and column are required");
            }
            this.key = key;
            this.title = title;
            this.column = column;
            this.subtitle = subtitle;
            this.tags = tags;
            this.icon = icon;
            this.color = color;
            this.tooltip = tooltip;
            this.readOnly = readOnly;
        }

        public Card subtitle(String s) {
            return new Card(key, title, column, s, tags, icon, color, tooltip, readOnly);
        }

        public Card tags(String... t) {
            return new Card(key, title, column, subtitle, Arrays.asList(t), icon, color, tooltip, readOnly);
        }

        public Card icon(Resource r) {
            return new Card(key, title, column, subtitle, tags, r, color, tooltip, readOnly);
        }

        public Card color(String css) {
            return new Card(key, title, column, subtitle, tags, icon, css, tooltip, readOnly);
        }

        public Card tooltip(String t) {
            return new Card(key, title, column, subtitle, tags, icon, color, t, readOnly);
        }

        public Card readOnly() {
            return new Card(key, title, column, subtitle, tags, icon, color, tooltip, true);
        }

        /** The same card in another column. */
        public Card in(String columnId) {
            return new Card(key, title, columnId, subtitle, tags, icon, color, tooltip, readOnly);
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public String getColumn() {
            return column;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public List<String> getTags() {
            return tags;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("k", key);
            m.put("t", title);
            m.put("col", column);
            if (subtitle != null) {
                m.put("s", subtitle);
            }
            if (!tags.isEmpty()) {
                m.put("tags", tags);
            }
            if (icon != null) {
                m.put("i", icon);
            }
            if (color != null) {
                m.put("c", color);
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

    /** A card event: {@link #getCard()}. */
    public static class CardEvent extends Event {
        public CardEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        public Card getCard() {
            return ((Kanban) getSource()).getCard(getKey());
        }
    }

    /** A card was moved: {@link #getFromColumn()} → {@link #getColumn()} at {@link #getIndex()}. The board is already updated. */
    public static class MoveEvent extends CardEvent {
        public MoveEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getColumn() {
            return getString("column");
        }

        public String getFromColumn() {
            return getString("from");
        }

        public int getIndex() {
            return getInt("index", 0);
        }
    }

    /** The add button of a column was clicked: {@link #getColumn()}. */
    public static class ColumnEvent extends Event {
        public ColumnEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getColumn() {
            return getString("column");
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Column>> COLUMNS = (Property<List<Column>>) (Property<?>) Property.of("columns", List.class, new ArrayList<Column>());
    @SuppressWarnings("unchecked")
    public static final Property<List<Card>> CARDS = (Property<List<Card>>) (Property<?>) Property.of("cards", List.class, new ArrayList<Card>());
    public static final Property<String> SELECTED = Property.of("selected", String.class, null).clientOwned();
    public static final Property<Boolean> DRAGGABLE = Property.of("draggable", Boolean.class, Boolean.TRUE);

    public static final EventType<CardEvent> CARD_CLICK = EventType.of("card", CardEvent::new);
    public static final EventType<CardEvent> CARD_ACTIVATE = EventType.of("cardActivate", CardEvent::new);
    public static final EventType<CardEvent> CARD_MENU = EventType.of("cardMenu", CardEvent::new);
    public static final EventType<MoveEvent> MOVE = EventType.of("move", MoveEvent::new);
    public static final EventType<ColumnEvent> ADD = EventType.of("add", ColumnEvent::new);
    static final EventType<Event> DROP = EventType.of("drop", Event::new);
    static final EventType<Event> SELECT = EventType.of("select", Event::new);

    private final List<Column> columns = new ArrayList<Column>();
    private final List<Card> cards = new ArrayList<Card>();

    public Kanban() {
        super("wb.Kanban");
        addListener(DROP, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                applyMove(e.getString("key"), e.getString("column"), e.getInt("index", Integer.MAX_VALUE));
            }
        });
        addListener(SELECT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(SELECTED, e.getString("key"));
            }
        });
    }

    // ---- columns ------------------------------------------------------------------------------------------------

    public Column addColumn(String id, String title) {
        Column c = new Column(id, title);
        columns.add(c);
        set(COLUMNS, new ArrayList<Column>(columns));
        return c;
    }

    public Kanban setColumns(Collection<Column> cols) {
        columns.clear();
        columns.addAll(cols);
        set(COLUMNS, new ArrayList<Column>(columns));
        return this;
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /** Re-sends the columns after a column was changed in place (colour, limit). */
    public Kanban updateColumns() {
        set(COLUMNS, new ArrayList<Column>(columns));
        return this;
    }

    // ---- cards --------------------------------------------------------------------------------------------------

    public Kanban setCards(Collection<Card> list) {
        cards.clear();
        cards.addAll(list);
        publish();
        return this;
    }

    public Kanban addCard(Card c) {
        removeQuietly(c.key);
        cards.add(c);
        publish();
        return this;
    }

    /** Replaces the card with the same key (keeping its position). */
    public Kanban updateCard(Card c) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).key.equals(c.key)) {
                cards.set(i, c);
                publish();
                return this;
            }
        }
        return addCard(c);
    }

    public Kanban removeCard(String key) {
        if (removeQuietly(key)) {
            publish();
        }
        return this;
    }

    private boolean removeQuietly(String key) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).key.equals(key)) {
                cards.remove(i);
                return true;
            }
        }
        return false;
    }

    public Card getCard(String key) {
        for (Card c : cards) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(new ArrayList<Card>(cards));
    }

    public List<Card> getCards(String columnId) {
        List<Card> out = new ArrayList<Card>();
        for (Card c : cards) {
            if (c.column.equals(columnId)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Moves a card to a column at an index among that column's cards (end if too large). */
    public Kanban moveCard(String key, String columnId, int index) {
        Card c = getCard(key);
        if (c == null) {
            return this;
        }
        removeQuietly(key);
        Card moved = c.in(columnId);
        int pos = cards.size();
        int seen = 0;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).column.equals(columnId)) {
                if (seen == index) {
                    pos = i;
                    break;
                }
                seen++;
                pos = i + 1;
            }
        }
        cards.add(Math.min(pos, cards.size()), moved);
        publish();
        return this;
    }

    private void applyMove(String key, String columnId, int index) {
        Card c = getCard(key);
        if (c == null || columnId == null || c.readOnly) {
            publish();
            return;
        }
        String from = c.column;
        moveCard(key, columnId, index);
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("key", key);
        d.put("column", columnId);
        d.put("from", from);
        d.put("index", index);
        fireEvent(MOVE, d);
    }

    private void publish() {
        set(CARDS, new ArrayList<Card>(cards));
    }

    public Kanban setDraggable(boolean b) {
        set(DRAGGABLE, b);
        return this;
    }

    public Kanban setSelected(String key) {
        set(SELECTED, key);
        return this;
    }

    public Card getSelected() {
        return getCard(get(SELECTED));
    }

    public Registration addCardClickListener(Listener<CardEvent> l) {
        return addListener(CARD_CLICK, l);
    }

    public Registration addCardActivateListener(Listener<CardEvent> l) {
        return addListener(CARD_ACTIVATE, l);
    }

    public Registration addCardMenuListener(Listener<CardEvent> l) {
        return addListener(CARD_MENU, l);
    }

    public Registration addMoveListener(Listener<MoveEvent> l) {
        return addListener(MOVE, l);
    }

    public Registration addAddListener(Listener<ColumnEvent> l) {
        return addListener(ADD, l);
    }
}
