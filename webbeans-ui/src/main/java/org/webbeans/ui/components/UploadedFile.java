package org.webbeans.ui.components;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A file the browser sent to an {@link Upload}: streamed to a temporary file by the server, handed to the
 * {@link Upload.UploadEvent} listeners on the session executor, and deleted when they return unless one of them
 * {@link #retain() retains} it (or {@link #moveTo moves} it somewhere).
 */
public final class UploadedFile {

    private final String name;
    private final long size;
    private final String contentType;
    private final Path path;
    private boolean retained;
    private boolean deleted;

    public UploadedFile(String name, long size, String contentType, Path path) {
        this.name = name;
        this.size = size;
        this.contentType = contentType;
        this.path = path;
    }

    /** The file name as the browser reported it (no directory; characters outside the safe set replaced). */
    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    /** The temporary file; valid until the event handlers return unless retained. */
    public Path getPath() {
        return path;
    }

    public InputStream open() throws IOException {
        return Files.newInputStream(path);
    }

    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    /** Keeps the temporary file after the handlers return; the application deletes it. */
    public UploadedFile retain() {
        retained = true;
        return this;
    }

    public boolean isRetained() {
        return retained;
    }

    /** Moves the file to its final place (creating parent directories); implies {@link #retain()}. */
    public void moveTo(Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        retained = true;
        deleted = true;
    }

    public void delete() {
        if (!deleted) {
            deleted = true;
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // best effort: the upload directory is swept on shutdown
            }
        }
    }

    @Override
    public String toString() {
        return name + " (" + size + " bytes, " + contentType + ")";
    }
}
