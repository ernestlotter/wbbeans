package org.webbeans.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Panel;

/**
 * Drives the real Jetty server with the raw protocol: login form, bootstrap page, hello / ev / poll / bye over
 * long polling, the same over a WebSocket, logout.
 */
public class WbServerTest {

    private Path dir;
    private WbServer server;
    private String base;
    private final Map<String, String> cookies = new LinkedHashMap<String, String>();

    /** A tiny UI: a label and a button that changes it. */
    public static final class TestProvider implements UIProvider {
        @Override
        public void init(UI ui) {
            Panel p = new Panel();
            final Label l = new Label("hello");
            l.setTestId("label");
            Button b = new Button("go");
            b.setTestId("button");
            b.addClickListener(new Listener<ClickEvent>() {
                @Override
                public void onEvent(ClickEvent e) {
                    l.setText("clicked");
                }
            });
            p.add(l);
            p.add(b);
            org.webbeans.ui.components.Upload up = new org.webbeans.ui.components.Upload();
            up.addUploadListener(new Listener<org.webbeans.ui.components.Upload.UploadEvent>() {
                @Override
                public void onEvent(org.webbeans.ui.components.Upload.UploadEvent e) {
                    try {
                        l.setText("got " + e.getFile().getName() + ":" + e.getFile().getSize() + ":" + e.getFile().getContentType()
                                + ":" + new String(e.getFile().getBytes(), StandardCharsets.UTF_8));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
            p.add(up);
            Button dl = new Button("download");
            dl.addClickListener(new Listener<ClickEvent>() {
                @Override
                public void onEvent(ClickEvent e) {
                    org.webbeans.ui.DownloadService.offer("col1,col2\n".getBytes(StandardCharsets.UTF_8), "r\u00e9sum\u00e9.csv", "text/csv");
                }
            });
            p.add(dl);
            ui.setContent(p);
        }
    }

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-server");
        DataDir dataDir = new DataDir(dir);
        Path users = dataDir.getSystemDir().resolve(PropertiesAuthenticationProvider.FILE_NAME);
        PropertiesAuthenticationProvider.putAccount(users, "alice", "pw".toCharArray(), "Alice", "user");
        Registry registry = new Registry();
        registry.rebuild(getClass().getClassLoader()); // the renderers of webbeans-ui
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(dataDir, registry, new DefaultAuthenticator(),
                new PropertiesAuthenticationProvider(users), new DefaultLoginPage(), new TestProvider(),
                getClass().getClassLoader()));
        server = new WbServer(new ServerConfig().host("127.0.0.1").port(0).pollWait(Duration.ofMillis(300)));
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    @Test
    public void fullRoundTrip() throws Exception {
        // unauthenticated: redirected to the login page
        Response r = request("GET", "/", null, null);
        assertEquals(303, r.status);
        assertTrue(r.header("Location"), r.header("Location").startsWith("/login?return="));

        r = request("GET", "/login", null, null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("name=\"username\""));
        String csrf = cookies.get("WBCSRF");
        assertNotNull(csrf);

        // wrong password: back on the login page with an error, no session cookie
        r = request("POST", "/login", "application/x-www-form-urlencoded",
                "csrf=" + csrf + "&provider=local&return=%2F&username=alice&password=nope");
        assertEquals(200, r.status);
        assertTrue(r.body.contains("role=\"alert\""));
        assertFalse(cookies.containsKey("WBSESSION"));

        r = request("POST", "/login", "application/x-www-form-urlencoded",
                "csrf=" + csrf + "&provider=local&return=%2F&username=alice&password=pw");
        assertEquals(303, r.status);
        assertEquals("/", r.header("Location"));
        assertNotNull(cookies.get("WBSESSION"));

        r = request("GET", "/auth/me", null, null);
        assertEquals(200, r.status);
        Map<String, Object> me = Json.parseObject(r.body);
        assertEquals("alice", Json.string(me, "name"));

        // bootstrap page creates a UI and carries its id and the token
        r = request("GET", "/", null, null);
        assertEquals(200, r.status);
        Matcher m = Pattern.compile("window\\.WB = (\\{.*?\\});").matcher(r.body);
        assertTrue(m.find());
        Map<String, Object> cfg = Json.parseObject(m.group(1));
        String uiId = Json.string(cfg, "uiId");
        String token = Json.string(cfg, "token");
        Matcher js = Pattern.compile("src=\"(/wb/app-[0-9a-f]+\\.js)\"").matcher(r.body);
        assertTrue(js.find());
        r = request("GET", js.group(1), null, null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("wb.register('wb.Root'"));
        assertTrue(r.body.contains("wb.register('wb.Button'"));

        // the token is required
        r = poll(uiId, null, "{\"t\":\"hello\",\"lastSeq\":-1}");
        assertEquals(403, r.status);

        // hello -> init with the label and the button listening to click
        r = poll(uiId, token, "{\"t\":\"hello\",\"lastSeq\":-1}");
        assertEquals(200, r.status);
        List<Object> messages = Json.array(Json.parseObject(r.body), "messages");
        assertEquals(1, messages.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> init = (Map<String, Object>) messages.get(0);
        assertEquals("init", Json.string(init, "t"));
        assertTrue(r.body.contains("\"text\":\"hello\""));
        int buttonId = -1;
        for (Object o : Json.array(init, "ops")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> op = (Map<String, Object>) o;
            if ("wb.Button".equals(Json.string(op, "type")) && buttonId < 0) {
                buttonId = (int) Json.integer(op, "id", -1);   // the first button is "go"
            }
        }
        assertTrue(buttonId > 0);

        // click -> delta setting the label text
        r = poll(uiId, token, "{\"t\":\"ev\",\"ack\":1,\"events\":[{\"s\":1,\"id\":" + buttonId + ",\"type\":\"click\",\"data\":{}}]}");
        assertEquals(200, r.status);
        assertTrue(r.body, r.body.contains("\"seq\":2") && r.body.contains("\"text\":\"clicked\""));

        // duplicate after a reconnect is ignored; an empty poll times out with no messages
        r = poll(uiId, token, "{\"t\":\"ev\",\"ack\":2,\"events\":[{\"s\":1,\"id\":" + buttonId + ",\"type\":\"click\",\"data\":{}}]}");
        assertEquals("{\"messages\":[]}", r.body);
        r = poll(uiId, token, "{\"t\":\"poll\",\"ack\":2}");
        assertEquals("{\"messages\":[]}", r.body);

        // reconnect with a known seq replays nothing new; with an old seq replays the delta
        r = poll(uiId, token, "{\"t\":\"hello\",\"lastSeq\":2}");
        assertEquals("{\"messages\":[]}", r.body);
        r = poll(uiId, token, "{\"t\":\"hello\",\"lastSeq\":1}");
        assertTrue(r.body, r.body.contains("\"seq\":2"));

        // unknown UI -> gone
        r = poll("nope", token, "{\"t\":\"hello\",\"lastSeq\":-1}");
        assertEquals(410, r.status);

        // bye disposes the UI; a later hello is gone
        r = poll(uiId, token, "{\"t\":\"bye\"}");
        assertEquals(204, r.status);
        Thread.sleep(100);
        r = poll(uiId, token, "{\"t\":\"hello\",\"lastSeq\":-1}");
        assertEquals(410, r.status);

        // logout needs the token, then the session is gone
        r = request("POST", "/logout", "application/x-www-form-urlencoded", "csrf=wrong");
        assertEquals(403, r.status);
        r = request("POST", "/logout", "application/x-www-form-urlencoded", "csrf=" + token);
        assertEquals(303, r.status);
        assertEquals(0, server.getSessions().size());
        r = request("GET", "/auth/me", null, null);
        assertEquals(401, r.status);
    }

    /** Uploads go to {@code /wb/upload} as raw bodies and reach the component on the session thread; downloads stream one-time tokens. */
    @Test
    public void uploadAndDownload() throws Exception {
        login();
        Response r = request("GET", "/", null, null);
        Matcher m = Pattern.compile("window\\.WB = (\\{.*?\\});").matcher(r.body);
        assertTrue(m.find());
        Map<String, Object> cfg = Json.parseObject(m.group(1));
        String uiId = Json.string(cfg, "uiId");
        String token = Json.string(cfg, "token");
        r = poll(uiId, token, "{\"t\":\"hello\",\"lastSeq\":-1}");
        int uploadId = -1, buttonId = -1;
        @SuppressWarnings("unchecked")
        Map<String, Object> init = (Map<String, Object>) Json.array(Json.parseObject(r.body), "messages").get(0);
        for (Object o : Json.array(init, "ops")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> op = (Map<String, Object>) o;
            if ("wb.Upload".equals(Json.string(op, "type"))) {
                uploadId = (int) Json.integer(op, "id", -1);
            }
            if ("wb.Button".equals(Json.string(op, "type")) && "download".equals(Json.object(op, "props").get("text"))) {
                buttonId = (int) Json.integer(op, "id", -1);
            }
        }
        assertTrue(uploadId > 0 && buttonId > 0);

        // no token → 403; then a real upload
        r = request("POST", "/wb/upload?ui=" + uiId + "&c=" + uploadId + "&name=notes.txt&size=5", "text/plain", "hello", null);
        assertEquals(403, r.status);
        r = request("POST", "/wb/upload?ui=" + uiId + "&c=" + uploadId + "&name=..%2F..%2Fnotes.txt&size=5", "text/plain", "hello", token);
        assertEquals(r.body, 200, r.status);
        r = poll(uiId, token, "{\"t\":\"poll\",\"ack\":1}");
        assertTrue(r.body, r.body.contains("\"text\":\"got notes.txt:5:text/plain:hello\""));

        // the button offers a download: the delta carries the one-time url
        r = poll(uiId, token, "{\"t\":\"ev\",\"ack\":2,\"events\":[{\"s\":1,\"id\":" + buttonId + ",\"type\":\"click\",\"data\":{}}]}");
        Matcher u = Pattern.compile("\"download\",\"args\":\\[\"(wb/download/[^\"]+)\"").matcher(r.body);
        assertTrue(r.body, u.find());
        r = request("GET", "/" + u.group(1), null, null);
        assertEquals(200, r.status);
        assertEquals("col1,col2\n", r.body);
        assertTrue(r.header("Content-Disposition"), r.header("Content-Disposition").startsWith("attachment; filename=\"r_sum_.csv\"; filename*=UTF-8''r%C3%A9sum%C3%A9.csv"));
        assertEquals("text/csv", r.header("Content-Type"));
        r = request("GET", "/" + u.group(1), null, null);
        assertEquals("single use", 404, r.status);
    }

    /** The WebSocket transport: upgrade needs the cookie and the token; hello/ev/ack/ping/bye frames; replay on reconnect. */
    @Test
    public void webSocketRoundTrip() throws Exception {
        login();
        Response r = request("GET", "/", null, null);
        Matcher m = Pattern.compile("window\\.WB = (\\{.*?\\});").matcher(r.body);
        assertTrue(m.find());
        Map<String, Object> cfg = Json.parseObject(m.group(1));
        String uiId = Json.string(cfg, "uiId");
        String token = Json.string(cfg, "token");
        assertEquals("/wb/ws", Json.string(cfg, "ws"));

        WebSocketClient client = new WebSocketClient();
        client.start();
        try {
            // no cookie: refused at the upgrade
            assertFalse(connect(client, uiId, token, false).opened);
            // wrong token: refused
            assertFalse(connect(client, uiId, "nope", true).opened);

            Socket ws = connect(client, uiId, token, true);
            assertTrue(ws.opened);
            ws.send("{\"t\":\"hello\",\"lastSeq\":-1}");
            Map<String, Object> init = ws.next();
            assertEquals("init", Json.string(init, "t"));
            int buttonId = -1;
            for (Object o : Json.array(init, "ops")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> op = (Map<String, Object>) o;
                if ("wb.Button".equals(Json.string(op, "type")) && buttonId < 0) {
                    buttonId = (int) Json.integer(op, "id", -1);   // the first button is "go"
                }
            }
            assertTrue(buttonId > 0);
            assertEquals(WebSocketTransport.class, server.getSessions().getSessions().get(0).getTransport(uiId).getClass());

            // events are acknowledged, then the delta they caused arrives
            ws.send("{\"t\":\"ev\",\"events\":[{\"s\":1,\"id\":" + buttonId + ",\"type\":\"click\",\"data\":{}}]}");
            Map<String, Object> ack = ws.next();
            assertEquals("ack", Json.string(ack, "t"));
            assertEquals(1, Json.integer(ack, "ev", -1));
            Map<String, Object> delta = ws.next();
            assertEquals("delta", Json.string(delta, "t"));
            assertEquals(2, Json.integer(delta, "seq", -1));
            assertTrue(Json.write(delta).contains("\"text\":\"clicked\""));

            ws.send("{\"t\":\"ping\"}");
            assertEquals("pong", Json.string(ws.next(), "t"));

            // a second socket for the same tab replaces the first, which is closed; an old seq is replayed
            Socket ws2 = connect(client, uiId, token, true);
            ws2.send("{\"t\":\"hello\",\"lastSeq\":1}");
            assertEquals(2, Json.integer(ws2.next(), "seq", -1));
            assertTrue(ws.closed.get(5, TimeUnit.SECONDS));

            // bye closes the UI; another socket for it is refused with 410
            ws2.send("{\"t\":\"bye\"}");
            assertTrue(ws2.closed.get(5, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse(connect(client, uiId, token, true).opened);
        } finally {
            client.stop();
        }
    }

    /** A test-side socket: queues the server's messages, completes {@code closed} when the server closes it. */
    private static final class Socket extends WebSocketAdapter {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();
        final CompletableFuture<Boolean> closed = new CompletableFuture<Boolean>();
        boolean opened;

        @Override
        public void onWebSocketText(String message) {
            messages.add(message);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            super.onWebSocketClose(statusCode, reason);
            closed.complete(Boolean.TRUE);
        }

        void send(String json) throws IOException {
            getRemote().sendString(json);
        }

        Map<String, Object> next() throws InterruptedException {
            String m = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull("no message within 5s", m);
            return Json.parseObject(m);
        }
    }

    private Socket connect(WebSocketClient client, String uiId, String token, boolean withCookie) throws Exception {
        Socket s = new Socket();
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        if (withCookie) {
            req.setHeader("Cookie", "WBSESSION=" + cookies.get("WBSESSION"));
        }
        try {
            Session session = client.connect(s, new java.net.URI(base.replace("http://", "ws://") + "/wb/ws?ui=" + uiId + "&token=" + token), req)
                    .get(5, TimeUnit.SECONDS);
            s.opened = session.isOpen();
        } catch (java.util.concurrent.ExecutionException e) {
            s.opened = false; // the upgrade was refused (401/403/410)
        }
        return s;
    }

    private void login() throws IOException {
        request("GET", "/login", null, null);
        Response r = request("POST", "/login", "application/x-www-form-urlencoded",
                "csrf=" + cookies.get("WBCSRF") + "&provider=local&return=%2F&username=alice&password=pw");
        assertEquals(303, r.status);
    }

    @Test
    public void resourcesAreServedFromTheClassPathOnly() throws Exception {
        Response r = request("GET", "/wb/res/org/webbeans/ui/client/wb.css", null, null);
        assertEquals(200, r.status);
        assertTrue(r.header("Content-Type").startsWith("text/css"));
        r = request("GET", "/wb/res/org/webbeans/ui/UI.class", null, null);
        assertEquals(404, r.status);
        r = request("GET", "/wb/res/../etc/passwd", null, null);
        assertEquals(404, r.status);
        r = request("GET", "/wb/res/META-INF/MANIFEST.MF", null, null);
        assertEquals(404, r.status);
    }

    // ---- tiny HTTP client with a cookie jar --------------------------------------------------------------------

    private static final class Response {
        int status;
        String body;
        Map<String, List<String>> headers;

        /** Case-insensitive: the JDK server writes {@code Set-cookie}, browsers do not care. */
        String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                    return e.getValue().get(0);
                }
            }
            return null;
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

    private Response poll(String uiId, String token, String json) throws IOException {
        String body = "{\"uiId\":\"" + uiId + "\"," + json.substring(1);
        return request("POST", "/wb/poll", "application/json", body, token);
    }

    private Response request(String method, String path, String contentType, String body) throws IOException {
        return request(method, path, contentType, body, null);
    }

    private Response request(String method, String path, String contentType, String body, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
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
        if (token != null) {
            c.setRequestProperty("X-WB-Token", token);
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            OutputStream out = c.getOutputStream();
            out.write(body.getBytes(StandardCharsets.UTF_8));
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
}
