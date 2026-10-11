package org.webbeans.windows.processor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * Registry entries for the window system annotations:
 * <ul>
 * <li>{@code @Mode.Registration(name, side, ...)} → {@code Windows/Modes/<name>} with the constraints as attributes</li>
 * <li>{@code @TopComponent.Registration(mode, openAtStartup, position, roles)} + {@code @TopComponent.Description}
 * → {@code Windows/Components/<preferredID>} (factory, attrs {@code iconBase}, {@code persistenceType}) and the
 * reference {@code Windows/Modes/<mode>/<preferredID>} (attrs {@code openAtStartup}, position, roles)</li>
 * <li>{@code @TopComponent.Description} alone → the component entry only (for components opened by code)</li>
 * <li>{@code @TopComponent.OpenActionRegistration(displayName)} → {@code Actions/Window/<id>} (attrs
 * {@code displayName}, {@code topComponent}) and the reference {@code Menu/Window/<id>}; the actions module
 * (step 5) turns the entry into an action</li>
 * <li>{@code @TopComponentGroup.Registration(name, topComponents)} → {@code Windows/Groups/<name>}</li>
 * </ul>
 * Lives on the processor path with the module; works on mirrors by name, so it has no dependency on the API.
 */
public final class WindowsAnnotationHandler implements AnnotationHandler {

