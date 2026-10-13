package org.webbeans.processor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.webbeans.processor.spi.Literals;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * {@code @Messages} support: collects {@code KEY=value} lines per package, generates the package-private
 * {@code Bundle} class (per round, so user code compiles against it) and writes the keys to the package's
 * {@code Bundle-generated.properties} in the class output at the end.
 *
 * <p>The processor never touches {@code Bundle.properties}: that file stays the developer's, is copied by the
 * build like any resource, and the runtime ({@code org.webbeans.core.i18n.Bundles}) merges the two with the
 * hand-written keys winning. The hand-written file is still looked up here, best effort, to report a key that is
 * declared in both places.
 */
final class MessagesSupport {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    /** Written next to {@code Bundle.properties}; read by {@code org.webbeans.core.i18n.Bundles}. */
    static final String GENERATED_FILE = "Bundle-generated.properties";
    static final String GENERATED_HEADER = "# Generated from @Messages by the WebBeans annotation processor";

    private final ProcessingEnvironment env;
    /** Packages whose Bundle.java has been generated, with the keys it contains. */
    private final Map<String, Set<String>> generated = new LinkedHashMap<String, Set<String>>();
    /** Packages touched in the current round. */
    private final Set<String> pending = new HashSet<String>();
    private final Map<String, List<Element>> originsByPackage = new LinkedHashMap<String, List<Element>>();
    private final Map<String, List<String>> commentsByPackage = new LinkedHashMap<String, List<String>>();
    /** Source directories of the annotated elements, per package (where a hand-written Bundle.properties would be). */
    private final Map<String, Set<File>> sourceDirsByPackage = new LinkedHashMap<String, Set<File>>();

    MessagesSupport(ProcessingEnvironment env) {
        this.env = env;
    }

    void collect(Element element, AnnotationMirror mirror, RegistrationModel model) {
        String pkg = model.packageOf(element);
        for (AnnotationValue at : model.values(mirror, "value")) {
            String line = String.valueOf(at.getValue());
            if (line.startsWith("#")) {
                comments(pkg).add(line);
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                model.error(element, mirror, at, "@Messages line must be KEY=value: '" + line + "'");
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1);
            if (!Literals.isJavaIdentifier(key)) {
                model.error(element, mirror, at, "@Messages key must be a Java identifier: '" + key + "'");
                continue;
            }
            Set<String> done = generated.get(pkg);
            if (done != null && !done.contains(key)) {
                model.error(element, mirror, at, "Bundle for package " + pkg + " was already generated in an earlier round; "
                        + "@Messages keys for one package must all be visible in the same round");
                continue;
            }
            model.message(pkg, key, value, element);
            pending.add(pkg);
            if (!origins(pkg).contains(element)) {
                origins(pkg).add(element);
                recordSourceDir(pkg, element);
            }
        }
    }

