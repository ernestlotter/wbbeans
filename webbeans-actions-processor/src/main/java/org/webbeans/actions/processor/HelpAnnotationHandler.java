package org.webbeans.actions.processor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * {@code @HelpPage(id, title, resource, parent, position, keywords, roles)} (and the {@code @HelpPages} container)
 * → {@code Help/Pages/<id>} with the attributes {@code displayName} (the title), {@code resource}, {@code parent}
 * and {@code keywords}; the help module builds the table of contents from the folder. Allowed on classes and
 * packages ({@code package-info.java}).
 */
public final class HelpAnnotationHandler implements AnnotationHandler {

    static final String PAGE = "org.webbeans.help.HelpPage";
    static final String PAGES = "org.webbeans.help.HelpPages";
    static final String FOLDER = "Help/Pages";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new LinkedHashSet<String>(Arrays.asList(PAGE, PAGES));
    }

    @Override
    public void handle(Element element, AnnotationMirror a, RegistrationModel model, ProcessingEnvironment env) {
        if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.PACKAGE && element.getKind() != ElementKind.INTERFACE) {
            model.error(element, "@HelpPage is only allowed on classes and packages");
            return;
        }
        if (a.getAnnotationType().toString().equals(PAGES)) {
            for (AnnotationMirror inner : model.annotations(a, "value")) {
                page(element, inner, model);
            }
        } else {
            page(element, a, model);
        }
    }

    private void page(Element origin, AnnotationMirror a, RegistrationModel model) {
        String id = model.string(a, "id");
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            model.error(origin, "@HelpPage.id must be an identifier (letters, digits, '.', '_', '-')");
            return;
        }
        String title = model.string(a, "title");
        if (title == null || title.isEmpty()) {
            model.error(origin, "@HelpPage.title must not be empty");
            return;
        }
        String resource = model.string(a, "resource");
        if (resource == null || resource.isEmpty()) {
            model.error(origin, "@HelpPage.resource must name a class-path resource (.md or .html)");
            return;
        }
        RegistrationModel.EntrySpec spec = model.instance(FOLDER + "/" + id, origin)
                .valueExpression("\"" + id + "\"")
                .instanceOf("java.lang.String")
                .attr("displayName", title)
                .attr("resource", resource)
                .position(model.integer(a, "position", Integer.MAX_VALUE))
                .roles(model.strings(a, "roles"));
        String parent = model.string(a, "parent");
        if (parent != null && !parent.isEmpty()) {
            spec.attr("parent", parent);
        }
        String keywords = model.string(a, "keywords");
        if (keywords != null && !keywords.isEmpty()) {
            spec.attr("keywords", keywords);
        }
    }
}
