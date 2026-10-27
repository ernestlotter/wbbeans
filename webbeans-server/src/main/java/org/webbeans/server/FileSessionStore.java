package org.webbeans.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.auth.spi.SessionStore;
import org.webbeans.core.json.Json;

/**
 * The example {@link SessionStore}: one JSON file per session in a directory every instance can reach (NFS,
 * SMB, a shared volume). Writes are atomic (temp file + move), reads tolerate a file vanishing underneath, and
 * expired files are pruned opportunistically. Good for a handful of instances and thousands of sessions; a
 * database or Redis store is the same interface with a different back end.
 *
 * <p>Enable with {@code --session-store DIR} (launcher) or {@code new ServerConfig().sessionStore(new FileSessionStore(dir))}.
 * The directory should be private to the server user: the files hold the session ids that authenticate browsers.
 */
public final class FileSessionStore implements SessionStore {

    private static final Logger LOG = Logger.getLogger(FileSessionStore.class.getName());

    private final Path dir;
    private volatile long lastPrune;

    public FileSessionStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
    }

    public Path getDirectory() {
        return dir;
    }

    private Path file(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{8,80}")) {
            throw new IllegalArgumentException("Bad session id");
        }
        return dir.resolve(id + ".json");
    }

    @Override
    public void put(StoredSession s) throws IOException {
        write(file(s.getId()), s);
        prune();
    }

    private void write(Path f, StoredSession s) throws IOException {
        Path tmp = Files.createTempFile(dir, "session", ".part");
        try {
            Files.write(tmp, Json.write(s.toJson()).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public StoredSession get(String id) throws IOException {
        Path f;
        try {
            f = file(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
        StoredSession s = read(f);
        if (s != null && s.isExpired()) {
            Files.deleteIfExists(f);
            return null;
        }
        return s;
    }

    private static StoredSession read(Path f) throws IOException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f);
        } catch (NoSuchFileException e) {
            return null;
        }
        try {
            return StoredSession.fromJson(Json.parseObject(new String(bytes, StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Unreadable session file " + f.getFileName() + ": " + e);
            return null;
        }
    }

    @Override
    public void touch(String id, Instant lastActivity, Instant expiresAt) throws IOException {
        Path f = file(id);
        StoredSession s = read(f);
        if (s != null) {
            write(f, s.touched(lastActivity, expiresAt));
        }
    }

    @Override
    public void remove(String id) throws IOException {
        Files.deleteIfExists(file(id));
    }

    @Override
    public Collection<StoredSession> list() throws IOException {
        List<StoredSession> out = new ArrayList<StoredSession>();
        DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json");
        try {
            for (Path f : ds) {
                StoredSession s = read(f);
                if (s != null && !s.isExpired()) {
                    out.add(s);
                }
            }
        } finally {
            ds.close();
        }
        return out;
    }

    /** Deletes expired files, at most once a minute. */
    private void prune() {
        long now = System.currentTimeMillis();
        if (now - lastPrune < 60000) {
            return;
        }
        lastPrune = now;
        try {
            DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json");
            try {
                for (Path f : ds) {
                    StoredSession s = read(f);
                    if (s != null && s.isExpired()) {
                        Files.deleteIfExists(f);
                    }
                }
            } finally {
                ds.close();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Session store prune failed: " + e);
        }
    }
}
