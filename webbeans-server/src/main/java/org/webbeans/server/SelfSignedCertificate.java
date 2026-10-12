package org.webbeans.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a self-signed server certificate with the JDK's {@code keytool} — for development and tests, where a
 * real certificate is not at hand. The launcher uses it when {@code --https-port} is given without a key store;
 * browsers will warn about the certificate, as they should.
 */
public final class SelfSignedCertificate {

    private static final Logger LOG = Logger.getLogger(SelfSignedCertificate.class.getName());

    private SelfSignedCertificate() {
    }

    /**
     * Makes sure a PKCS12 key store with a self-signed certificate for {@code localhost} and {@code 127.0.0.1} exists
     * at {@code keyStore} (created when missing; an existing file is kept) and returns it.
     */
    public static Path ensure(Path keyStore, String password) throws IOException {
        if (Files.isRegularFile(keyStore)) {
            return keyStore;
        }
        generate(keyStore, password, "localhost", new String[] {"dns:localhost", "ip:127.0.0.1"}, 3650);
        return keyStore;
    }

    /** Generates a PKCS12 key store with a self-signed RSA certificate (alias {@code webbeans}). */
    public static void generate(Path keyStore, String password, String commonName, String[] subjectAltNames, int validityDays)
            throws IOException {
        Path parent = keyStore.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> cmd = new ArrayList<String>();
        cmd.add(keytool());
        cmd.add("-genkeypair");
        cmd.add("-alias"); cmd.add("webbeans");
        cmd.add("-keyalg"); cmd.add("RSA");
        cmd.add("-keysize"); cmd.add("2048");
        cmd.add("-sigalg"); cmd.add("SHA256withRSA");
        cmd.add("-validity"); cmd.add(String.valueOf(validityDays));
        cmd.add("-dname"); cmd.add("CN=" + commonName + ", O=WebBeans");
        if (subjectAltNames != null && subjectAltNames.length > 0) {
            StringBuilder san = new StringBuilder("SAN=");
            for (int i = 0; i < subjectAltNames.length; i++) {
                san.append(i == 0 ? "" : ",").append(subjectAltNames[i]);
            }
            cmd.add("-ext"); cmd.add(san.toString());
        }
        cmd.add("-storetype"); cmd.add("PKCS12");
        cmd.add("-keystore"); cmd.add(keyStore.toAbsolutePath().toString());
        cmd.add("-storepass"); cmd.add(password);
        cmd.add("-keypass"); cmd.add(password);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] output;
        try {
            output = readAll(p);
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroy();
                throw new IOException("keytool did not finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running keytool", e);
        }
        if (p.exitValue() != 0) {
            throw new IOException("keytool failed (" + p.exitValue() + "): " + new String(output, StandardCharsets.UTF_8).trim());
        }
        LOG.log(Level.INFO, "Generated a self-signed certificate for {0} in {1}", new Object[] {commonName, keyStore});
    }

    private static byte[] readAll(Process p) throws IOException {
        java.io.InputStream in = p.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** The {@code keytool} of the running JVM (a JRE has it too). */
    private static String keytool() {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        File f = new File(home, "bin" + File.separator + (windows ? "keytool.exe" : "keytool"));
        return f.isFile() ? f.getAbsolutePath() : "keytool";
    }
}
