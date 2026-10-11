package org.webbeans.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.openide.util.lookup.Lookups;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.Actions;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Event;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.Spacer;
import org.webbeans.ui.table.Cell;
import org.webbeans.ui.table.Column;
import org.webbeans.ui.table.ListTableModel;
import org.webbeans.ui.table.Table;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * Step 11: a {@link Table} over 10 000 generated orders — virtual scrolling, server-side sort and filter,
 * multi-selection, inline editing with validation, a row context menu, keyed updates from the model.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 160)
@TopComponent.Description(preferredID = "OrdersTC", iconBase = "org/webbeans/demo/icons/log.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenOrders", position = 160)
@Messages({
    "CTL_OrdersTC=Orders",
    "CTL_OpenOrders=Orders",
    "COL_Id=Id",
    "COL_Customer=Customer",
    "COL_Date=Date",
    "COL_Items=Items",
    "COL_Total=Total",
    "COL_Status=Status",
    "BTN_Add=Add order",
    "BTN_MarkPaid=Mark paid",
    "BTN_Delete=Delete",
    "MSG_OrdersSelected={0} selected, {1} rows",
    "MSG_Opened=Order {0} for {1}",
    "ERR_Customer=Customer name needed",
    "ST_PAID=Paid",
    "ST_OPEN=Open",
    "ST_CANCELLED=Cancelled"
})
public final class OrdersTopComponent extends TopComponent {

    enum Status { OPEN, PAID, CANCELLED }

    static final class Order {
        final int id;
        String customer;
        final LocalDate date;
        final int items;
        double total;
        Status status;

        Order(int id, String customer, LocalDate date, int items, double total, Status status) {
            this.id = id;
            this.customer = customer;
            this.date = date;
            this.items = items;
            this.total = total;
            this.status = status;
        }
    }

    private static final String[] NAMES = {"Acme", "Globex", "Initech", "Umbrella", "Hooli", "Vandelay", "Stark", "Wayne", "Wonka", "Tyrell",
        "Cyberdyne", "Soylent", "Aperture", "Oscorp", "Dunder", "Pied Piper", "Gringotts", "Monarch", "Sirius", "Vehement"};

    private final Table<Order> table = new Table<Order>();
    private final ListTableModel<Order> model;
    private final Label status = new Label();
    private int nextId = 1;

