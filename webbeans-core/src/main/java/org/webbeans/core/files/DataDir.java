package org.webbeans.core.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * The platform's data directory ({@code --datadir}):
 * <pre>
 * &lt;datadir&gt;/
 *   users/&lt;username&gt;/...     per-user files ({@link UserFiles})
 *   system/...                admin-managed files (accounts, system preferences)
 *   cache/...                 regenerable (extracted module libraries, bundles)
 * </pre>
 */
public final class DataDir {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._@-]{1,128}");

    private final Path root;

    public DataDir(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root.resolve("users"));
        Files.createDirectories(this.root.resolve("system"));
        Files.createDirectories(this.root.resolve("cache"));
    }

    public Path getRoot() {
        return root;
    }

    public Path getSystemDir() {
        return root.resolve("system");
    }

    public Path getCacheDir() {
        return root.resolve("cache");
    }

    /** The files of one user; the directory is created on first use. User names are restricted to a safe charset. */
    public UserFiles userFiles(String userName) throws IOException {
        if (!SAFE_NAME.matcher(userName).matches()) {
            throw new IllegalArgumentException("Unsafe user name");
        }
        Path dir = root.resolve("users").resolve(userName);
        Files.createDirectories(dir);
        return new UserFiles(userName, dir);
    }

    /** Writes a file atomically: to a sibling temp file, then an atomic move over the target. */
    public static void writeAtomic(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("." + file.getFileName() + ".tmp");
        Files.write(tmp, content);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
