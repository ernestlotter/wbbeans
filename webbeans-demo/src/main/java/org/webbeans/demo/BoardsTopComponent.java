package org.webbeans.demo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Calendar;
import org.webbeans.ui.components.Diagram;
import org.webbeans.ui.components.ImageViewer;
import org.webbeans.ui.components.Kanban;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Palette;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.PdfViewer;
import org.webbeans.ui.components.SplitPane;
import org.webbeans.ui.components.TabbedPane;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/** Step 12: {@link Calendar}, {@link Kanban}, {@link Diagram} with a {@link Palette}, {@link ImageViewer} and {@link PdfViewer}. */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 190)
@TopComponent.Description(preferredID = "BoardsTC", iconBase = "org/webbeans/demo/icons/orbit.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenBoards", position = 190)
@Messages({
    "CTL_BoardsTC=Boards",
    "CTL_OpenBoards=Boards",
    "TAB_Calendar=Calendar",
    "TAB_Kanban=Missions",
    "TAB_Diagram=Diagram",
    "TAB_Image=Image",
    "TAB_Pdf=PDF",
    "COL_Planned=Planned",
    "COL_Flying=In flight",
    "COL_Arrived=Arrived",
    "CAT_Bodies=Bodies",
    "CAT_Links=Links",
    "MSG_Moved={0} moved to {1}",
    "MSG_Dropped={0} dropped at {1}, {2}",
    "MSG_Connected={0} → {1}",
    "MSG_Entry={0} on {1}"
})
public final class BoardsTopComponent extends TopComponent {

    private final Label status = new Label();
    private int seq;

    public BoardsTopComponent() {
        setDisplayName(Bundle.CTL_BoardsTC());
        setLayout(new Layout.Border());
        status.setTestId("boardLog");
        TabbedPane tabs = new TabbedPane();
        tabs.setTestId("boardTabs");
        tabs.addTab(Bundle.TAB_Calendar(), calendar());
        tabs.addTab(Bundle.TAB_Kanban(), kanban());
        tabs.addTab(Bundle.TAB_Diagram(), diagram());
        tabs.addTab(Bundle.TAB_Image(), image());
        tabs.addTab(Bundle.TAB_Pdf(), pdf());
        add(tabs, Layout.Border.CENTER);
        Panel south = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        south.setPadding(4);
        south.add(status);
        add(south, Layout.Border.SOUTH);
    }

    private Calendar calendar() {
        final Calendar cal = new Calendar();
        cal.setWeekNumbers(true).setTestId("calendar");
        LocalDate base = LocalDate.now().withDayOfMonth(1);
        List<Calendar.Entry> entries = new ArrayList<Calendar.Entry>();
        entries.add(new Calendar.Entry("launch", "Launch window", base.plusDays(2)).until(base.plusDays(4)).color("#c62828"));
        entries.add(new Calendar.Entry("review", "Mission review", base.plusDays(9)).at(LocalTime.of(10, 0), LocalTime.of(11, 30)).color("#1565c0"));
        entries.add(new Calendar.Entry("flyby", "Mars flyby", base.plusDays(16)).at(LocalTime.of(14, 0), LocalTime.of(15, 0)).icon(Resource.of("org/webbeans/demo/icons/planet.svg")));
        entries.add(new Calendar.Entry("holiday", "Public holiday", base.plusDays(20)).color("#2e7d32").readOnly());
        cal.setEntries(entries);
        cal.addMoveListener(new Listener<Calendar.MoveEvent>() {
            @Override
            public void onEvent(Calendar.MoveEvent e) {
                cal.addEntry(e.getMoved());
                status.setText(Bundle.MSG_Moved(e.getEntry().getTitle(), e.getDate()));
            }
        });
        cal.addEntryClickListener(new Listener<Calendar.EntryEvent>() {
            @Override
            public void onEvent(Calendar.EntryEvent e) {
                status.setText(Bundle.MSG_Entry(e.getEntry().getTitle(), e.getEntry().getStart()));
            }
        });
        cal.addDayActivateListener(new Listener<Calendar.DayEvent>() {
            @Override
            public void onEvent(Calendar.DayEvent e) {
                cal.addEntry(new Calendar.Entry("new" + (++seq), "New entry " + seq, e.getDate()));
                status.setText(Bundle.MSG_Entry("New entry " + seq, e.getDate()));
            }
        });
        return cal;
    }

    private Kanban kanban() {
        final Kanban board = new Kanban();
        board.setTestId("kanban");
        board.addColumn("planned", Bundle.COL_Planned()).color("#757575").addButton(true);
        board.addColumn("flying", Bundle.COL_Flying()).color("#1565c0").limit(2);
        board.addColumn("arrived", Bundle.COL_Arrived()).color("#2e7d32");
        for (Planet p : SolarSystem.get().getPlanets()) {
            String col = p.isExplored() ? "arrived" : p.getType() == Planet.Type.ROCKY ? "flying" : "planned";
            board.addCard(new Kanban.Card(p.getName(), p.getName() + " mission", col).subtitle(p.getType().getDisplayName()).icon(p.getType().getIcon())
                    .tags(p.getMoons() + " moons").color(p.getType() == Planet.Type.GAS_GIANT ? "#ef6c00" : null));
        }
        board.addMoveListener(new Listener<Kanban.MoveEvent>() {
            @Override
            public void onEvent(Kanban.MoveEvent e) {
                status.setText(Bundle.MSG_Moved(e.getCard().getTitle(), e.getColumn()));
                Planet p = SolarSystem.get().find(e.getKey());
                if (p != null) {
                    p.setExplored("arrived".equals(e.getColumn()));
                    SolarSystem.get().planetChanged(p, "explored");
                }
            }
        });
        board.addAddListener(new Listener<Kanban.ColumnEvent>() {
            @Override
            public void onEvent(Kanban.ColumnEvent e) {
                board.addCard(new Kanban.Card("m" + (++seq), "Mission " + seq, e.getColumn()).subtitle("new"));
            }
        });
        board.addCardActivateListener(new Listener<Kanban.CardEvent>() {
            @Override
            public void onEvent(Kanban.CardEvent e) {
                StatusDisplayer.getDefault().setStatusText(e.getCard().getTitle());
            }
        });
        return board;
    }

