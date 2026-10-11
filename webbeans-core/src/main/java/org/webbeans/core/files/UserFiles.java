package org.webbeans.core.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Per-user file storage. Modules get a private directory with {@link #dir(String)}; the platform keeps
 * layouts, preferences and registry customisations in reserved names.
 *
 * <pre>
 * users/&lt;name&gt;/
 *   layout.json                 last window layout
 *   layouts/&lt;name&gt;.json        named layouts
 *   config/registry.properties  menu/toolbar customisation ({@code UserOverlay})
 *   preferences/...             java.util.prefs tree
 *   &lt;module-id&gt;/...             module-private files
 * </pre>
 */
public final class UserFiles {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]{1,200}");

    private final String userName;
    private final Path root;

    UserFiles(String userName, Path root) {
        this.userName = userName;
        this.root = root;
    }

    public String getUserName() {
        return userName;
    }

    public Path getRoot() {
        return root;
    }

    /** A directory owned by the module, created on demand. */
    public Path dir(String moduleId) throws IOException {
        if (!SAFE_ID.matcher(moduleId).matches() || moduleId.startsWith(".")) {
            throw new IllegalArgumentException("Unsafe module id");
        }
        Path dir = root.resolve(moduleId);
        Files.createDirectories(dir);
        return dir;
    }

    public Path configDir() throws IOException {
        return Files.createDirectories(root.resolve("config"));
    }

    public Path preferencesDir() throws IOException {
        return Files.createDirectories(root.resolve("preferences"));
    }

    public Path layoutFile() {
        return root.resolve("layout.json");
    }

    public Path layoutFile(String name) {
        if (!SAFE_ID.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe layout name");
        }
        return root.resolve("layouts").resolve(name + ".json");
    }

    public Path registryOverlayFile() {
        return root.resolve("config").resolve("registry.properties");
    }

    /** Atomic write (temp file + rename). */
    public void write(Path file, byte[] content) throws IOException {
        checkInside(file);
        DataDir.writeAtomic(file, content);
    }

    public void writeString(Path file, String content) throws IOException {
        write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    public String readString(Path file) throws IOException {
        checkInside(file);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private void checkInside(Path file) {
        if (!file.toAbsolutePath().normalize().startsWith(root)) {
            throw new IllegalArgumentException("Not a file of user " + userName + ": " + file);
        }
    }
}
