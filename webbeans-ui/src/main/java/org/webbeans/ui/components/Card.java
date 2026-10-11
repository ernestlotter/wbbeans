package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/**
 * A {@link Panel} drawn as a card: a header (icon, title, subtitle, optional banner image) over the children
 * (laid out by the panel's {@link Layout}, vertical flow by default). Optionally clickable and selectable, for
 * galleries of things.
 */
@Renderer(id = "wb.Card", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Card extends Panel {

    public static final Property<String> CARD_TITLE = Property.of("cardTitle", String.class, null);
    public static final Property<String> SUBTITLE = Property.of("subtitle", String.class, null);
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Resource> IMAGE = Property.of("image", Resource.class, null);
    public static final Property<Boolean> CLICKABLE = Property.of("clickable", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> CARD_SELECTED = Property.of("cardSelected", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> ELEVATED = Property.of("elevated", Boolean.class, Boolean.TRUE);

    public Card() {
        super("wb.Card", new Layout.Flow(Layout.Orientation.VERTICAL, 8));
        setPadding(12);
    }

    public Card(String title) {
        this();
        setCardTitle(title);
    }

    public Card setCardTitle(String t) {
        set(CARD_TITLE, t);
        return this;
    }

    public Card setSubtitle(String t) {
        set(SUBTITLE, t);
        return this;
    }

    public Card setIcon(Resource r) {
        set(ICON, r);
        return this;
    }

    /** A banner image across the top of the card. */
    public Card setImage(Resource r) {
        set(IMAGE, r);
        return this;
    }

    public Card setClickable(boolean b) {
        set(CLICKABLE, b);
        return this;
    }

    public Card setCardSelected(boolean b) {
        set(CARD_SELECTED, b);
        return this;
    }

    public Card setElevated(boolean b) {
        set(ELEVATED, b);
        return this;
    }

    public Card onClick(Listener<ClickEvent> l) {
        setClickable(true);
        addClickListener(l);
        return this;
    }
}
