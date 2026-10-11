package org.webbeans.processor.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/**
 * Accumulates registrations during a compilation; the processor turns it into {@code WbRegistrations.java}.
 * Handlers add entries through the fluent {@link EntrySpec}; values are kept as Java source expressions.
 */
public final class RegistrationModel {

    public enum Kind { INSTANCE, REFERENCE, SEPARATOR, FOLDER }

    /** One registration. All string arguments are plain values; the model handles quoting. */
    public final class EntrySpec {
        final Kind kind;
        final String path;
        final String target;
        final Element origin;
        String factoryExpression;         // Java expression of type Function<Entry,?> or Supplier<?> (see supplier flag)
        boolean supplier;
        String instanceOf;                // fully qualified type name
        String bundle;
        int position = Integer.MAX_VALUE;
        final Map<String, String> attrs = new LinkedHashMap<String, String>();   // name -> Java literal expression
        final Set<String> roles = new LinkedHashSet<String>();

        EntrySpec(Kind kind, String path, String target, Element origin) {
            this.kind = kind;
            this.path = path;
            this.target = target;
            this.origin = origin;
        }

        /** Instantiate the given class with its public no-arg constructor; reports an error if that is impossible. */
        public EntrySpec supplierOf(TypeElement type) {
            if (!checkInstantiable(type)) {
                return this;
            }
            this.factoryExpression = type.getQualifiedName() + "::new";
            this.supplier = true;
            if (instanceOf == null) {
                instanceOf = type.getQualifiedName().toString();
            }
            return this;
        }

        /** Call the given public static method: no parameters, or one {@code Entry} parameter. */
        public EntrySpec factoryMethod(ExecutableElement method) {
            TypeElement owner = (TypeElement) method.getEnclosingElement();
            if (!method.getModifiers().contains(Modifier.STATIC) || !method.getModifiers().contains(Modifier.PUBLIC)
                    || !owner.getModifiers().contains(Modifier.PUBLIC) || method.getReturnType().getKind() == TypeKind.VOID) {
                error(method, "A factory method must be public static and return a value");
                return this;
            }
            int params = method.getParameters().size();
            if (params == 0) {
                this.factoryExpression = owner.getQualifiedName() + "::" + method.getSimpleName();
                this.supplier = true;
            } else if (params == 1 && method.getParameters().get(0).asType().toString().equals("org.webbeans.core.registry.Entry")) {
                this.factoryExpression = owner.getQualifiedName() + "::" + method.getSimpleName();
                this.supplier = false;
            } else {
                error(method, "A factory method takes no parameters or one org.webbeans.core.registry.Entry");
                return this;
            }
            if (instanceOf == null && method.getReturnType().getKind() == TypeKind.DECLARED) {
                instanceOf = erasure(method.getReturnType().toString());
            }
            return this;
        }

        /** A raw Java expression of type {@code java.util.function.Supplier<?>} (true) or {@code Function<Entry,?>} (false). */
        public EntrySpec factoryExpression(String expression, boolean isSupplier) {
            this.factoryExpression = expression;
            this.supplier = isSupplier;
            return this;
        }

        /** A Java expression evaluating to the instance itself (a class literal, a constant...). */
        public EntrySpec valueExpression(String expression) {
            this.factoryExpression = "() -> " + expression;
            this.supplier = true;
            return this;
        }

        public EntrySpec instanceOf(String fqcn) {
            this.instanceOf = fqcn;
            return this;
        }

        public EntrySpec instanceOf(TypeElement type) {
            return instanceOf(type.getQualifiedName().toString());
        }

        public EntrySpec attr(String name, String value) {
            attrs.put(name, Literals.string(value));
            return this;
        }

        public EntrySpec attr(String name, int value) {
            attrs.put(name, "Integer.valueOf(" + value + ")");
            return this;
        }

        public EntrySpec attr(String name, boolean value) {
            attrs.put(name, "Boolean." + (value ? "TRUE" : "FALSE"));
            return this;
        }

        public EntrySpec attr(String name, String[] values) {
            attrs.put(name, Literals.stringArray(values));
            return this;
        }

        /** A class literal attribute. */
        public EntrySpec attrClass(String name, String fqcn) {
            attrs.put(name, fqcn + ".class");
            return this;
        }

        /** Any Java expression as the attribute value. */
        public EntrySpec attrExpression(String name, String expression) {
            attrs.put(name, expression);
            return this;
        }

        /**
         * Parses a {@code key=value} list as used by {@code @Registration.attrs}: {@code true}/{@code false} become
         * booleans, integers become {@code Integer}, everything else a string.
         */
        public EntrySpec attrs(String[] keyValues) {
            for (String kv : keyValues) {
                attr(kv, null, null);
            }
            return this;
        }

        /** The same for the array attribute {@code name} of {@code annotation}; errors point at the bad entry. */
        public EntrySpec attrs(AnnotationMirror annotation, String name) {
            for (AnnotationValue v : values(annotation, name)) {
                attr(String.valueOf(v.getValue()), annotation, v);
            }
            return this;
        }