    private Panel diagram() {
        final Diagram d = new Diagram();
        d.setTestId("diagram");
        d.addNode("sun", "Sun", 40, 160).shape(Diagram.Shape.CIRCLE).size(70, 70).color("#f9a825").icon(Resource.of("org/webbeans/demo/icons/sun.svg")).locked(true);
        int i = 0;
        for (Planet p : SolarSystem.get().getPlanets()) {
            d.addNode(p.getName(), p.getName(), 200 + (i % 4) * 160, 40 + (i / 4) * 120).shape(p.getType() == Planet.Type.DWARF ? Diagram.Shape.DIAMOND : Diagram.Shape.ROUNDED)
                    .icon(p.getType().getIcon()).tooltip(p.getType().getDisplayName());
            d.addEdge("orbit-" + p.getName(), "sun", p.getName()).label(p.getMoons() + " moons").lineStyle(p.isExplored() ? Diagram.LineStyle.SOLID : Diagram.LineStyle.DASHED);
            i++;
        }
        d.addConnectListener(new Listener<Diagram.ConnectEvent>() {
            @Override
            public void onEvent(Diagram.ConnectEvent e) {
                d.addEdge("link" + (++seq), e.getFrom(), e.getTo()).label("link");
                status.setText(Bundle.MSG_Connected(e.getFrom(), e.getTo()));
            }
        });
        d.addDropListener(new Listener<Diagram.DropEvent>() {
            @Override
            public void onEvent(Diagram.DropEvent e) {
                String key = e.getItemKey() + (++seq);
                d.addNode(key, e.getItemKey() + " " + seq, e.getX(), e.getY()).shape("comet".equals(e.getItemKey()) ? Diagram.Shape.DIAMOND : Diagram.Shape.ELLIPSE);
                status.setText(Bundle.MSG_Dropped(e.getItemKey(), (int) e.getX(), (int) e.getY()));
            }
        });
        d.addDeleteListener(new Listener<Diagram.SelectionEvent>() {
            @Override
            public void onEvent(Diagram.SelectionEvent e) {
                for (String k : e.getNodeKeys()) {
                    if (!"sun".equals(k)) {
                        d.removeNode(k);
                    }
                }
                for (String k : e.getEdgeKeys()) {
                    d.removeEdge(k);
                }
            }
        });
        d.addNodeMoveListener(new Listener<Diagram.NodeEvent>() {
            @Override
            public void onEvent(Diagram.NodeEvent e) {
                status.setText(Bundle.MSG_Moved(e.getKey(), (int) e.getX() + "," + (int) e.getY()));
            }
        });
        Palette palette = new Palette();
        palette.setShowFilter(true).setTestId("palette");
        palette.addItem(Bundle.CAT_Bodies(), "moon", "Moon", Resource.of("org/webbeans/demo/icons/dwarf.svg"));
        palette.addItem(Bundle.CAT_Bodies(), "comet", "Comet", Resource.of("org/webbeans/demo/icons/scan.svg"));
        palette.addItem(Bundle.CAT_Bodies(), "station", "Station", Resource.of("org/webbeans/demo/icons/cube.svg"));
        palette.addItem(Bundle.CAT_Links(), "orbit", "Orbit", Resource.of("org/webbeans/demo/icons/orbit.svg"), "Drag a node's port to another node to connect");
        Panel p = new Panel(new Layout.Fill());
        SplitPane split = new SplitPane(SplitPane.Orientation.HORIZONTAL, palette, d);
        split.setPosition(180);
        p.add(split);
        return p;
    }

    private ImageViewer image() {
        ImageViewer v = new ImageViewer(Resource.of("org/webbeans/demo/icons/planet.svg"));
        v.setAlt("Planet").setCheckerboard(true).setTestId("imageViewer");
        return v;
    }

    private PdfViewer pdf() {
        PdfViewer v = new PdfViewer();
        v.setTestId("pdfViewer");
        v.setDocument(minimalPdf("WebBeans demo: the solar system report"), "solar-system.pdf");
        return v;
    }

    /** A one-page PDF written by hand (no library): enough for the browser's viewer. */
    static byte[] minimalPdf(String text) {
        String content = "BT /F1 24 Tf 72 720 Td (" + text.replace("(", "\\(").replace(")", "\\)") + ") Tj ET";
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder sb = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<Integer>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(sb.length());
            sb.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int off : offsets) {
            sb.append(String.format("%010d 00000 n \n", off));
        }
        sb.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
