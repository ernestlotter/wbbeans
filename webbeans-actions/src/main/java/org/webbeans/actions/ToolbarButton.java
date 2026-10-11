package org.webbeans.actions;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/** A toolbar button ({@code wb.ToolbarButton}): icon, optional text, tooltip; toggles show {@link #SELECTED}. */
@Renderer(id = "wb.ToolbarButton", resource = "org/webbeans/actions/client/toolbar.js")
public class ToolbarButton extends Component {

    public static final Property<String> TEXT = Property.of("text", String.class, null);
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Boolean> SELECTED = Property.of("selected", Boolean.class, null);
    public static final EventType<Event> ACTION = EventType.of("action", Event::new);

    private ActionPresenters.Binding binding;

    public ToolbarButton() {
        super("wb.ToolbarButton");
    }

    public ToolbarButton setText(String text) {
        set(TEXT, text);
        return this;
    }

    public ToolbarButton setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }

    public ToolbarButton setSelected(Boolean b) {
        set(SELECTED, b);
        return this;
    }

    public org.webbeans.ui.Registration addActionListener(Listener<Event> l) {
        return addListener(ACTION, l);
    }

    public ToolbarButton setAction(Action action, Lookup context) {
        if (binding != null) {
            binding.stop();
        }
        binding = action == null ? null : new ActionPresenters.Binding(action, context, this, false);
        if (binding != null && isAttached()) {
            binding.start();
        }
        return this;
    }

    public Action getAction() {
        return binding == null ? null : binding.getAction();
    }

    @Override
    protected void attached() {
        if (binding != null) {
            binding.start();
        }
    }

    @Override
    protected void detached() {
        if (binding != null) {
            binding.stop();
        }
    }
}
