package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

@Renderer(id = "wb.Image", resource = "org/webbeans/ui/client/renderers/image.js")
public class Image extends Component {

    public enum Fit { CONTAIN, COVER, NONE }

    public static final Property<Resource> SOURCE = Property.of("source", Resource.class, null);
    public static final Property<String> ALT = Property.of("alt", String.class, "");
    public static final Property<Fit> FIT = Property.of("fit", Fit.class, Fit.CONTAIN);

    public Image(Resource source, String alt) {
        super("wb.Image");
        set(SOURCE, source);
        set(ALT, alt);
    }

    public Image setFit(Fit f) {
        set(FIT, f);
        return this;
    }
}
