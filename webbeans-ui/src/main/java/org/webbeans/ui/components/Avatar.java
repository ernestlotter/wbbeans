package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/** A user avatar: an image, or initials on a colour derived from the name; optional presence dot. */
@Renderer(id = "wb.Avatar", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Avatar extends Component {

    public enum Status { NONE, ONLINE, AWAY, BUSY, OFFLINE }

    public static final Property<String> NAME = Property.of("name", String.class, "");
    public static final Property<Resource> IMAGE = Property.of("image", Resource.class, null);
    public static final Property<Integer> SIZE = Property.of("size", Integer.class, Integer.valueOf(32));
    public static final Property<String> COLOR = Property.of("color", String.class, null);
    public static final Property<Status> STATUS = Property.of("status", Status.class, Status.NONE);
    public static final Property<Boolean> SQUARE = Property.of("square", Boolean.class, Boolean.FALSE);

    public Avatar() {
        super("wb.Avatar");
    }

    public Avatar(String name) {
        this();
        setName(name);
    }

    public Avatar(String name, Resource image) {
        this(name);
        setImage(image);
    }

    public Avatar setName(String n) {
        set(NAME, n);
        if (get(TOOLTIP) == null) {
            setTooltip(n);
        }
        return this;
    }

    public Avatar setImage(Resource r) {
        set(IMAGE, r);
        return this;
    }

    public Avatar setSize(int px) {
        set(SIZE, px);
        return this;
    }

    public Avatar setColor(String css) {
        set(COLOR, css);
        return this;
    }

    public Avatar setStatus(Status s) {
        set(STATUS, s);
        return this;
    }

    public Avatar setSquare(boolean b) {
        set(SQUARE, b);
        return this;
    }

    /** The initials shown without an image: first letters of the first and last word. */
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) {
            s += parts[parts.length - 1].substring(0, 1);
        }
        return s.toUpperCase();
    }
}