    /** Generates {@code Bundle.java} for every package that received keys in this round. */
    void writeBundleClasses(RegistrationModel model) {
        for (String pkg : new ArrayList<String>(pending)) {
            if (generated.containsKey(pkg)) {
                continue;
            }
            Map<String, String> keys = model.getMessages().get(pkg);
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            try {
                writeBundleClass(pkg, keys);
                generated.put(pkg, new HashSet<String>(keys.keySet()));
            } catch (IOException e) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot write Bundle class for " + pkg + ": " + e);
            }
        }
        pending.clear();
    }

    private void writeBundleClass(String pkg, Map<String, String> keys) throws IOException {
        Filer filer = env.getFiler();
        JavaFileObject src = filer.createSourceFile(pkg.isEmpty() ? "Bundle" : pkg + ".Bundle",
                origins(pkg).toArray(new Element[0]));
        Writer w = src.openWriter();
        try {
            if (!pkg.isEmpty()) {
                w.write("package " + pkg + ";\n\n");
            }
            w.write("/** Localised messages of this package, generated from @Messages. Do not edit. */\n");
            w.write("final class Bundle {\n\n    private Bundle() {\n    }\n");
            for (Map.Entry<String, String> e : keys.entrySet()) {
                int params = parameterCount(e.getValue());
                w.write("\n    /** {@code " + e.getKey() + "=" + e.getValue().replace("*/", "* /") + "} */\n");
                w.write("    static String " + e.getKey() + "(");
                StringBuilder args = new StringBuilder();
                for (int i = 0; i < params; i++) {
                    if (i > 0) {
                        w.write(", ");
                    }
                    w.write("Object p" + i);
                    args.append(", p").append(i);
                }
                w.write(") {\n        return org.webbeans.core.i18n.Bundles.getMessage(Bundle.class, "
                        + Literals.string(e.getKey()) + args + ");\n    }\n");
            }
            w.write("}\n");
        } finally {
            w.close();
        }
    }

    static int parameterCount(String value) {
        int max = -1;
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max + 1;
    }

    /** Writes each package's {@code @Messages} keys to {@code Bundle-generated.properties} in the class output. */
    void writeProperties(RegistrationModel model) throws IOException {
        Filer filer = env.getFiler();
        for (Map.Entry<String, Map<String, String>> e : model.getMessages().entrySet()) {
            String pkg = e.getKey();
            Properties existing = findWrittenBundle(filer, pkg);
            for (String key : e.getValue().keySet()) {
                if (existing.containsKey(key)) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Key " + key + " is both in Bundle.properties and in @Messages of package " + pkg
                            + " (the Bundle.properties value would win at run time)",
                            origins(pkg).isEmpty() ? null : origins(pkg).get(0));
                }
            }
            FileObject out = filer.createResource(StandardLocation.CLASS_OUTPUT, pkg, GENERATED_FILE,
                    origins(pkg).toArray(new Element[0]));
            OutputStream os = out.openOutputStream();
            try {
                Writer w = new java.io.OutputStreamWriter(os, "ISO-8859-1");
                w.write(GENERATED_HEADER + "\n");
                w.write("# Base-locale defaults of the @Messages in package " + (pkg.isEmpty() ? "(default)" : pkg)
                        + ". Do not edit: Bundle.properties and Bundle_<locale>.properties override these keys.\n");
                for (String c : comments(pkg)) {
                    w.write(c + "\n");
                }
                for (String key : new java.util.TreeSet<String>(e.getValue().keySet())) {
                    w.write(escape(key, true) + "=" + escape(e.getValue().get(key), false) + "\n");
                }
                w.flush();
            } finally {
                os.close();
            }
        }
    }

    /** {@code java.util.Properties} escaping, without the timestamp comment {@code store} would add (reproducible builds). */
    static String escape(String s, boolean key) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '=': case ':': case '#': case '!':
                    if (key) {
                        sb.append('\\');
                    }
                    sb.append(c);
                    break;
                case ' ':
                    if (key || i == 0) {
                        sb.append('\\');
                    }
                    sb.append(c);
                    break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * The developer's {@code Bundle.properties} of a package, if it can be found: on the source path (Maven, the
     * offline build), next to the source file of an annotated element (any build whose sources are laid out by
     * package, including NetBeans' Ant projects and Gradle, which pass no {@code -sourcepath}), or already copied
     * into the class output. Empty when there is none or the build hides it.
     */
    private Properties findWrittenBundle(Filer filer, String pkg) {
        Properties p = new Properties();
        if (load(p, sourcePathBundle(filer, pkg)) || load(p, sourceDirBundle(pkg))) {
            return p;
        }
        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, pkg, "Bundle.properties");
            String content = f.getCharContent(true).toString();
            if (!content.contains("# Generated from @Messages")) {   // not a stale merged file of an earlier processor
                p.load(new java.io.StringReader(content));
            }
        } catch (IOException e) {
            // not copied before compilation (or not at all): nothing to check against
        } catch (IllegalArgumentException e) {
            // some compilers reject the lookup
        }
        return p;
    }

    private static InputStream sourcePathBundle(Filer filer, String pkg) {
        try {
            return filer.getResource(StandardLocation.SOURCE_PATH, pkg, "Bundle.properties").openInputStream();
        } catch (IOException e) {
            return null;    // no source bundle, or no source path configured
        } catch (IllegalArgumentException e) {
            return null;    // some compilers reject SOURCE_PATH lookups
        }
    }

    /** {@code Bundle.properties} in the directory of an annotated element's source file (recorded while collecting). */
    private InputStream sourceDirBundle(String pkg) {
        Set<File> dirs = sourceDirsByPackage.get(pkg);
        if (dirs != null) {
            for (File dir : dirs) {
                File bundle = new File(dir, "Bundle.properties");
                if (bundle.isFile()) {
                    try {
                        return new FileInputStream(bundle);
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Remembers the source directory of an annotated element, found through javac's
     * {@code com.sun.source.util.Trees} (used reflectively: it is not part of the processor's compile-time API and
     * other compilers do not have it). Must happen in the round that sees the element: javac re-enters the
     * sources every round, so an element of an earlier round has no tree any more.
     */
    private void recordSourceDir(String pkg, Element element) {
        Element top = element;
        while (top.getEnclosingElement() != null && top.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            top = top.getEnclosingElement();
        }
        try {
            // the compiler's own loader sees the API whether or not the processor's does (tests, IDEs)
            ClassLoader javac = env.getClass().getClassLoader();
            Class<?> treesClass = Class.forName("com.sun.source.util.Trees", true, javac);
            Object trees = treesClass.getMethod("instance", ProcessingEnvironment.class).invoke(null, env);
            Object path = treesClass.getMethod("getPath", Element.class).invoke(trees, top);
            if (path == null) {
                return;
            }
            Object unit = Class.forName("com.sun.source.util.TreePath", true, javac).getMethod("getCompilationUnit").invoke(path);
            Object source = Class.forName("com.sun.source.tree.CompilationUnitTree", true, javac).getMethod("getSourceFile").invoke(unit);
            URI uri = (URI) javax.tools.FileObject.class.getMethod("toUri").invoke(source);
            if (!"file".equals(uri.getScheme())) {
                return;
            }
            File dir = new File(uri).getParentFile();
            if (dir != null) {
                Set<File> dirs = sourceDirsByPackage.get(pkg);
                if (dirs == null) {
                    dirs = new java.util.LinkedHashSet<File>();
                    sourceDirsByPackage.put(pkg, dirs);
                }
                dirs.add(dir);
            }
        } catch (ReflectiveOperationException e) {
            // not javac: no Trees API
        } catch (RuntimeException e) {
            // an element without a source, an opaque URI, ...
        }
    }

    /** Loads and closes; false if there was nothing to load. */
    private static boolean load(Properties p, InputStream in) {
        if (in == null) {
            return false;
        }
        try {
            try {
                p.load(in);
            } finally {
                in.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private List<Element> origins(String pkg) {
        List<Element> l = originsByPackage.get(pkg);
        if (l == null) {
            l = new ArrayList<Element>();
            originsByPackage.put(pkg, l);
        }
        return l;
    }

    private List<String> comments(String pkg) {
        List<String> l = commentsByPackage.get(pkg);
        if (l == null) {
            l = new ArrayList<String>();
            commentsByPackage.put(pkg, l);
        }
        return l;
    }
}
