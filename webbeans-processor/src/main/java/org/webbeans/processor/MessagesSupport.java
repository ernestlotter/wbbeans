package org.webbeans.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
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
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.webbeans.processor.spi.Literals;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * {@code @Messages} support: collects {@code KEY=value} lines per package, generates the package-private
 * {@code Bundle} class (per round, so user code compiles against it) and merges the keys into the package's
 * {@code Bundle.properties} in the class output at the end.
 */
final class MessagesSupport {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    private final ProcessingEnvironment env;
    /** Packages whose Bundle.java has been generated, with the keys it contains. */
    private final Map<String, Set<String>> generated = new LinkedHashMap<String, Set<String>>();
    /** Packages touched in the current round. */
    private final Set<String> pending = new HashSet<String>();
    private final Map<String, List<Element>> originsByPackage = new LinkedHashMap<String, List<Element>>();
    private final Map<String, List<String>> commentsByPackage = new LinkedHashMap<String, List<String>>();

    MessagesSupport(ProcessingEnvironment env) {
        this.env = env;
    }

    void collect(Element element, AnnotationMirror mirror, RegistrationModel model) {
        String pkg = model.packageOf(element);
        for (String line : model.strings(mirror, "value")) {
            if (line.startsWith("#")) {
                comments(pkg).add(line);
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                model.error(element, "@Messages line must be KEY=value: '" + line + "'");
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1);
            if (!Literals.isJavaIdentifier(key)) {
                model.error(element, "@Messages key must be a Java identifier: '" + key + "'");
                continue;
            }
            Set<String> done = generated.get(pkg);
            if (done != null && !done.contains(key)) {
                model.error(element, "Bundle for package " + pkg + " was already generated in an earlier round; "
                        + "@Messages keys for one package must all be visible in the same round");
                continue;
            }
            model.message(pkg, key, value, element);
            pending.add(pkg);
            origins(pkg).add(element);
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

    /** Merges the collected keys into each package's {@code Bundle.properties} (source copy + generated keys). */
    void writeProperties(RegistrationModel model) throws IOException {
        Filer filer = env.getFiler();
        for (Map.Entry<String, Map<String, String>> e : model.getMessages().entrySet()) {
            String pkg = e.getKey();
            Properties existing = readSourceBundle(filer, pkg);
            for (String key : e.getValue().keySet()) {
                if (existing.containsKey(key)) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Key " + key + " is both in Bundle.properties and in @Messages of package " + pkg,
                            origins(pkg).isEmpty() ? null : origins(pkg).get(0));
                }
            }
            FileObject out = filer.createResource(StandardLocation.CLASS_OUTPUT, pkg, "Bundle.properties",
                    origins(pkg).toArray(new Element[0]));
            OutputStream os = out.openOutputStream();
            try {
                Writer w = new java.io.OutputStreamWriter(os, "ISO-8859-1");
                if (!existing.isEmpty()) {
                    w.write("# Copied from the source Bundle.properties\n");
                    for (String key : new java.util.TreeSet<String>(existing.stringPropertyNames())) {
                        w.write(escape(key, true) + "=" + escape(existing.getProperty(key), false) + "\n");
                    }
                    w.write("\n");
                }
                w.write("# Generated from @Messages\n");
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

    private static Properties readSourceBundle(Filer filer, String pkg) {
        Properties p = new Properties();
        try {
            FileObject f = filer.getResource(StandardLocation.SOURCE_PATH, pkg, "Bundle.properties");
            InputStream in = f.openInputStream();
            try {
                p.load(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // no source bundle (or no source path configured): nothing to merge
        } catch (IllegalArgumentException e) {
            // some compilers reject SOURCE_PATH lookups
        }
        return p;
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
