package org.webbeans.processor.handlers;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/** {@code @FolderRegistration(path, displayName, position, attrs)} on a class or package → a folder with attributes. */
public final class FolderHandler implements AnnotationHandler {

    public static final String FOLDER = "org.webbeans.core.registry.FolderRegistration";
    public static final String FOLDERS = "org.webbeans.core.registry.FolderRegistrations";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new LinkedHashSet<String>(Arrays.asList(FOLDER, FOLDERS));
    }

    @Override
    public void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env) {
        if (annotation.getAnnotationType().toString().equals(FOLDERS)) {
            for (AnnotationMirror inner : model.annotations(annotation, "value")) {
                one(element, inner, model);
            }
        } else {
            one(element, annotation, model);
        }
    }

    private void one(Element element, AnnotationMirror a, RegistrationModel model) {
        String path = model.string(a, "path");
        if (path == null || path.trim().isEmpty()) {
            model.error(element, "@FolderRegistration.path must not be empty");
            return;
        }
        RegistrationModel.EntrySpec spec = model.folder(path, element)
                .position(model.integer(a, "position", Integer.MAX_VALUE))
                .attrs(a, "attrs");
        String dn = model.string(a, "displayName");
        if (dn != null && !dn.isEmpty()) {
            spec.attr("displayName", dn);
        }
    }
}
