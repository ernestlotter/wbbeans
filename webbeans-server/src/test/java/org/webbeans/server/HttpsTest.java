package org.webbeans.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.impl.DefaultAuthenticator;
import org.webbeans.auth.impl.DefaultLoginPage;
import org.webbeans.auth.providers.PropertiesAuthenticationProvider;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.registry.Registry;

/** The server terminating TLS itself: a generated self-signed certificate, HSTS, Secure cookies, redirect, wss. */
public class HttpsTest {

    private Path dir;
    private WbServer server;
    private String https;
    private String http;
    private final Map<String, String> cookies = new LinkedHashMap<String, String>();

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-https");
        DataDir dataDir = new DataDir(dir);
        Path users = dataDir.getSystemDir().resolve(PropertiesAuthenticationProvider.FILE_NAME);
        PropertiesAuthenticationProvider.putAccount(users, "alice", "pw".toCharArray(), "Alice", "user");
        Registry registry = new Registry();
        registry.rebuild(getClass().getClassLoader());
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(dataDir, registry, new DefaultAuthenticator(),
                new PropertiesAuthenticationProvider(users), new DefaultLoginPage(), new WbServerTest.TestProvider(),
                getClass().getClassLoader()));

        Path keyStore = SelfSignedCertificate.ensure(dir.resolve("selfsigned.p12"), "secret");
        assertTrue(Files.size(keyStore) > 0);
        assertEquals("PKCS12", new ServerConfig().https(0, keyStore, "secret").getKeyStoreType());

        // the redirect needs the HTTPS port up front, so pick a free one rather than 0
        int httpsPort = freePort();
        server = new WbServer(new ServerConfig().host("127.0.0.1").port(0).pollWait(Duration.ofMillis(300))
                .https(httpsPort, keyStore, "secret").httpsRedirect(true));
        server.start();
        assertEquals(httpsPort, server.getHttpsPort());
        https = "https://127.0.0.1:" + server.getHttpsPort();
        http = "http://127.0.0.1:" + server.getPort();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    @Test
    public void httpsServesTheApplicationWithHstsAndSecureCookies() throws Exception {
        Response r = request("GET", https + "/login", null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("name=\"username\""));
        assertEquals("max-age=31536000", r.header("Strict-Transport-Security"));
        List<String> set = r.all("Set-Cookie");
        assertNotNull(set);
        for (String c : set) {
            assertTrue(c, c.contains("Secure"));
        }

        r = request("POST", https + "/login", "csrf=" + cookies.get("WBCSRF") + "&provider=local&return=%2F&username=alice&password=pw");
        assertEquals(303, r.status);
        r = request("GET", https + "/", null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("window.WB"));
        for (String c : r.all("Set-Cookie") == null ? java.util.Collections.<String>emptyList() : r.all("Set-Cookie")) {
            assertTrue(c, c.contains("Secure"));
        }
    }

    @Test
    public void plainHttpRedirectsToHttps() throws Exception {
        Response r = request("GET", http + "/login?return=%2F", null);
        assertTrue(String.valueOf(r.status), r.status == 301 || r.status == 302);
        assertEquals(https + "/login?return=%2F", r.header("Location"));
        assertTrue("no HSTS on plain responses", r.header("Strict-Transport-Security") == null);
    }

    @Test
    public void webSocketOverTls() throws Exception {
        request("GET", https + "/login", null);
        request("POST", https + "/login", "csrf=" + cookies.get("WBCSRF") + "&provider=local&return=%2F&username=alice&password=pw");
        Response r = request("GET", https + "/", null);
        Matcher m = Pattern.compile("window\\.WB = (\\{.*?\\});").matcher(r.body);
        assertTrue(m.find());
        Map<String, Object> cfg = Json.parseObject(m.group(1));
        String uiId = Json.string(cfg, "uiId");
        String token = Json.string(cfg, "token");

        SslContextFactory.Client ssl = new SslContextFactory.Client(true);   // trust the self-signed certificate
        org.eclipse.jetty.client.HttpClient httpClient = new org.eclipse.jetty.client.HttpClient(ssl);
        WebSocketClient client = new WebSocketClient(httpClient);
        httpClient.start();
        client.start();
        try {
            final java.util.concurrent.BlockingQueue<String> inbox = new java.util.concurrent.LinkedBlockingQueue<String>();
            WebSocketAdapter socket = new WebSocketAdapter() {
                @Override
                public void onWebSocketText(String message) {
                    inbox.add(message);
                }
            };
            ClientUpgradeRequest req = new ClientUpgradeRequest();
            req.setHeader("Cookie", "WBSESSION=" + cookies.get("WBSESSION"));
            Session session = client.connect(socket, new java.net.URI(https.replace("https://", "wss://") + "/wb/ws?ui=" + uiId + "&token=" + token), req)
                    .get(5, TimeUnit.SECONDS);
            assertTrue(session.isOpen());
            assertTrue(session.isSecure());
            session.getRemote().sendString("{\"t\":\"hello\",\"lastSeq\":-1}");
            String first = inbox.poll(5, TimeUnit.SECONDS);
            assertNotNull(first);
            assertTrue(first, first.contains("\"init\""));
            session.close();
        } finally {
            client.stop();
            httpClient.stop();
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static int freePort() throws IOException {
        ServerSocket s = new ServerSocket(0);
        try {
            return s.getLocalPort();
        } finally {
            s.close();
        }
    }

    private static final class Response {
        int status;
        String body;
        Map<String, List<String>> headers;

        String header(String name) {
            List<String> l = all(name);
            return l == null || l.isEmpty() ? null : l.get(0);
        }

        List<String> all(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }

    private Response request(String method, String url, String form) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c instanceof HttpsURLConnection) {
            HttpsURLConnection sc = (HttpsURLConnection) c;
            sc.setSSLSocketFactory(trustAll().getSocketFactory());
            sc.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        if (!cookies.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : cookies.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            c.setRequestProperty("Cookie", sb.toString());
        }
        if (form != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream out = c.getOutputStream();
            out.write(form.getBytes(StandardCharsets.UTF_8));
            out.close();
        }
        Response r = new Response();
        r.status = c.getResponseCode();
        r.headers = c.getHeaderFields();
        List<String> setCookies = r.all("Set-Cookie");
        if (setCookies != null) {
            for (String sc : setCookies) {
                String pair = sc.split(";")[0];
                int eq = pair.indexOf('=');
                String name = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                if (value.isEmpty() || sc.contains("Max-Age=0")) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
        }
        InputStream in = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
            in.close();
        }
        r.body = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        return r;
    }

    private static SSLContext trustAll() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        return ctx;
    }
}
