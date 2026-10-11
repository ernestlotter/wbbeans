package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.ValueComponent;

/**
 * A file chooser button with an optional drop zone. The browser posts each file to {@code /wb/upload} (with the
 * session token, the tab and this component's id); the server stores it in a temporary file and fires an
 * {@link UploadEvent} on the session executor — the one event here that does not come through the delta channel.
 * {@link #PROGRESS} reports bytes sent (throttled); {@link #FAILED} a rejected or interrupted transfer.
 *
 * <pre>
 * Upload up = new Upload().setAccept(".csv,text/csv").setMaxSize(5 * 1024 * 1024);
 * up.addUploadListener(e -&gt; importCsv(e.getFile().open()));     // the temp file is deleted when this returns
 * </pre>
 */
@Renderer(id = "wb.Upload", resource = "org/webbeans/ui/client/renderers/upload.js")
public class Upload extends Component {

    private static final Logger LOG = Logger.getLogger(Upload.class.getName());

    /** The {@code accept} attribute: extensions and/or MIME types, comma separated. */
    public static final Property<String> ACCEPT = Property.of("accept", String.class, null);
    public static final Property<Boolean> MULTIPLE = Property.of("multiple", Boolean.class, Boolean.FALSE);
    /** Largest accepted file in bytes (checked in the browser and on the server); null: the server's limit. */
    public static final Property<Long> MAX_SIZE = Property.of("maxSize", Long.class, null);
    public static final Property<Boolean> DROP_ZONE = Property.of("dropZone", Boolean.class, Boolean.TRUE);
    public static final Property<String> TEXT = Property.of("text", String.class, null);
    public static final Property<String> HINT = Property.of("hint", String.class, null);
    public static final Property<Boolean> SHOW_LIST = Property.of("showList", Boolean.class, Boolean.TRUE);

    public static final EventType<ProgressEvent> PROGRESS = EventType.of("progress", ProgressEvent::new).lazy(250);
    public static final EventType<FailedEvent> FAILED = EventType.of("failed", FailedEvent::new);
    /** Every file of a selection was transferred (or failed). */
    public static final EventType<Event> FINISHED = EventType.of("finished", Event::new);

    /** A file arrived: fired on the session executor by the server, not sent by the browser. */
    public static class UploadEvent extends Event {
        private final UploadedFile file;

        public UploadEvent(Component source, UploadedFile file) {
            super(source, new java.util.LinkedHashMap<String, Object>());
            this.file = file;
        }

        public UploadedFile getFile() {
            return file;
        }

        @Override
        public String toString() {
            return "UploadEvent[" + file + "]";
        }
    }

    public static class ProgressEvent extends Event {
        public ProgressEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getFileName() {
            return getString("name");
        }

        public long getLoaded() {
            return (long) getDouble("loaded", 0);
        }

        public long getTotal() {
            return (long) getDouble("total", 0);
        }
    }

    public static class FailedEvent extends Event {
        public FailedEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getFileName() {
            return getString("name");
        }

        /** {@code too-large}, {@code rejected}, {@code network}, or the HTTP status as text. */
        public String getReason() {
            return getString("reason");
        }
    }

    private final List<Listener<UploadEvent>> uploadListeners = new ArrayList<Listener<UploadEvent>>();

    public Upload() {
        super("wb.Upload");
    }

    public Upload(String text) {
        this();
        setText(text);
    }

    public Upload setAccept(String accept) {
        set(ACCEPT, accept);
        return this;
    }

    public Upload setMultiple(boolean b) {
        set(MULTIPLE, b);
        return this;
    }

    public Upload setMaxSize(Long bytes) {
        set(MAX_SIZE, bytes);
        return this;
    }

    public Upload setDropZone(boolean b) {
        set(DROP_ZONE, b);
        return this;
    }

    public Upload setText(String t) {
        set(TEXT, t);
        return this;
    }

    public Upload setHint(String t) {
        set(HINT, t);
        return this;
    }

    /** Whether transferred files are listed under the button with their progress. */
    public Upload setShowList(boolean b) {
        set(SHOW_LIST, b);
        return this;
    }

    @Override
    protected void attached() {
        if (get(TEXT) == null) {
            set(TEXT, Bundles.getMessage(ValueComponent.class, "LBL_Upload"));
        }
        if (get(HINT) == null && get(DROP_ZONE)) {
            set(HINT, Bundles.getMessage(ValueComponent.class, "LBL_DropHere"));
        }
    }

    /** Opens the browser's file chooser (must follow a user gesture to work in most browsers). */
    public void open() {
        call("open");
    }

    /** Clears the list of transferred files. */
    public void clear() {
        call("clear");
    }

    public Registration addUploadListener(final Listener<UploadEvent> l) {
        uploadListeners.add(l);
        return new Registration() {
            @Override
            public void remove() {
                uploadListeners.remove(l);
            }
        };
    }

    public Registration addProgressListener(Listener<ProgressEvent> l) {
        return addListener(PROGRESS, l);
    }

    public Registration addFailedListener(Listener<FailedEvent> l) {
        return addListener(FAILED, l);
    }

    public Registration addFinishedListener(Listener<Event> l) {
        return addListener(FINISHED, l);
    }

    /**
     * Called by the upload endpoint on the session executor once the file is stored. Fires the listeners and
     * deletes the temporary file unless a listener retained it.
     */
    public void received(UploadedFile file) {
        checkThread();
        UploadEvent e = new UploadEvent(this, file);
        try {
            for (Listener<UploadEvent> l : new ArrayList<Listener<UploadEvent>>(uploadListeners)) {
                try {
                    l.onEvent(e);
                } catch (RuntimeException ex) {
                    LOG.log(Level.SEVERE, "Upload listener failed for " + file, ex);
                }
            }
        } finally {
            if (!file.isRetained()) {
                file.delete();
            }
        }
    }
}
