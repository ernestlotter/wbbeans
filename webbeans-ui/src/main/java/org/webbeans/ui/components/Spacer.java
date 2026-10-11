package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;

/** Takes the remaining space in a flow layout or toolbar. */
@Renderer(id = "wb.Spacer", resource = "org/webbeans/ui/client/renderers/spacer.js")
public class Spacer extends Component {
    public Spacer() {
        super("wb.Spacer");
    }
}
