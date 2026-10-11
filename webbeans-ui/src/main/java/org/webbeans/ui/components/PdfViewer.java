package org.webbeans.ui.components;

import java.nio.file.Path;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.DownloadService;
import org.webbeans.ui.Property;

/**
 * Shows a PDF in the browser's built-in viewer (an iframe) with a download button. The document is served
 * from this session through the {@link DownloadService} ({@link #setDocument}) or comes from any URL. Nothing
 * is parsed on the server; what the viewer offers (search, print, page navigation) is the browser's.
 */
@Renderer(id = "wb.PdfViewer", resource = "org/webbeans/ui/client/renderers/viewers.js", css = "org/webbeans/ui/client/renderers/viewers.css")
public class PdfViewer extends Component {

    public static final Property<String> URL = Property.of("url", String.class, null);
    public static final Property<String> FILE_NAME = Property.of("fileName", String.class, null);
    public static final Property<Boolean> TOOLBAR = Property.of("toolbar", Boolean.class, Boolean.TRUE);
    /** The page to open at (1-based); honoured by most browsers' viewers. */
    public static final Property<Integer> PAGE = Property.of("page", Integer.class, null);

    public PdfViewer() {
        super("wb.PdfViewer");
    }

    public PdfViewer setUrl(String url) {
        set(URL, url);
        return this;
    }

    /** Serves the bytes from this session (needs a current UI). */
    public PdfViewer setDocument(final byte[] bytes, final String fileName) {
        set(FILE_NAME, fileName);
        set(URL, DownloadService.url(new java.util.function.Supplier<DownloadService.Content>() {
            @Override
            public DownloadService.Content get() {
                return DownloadService.content(bytes, fileName, "application/pdf");
            }
        }));
        return this;
    }

    public PdfViewer setDocument(final Path file, final String fileName) {
        set(FILE_NAME, fileName);
        set(URL, DownloadService.url(new java.util.function.Supplier<DownloadService.Content>() {
            @Override
            public DownloadService.Content get() {
                return DownloadService.content(file, fileName, "application/pdf");
            }
        }));
        return this;
    }

    public PdfViewer setToolbar(boolean b) {
        set(TOOLBAR, b);
        return this;
    }

    public PdfViewer setPage(Integer page) {
        set(PAGE, page);
        return this;
    }
}
