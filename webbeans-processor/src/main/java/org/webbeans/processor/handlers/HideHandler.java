package org.webbeans.processor.handlers;

import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/** {@code @Hide({"path", ...})} on a class or package. */
public final class HideHandler implements AnnotationHandler {

    public static final String HIDE = "org.webbeans.core.registry.Hide";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(HIDE);
    }

    @Override
    public void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env) {
        for (String path : model.strings(annotation, "value")) {
            if (path.trim().isEmpty()) {
                model.error(element, "@Hide paths must not be empty");
            } else {
                model.hide(path, element);
            }
        }
    }
}
