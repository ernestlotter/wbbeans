package org.webbeans.processor.handlers;

import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * {@code @Renderer(id, resource, css)} on a component class → {@code Renderers/<id>} with attributes
 * {@code id}, {@code resource}, {@code css}, {@code component} (class name); the instance is the component class.
 */
public final class RendererHandler implements AnnotationHandler {

    public static final String RENDERER = "org.webbeans.core.registry.Renderer";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(RENDERER);
    }

    @Override
    public void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env) {
        if (element.getKind() != ElementKind.CLASS) {
            model.error(element, "@Renderer is only allowed on classes");
            return;
        }
        TypeElement type = (TypeElement) element;
        String id = model.string(annotation, "id");
        String resource = model.string(annotation, "resource");
        if (id == null || !id.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            model.error(element, "@Renderer.id must be a dotted identifier such as acme.Gauge");
            return;
        }
        if (resource == null || resource.isEmpty() || resource.startsWith("/")) {
            model.error(element, "@Renderer.resource must be a resource path without a leading slash");
            return;
        }
        RegistrationModel.EntrySpec spec = model.instance("Renderers/" + id, element)
                .valueExpression(type.getQualifiedName() + ".class")
                .instanceOf("java.lang.Class")
                .attr("id", id)
                .attr("resource", resource)
                .attr("component", type.getQualifiedName().toString());
        String css = model.string(annotation, "css");
        if (css != null && !css.isEmpty()) {
            spec.attr("css", css);
        }
    }
}
