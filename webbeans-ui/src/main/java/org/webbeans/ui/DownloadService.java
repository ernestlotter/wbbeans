package org.webbeans.ui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.webbeans.ui.components.Link;

/**
 * Sends files to the browser. {@link #offer} makes the current tab download content once through a one-time
 * URL ({@code /wb/download/<token>}, five minutes, this session only); {@link #link} gives a {@link Link}
 * component whose URL produces the content every time it is clicked, for as long as the tab lives. Content is
 * streamed by the server when the browser asks for it — nothing travels through the delta channel.
 *
 * <pre>
 * DownloadService.offer(csvBytes, "report.csv", "text/csv");
 * panel.add(DownloadService.link(() -&gt; DownloadService.content(pdf(), "invoice.pdf", "application/pdf"), "Invoice"));
 * </pre>
 */
public final class DownloadService {

    /** What a download serves. */
    public interface Content {
        String getFileName();

        String getContentType();

        /** Length in bytes, or -1 when unknown (chunked). */
        long getLength();

        InputStream open() throws IOException;
    }

    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** One registered download: a fixed content or a producer; single-use entries expire. */
    private static final class Entry {
        final Content content;
        final Supplier<Content> producer;
        final boolean singleUse;
        final long created = System.currentTimeMillis();

        Entry(Content content, Supplier<Content> producer, boolean singleUse) {
            this.content = content;
            this.producer = producer;
            this.singleUse = singleUse;
        }
    }

    /** The per-UI registry, kept as a UI attribute. */
    private static final class Registry {
        final Map<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    }

    private DownloadService() {
    }

    public static Content content(final byte[] bytes, final String fileName, final String contentType) {
        return new Content() {
            @Override
            public String getFileName() {
                return fileName;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public long getLength() {
                return bytes.length;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    public static Content content(final Path file, final String fileName, final String contentType) {
        return new Content() {
            @Override
            public String getFileName() {
                return fileName != null ? fileName : file.getFileName().toString();
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public long getLength() {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    return -1;
                }
            }

            @Override
            public InputStream open() throws IOException {
                return Files.newInputStream(file);
            }
        };
    }

    /** Starts a download in the current tab ("save as" in the browser). Session executor. */
    public static void offer(byte[] bytes, String fileName, String contentType) {
        offer(content(bytes, fileName, contentType));
    }

    public static void offer(Content content) {
        UI ui = UI.current();
        if (ui == null) {
            throw new IllegalStateException("DownloadService.offer needs a current UI");
        }
        String token = register(ui, new Entry(content, null, true));
        ui.getRoot().call("download", "wb/download/" + token);
    }

    /** A link that produces its content on every click; the producer runs on an HTTP thread. */
    public static Link link(Supplier<Content> producer, String text) {
        UI ui = UI.current();
        if (ui == null) {
            throw new IllegalStateException("DownloadService.link needs a current UI");
        }
        String token = register(ui, new Entry(null, producer, false));
        Link l = new Link(text, "wb/download/" + token);
        return l;
    }

    /** The relative URL of a reusable download for an existing component (an image, a custom link). */
    public static String url(Supplier<Content> producer) {
        UI ui = UI.current();
        if (ui == null) {
            throw new IllegalStateException("DownloadService.url needs a current UI");
        }
        return "wb/download/" + register(ui, new Entry(null, producer, false));
    }

    private static String register(UI ui, Entry e) {
        Registry r = ui.getAttribute(Registry.class);
        if (r == null) {
            r = new Registry();
            ui.setAttribute(Registry.class, r);
        }
        byte[] b = new byte[18];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        r.entries.put(token, e);
        return token;
    }

    /**
     * Resolves a token for the server: the content to stream, or null (unknown, expired, already used, or
     * another tab's). Single-use entries are removed here. Any thread.
     */
    public static Content take(UI ui, String token) {
        Registry r = ui.getAttribute(Registry.class);
        if (r == null || token == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> me : r.entries.entrySet()) {
            if (me.getValue().singleUse && now - me.getValue().created > TTL_MS) {
                r.entries.remove(me.getKey());
            }
        }
        Entry e = r.entries.get(token);
        if (e == null) {
            return null;
        }
        if (e.singleUse) {
            r.entries.remove(token);
            return e.content;
        }
        return e.producer.get();
    }
}
