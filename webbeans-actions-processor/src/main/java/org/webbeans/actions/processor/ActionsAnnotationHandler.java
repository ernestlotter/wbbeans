package org.webbeans.actions.processor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * Registry entries for the action annotations:
 * <ul>
 * <li>{@code @ActionID(category, id)} + {@code @ActionRegistration(...)} → {@code Actions/<category>/<id>}: an
 * {@code Action} class is registered with its no-arg constructor; an {@code ActionListener} class becomes an
 * entry with the factory {@code Actions::fromEntry} and attributes describing it ({@code delegate},
 * {@code type}/{@code selectionType} from the constructor, {@code key} for callbacks, flags);</li>
 * <li>{@code @ActionReference(path, position, separatorBefore/After, name)} → a reference entry under the path
 * ({@code Shortcuts/<name>} for shortcuts) plus separator entries.</li>
 * </ul>
 */
public final class ActionsAnnotationHandler implements AnnotationHandler {

    static final String ACTION_ID = "org.webbeans.actions.ActionID";
    static final String ACTION_REG = "org.webbeans.actions.ActionRegistration";
    static final String ACTION_REF = "org.webbeans.actions.ActionReference";
    static final String ACTION_REFS = "org.webbeans.actions.ActionReferences";
    static final String ACTION = "org.webbeans.actions.Action";
    static final String LISTENER = "org.webbeans.actions.ActionListener";
    static final String FACTORY = "org.webbeans.actions.Actions::fromEntry";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new LinkedHashSet<String>(Arrays.asList(ACTION_ID, ACTION_REG, ACTION_REF, ACTION_REFS));
    }

    @Override
    public void handle(Element element, AnnotationMirror annotation, RegistrationModel model, ProcessingEnvironment env) {
        String type = annotation.getAnnotationType().toString();
        if (element.getKind() != ElementKind.CLASS) {
            model.error(element, "@" + type.substring(type.lastIndexOf('.') + 1) + " is only allowed on classes");
            return;
        }
        TypeElement cls = (TypeElement) element;
        AnnotationMirror id = find(cls, ACTION_ID);
        if (id == null) {
            model.error(cls, "@ActionID is required next to @" + type.substring(type.lastIndexOf('.') + 1));
            return;
        }
        String path = actionPath(model, id, cls);
        if (path == null) {
            return;
        }
        if (type.equals(ACTION_ID)) {
            if (find(cls, ACTION_REG) == null) {
                model.error(cls, "@ActionID needs an @ActionRegistration");
            }
        } else if (type.equals(ACTION_REG)) {
            registration(cls, annotation, path, model, env);
        } else if (type.equals(ACTION_REF)) {
            reference(cls, annotation, path, model);
        } else if (type.equals(ACTION_REFS)) {
            for (AnnotationMirror inner : model.annotations(annotation, "value")) {
                reference(cls, inner, path, model);
            }
        }
    }

    private static AnnotationMirror find(TypeElement cls, String annotationType) {
        for (AnnotationMirror m : cls.getAnnotationMirrors()) {
            if (m.getAnnotationType().toString().equals(annotationType)) {
                return m;
            }
        }
        return null;
    }

    private static String actionPath(RegistrationModel model, AnnotationMirror id, TypeElement cls) {
        String category = model.string(id, "category");
        String name = model.string(id, "id");
        if (category == null || category.trim().isEmpty() || category.contains("/")) {
            model.error(cls, "@ActionID.category must be a simple folder name");
            return null;
        }
        if (name == null || name.trim().isEmpty()) {
            model.error(cls, "@ActionID.id must not be empty");
            return null;
        }
        return "Actions/" + category.trim() + "/" + name.trim().replace('.', '-');
    }

    private void registration(TypeElement cls, AnnotationMirror a, String path, RegistrationModel model, ProcessingEnvironment env) {
        String dn = model.string(a, "displayName");
        if (dn == null || dn.isEmpty()) {
            model.error(cls, "@ActionRegistration.displayName must not be empty");
            return;
        }
        if (!cls.getModifiers().contains(Modifier.PUBLIC)) {
            model.error(cls, "A registered action class must be public");
            return;
        }
        TypeElement actionType = env.getElementUtils().getTypeElement(ACTION);
        TypeElement listenerType = env.getElementUtils().getTypeElement(LISTENER);
        boolean isAction = actionType != null && env.getTypeUtils().isSubtype(cls.asType(), actionType.asType());
        boolean isListener = listenerType != null && env.getTypeUtils().isSubtype(cls.asType(), listenerType.asType());
        if (!isAction && !isListener) {
            model.error(cls, "A registered action must implement org.webbeans.actions.Action or org.webbeans.actions.ActionListener");
            return;
        }
        String key = model.string(a, "key");
        boolean isAbstract = cls.getModifiers().contains(Modifier.ABSTRACT);
        RegistrationModel.EntrySpec spec = model.instance(path, cls)
                .instanceOf(ACTION)
                .attr("displayName", dn)
                .roles(model.strings(a, "roles"));
        String icon = model.string(a, "iconBase");
        if (icon != null && !icon.isEmpty()) {
            spec.attr("iconBase", icon);
        }
        for (String flag : new String[] {"asynchronous", "surviveFocusChange", "noIconInMenu"}) {
            if (model.bool(a, flag)) {
                spec.attr(flag, true);
            }
        }
        for (String text : new String[] {"menuText", "popupText"}) {
            String v = model.string(a, text);
            if (v != null && !v.isEmpty()) {
                spec.attr(text, v);
            }
        }
        if (isAction && (key == null || key.isEmpty())) {
            if (isAbstract) {
                model.error(cls, "An Action class must be concrete to be registered");
                return;
            }
            spec.supplierOf(cls).instanceOf(ACTION);
            return;
        }
        spec.factoryExpression(FACTORY, false);
        if (key != null && !key.isEmpty()) {
            spec.attr("kind", "callback").attr("key", key);
            if (!isAbstract) {
                if (!hasNoArgConstructor(cls)) {
                    model.error(cls, "The fallback of a callback action needs a public no-argument constructor");
                    return;
                }
                spec.attr("delegate", env.getElementUtils().getBinaryName(cls).toString());
            }
            return;
        }
        if (isAbstract) {
            model.error(cls, "An ActionListener class must be concrete to be registered");
            return;
        }
        spec.attr("delegate", env.getElementUtils().getBinaryName(cls).toString());
        // the constructor decides the kind
        ExecutableElement ctor = null;
        int count = 0;
        for (Element e : cls.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR && e.getModifiers().contains(Modifier.PUBLIC)) {
                ctor = (ExecutableElement) e;
                count++;
            }
        }
        if (ctor == null) {
            model.error(cls, "A registered ActionListener needs a public constructor");
            return;
        }
        if (count > 1) {
            model.error(cls, "A registered ActionListener must have exactly one public constructor");
            return;
        }
        List<? extends javax.lang.model.element.VariableElement> params = ctor.getParameters();
        if (params.isEmpty()) {
            return; // always enabled
        }
        if (params.size() != 1) {
            model.error(cls, "The constructor of a registered ActionListener takes no argument, one context object or a List of them");
            return;
        }
        TypeMirror p = params.get(0).asType();
        if (p.getKind() != TypeKind.DECLARED) {
            model.error(cls, "The context parameter must be a class or interface");
            return;
        }
        DeclaredType dt = (DeclaredType) p;
        String erased = env.getTypeUtils().erasure(p).toString();
        if (erased.equals("java.util.List") || erased.equals("java.util.Collection")) {
            if (dt.getTypeArguments().size() != 1 || dt.getTypeArguments().get(0).getKind() != TypeKind.DECLARED) {
                model.error(cls, "The List parameter needs a concrete type argument (List<Planet>)");
                return;
            }
            spec.attr("type", binaryName(env, env.getTypeUtils().erasure(dt.getTypeArguments().get(0)))).attr("selectionType", "ANY");
        } else {
            spec.attr("type", binaryName(env, env.getTypeUtils().erasure(p))).attr("selectionType", "EXACTLY_ONE");
        }
    }

    /** The JVM name of a declared type ({@code a.b.Outer$Inner}), as {@code Class.forName} wants it. */
    private static String binaryName(ProcessingEnvironment env, javax.lang.model.type.TypeMirror t) {
        if (t instanceof DeclaredType && ((DeclaredType) t).asElement() instanceof TypeElement) {
            return env.getElementUtils().getBinaryName((TypeElement) ((DeclaredType) t).asElement()).toString();
        }
        return t.toString();
    }

    private static boolean hasNoArgConstructor(TypeElement cls) {
        boolean any = false;
        for (Element e : cls.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                any = true;
                ExecutableElement c = (ExecutableElement) e;
                if (c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC)) {
                    return true;
                }
            }
        }
        return !any;
    }

    private void reference(TypeElement cls, AnnotationMirror a, String actionPath, RegistrationModel model) {
        String path = model.string(a, "path");
        if (path == null || path.trim().isEmpty()) {
            model.error(cls, "@ActionReference.path must not be empty");
            return;
        }
        path = path.trim();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String name = model.string(a, "name");
        int slash = actionPath.lastIndexOf('/');
        String entryName = name != null && !name.isEmpty() ? name : actionPath.substring(slash + 1);
        if (path.equals("Shortcuts") || path.startsWith("Shortcuts/")) {
            if (name == null || name.isEmpty()) {
                model.error(cls, "A Shortcuts reference needs the key in 'name' (e.g. D-S)");
                return;
            }
            model.reference(path + "/" + name, actionPath, cls);
            return;
        }
        int position = model.integer(a, "position", Integer.MAX_VALUE);
        model.reference(path + "/" + entryName, actionPath, cls).position(position);
        int before = model.integer(a, "separatorBefore", -1);
        int after = model.integer(a, "separatorAfter", -1);
        if (before >= 0) {
            model.separator(path, before, cls);
        }
        if (after >= 0) {
            model.separator(path, after, cls);
        }
    }
}