        private void attr(String kv, AnnotationMirror annotation, AnnotationValue at) {
            int eq = kv.indexOf('=');
            if (eq <= 0) {
                error(origin, annotation, at, "Attribute must be key=value: '" + kv + "'");
                return;
            }
            String key = kv.substring(0, eq).trim();
            String value = kv.substring(eq + 1);
            if (value.equals("true") || value.equals("false")) {
                attr(key, Boolean.parseBoolean(value));
            } else if (value.matches("-?\\d{1,9}")) {
                attr(key, Integer.parseInt(value));
            } else {
                attr(key, value);
            }
        }

        /** Base name of the bundle for {@code #KEY} attributes; defaults to {@code <package of origin>.Bundle}. */
        public EntrySpec bundle(String baseName) {
            this.bundle = baseName;
            return this;
        }

        public EntrySpec position(int position) {
            this.position = position;
            return this;
        }

        public EntrySpec roles(String... roles) {
            for (String r : roles) {
                this.roles.add(r);
            }
            return this;
        }

        public String getPath() {
            return path;
        }

        public Kind getKind() {
            return kind;
        }

        public String getTarget() {
            return target;
        }

        public Element getOrigin() {
            return origin;
        }

        public String getFactoryExpression() {
            return factoryExpression;
        }

        public boolean isSupplier() {
            return supplier;
        }

        public String getInstanceOf() {
            return instanceOf;
        }

        public String getBundle() {
            return bundle;
        }

        public int getPosition() {
            return position;
        }

        /** Attribute name → Java expression. */
        public Map<String, String> getAttrs() {
            return attrs;
        }

        public Set<String> getRoles() {
            return roles;
        }