    static final String MODE_REG = "org.webbeans.windows.Mode.Registration";
    static final String MODE_REGS = "org.webbeans.windows.Mode.Registrations";
    static final String TC_REG = "org.webbeans.windows.TopComponent.Registration";
    static final String TC_DESC = "org.webbeans.windows.TopComponent.Description";
    static final String TC_OPEN = "org.webbeans.windows.TopComponent.OpenActionRegistration";
    static final String GROUP_REG = "org.webbeans.windows.TopComponentGroup.Registration";
    static final String LAYOUT_REG = "org.webbeans.windows.LayoutRegistration";
    static final String LAYOUT_REGS = "org.webbeans.windows.LayoutRegistration.Registrations";
    static final String LAYOUTS = "Windows/Layouts";
    static final String MODES = "Windows/Modes";
    static final String COMPONENTS = "Windows/Components";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new LinkedHashSet<String>(Arrays.asList(MODE_REG, MODE_REGS, TC_REG, TC_DESC, TC_OPEN, GROUP_REG, LAYOUT_REG, LAYOUT_REGS));
    }

    @Override
    public void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env) {
        String type = annotation.getAnnotationType().toString();
        if (element.getKind() != ElementKind.CLASS) {
            model.error(element, "@" + type.substring(type.lastIndexOf('.') + 1) + " is only allowed on classes");
            return;
        }
        TypeElement cls = (TypeElement) element;
        if (type.equals(MODE_REGS)) {
            for (AnnotationMirror inner : model.annotations(annotation, "value")) {
                mode(cls, inner, model);
            }
        } else if (type.equals(MODE_REG)) {
            mode(cls, annotation, model);
        } else if (type.equals(TC_REG)) {
            component(cls, annotation, find(cls, TC_DESC), model, env);
        } else if (type.equals(TC_DESC)) {
            if (find(cls, TC_REG) == null) {
                component(cls, null, annotation, model, env);
            }
        } else if (type.equals(TC_OPEN)) {
            openAction(cls, annotation, model);
        } else if (type.equals(GROUP_REG)) {
            group(cls, annotation, model);
        } else if (type.equals(LAYOUT_REGS)) {
            for (AnnotationMirror inner : model.annotations(annotation, "value")) {
                layout(cls, inner, model);
            }
        } else if (type.equals(LAYOUT_REG)) {
            layout(cls, annotation, model);
        }
    }

    private void layout(TypeElement cls, AnnotationMirror a, RegistrationModel model) {
        String role = model.string(a, "role");
        if (role == null || role.isEmpty()) {
            role = "default";
        }
        String resource = model.string(a, "resource");
        if (resource == null || resource.isEmpty()) {
            model.error(cls, "@LayoutRegistration.resource must name a class-path resource");
            return;
        }
        model.instance(LAYOUTS + "/" + role, cls)
                .valueExpression("\"" + resource + "\"")
                .instanceOf("java.lang.String")
                .attr("resource", resource);
    }

    private static AnnotationMirror find(TypeElement cls, String annotationType) {
        for (AnnotationMirror m : cls.getAnnotationMirrors()) {
            if (m.getAnnotationType().toString().equals(annotationType)) {
                return m;
            }
        }
        return null;
    }

    private void mode(TypeElement cls, AnnotationMirror a, RegistrationModel model) {
        String name = model.string(a, "name");
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            model.error(cls, "@Mode.Registration.name must be an identifier");
            return;
        }
        String side = model.string(a, "side");
        RegistrationModel.EntrySpec spec = model.instance(MODES + "/" + name, cls)
                .valueExpression("\"" + name + "\"")
                .instanceOf("java.lang.String")
                .attr("side", side == null ? "CENTER" : side)
                .attr("defaultSize", model.integer(a, "defaultSize", 250))
                .attr("closeable", model.bool(a, "closeable"))
                .attr("floatable", model.bool(a, "floatable"))
                .attr("maximizable", model.bool(a, "maximizable"))
                .attr("permanent", model.bool(a, "permanent"))
                .position(model.integer(a, "position", Integer.MAX_VALUE));
        String dn = model.string(a, "displayName");
        if (dn != null && !dn.isEmpty()) {
            spec.attr("displayName", dn);
        }
    }

    private void component(TypeElement cls, AnnotationMirror reg, AnnotationMirror desc, RegistrationModel model, ProcessingEnvironment env) {
        if (desc == null) {
            model.error(cls, "@TopComponent.Registration needs a @TopComponent.Description with the preferredID");
            return;
        }
        String id = model.string(desc, "preferredID");
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            model.error(cls, "@TopComponent.Description.preferredID must be an identifier (no '#')");
            return;
        }
        TypeElement tcType = env.getElementUtils().getTypeElement("org.webbeans.windows.TopComponent");
        if (tcType != null && !env.getTypeUtils().isSubtype(cls.asType(), tcType.asType())) {
            model.error(cls, "Only subclasses of TopComponent can be registered as components");
            return;
        }
        RegistrationModel.EntrySpec spec = model.instance(COMPONENTS + "/" + id, cls)
                .instanceOf("org.webbeans.windows.TopComponent")
                .attr("persistenceType", model.integer(desc, "persistenceType", 0));
        if (reg != null || hasNoArgConstructor(cls)) {
            spec.supplierOf(cls);          // singleton: the window manager creates it on demand
        } else {
            spec.attr("multiInstance", true); // created by the application (editors); the entry only describes it
        }
        String icon = model.string(desc, "iconBase");
        if (icon != null && !icon.isEmpty()) {
            spec.attr("iconBase", icon);
        }
        String factory = model.type(desc, "factory", "org.webbeans.windows.TopComponent.Factory");
        if (factory != null) {
            spec.attrClass("factory", factory);
        }
        if (reg != null) {
            String mode = model.string(reg, "mode");
            if (mode == null || mode.isEmpty()) {
                model.error(cls, "@TopComponent.Registration.mode must not be empty");
                return;
            }
            String[] roles = model.strings(reg, "roles");
            spec.roles(roles);
            model.reference(MODES + "/" + mode + "/" + id, spec.getPath(), cls)
                    .attr("openAtStartup", model.bool(reg, "openAtStartup"))
                    .position(model.integer(reg, "position", Integer.MAX_VALUE))
                    .roles(roles);
        }
    }

    private static boolean hasNoArgConstructor(TypeElement cls) {
        boolean any = false;
        for (Element e : cls.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                any = true;
                if (((javax.lang.model.element.ExecutableElement) e).getParameters().isEmpty()) {
                    return true;
                }
            }
        }
        return !any;
    }

    private void openAction(TypeElement cls, AnnotationMirror a, RegistrationModel model) {
        String id = model.string(a, "preferredID");
        if (id == null || id.isEmpty()) {
            AnnotationMirror desc = find(cls, TC_DESC);
            id = desc == null ? null : model.string(desc, "preferredID");
        }
        if (id == null || id.isEmpty()) {
            model.error(cls, "@TopComponent.OpenActionRegistration needs a preferredID (here or in @TopComponent.Description)");
            return;
        }
        String dn = model.string(a, "displayName");
        if (dn == null || dn.isEmpty()) {
            model.error(cls, "@TopComponent.OpenActionRegistration.displayName must not be empty");
            return;
        }
        AnnotationMirror reg = find(cls, TC_REG);
        String[] roles = reg == null ? new String[0] : model.strings(reg, "roles");   // the window's roles also gate its open action
        RegistrationModel.EntrySpec spec = model.instance("Actions/Window/" + id, cls)
                .valueExpression("\"" + id + "\"")
                .instanceOf("java.lang.String")
                .attr("displayName", dn)
                .attr("topComponent", id)
                .attr("kind", "openTopComponent")
                .position(model.integer(a, "position", Integer.MAX_VALUE))
                .roles(roles);
        model.reference("Menu/Window/" + id, spec.getPath(), cls).position(model.integer(a, "position", Integer.MAX_VALUE)).roles(roles);
    }

    private void group(TypeElement cls, AnnotationMirror a, RegistrationModel model) {
        String name = model.string(a, "name");
        if (name == null || name.isEmpty()) {
            model.error(cls, "@TopComponentGroup.Registration.name must not be empty");
            return;
        }
        model.instance("Windows/Groups/" + name, cls)
                .valueExpression("\"" + name + "\"")
                .instanceOf("java.lang.String")
                .attr("topComponents", model.strings(a, "topComponents"));
    }
}
