package org.webbeans.processor.handlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/** {@code @Registration} / {@code @Registrations} on classes and static methods. */
public final class RegistrationHandler implements AnnotationHandler {

    public static final String REGISTRATION = "org.webbeans.core.registry.Registration";
    public static final String REGISTRATIONS = "org.webbeans.core.registry.Registrations";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<String>(Arrays.asList(REGISTRATION, REGISTRATIONS));
    }

    @Override
    public void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env) {
        if (annotation.getAnnotationType().toString().equals(REGISTRATIONS)) {
            for (AnnotationMirror inner : model.annotations(annotation, "value")) {
                handleOne(element, inner, model);
            }
        } else {
            handleOne(element, annotation, model);
        }
    }

    private void handleOne(Element element, AnnotationMirror a, RegistrationModel model) {
        String path = model.string(a, "path");
        if (path == null || path.trim().isEmpty()) {
            model.error(element, "@Registration.path must not be empty");
            return;
        }
        String name = model.string(a, "name");
        RegistrationModel.EntrySpec spec;
        if (element.getKind() == ElementKind.CLASS) {
            TypeElement type = (TypeElement) element;
            if (name == null || name.isEmpty()) {
                name = RegistrationModel.entryName(type);
            }
            spec = model.instance(path + "/" + name, element).supplierOf(type);
        } else if (element.getKind() == ElementKind.METHOD) {
            ExecutableElement method = (ExecutableElement) element;
            if (name == null || name.isEmpty()) {
                name = RegistrationModel.entryName((TypeElement) method.getEnclosingElement()) + "-" + method.getSimpleName();
            }
            spec = model.instance(path + "/" + name, element).factoryMethod(method);
        } else {
            model.error(element, "@Registration is only allowed on classes and static methods");
            return;
        }
        String type = model.type(a, "type", "java.lang.Object");
        if (type != null) {
            spec.instanceOf(type);
        }
        spec.position(model.integer(a, "position", Integer.MAX_VALUE));
        spec.attrs(a, "attrs");
        spec.roles(model.strings(a, "roles"));
        for (AnnotationMirror ref : model.annotations(a, "references")) {
            String refPath = model.string(ref, "path");
            int position = model.integer(ref, "position", Integer.MAX_VALUE);
            model.reference(refPath + "/" + name, spec.getPath(), element).position(position);
            int before = model.integer(ref, "separatorBefore", -1);
            if (before >= 0) {
                model.separator(refPath, before, element);
            }
            int after = model.integer(ref, "separatorAfter", -1);
            if (after >= 0) {
                model.separator(refPath, after, element);
            }
        }
    }
}
