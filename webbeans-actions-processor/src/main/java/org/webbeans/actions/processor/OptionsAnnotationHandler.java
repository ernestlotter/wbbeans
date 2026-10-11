package org.webbeans.actions.processor;

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
 * {@code @OptionsPanelController.TopLevelRegistration(categoryName, iconBase, keywords, position, roles)} →
 * {@code OptionsDialog/<id>} (attrs {@code displayName}, {@code iconBase}, {@code keywords}); the options
 * displayer instantiates the controller per dialog.
 */
public final class OptionsAnnotationHandler implements AnnotationHandler {

    static final String REG = "org.webbeans.options.OptionsPanelController.TopLevelRegistration";
    static final String CONTROLLER = "org.webbeans.options.OptionsPanelController";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(REG);
    }

    @Override
    public void handle(Element element, AnnotationMirror a, RegistrationModel model, ProcessingEnvironment env) {
        if (element.getKind() != ElementKind.CLASS) {
            model.error(element, "@TopLevelRegistration is only allowed on classes");
            return;
        }
        TypeElement cls = (TypeElement) element;
        TypeElement ctl = env.getElementUtils().getTypeElement(CONTROLLER);
        if (ctl != null && !env.getTypeUtils().isSubtype(cls.asType(), ctl.asType())) {
            model.error(cls, "@TopLevelRegistration needs a subclass of OptionsPanelController");
            return;
        }
        String name = model.string(a, "categoryName");
        if (name == null || name.isEmpty()) {
            model.error(cls, "@TopLevelRegistration.categoryName must not be empty");
            return;
        }
        String id = model.string(a, "id");
        if (id == null || id.isEmpty()) {
            id = cls.getSimpleName().toString();
        }
        RegistrationModel.EntrySpec spec = model.instance("OptionsDialog/" + id, cls)
                .supplierOf(cls)
                .instanceOf(CONTROLLER)
                .attr("displayName", name)
                .position(model.integer(a, "position", Integer.MAX_VALUE))
                .roles(model.strings(a, "roles"));
        String icon = model.string(a, "iconBase");
        if (icon != null && !icon.isEmpty()) {
            spec.attr("iconBase", icon);
        }
        String kw = model.string(a, "keywords");
        if (kw != null && !kw.isEmpty()) {
            spec.attr("keywords", kw);
        }
    }
}