    public OrdersTopComponent() {
        setDisplayName(Bundle.CTL_OrdersTC());
        setLayout(new Layout.Border());
        model = new ListTableModel<Order>(generate(10000));

        table.setKeyFunction(new Function<Order, String>() {
            @Override
            public String apply(Order o) {
                return String.valueOf(o.id);
            }
        });
        table.addColumn("id", Bundle.COL_Id(), new Function<Order, Object>() {
            @Override
            public Object apply(Order o) {
                return o.id;
            }
        }).width(70).align(Column.Align.RIGHT);
        table.addColumn("customer", Bundle.COL_Customer(), new Function<Order, Object>() {
            @Override
            public Object apply(Order o) {
                return o.customer;
            }
        }).minWidth(140).editable(new BiConsumer<Order, String>() {
            @Override
            public void accept(Order o, String text) {
                if (text.trim().isEmpty()) {
                    throw new IllegalArgumentException(Bundle.ERR_Customer());
                }
                o.customer = text.trim();
            }
        });
        table.addColumn("date", Bundle.COL_Date(), new Function<Order, Object>() {
            @Override
            public Object apply(Order o) {
                return o.date;
            }
        }).width(110);
        table.addColumn("items", Bundle.COL_Items(), new Function<Order, Object>() {
            @Override
            public Object apply(Order o) {
                return o.items;
            }
        }).width(70).align(Column.Align.RIGHT);
        table.addColumn("total", Bundle.COL_Total(), new Function<Order, Object>() {
            @Override
            public Object apply(Order o) {
                return o.total;
            }
        }).width(110).align(Column.Align.RIGHT);
        table.addColumn("status", Bundle.COL_Status(), new Function<Order, Object>() {
            @Override
            public Object apply(Order o) {
                String label = o.status == Status.PAID ? Bundle.ST_PAID() : o.status == Status.OPEN ? Bundle.ST_OPEN() : Bundle.ST_CANCELLED();
                return Cell.of(label, Resource.of("org/webbeans/demo/icons/" + (o.status == Status.PAID ? "planet.svg" : o.status == Status.OPEN ? "orbit.svg" : "dwarf.svg")))
                        .style("demo-status-" + o.status.name().toLowerCase());
            }
        }).width(120);
        table.setModel(model).setSelectionMode(Table.SelectionMode.MULTI).setShowFilter(true).setTestId("orders");
        table.setRowStyle(new Function<Order, String>() {
            @Override
            public String apply(Order o) {
                return o.status == Status.CANCELLED ? "demo-row-cancelled" : null;
            }
        });
        table.addSelectionListener(new Listener<Table.SelectionEvent>() {
            @Override
            public void onEvent(Table.SelectionEvent e) {
                updateStatus();
            }
        });
        table.addActivateListener(new Listener<Table.RowEvent>() {
            @Override
            public void onEvent(Table.RowEvent e) {
                Order o = e.getRow();
                if (o != null) {
                    NotificationDisplayer.getDefault().notify(Bundle.CTL_OrdersTC(), Bundle.MSG_Opened(o.id, o.customer), NotificationDisplayer.Category.INFO);
                }
            }
        });
        table.addRowMenuListener(new Listener<Table.RowEvent>() {
            @Override
            public void onEvent(Table.RowEvent e) {
                Actions.actionsToPopup(Arrays.<Action>asList(markPaid(), delete()), Lookups.fixed(table.getSelected().toArray()), e.getX(), e.getY());
            }
        });
        table.addCellEditListener(new Listener<Table.CellEditEvent>() {
            @Override
            public void onEvent(Table.CellEditEvent e) {
                StatusDisplayer.getDefault().setStatusText("Edited " + e.getColumnId() + " of order " + e.getKey());
            }
        });
        table.addSortListener(new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                updateStatus();
            }
        });

        Panel bar = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        bar.setPadding(6);
        Button add = new Button(Bundle.BTN_Add());
        add.setTestId("add-order");
        add.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                Order o = new Order(nextId++, "New Customer", LocalDate.now(), 1, 9.99, Status.OPEN);
                model.add(0, o);
                table.setSelectedRow(o);
                table.scrollToIndex(0);
                updateStatus();
            }
        });
        Button paid = new Button(Bundle.BTN_MarkPaid());
        paid.setTestId("mark-paid");
        paid.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                markPaid(table.getSelected());
            }
        });
        Button del = new Button(Bundle.BTN_Delete());
        del.setKind(Button.Kind.DANGER).setTestId("delete-orders");
        del.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                model.removeAll(table.getSelected());
                table.clearSelection();
                updateStatus();
            }
        });
        status.setTestId("orders-status");
        bar.add(add);
        bar.add(paid);
        bar.add(del);
        bar.add(new Spacer());
        bar.add(status);
        add(bar, Layout.Border.NORTH);
        add(table, Layout.Border.CENTER);
        updateStatus();
    }

    private void markPaid(List<Order> orders) {
        for (Order o : orders) {
            o.status = Status.PAID;
            model.update(o);          // keyed update: the rows re-render in place
        }
    }

    private Action markPaid() {
        return new AbstractAction(Bundle.BTN_MarkPaid()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                markPaid(table.getSelected());
            }
        };
    }

    private Action delete() {
        return new AbstractAction(Bundle.BTN_Delete()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.removeAll(table.getSelected());
                table.clearSelection();
                updateStatus();
            }
        };
    }

    private void updateStatus() {
        status.setText(Bundle.MSG_OrdersSelected(table.getSelected().size(), model.size()));
    }

    private List<Order> generate(int n) {
        Random rnd = new Random(42);
        List<Order> out = new ArrayList<Order>(n);
        LocalDate start = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < n; i++) {
            int items = 1 + rnd.nextInt(12);
            Status s = rnd.nextInt(10) < 6 ? Status.PAID : rnd.nextInt(10) < 8 ? Status.OPEN : Status.CANCELLED;
            out.add(new Order(nextId++, NAMES[rnd.nextInt(NAMES.length)] + " " + (char) ('A' + rnd.nextInt(26)) + rnd.nextInt(90),
                    start.plusDays(rnd.nextInt(900)), items, Math.round(items * (5 + rnd.nextDouble() * 200) * 100) / 100.0, s));
        }
        return out;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_OrdersTC());
        table.getColumn("id").title(Bundle.COL_Id());
        table.getColumn("customer").title(Bundle.COL_Customer());
        table.getColumn("date").title(Bundle.COL_Date());
        table.getColumn("items").title(Bundle.COL_Items());
        table.getColumn("total").title(Bundle.COL_Total());
        table.getColumn("status").title(Bundle.COL_Status());
        table.publishColumns();
        table.refresh();
    }
}
