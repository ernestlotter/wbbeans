package org.webbeans.core.modules;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Test helper: builds module descriptors and module jars (with compiled classes) on the fly. */
public final class TestModules {

    private TestModules() {
    }

    /** A descriptor without a jar, for resolver tests. Attributes alternate name/value after the version. */
    public static ModuleInfo info(String codeName, String version, String... attrs) {
        return ModuleInfo.fromManifest(manifest(codeName, version, attrs), null);
    }

    public static Manifest manifest(String codeName, String version, String... attrs) {
        Manifest mf = new Manifest();
        Attributes a = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.putValue(ModuleInfo.ATTR_MODULE, codeName);
        a.putValue(ModuleInfo.ATTR_VERSION, version);
        for (int i = 0; i + 1 < attrs.length; i += 2) {
            a.putValue(attrs[i], attrs[i + 1]);
        }
        return mf;
    }

    /** Builder for a module jar with Java sources compiled into it. */
    public static final class Jar {
        private final String codeName;
        private final String version;
        private final List<String> attrs = new ArrayList<String>();
        private final Map<String, String> sources = new LinkedHashMap<String, String>();
        private final Map<String, byte[]> resources = new LinkedHashMap<String, byte[]>();
        private final List<File> compileClasspath = new ArrayList<File>();

        public Jar(String codeName, String version) {
            this.codeName = codeName;
            this.version = version;
        }

        public Jar attr(String name, String value) {
            attrs.add(name);
            attrs.add(value);
            return this;
        }

        public Jar dependsOn(String deps) {
            return attr(ModuleInfo.ATTR_DEPENDENCIES, deps);
        }

        public Jar exports(String packages) {
            return attr(ModuleInfo.ATTR_PUBLIC_PACKAGES, packages);
        }

        public Jar install(String fqcn) {
            return attr(ModuleInfo.ATTR_INSTALL, fqcn);
        }

        /** @param fqcn fully qualified class name; the source must declare the matching package */
        public Jar source(String fqcn, String code) {
            sources.put(fqcn, code);
            return this;
        }

        public Jar resource(String path, String content) {
            resources.put(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return this;
        }

        /** Jars (e.g. other modules) needed to compile the sources. */
        public Jar compileAgainst(File... jars) {
            compileClasspath.addAll(Arrays.asList(jars));
            return this;
        }

        public File build(File dir) throws IOException {
            Map<String, byte[]> classes = compile();
            File jar = new File(dir, codeName + "-" + version + ".jar");
            JarOutputStream out = new JarOutputStream(new FileOutputStream(jar),
                    manifest(codeName, version, attrs.toArray(new String[0])));
            try {
                for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                    out.putNextEntry(new JarEntry(e.getKey()));
                    out.write(e.getValue());
                    out.closeEntry();
                }
                for (Map.Entry<String, byte[]> e : resources.entrySet()) {
                    out.putNextEntry(new JarEntry(e.getKey()));
                    out.write(e.getValue());
                    out.closeEntry();
                }
            } finally {
                out.close();
            }
            return jar;
        }

        private Map<String, byte[]> compile() throws IOException {
            Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
            if (sources.isEmpty()) {
                return result;
            }
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            final Path tmp = Files.createTempDirectory("wbtest-classes");
            List<JavaFileObject> units = new ArrayList<JavaFileObject>();
            for (Map.Entry<String, String> e : sources.entrySet()) {
                final String code = e.getValue();
                units.add(new SimpleJavaFileObject(URI.create("string:///" + e.getKey().replace('.', '/') + ".java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return code;
                    }
                });
            }
            StringBuilder cp = new StringBuilder(System.getProperty("java.class.path"));
            for (File f : compileClasspath) {
                cp.append(File.pathSeparator).append(f.getPath());
            }
            List<String> options = Arrays.asList("-d", tmp.toString(), "-cp", cp.toString(), "-proc:none", "-nowarn");
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            Boolean ok = javac.getTask(new java.io.PrintWriter(err, true), null, null, options, null, units).call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IOException("Compilation failed:\n" + err);
            }
            Files.walkFileTree(tmp, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String rel = tmp.relativize(file).toString().replace(File.separatorChar, '/');
                    result.put(rel, Files.readAllBytes(file));
                    return FileVisitResult.CONTINUE;
                }
            });
            deleteRecursively(tmp.toFile());
            return result;
        }
    }

    public static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }

    public static void write(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }
}
