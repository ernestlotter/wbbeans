package org.webbeans.actions;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;
import org.webbeans.ui.Property;

/** One toolbar ({@code wb.Toolbar}): {@link ToolbarButton}s, {@link Separator}s or any component. */
@Renderer(id = "wb.Toolbar", resource = "org/webbeans/actions/client/toolbar.js")
public class Toolbar extends Container {

    public static final Property<String> NAME = Property.of("name", String.class, "");

    public Toolbar() {
        super("wb.Toolbar");
    }

    public Toolbar(String name) {
        this();
        set(NAME, name);
    }

    public String getName() {
        return get(NAME);
    }

    public Toolbar addSeparator() {
        add(new Separator());
        return this;
    }
}
