package org.webbeans.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.DownloadService;
import org.webbeans.ui.Event;
import org.webbeans.ui.Form;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Size;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.DatePicker;
import org.webbeans.ui.components.DateTimePicker;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.ListBox;
import org.webbeans.ui.components.NumberField;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.RadioGroup;
import org.webbeans.ui.components.ScrollPane;
import org.webbeans.ui.components.Slider;
import org.webbeans.ui.components.SplitPane;
import org.webbeans.ui.components.TextField;
import org.webbeans.ui.components.Tree;
import org.webbeans.ui.components.TreeModel;
import org.webbeans.ui.components.Upload;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * Step 11: the form inputs (NumberField, Slider, RadioGroup, DatePicker, DateTimePicker) under a {@link Form},
 * a {@link SplitPane} with a {@link Tree} and a {@link ListBox} in a {@link ScrollPane}, an {@link Upload} and
 * {@link DownloadService}.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 150)
@TopComponent.Description(preferredID = "GalleryTC", iconBase = "org/webbeans/demo/icons/form.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenGallery", position = 150)
@Messages({
    "CTL_GalleryTC=Components",
    "CTL_OpenGallery=Components",
    "LBL_Quantity=Quantity",
    "LBL_Price=Price",
    "LBL_Discount=Discount",
    "LBL_Shipping=Shipping",
    "LBL_Delivery=Delivery date",
    "LBL_Pickup=Pickup time",
    "LBL_Reference=Reference",
    "BTN_Place=Place order",
    "BTN_Export=Export CSV",
    "MSG_Order=Order placed: {0} \u00d7 {1}, {2}% off, {3}, delivery {4}, pickup {5}",
    "MSG_Summary={0} selected in the tree, {1} in the list",
    "MSG_Uploaded=Received {0} ({1} bytes, {2}): {3}",
    "LBL_Upload=Drop a text file here to see its first line",
    "LBL_Standard=Standard (3–5 days)",
    "LBL_Express=Express (next day)",
    "LBL_Collect=Collect in store",
    "LBL_Cities=Pick cities (Ctrl/Shift for several)"
})
public final class GalleryTopComponent extends TopComponent {

    private final NumberField quantity = new NumberField(Double.valueOf(1));
    private final NumberField price = new NumberField(Double.valueOf(19.99));
    private final Slider discount = new Slider(0, 50, 10);
    private final RadioGroup<String> shipping = new RadioGroup<String>(Arrays.asList("standard", "express", "collect"));
    private final DatePicker delivery = new DatePicker(LocalDate.now().plusDays(3));
    private final DateTimePicker pickup = new DateTimePicker(LocalDateTime.now().withSecond(0).withNano(0).plusHours(2));
    private final TextField reference = new TextField();
    private final Label summary = new Label();
    private final Label treeSummary = new Label();
    private final Tree<String> tree;
    private final ListBox<String> cities = new ListBox<String>();
    private final Upload upload = new Upload();
    private final Label uploaded = new Label();

