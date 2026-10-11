package org.webbeans.ui;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openide.util.Lookup;

/**
 * Content-addressed binary payloads (geometry, textures, large arrays) the client fetches from
 * {@code /wb/blob/<id>} and caches by id: a blob referenced by many components, tabs or reconnects crosses
 * the wire once. Ids are the SHA-256 of the content, so equal content is stored once. The default store is
 * in-memory and bounded (least recently used blobs are dropped; a client asking for a dropped blob gets 404
 * and the component re-registers it on the next resync). Register another {@link BlobStore} in
 * {@code Lookup.getDefault()} to persist blobs.
 */
public class BlobStore {

    /** One stored payload. */
    public static final class Blob {
        private final String id;
        private final String contentType;
        private final byte[] bytes;

        Blob(String id, String contentType, byte[] bytes) {
            this.id = id;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        public String getId() {
            return id;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }
    }

    private static final BlobStore DEFAULT = new BlobStore(256L * 1024 * 1024);

    private final long maxBytes;
    private long size;
    private final Map<String, Blob> blobs = new LinkedHashMap<String, Blob>(64, 0.75f, true);

    public BlobStore(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** The process-wide store: one registered in the default lookup, else the built-in in-memory store. */
    public static BlobStore getDefault() {
        BlobStore s = Lookup.getDefault().lookup(BlobStore.class);
        return s != null ? s : DEFAULT;
    }

    /** Stores the bytes (no copy) and returns their id; storing equal content again is a no-op. */
    public synchronized String put(byte[] bytes, String contentType) {
        String id = hash(bytes);
        if (!blobs.containsKey(id)) {
            blobs.put(id, new Blob(id, contentType == null ? "application/octet-stream" : contentType, bytes));
            size += bytes.length;
            evict();
        }
        return id;
    }

    public synchronized Blob get(String id) {
        return id == null ? null : blobs.get(id);
    }

    public synchronized boolean contains(String id) {
        return blobs.containsKey(id);
    }

    public synchronized long getSize() {
        return size;
    }

    private void evict() {
        java.util.Iterator<Map.Entry<String, Blob>> it = blobs.entrySet().iterator();
        while (size > maxBytes && it.hasNext()) {
            Map.Entry<String, Blob> e = it.next();
            if (blobs.size() == 1) {
                break; // never evict the one just stored
            }
            size -= e.getValue().bytes.length;
            it.remove();
        }
    }

    /** SHA-256 hex of the bytes (the blob id). */
    public static String hash(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