        private boolean checkInstantiable(TypeElement type) {
            if (type.getKind() != ElementKind.CLASS || type.getModifiers().contains(Modifier.ABSTRACT)) {
                error(type, "Must be a concrete class");
                return false;
            }
            if (!type.getModifiers().contains(Modifier.PUBLIC)) {
                error(type, "Must be public to be registered");
                return false;
            }
            if (type.getNestingKind() != NestingKind.TOP_LEVEL && !type.getModifiers().contains(Modifier.STATIC)) {
                error(type, "A nested class must be static to be registered");
                return false;
            }
            boolean hasCtor = false;
            boolean ok = false;
            for (Element e : type.getEnclosedElements()) {
                if (e.getKind() == ElementKind.CONSTRUCTOR) {
                    hasCtor = true;
                    ExecutableElement c = (ExecutableElement) e;
                    if (c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC)) {
                        ok = true;
                    }
                }
            }
            if (hasCtor && !ok) {
                error(type, "Needs a public no-argument constructor to be registered");
                return false;
            }
            return true;
        }
    }

    private final Messager messager;
    private final Elements elements;
    private final List<EntrySpec> entries = new ArrayList<EntrySpec>();
    private final Set<String> hidden = new LinkedHashSet<String>();
    private final Set<Element> origins = new LinkedHashSet<Element>();
    private final Map<String, Map<String, String>> messages = new LinkedHashMap<String, Map<String, String>>();
    private boolean errors;

    public RegistrationModel(Messager messager, Elements elements) {
        this.messager = messager;
        this.elements = elements;
    }

    public EntrySpec instance(String path, Element origin) {
        return add(new EntrySpec(Kind.INSTANCE, normalize(path), null, origin));
    }

    public EntrySpec reference(String path, String targetPath, Element origin) {
        return add(new EntrySpec(Kind.REFERENCE, normalize(path), normalize(targetPath), origin));
    }

    /** A separator in the folder; the name is derived from the origin and position so it is stable across builds. */
    public EntrySpec separator(String folderPath, int position, Element origin) {
        String name = "separator-" + Literals.identifier(originName(origin)) + "-" + position;
        String folder = normalize(folderPath);
        return add(new EntrySpec(Kind.SEPARATOR, folder.isEmpty() ? name : folder + "/" + name, null, origin)).position(position);
    }

    public EntrySpec folder(String path, Element origin) {
        return add(new EntrySpec(Kind.FOLDER, normalize(path), null, origin));
    }

    public void hide(String path, Element origin) {
        hidden.add(normalize(path));
        origins.add(origin);
    }

    /** Adds a message key for the package of the origin element (merged into {@code Bundle.properties}). */
    public void message(String packageName, String key, String value, Element origin) {
        Map<String, String> pkg = messages.get(packageName);
        if (pkg == null) {
            pkg = new LinkedHashMap<String, String>();
            messages.put(packageName, pkg);
        }
        if (pkg.containsKey(key) && !pkg.get(key).equals(value)) {
            error(origin, "Duplicate message key " + key + " in package " + packageName + " with a different value");
        }
        pkg.put(key, value);
        origins.add(origin);
    }

    public void error(Element element, String message) {
        errors = true;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    /**
     * Reports an error at one value inside an annotation (the offending string of an array attribute, say).
     * Besides pointing the IDE at the exact spot, this keeps several errors on one element apart: javac 8
     * reports only the first error per source position, so errors all placed on the element would hide each
     * other.
     */
    public void error(Element element, AnnotationMirror annotation, AnnotationValue value, String message) {
        errors = true;
        if (annotation == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, message, element);
        } else if (value == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation, value);
        }
    }

    public void warning(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    public boolean hasErrors() {
        return errors;
    }

    // ---- helpers for handlers ------------------------------------------------------------------------------

    /** The package of an element. */
    public String packageOf(Element element) {
        return elements.getPackageOf(element).getQualifiedName().toString();
    }

    /** {@code org.example.Foo} → {@code org-example-Foo}; the NetBeans entry naming convention. */
    public static String entryName(TypeElement type) {
        return type.getQualifiedName().toString().replace('.', '-').replace('$', '-');
    }

    /** The value of an annotation attribute by name, including defaults, or null. */
    public AnnotationValue value(AnnotationMirror annotation, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
                : elements.getElementValuesWithDefaults(annotation).entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public String string(AnnotationMirror annotation, String name) {
        AnnotationValue v = value(annotation, name);
        return v == null ? null : String.valueOf(v.getValue());
    }

    public int integer(AnnotationMirror annotation, String name, int defaultValue) {
        AnnotationValue v = value(annotation, name);
        return v == null ? defaultValue : ((Number) v.getValue()).intValue();
    }

    public boolean bool(AnnotationMirror annotation, String name) {
        AnnotationValue v = value(annotation, name);
        return v != null && Boolean.TRUE.equals(v.getValue());
    }

    public String[] strings(AnnotationMirror annotation, String name) {
        AnnotationValue v = value(annotation, name);
        if (v == null) {
            return new String[0];
        }
        List<?> list = (List<?>) v.getValue();
        String[] out = new String[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = String.valueOf(((AnnotationValue) list.get(i)).getValue());
        }
        return out;
    }

    /** The elements of an array-valued attribute as annotation values (for precise error positions). */
    public List<AnnotationValue> values(AnnotationMirror annotation, String name) {
        AnnotationValue v = value(annotation, name);
        List<AnnotationValue> out = new ArrayList<AnnotationValue>();
        if (v != null) {
            for (Object o : (List<?>) v.getValue()) {
                out.add((AnnotationValue) o);
            }
        }
        return out;
    }

    public List<AnnotationMirror> annotations(AnnotationMirror annotation, String name) {
        AnnotationValue v = value(annotation, name);
        List<AnnotationMirror> out = new ArrayList<AnnotationMirror>();
        if (v != null) {
            for (Object o : (List<?>) v.getValue()) {
                out.add((AnnotationMirror) ((AnnotationValue) o).getValue());
            }
        }
        return out;
    }

    /** A class-valued attribute as a fully qualified name, or null if it is the given default. */
    public String type(AnnotationMirror annotation, String name, String defaultFqcn) {
        AnnotationValue v = value(annotation, name);
        if (v == null) {
            return null;
        }
        String s = erasure(String.valueOf(v.getValue()));
        return s.equals(defaultFqcn) ? null : s;
    }

    // ---- accessors for the writers ---------------------------------------------------------------------------

    public List<EntrySpec> getEntries() {
        return entries;
    }

    public Set<String> getHidden() {
        return hidden;
    }

    /** Every element that contributed something; passed as originating elements of generated files. */
    public Set<Element> getOrigins() {
        return origins;
    }

    /** Package → (key → value). */
    public Map<String, Map<String, String>> getMessages() {
        return messages;
    }

    private EntrySpec add(EntrySpec e) {
        for (EntrySpec existing : entries) {
            if (existing.path.equals(e.path) && existing.kind != Kind.FOLDER && e.kind != Kind.FOLDER) {
                error(e.origin, "Registry path '" + e.path + "' is already registered by "
                        + (existing.origin != null ? existing.origin : "another registration"));
            }
        }
        entries.add(e);
        if (e.origin != null) {
            origins.add(e.origin);
            if (e.bundle == null) {
                e.bundle = packageOf(e.origin) + ".Bundle";
            }
        }
        return e;
    }

    private static String originName(Element origin) {
        if (origin == null) {
            return "x";
        }
        if (origin instanceof TypeElement) {
            return ((TypeElement) origin).getQualifiedName().toString();
        }
        if (origin instanceof PackageElement) {
            return ((PackageElement) origin).getQualifiedName().toString();
        }
        return originName(origin.getEnclosingElement()) + "." + origin.getSimpleName();
    }

    static String erasure(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type : type.substring(0, lt);
    }

    static String normalize(String path) {
        String p = path.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