    public GalleryTopComponent() {
        setDisplayName(Bundle.CTL_GalleryTC());
        setLayout(new Layout.Border());

        // --- the form: every new input under one Form -------------------------------------------------------------
        Panel form = new Panel(new Layout.Form().labelWidth(Size.px(120)));
        form.setPadding(12);
        quantity.setIntegerOnly().setRange(1, 999).setSpinner(true).setRequired(true).setTestId("qty");
        price.setDecimals(2).setPrefix("€").setMin(Double.valueOf(0)).setTestId("price");
        discount.setStep(5).setTicks(Double.valueOf(10)).setShowValue(true).setTestId("discount");
        shipping.setItems(Arrays.asList("standard", "express", "collect"), new Function<String, String>() {
            @Override
            public String apply(String s) {
                return s.equals("standard") ? Bundle.LBL_Standard() : s.equals("express") ? Bundle.LBL_Express() : Bundle.LBL_Collect();
            }
        }).setValue("standard").setOrientation(RadioGroup.Orientation.HORIZONTAL).setTestId("shipping");
        delivery.setMin(LocalDate.now()).setClearButton(true).setTestId("delivery");
        pickup.setTestId("pickup");
        reference.setPlaceholder("ORD-0000").setTestId("reference");
        form.add(quantity, Layout.Form.row(Bundle.LBL_Quantity()));
        form.add(price, Layout.Form.row(Bundle.LBL_Price()));
        form.add(discount, Layout.Form.row(Bundle.LBL_Discount()));
        form.add(shipping, Layout.Form.row(Bundle.LBL_Shipping()));
        form.add(delivery, Layout.Form.row(Bundle.LBL_Delivery()));
        form.add(pickup, Layout.Form.row(Bundle.LBL_Pickup()));
        form.add(reference, Layout.Form.row(Bundle.LBL_Reference()));

        final Button place = new Button(Bundle.BTN_Place());
        place.setKind(Button.Kind.PRIMARY).setTestId("place");
        final Form f = new Form().add(quantity, price, discount, shipping, delivery, pickup, reference).enable(place);
        place.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                if (f.validate()) {
                    summary.setText(Bundle.MSG_Order(quantity.getIntValue(), price.format(price.getValue()), (int) discount.getDoubleValue(),
                            shipping.getValue(), DatePicker.format(delivery.getValue()), DateTimePicker.format(pickup.getValue())));
                }
            }
        });
        Button export = new Button(Bundle.BTN_Export());
        export.setTestId("export");
        export.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                String csv = "quantity,price,discount,shipping,delivery\n" + quantity.getIntValue() + "," + price.getValue() + ","
                        + discount.getDoubleValue() + "," + shipping.getValue() + "," + delivery.getValue() + "\n";
                DownloadService.offer(csv.getBytes(StandardCharsets.UTF_8), "order.csv", "text/csv");
            }
        });
        summary.setTestId("summary");
        Panel buttons = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        buttons.add(place);
        buttons.add(export);
        buttons.add(DownloadService.link(new Supplier<DownloadService.Content>() {
            @Override
            public DownloadService.Content get() {
                return DownloadService.content(("reference=" + reference.getValue() + "\n").getBytes(StandardCharsets.UTF_8), "reference.txt", "text/plain");
            }
        }, "reference.txt").setTestId("ref-link"));
        Panel left = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 8));
        left.setPadding(8);
        left.add(form);
        left.add(buttons);
        left.add(summary);

        // --- upload / download ---------------------------------------------------------------------------------------
        upload.setAccept(".txt,.csv,.md,text/*").setMaxSize(256L * 1024).setHint(Bundle.LBL_Upload()).setTestId("upload");
        upload.addUploadListener(new Listener<Upload.UploadEvent>() {
            @Override
            public void onEvent(Upload.UploadEvent e) {
                try {
                    String text = new String(e.getFile().getBytes(), StandardCharsets.UTF_8);
                    String first = text.split("\r?\n", 2)[0];
                    uploaded.setText(Bundle.MSG_Uploaded(e.getFile().getName(), e.getFile().getSize(), e.getFile().getContentType(), first));
                    NotificationDisplayer.getDefault().notify(e.getFile().getName(), first, NotificationDisplayer.Category.INFO);
                } catch (IOException ex) {
                    uploaded.setText(ex.toString());
                }
            }
        });
        uploaded.setTestId("uploaded");
        left.add(upload);
        left.add(uploaded);

        // --- tree + list in a split pane, the list inside a scroll pane -------------------------------------------
        tree = new Tree<String>(new TreeModel<String>() {
            @Override
            public List<String> getChildren(String parent) {
                if (parent == null) {
                    return Arrays.asList("Europe", "Asia", "Americas");
                }
                if (parent.equals("Europe")) {
                    return Arrays.asList("Germany", "France", "Italy");
                }
                if (parent.equals("Asia")) {
                    return Arrays.asList("Japan", "India");
                }
                if (parent.equals("Americas")) {
                    return Arrays.asList("Brazil", "Canada");
                }
                if (parent.equals("Germany")) {
                    return Arrays.asList("Berlin", "Munich", "Hamburg");
                }
                if (parent.equals("France")) {
                    return Arrays.asList("Paris", "Lyon");
                }
                if (parent.equals("Japan")) {
                    return Arrays.asList("Tokyo", "Osaka");
                }
                return Collections.emptyList();
            }

            @Override
            public String getLabel(String node) {
                return node;
            }

            @Override
            public boolean isLeaf(String node) {
                return getChildren(node).isEmpty();
            }
        });
        tree.setTestId("tree");
        tree.addSelectionListener(new Listener<Tree.SelectionEvent>() {
            @Override
            public void onEvent(Tree.SelectionEvent e) {
                String n = tree.getSelectedNode();
                List<String> kids = n == null ? Collections.<String>emptyList() : tree.getModel().getChildren(n);
                cities.setItems(kids.isEmpty() && n != null ? Collections.singletonList(n) : kids);
                refreshSummary();
            }
        });
        cities.setMulti(true).setEmptyText(Bundle.LBL_Cities()).setTestId("cities");
        cities.addSelectionListener(new Listener<ListBox.SelectionEvent>() {
            @Override
            public void onEvent(ListBox.SelectionEvent e) {
                refreshSummary();
            }
        });
        cities.addActivateListener(new Listener<ListBox.ActivateEvent>() {
            @Override
            public void onEvent(ListBox.ActivateEvent e) {
                StatusDisplayer.getDefault().setStatusText("Activated " + e.getItem());
            }
        });
        treeSummary.setTestId("tree-summary");
        Panel right = new Panel(new Layout.Border());
        ScrollPane scroll = new ScrollPane(cities);
        scroll.setTestId("scroll");
        right.add(scroll, Layout.Border.CENTER);
        right.add(treeSummary, Layout.Border.SOUTH);
        SplitPane split = new SplitPane(SplitPane.Orientation.HORIZONTAL, tree, right);
        split.setPosition(220).setCollapsible(true).setTestId("split");
        split.addChangeListener(new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                StatusDisplayer.getDefault().setStatusText("Divider at " + e.getInt("position", 0) + " px");
            }
        });

        SplitPane outer = new SplitPane(SplitPane.Orientation.HORIZONTAL, new ScrollPane(left), split);
        outer.setPosition(520).setTestId("outer");
        add(outer, Layout.Border.CENTER);
        refreshSummary();
    }

    private void refreshSummary() {
        treeSummary.setText(Bundle.MSG_Summary(tree.getSelected(), new ArrayList<String>(cities.getSelected())));
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_GalleryTC());
    }
}
