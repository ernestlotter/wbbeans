package org.webbeans.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Module jars embedded in an application jar ("fat jar"): {@code WEBBEANS-INF/modules.list} names them, the jars
 * sit next to it under {@code WEBBEANS-INF/modules/}. They are extracted into {@code <datadir>/cache/modules/}
 * (replaced when their size or timestamp changed) and that directory is used as the module directory.
 *
 * <pre>
 * workbench.jar
 *   META-INF/MANIFEST.MF            Main-Class: org.webbeans.launcher.Main
 *   org/webbeans/...                the platform
 *   WEBBEANS-INF/modules.list       workbench-files.jar\nworkbench-dashboard.jar ...
 *   WEBBEANS-INF/modules/*.jar
 * </pre>
 */
final class EmbeddedModules {

    private static final Logger LOG = Logger.getLogger(EmbeddedModules.class.getName());
    static final String LIST = "WEBBEANS-INF/modules.list";
    static final String DIR = "WEBBEANS-INF/modules/";

    private EmbeddedModules() {
    }

    /** Extracts the embedded modules, if any; returns the directory or null when the jar has none. */
    static File extract(Path dataDir) throws IOException {
        ClassLoader l = EmbeddedModules.class.getClassLoader();
        URL list = l.getResource(LIST);
        if (list == null) {
            return null;
        }
        List<String> names = new ArrayList<String>();
        InputStream in = list.openStream();
        try {
            for (String line : new String(readAll(in), StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    names.add(line);
                }
            }
        } finally {
            in.close();
        }
        Path dir = dataDir.resolve("cache").resolve("modules");
        Files.createDirectories(dir);
        for (String name : names) {
            URL res = l.getResource(DIR + name);
            if (res == null) {
                LOG.log(Level.WARNING, "Embedded module {0} listed but missing", name);
                continue;
            }
            InputStream src = res.openStream();
            try {
                byte[] bytes = readAll(src);
                Path target = dir.resolve(name);
                if (!Files.exists(target) || Files.size(target) != bytes.length || !java.util.Arrays.equals(Files.readAllBytes(target), bytes)) {
                    Path tmp = dir.resolve(name + ".tmp");
                    Files.write(tmp, bytes);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    LOG.log(Level.INFO, "Extracted embedded module {0}", name);
                }
            } finally {
                src.close();
            }
        }
        // modules that are no longer embedded are removed from the cache
        File[] present = dir.toFile().listFiles();
        if (present != null) {
            for (File f : present) {
                if (f.getName().endsWith(".jar") && !names.contains(f.getName())) {
                    if (!f.delete()) {
                        LOG.log(Level.WARNING, "Cannot remove stale module {0}", f);
                    }
                }
            }
        }
        return dir.toFile();
    }

    /** Copies the extracted modules into an explicit module directory, so both sets load together. */
    static void link(File embedded, File modulesDir) throws IOException {
        File[] jars = embedded.listFiles();
        if (jars == null) {
            return;
        }
        Files.createDirectories(modulesDir.toPath());
        for (File j : jars) {
            if (j.getName().endsWith(".jar")) {
                Files.copy(j.toPath(), modulesDir.toPath().resolve(j.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
