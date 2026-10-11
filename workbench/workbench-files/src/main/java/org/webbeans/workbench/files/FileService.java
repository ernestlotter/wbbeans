package org.webbeans.workbench.files;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The per-user working directory the Files window shows. Implemented by {@link DefaultFileService} (a global
 * service: the directory comes from the current session's files); applications can replace it with
 * {@code @ServiceProvider(..., position = 50)}.
 */
public interface FileService {

    /** The user's working directory (created and seeded with sample files on first use). */
    Path getWorkDir() throws IOException;

    /** Notifies views that the contents of a directory changed. */
    void changed(Path directory);
}
