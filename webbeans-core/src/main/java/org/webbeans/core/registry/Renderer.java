package org.webbeans.core.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the client-side renderer of a component class. The processor registers it under
 * {@code Renderers/<id>} with attributes {@code resource} (the JS module inside the jar) and {@code css}
 * (optional stylesheet); the bundler collects these at startup.
 *
 * <pre>&#64;Renderer(id = "acme.Gauge", resource = "com/acme/widgets/gauge.js", css = "com/acme/widgets/gauge.css")</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Renderer {
    /** Renderer id as used in the protocol, e.g. {@code wb.Button}. */
    String id();

    /** Resource path of the ES module, e.g. {@code org/webbeans/ui/renderers/button.js}. */
    String resource();

    /** Optional stylesheet resource path. */
    String css() default "";
}
