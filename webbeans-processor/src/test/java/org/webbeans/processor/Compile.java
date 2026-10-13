package org.webbeans.processor;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Test helper: compiles sources with the WebBeans processor into a temp directory. */
final class Compile {

    final Path out;
    final Path src;
    final boolean ok;
    final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    final String log;

    private Compile(Path out, Path src, boolean ok, List<Diagnostic<? extends JavaFileObject>> diagnostics, String log) {
        this.out = out;
        this.src = src;
        this.ok = ok;
        this.diagnostics = diagnostics;
        this.log = log;
    }

    /** @param sources alternating fully-qualified-name, source-code (or "res:path", content for resources) */
    static Compile run(List<String> options, String... sources) throws IOException {
        return run(true, true, options, sources);
    }

    /**
     * @param sourcePath    pass {@code -sourcepath} (Maven does; NetBeans' Ant projects and Gradle do not)
     * @param copyResources copy the resources into the class output before compiling (Maven) — otherwise they stay
     *                      in the source tree only, as in a build that copies them afterwards
     */
    static Compile run(boolean sourcePath, boolean copyResources, List<String> options, String... sources) throws IOException {
        Path root = Files.createTempDirectory("wb-proc");
        Path src = root.resolve("src");
        Path out = root.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        List<JavaFileObject> units = new ArrayList<JavaFileObject>();
        for (int i = 0; i + 1 < sources.length; i += 2) {
            String name = sources[i];
            String code = sources[i + 1];
            if (name.startsWith("res:")) {
                // like Maven: resources are both on the source path and copied to the class output
                for (Path base : copyResources ? new Path[] {src, out} : new Path[] {src}) {
                    Path f = base.resolve(name.substring(4));
                    Files.createDirectories(f.getParent());
                    Files.write(f, code.getBytes(StandardCharsets.ISO_8859_1));
                }
                continue;
            }
            Path f = src.resolve(name.replace('.', '/') + ".java");
            Files.createDirectories(f.getParent());
            Files.write(f, code.getBytes(StandardCharsets.UTF_8));
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fm = javac.getStandardFileManager(diags, null, StandardCharsets.UTF_8);
        List<File> files = new ArrayList<File>();
        Files.walk(src).filter(p -> p.toString().endsWith(".java")).forEach(p -> files.add(p.toFile()));
        List<String> opts = new ArrayList<String>(Arrays.asList(
                "-d", out.toString(),
                "-cp", System.getProperty("java.class.path"),
                "-processorpath", System.getProperty("java.class.path"),
                "-processor", WbProcessor.class.getName(),
                "-implicit:class",
                "-Xlint:-options"));
        if (sourcePath) {
            opts.addAll(Arrays.asList("-sourcepath", src.toString()));
        }
        // Java 8 output from whichever JDK runs the tests: javac 8 has no --release flag
        if (System.getProperty("java.specification.version").startsWith("1.")) {
            opts.addAll(Arrays.asList("-source", "8", "-target", "8"));
        } else {
            opts.addAll(Arrays.asList("--release", "8"));
        }
        opts.addAll(options);
        StringWriter log = new StringWriter();
        Boolean result = javac.getTask(log, fm, diags, opts, null, fm.getJavaFileObjectsFromFiles(files)).call();
        fm.close();
        StringBuilder sb = new StringBuilder(log.toString());
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            sb.append(d.getKind()).append(": ").append(d.getMessage(null)).append('\n');
        }
        return new Compile(out, src, Boolean.TRUE.equals(result), diags.getDiagnostics(), sb.toString());
    }

    String read(String relativePath) throws IOException {
        Path p = out.resolve(relativePath);
        return Files.exists(p) ? new String(Files.readAllBytes(p), StandardCharsets.UTF_8) : null;
    }

    /** The generated source (javac keeps it in the output dir only with -s; we re-read it from the processor's filer location). */
    boolean exists(String relativePath) {
        return Files.exists(out.resolve(relativePath));
    }

    ClassLoader loader() throws IOException {
        return new URLClassLoader(new URL[] {out.toUri().toURL()}, Compile.class.getClassLoader());
    }

    List<String> errors() {
        List<String> errors = new ArrayList<String>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(d.getMessage(null));
            }
        }
        return errors;
    }

    static Map<String, String> properties(String content) throws IOException {
        java.util.Properties p = new java.util.Properties();
        p.load(new java.io.StringReader(content));
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String k : new java.util.TreeSet<String>(p.stringPropertyNames())) {
            m.put(k, p.getProperty(k));
        }
        return m;
    }

    static URI uri(String name) {
        return URI.create("string:///" + name.replace('.', '/') + ".java");
    }

    static JavaFileObject source(String name, final String code) {
        return new SimpleJavaFileObject(uri(name), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignore) {
                return code;
            }
        };
    }
}
