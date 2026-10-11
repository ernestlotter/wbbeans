package org.webbeans.processor.spi;

import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Extension point of the WebBeans annotation processor. Platform API modules (actions, windows, ...) ship a
 * handler for their registration annotations and list it in
 * {@code META-INF/services/org.webbeans.processor.spi.AnnotationHandler} on the processor path; the core
 * processor dispatches every annotated element to the handler claiming its annotation type.
 *
 * <p>Handlers work on annotation mirrors by name, so they need no compile dependency on the annotation classes.
 */
public interface AnnotationHandler {

    /** Fully qualified names of the annotations this handler processes. */
    Set<String> getSupportedAnnotationTypes();

    /**
     * Translates one annotation occurrence into registrations.
     *
     * @param element    the annotated element
     * @param annotation the annotation mirror (its type is one of {@link #getSupportedAnnotationTypes()})
     * @param model      where to add entries, hides and messages; also reports errors
     * @param env        the processing environment (elements, types, messager)
     */
    void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env);
}
