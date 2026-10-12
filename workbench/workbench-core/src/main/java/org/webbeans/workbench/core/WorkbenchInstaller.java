package org.webbeans.workbench.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.webbeans.auth.providers.PropertiesAuthenticationProvider;
import org.webbeans.core.Installer;
import org.webbeans.core.files.DataDir;

/**
 * Start-up hook: on the first start creates the two demo accounts ({@code alice}: user + admin,
 * {@code bob}: user, both with password {@code secret}) so the launcher does not need to generate one.
 */
@ServiceProvider(service = Installer.class)
public final class WorkbenchInstaller extends Installer {

    private static final Logger LOG = Logger.getLogger(WorkbenchInstaller.class.getName());

    @Override
    public void restored() {
        DataDir dd = Lookup.getDefault().lookup(DataDir.class);
        if (dd == null) {
            return;
        }
        Path users = dd.getSystemDir().resolve(PropertiesAuthenticationProvider.FILE_NAME);
        if (Files.exists(users)) {
            return;
        }
        try {
            PropertiesAuthenticationProvider.putAccount(users, "alice", "secret".toCharArray(), "Alice Admin", "user", "admin");
            PropertiesAuthenticationProvider.putAccount(users, "bob", "secret".toCharArray(), "Bob User", "user");
            LOG.log(Level.INFO, "Created demo accounts alice (admin) and bob (user), password ''secret'' in {0}", users);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot seed accounts", e);
        }
    }
}
