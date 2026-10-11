package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;

@Renderer(id = "wb.ProgressBar", resource = "org/webbeans/ui/client/renderers/progressbar.js")
public class ProgressBar extends Component {

    public static final Property<Double> VALUE = Property.of("value", Double.class, 0.0);
    public static final Property<Boolean> INDETERMINATE = Property.of("indeterminate", Boolean.class, Boolean.FALSE);
    public static final Property<String> TEXT = Property.of("text", String.class, null);

    public ProgressBar() {
        super("wb.ProgressBar");
    }

    /** @param fraction 0..1 */
    public ProgressBar setValue(double fraction) {
        set(VALUE, Math.max(0, Math.min(1, fraction)));
        return this;
    }

    public ProgressBar setIndeterminate(boolean b) {
        set(INDETERMINATE, b);
        return this;
    }

    public ProgressBar setText(String t) {
        set(TEXT, t);
        return this;
    }
}
