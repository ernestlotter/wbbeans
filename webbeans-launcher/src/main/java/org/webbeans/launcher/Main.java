package org.webbeans.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.auth.providers.PropertiesAuthenticationProvider;
import org.webbeans.core.Platform;
import org.webbeans.server.SelfSignedCertificate;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.WbServer;

/**
 * Starts the platform and the HTTP server.
 *
 * <pre>
 * java -cp 'lib/*' org.webbeans.launcher.Main [--port 8080] [--host 0.0.0.0] [--datadir data]
 *      [--context /app] [--title "My App"] [--locale de] [--session-timeout 30m] [--session-store DIR] [--node-id NAME] [--dev] [--admin-password x]
 *      [--no-websocket] [--http-threads 32] [--max-upload 256m]
 *      [--https-port 8443 [--keystore server.p12 --keystore-password x [--key-password y] [--keystore-type PKCS12]]
 *       [--https-redirect] [--no-http] [--no-hsts]]
 * </pre>
 *
 * The application is whatever is on the class path: every jar's {@code META-INF/services} (registrations,
 * services, installers) takes part; there is nothing to declare.
 *
 * HTTPS: {@code --https-port} serves TLS from the given key store (PKCS12 or JKS). Without {@code --keystore} a
 * self-signed certificate for {@code localhost} is generated into {@code <datadir>/system/selfsigned.p12} on the
 * first start (development only; browsers warn about it). {@code --https-redirect} keeps the HTTP port open only
 * to redirect to HTTPS, {@code --no-http} closes it. The key store password may also come from the
 * {@code WEBBEANS_KEYSTORE_PASSWORD} environment variable.
 *
 * On the first start with no {@code users.properties} an {@code admin} account is created; its password is taken
 * from {@code --admin-password} or the {@code WEBBEANS_ADMIN_PASSWORD} environment variable, otherwise generated
 * and printed once.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = new ServerConfig();
        Path dataDir = Paths.get("data");
        String adminPassword = System.getenv("WEBBEANS_ADMIN_PASSWORD");
        boolean noDefaultAdmin = false;
        String branding = null;
        int httpsPort = -1;
        Path keyStore = null;
        String keyStorePassword = System.getenv("WEBBEANS_KEYSTORE_PASSWORD");
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String v = i + 1 < args.length ? args[i + 1] : null;
            if (a.equals("--port")) {
                config.port(Integer.parseInt(require(a, v)));
                i++;
            } else if (a.equals("--host")) {
                config.host(require(a, v));
                i++;
            } else if (a.equals("--datadir")) {
                dataDir = Paths.get(require(a, v));
                i++;
            } else if (a.equals("--context")) {
                config.contextPath(require(a, v));
                i++;
            } else if (a.equals("--title")) {
                config.applicationTitle(require(a, v));
                i++;
            } else if (a.equals("--locale")) {
                Locale.setDefault(Locale.forLanguageTag(require(a, v)));
                i++;
            } else if (a.equals("--session-timeout")) {
                config.sessionIdleTimeout(parseDuration(require(a, v)));
                i++;
            } else if (a.equals("--session-store")) {
                config.sessionStore(new org.webbeans.server.FileSessionStore(java.nio.file.Paths.get(require(a, v))));
                i++;
            } else if (a.equals("--node-id")) {
                config.nodeId(require(a, v));
                i++;
            } else if (a.equals("--admin-password")) {
                adminPassword = require(a, v);
                i++;
            } else if (a.equals("--no-default-admin")) {
                noDefaultAdmin = true;
            } else if (a.equals("--branding")) {
                branding = require(a, v);
                i++;
            } else if (a.equals("--dev")) {
                config.devMode(true);
            } else if (a.equals("--secure-cookies")) {
                config.secureCookies(true);
            } else if (a.equals("--trusted-proxy")) {
                config.trustedProxy(require(a, v));
                i++;
            } else if (a.equals("--no-websocket")) {
                config.webSocketEnabled(false);   // long polling only (a proxy that cannot upgrade)
            } else if (a.equals("--http-threads")) {
                config.httpThreads(Integer.parseInt(require(a, v)));
                i++;
            } else if (a.equals("--max-upload")) {
                config.maxUploadSize(parseSize(require(a, v)));   // 10m, 2g, 512k, or bytes
                i++;
            } else if (a.equals("--https-port")) {
                httpsPort = Integer.parseInt(require(a, v));
                i++;
            } else if (a.equals("--keystore")) {
                keyStore = Paths.get(require(a, v));
                i++;
            } else if (a.equals("--keystore-password")) {
                keyStorePassword = require(a, v);
                i++;
            } else if (a.equals("--key-password")) {
                config.keyManagerPassword(require(a, v));
                i++;
            } else if (a.equals("--keystore-type")) {
                config.keyStoreType(require(a, v));
                i++;
            } else if (a.equals("--https-redirect")) {
                config.httpsRedirect(true);
            } else if (a.equals("--no-http")) {
                config.httpEnabled(false);
            } else if (a.equals("--no-hsts")) {
                config.hsts(false);
            } else if (a.equals("--help") || a.equals("-h")) {
                System.out.println("Options: --port N --host H --datadir DIR --context /path --title T --locale xx"
                        + " --session-timeout 30m --session-store DIR --node-id NAME --admin-password PW --no-default-admin --branding NAME --dev --secure-cookies --trusted-proxy IP"
                        + " --no-websocket --http-threads N --max-upload 256m"
                        + " --https-port N --keystore FILE --keystore-password PW --key-password PW --keystore-type PKCS12|JKS"
                        + " --https-redirect --no-http --no-hsts");
                return;
            } else {
                throw new IllegalArgumentException("Unknown option " + a);
            }
        }

        if (keyStore != null && httpsPort < 0) {
            throw new IllegalArgumentException("--keystore needs --https-port");
        }
        if (httpsPort >= 0) {
            if (keyStore == null) {
                // development convenience: a self-signed certificate in the data directory
                if (keyStorePassword == null) {
                    keyStorePassword = "webbeans";
                }
                keyStore = SelfSignedCertificate.ensure(dataDir.resolve("system").resolve("selfsigned.p12"), keyStorePassword);
                LOG.log(Level.WARNING, "HTTPS with a self-signed certificate ({0}); use --keystore for a real one", keyStore);
            } else if (!Files.isRegularFile(keyStore)) {
                throw new IllegalArgumentException("Key store not found: " + keyStore);
            } else if (keyStorePassword == null) {
                throw new IllegalArgumentException("--keystore needs --keystore-password (or WEBBEANS_KEYSTORE_PASSWORD)");
            }
            config.https(httpsPort, keyStore, keyStorePassword);
        } else if (!config.isHttpEnabled() || config.isHttpsRedirect()) {
            throw new IllegalArgumentException("--no-http and --https-redirect need --https-port");
        }

        if (branding != null && !branding.isEmpty()) {
            org.webbeans.core.i18n.Bundles.setBranding(branding);   // Bundle_<branding>.properties overlays every bundle
        }
        final Platform platform = Platform.start(new Platform.Config().dataDir(dataDir));
        if (!noDefaultAdmin) {
            ensureAdminAccount(platform, adminPassword);
        }

        final WbServer server = new WbServer(config);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread("wb-shutdown") {
            @Override
            public void run() {
                server.stop();
                platform.shutdown(true);
            }
        });
        String url = server.getHttpsPort() >= 0 && (!config.isHttpEnabled() || config.isHttpsRedirect())
                ? "https://localhost:" + server.getHttpsPort()
                : server.getPort() >= 0 ? "http://localhost:" + server.getPort() : "https://localhost:" + server.getHttpsPort();
        LOG.log(Level.INFO, "Ready: {0}{1}/", new Object[] {url, config.getContextPath()});
        Thread.currentThread().join();
    }

    /** {@code 512k}, {@code 10m}, {@code 2g} or plain bytes. */
    static long parseSize(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        long mult = 1;
        if (t.endsWith("k")) {
            mult = 1024L;
        } else if (t.endsWith("m")) {
            mult = 1024L * 1024;
        } else if (t.endsWith("g")) {
            mult = 1024L * 1024 * 1024;
        }
        if (mult > 1) {
            t = t.substring(0, t.length() - 1);
        }
        return Long.parseLong(t.trim()) * mult;
    }

    private static String require(String option, String value) {
        if (value == null) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return value;
    }

    static Duration parseDuration(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
        }
        char unit = s.charAt(s.length() - 1);
        long n = Long.parseLong(Character.isDigit(unit) ? s : s.substring(0, s.length() - 1));
        switch (unit) {
            case 's': return Duration.ofSeconds(n);
            case 'h': return Duration.ofHours(n);
            case 'd': return Duration.ofDays(n);
            default: return Duration.ofMinutes(n);
        }
    }

    static void ensureAdminAccount(Platform platform, String password) throws IOException {
        Path users = platform.getDataDir().getSystemDir().resolve(PropertiesAuthenticationProvider.FILE_NAME);
        if (Files.exists(users)) {
            return;
        }
        boolean generated = password == null || password.isEmpty();
        if (generated) {
            password = randomPassword();
        }
        PropertiesAuthenticationProvider.putAccount(users, "admin", password.toCharArray(), "Administrator", "admin", "user");
        if (generated) {
            System.out.println("==============================================================");
            System.out.println(" Created account 'admin' with password: " + password);
            System.out.println(" (" + users + ")");
            System.out.println("==============================================================");
        } else {
            LOG.log(Level.INFO, "Created account ''admin'' in {0}", users);
        }
    }

    private static String randomPassword() {
        String alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
