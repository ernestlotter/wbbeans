package org.webbeans.workbench.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.ui.UISession;

/** {@code <datadir>/users/<user>/org.webbeans.workbench.files/workdir}, seeded with a few sample files. */
@ServiceProvider(service = FileService.class)
public final class DefaultFileService implements FileService {

    static final String MODULE_DIR = "org.webbeans.workbench.files";

    @Override
    public Path getWorkDir() throws IOException {
        Path dir = UISession.current().getUserFiles().dir(MODULE_DIR).resolve("workdir");
        if (!Files.isDirectory(dir)) {
            seed(dir);
        }
        return dir;
    }

    static void seed(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src"));
        write(dir.resolve("README.md"), "# Workbench\n\nA WebBeans sample application.\n\n- Files: this explorer and editor\n- Dashboard: live charts\n- 3D Viewer: a turbine you can pick and inspect\n\nEdit this file and press Ctrl+S.\n");
        write(dir.resolve("notes.txt"), "Shopping list\n- coffee\n- beans\n");
        write(dir.resolve("src/Hello.java"), "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, Workbench\");\n    }\n}\n");
        write(dir.resolve("src/data.csv"), "name,value\nalpha,1\nbeta,2\ngamma,3\n");
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void changed(Path directory) {
        FolderChildFactory.refreshDir(directory);
    }
}
