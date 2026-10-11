package org.webbeans.demo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Event;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.Size;
import org.webbeans.ui.components.Accordion;
import org.webbeans.ui.components.Avatar;
import org.webbeans.ui.components.Badge;
import org.webbeans.ui.components.Breadcrumb;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.ButtonGroup;
import org.webbeans.ui.components.Card;
import org.webbeans.ui.components.Chip;
import org.webbeans.ui.components.ColorPicker;
import org.webbeans.ui.components.EmptyState;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.PasswordField;
import org.webbeans.ui.components.Popover;
import org.webbeans.ui.components.PropertyGrid;
import org.webbeans.ui.components.ScrollPane;
import org.webbeans.ui.components.SplitButton;
import org.webbeans.ui.components.SplitPane;
import org.webbeans.ui.components.Stepper;
import org.webbeans.ui.components.TagInput;
import org.webbeans.ui.components.TimePicker;
import org.webbeans.ui.components.ToggleButton;
import org.webbeans.ui.table.Cell;
import org.webbeans.ui.table.TreeTable;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * Step 12: the small widgets (toggle and split buttons, colour/time pickers, password strength, tags, badges,
 * chips, avatars, cards, accordion, breadcrumb, stepper, empty state, popover, busy panels), a
 * {@link PropertyGrid} over a {@link Planet} and a {@link TreeTable} of the solar system by planet type.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 180)
@TopComponent.Description(preferredID = "WidgetsTC", iconBase = "org/webbeans/demo/icons/props.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenWidgets", position = 180)
@Messages({
    "CTL_WidgetsTC=Widgets",
    "CTL_OpenWidgets=Widgets",
    "SEC_Buttons=Buttons",
    "SEC_Inputs=Inputs",
    "SEC_Display=Display",
    "SEC_Navigation=Navigation",
    "LBL_Orbits=Show orbits",
    "LBL_PlanetType=Planet type",
    "LBL_Color=Colour",
    "LBL_Launch=Launch time",
    "LBL_Password=Password",
    "LBL_Tags=Tags",
    "LBL_Save=Save",
    "LBL_SaveAs=Save as…",
    "LBL_SaveAll=Save all",
    "LBL_Busy=Busy for 2 s",
    "LBL_Scanning=Scanning…",
    "LBL_Details=Details",
    "LBL_Nothing=Nothing here yet",
    "LBL_NothingText=Add a planet with File › New Planet, or pick one in the explorer.",
    "LBL_AddPlanet=New planet",
    "STEP_Choose=Choose",
    "STEP_Configure=Configure",
    "STEP_Launch=Launch",
    "STEP_ChooseDesc=Pick a planet",
    "STEP_ConfigureDesc=Set the mission",
    "STEP_LaunchDesc=Go",
    "MSG_Widget={0}: {1}",
    "COL_Name=Name",
    "COL_Explored=Explored",
    "PROP_GravityUnit=Gravity (m/s²)",
    "CAT_Identity=Identity",
    "CAT_Physics=Physics"
})
public final class WidgetsTopComponent extends TopComponent {

    private final Label log = new Label();
    private final Stepper stepper = new Stepper(Bundle.STEP_Choose(), Bundle.STEP_Configure(), Bundle.STEP_Launch());

    public WidgetsTopComponent() {
        setDisplayName(Bundle.CTL_WidgetsTC());
        setLayout(new Layout.Fill());
        log.setTestId("widgetLog");

        Panel left = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 10));
        left.setPadding(12);
        left.add(section(Bundle.SEC_Buttons(), buttons()));
        left.add(section(Bundle.SEC_Inputs(), inputs()));
        left.add(section(Bundle.SEC_Display(), display()));
        left.add(section(Bundle.SEC_Navigation(), navigation()));
        left.add(log);

        SplitPane split = new SplitPane(SplitPane.Orientation.HORIZONTAL, new ScrollPane(left), right());
        split.setPosition(560);
        add(split);
    }

    private static Panel section(String title, Panel body) {
        Panel p = new Panel(new Layout.Fill());
        p.setTitle(title);
        p.add(body);
        return p;
    }

    private void note(String what, Object value) {
        log.setText(Bundle.MSG_Widget(what, String.valueOf(value)));
    }

    private Panel buttons() {
        Panel p = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 10).wrap(true).align(Layout.Align.CENTER));
        p.setPadding(8);
        final ToggleButton orbits = new ToggleButton(Bundle.LBL_Orbits(), true);
        orbits.setTestId("toggle");
        orbits.addChangeListener(new Listener<ChangeEvent<Boolean>>() {
            @Override
            public void onEvent(ChangeEvent<Boolean> e) {
                note("toggle", orbits.isSelected());
            }
        });
        p.add(orbits);
        final ButtonGroup<Planet.Type> types = ButtonGroup.of(Planet.Type.class);
        types.setValue(Planet.Type.ROCKY).setTestId("buttonGroup");
        types.addChangeListener(new Listener<ChangeEvent<Planet.Type>>() {
            @Override
            public void onEvent(ChangeEvent<Planet.Type> e) {
                note("group", types.getValue());
            }
        });
        p.add(types);
        SplitButton save = new SplitButton(Bundle.LBL_Save());
        save.setKind(Button.Kind.PRIMARY).addItem("saveAs", Bundle.LBL_SaveAs()).addItem("saveAll", Bundle.LBL_SaveAll()).setTestId("splitButton");
        save.onClick(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                note("split", "save");
            }
        });
        save.addSelectListener(new Listener<SplitButton.SelectEvent>() {
            @Override
            public void onEvent(SplitButton.SelectEvent e) {
                note("split", e.getKey());
            }
        });
        p.add(save);
        final Panel busyHost = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 6));
        busyHost.setTestId("busyPanel");
        final Button busy = new Button(Bundle.LBL_Busy());
        busy.setTestId("busy");
        busy.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                busyHost.setBusy(true, Bundle.LBL_Scanning());
                getUI().schedule(new Runnable() {
                    @Override
                    public void run() {
                        busyHost.setBusy(false);
                        note("busy", "done");
                    }
                }, 1500);
            }
        });
        busyHost.add(busy);
        p.add(busyHost);
        return p;
    }

    private Panel inputs() {
        Panel form = new Panel(new Layout.Form().labelWidth(Size.px(110)));
        form.setPadding(8);
        final ColorPicker color = new ColorPicker("#1565c0");
        color.setClearable(true).setTestId("color");
        color.addChangeListener(new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                note("color", color.getValue());
            }
        });
        form.addRow(Bundle.LBL_Color(), color);
        final TimePicker time = new TimePicker(LocalTime.of(9, 30));
        time.setMin(LocalTime.of(6, 0)).setMax(LocalTime.of(22, 0)).setStepMinutes(15).setTestId("time");
        time.addChangeListener(new Listener<ChangeEvent<LocalTime>>() {
            @Override
            public void onEvent(ChangeEvent<LocalTime> e) {
                note("time", time.getValue());
            }
        });
        form.addRow(Bundle.LBL_Launch(), time);
        final PasswordField password = new PasswordField();
        password.setStrengthMeter(true).setPlaceholder("••••••••").setTestId("password");
        password.addChangeListener(new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                note("password", password.getStrength());
            }
        });
        form.addRow(Bundle.LBL_Password(), password);
        final TagInput tags = new TagInput(Arrays.asList("rocky", "inner"));
        tags.setSuggestions(Arrays.asList("rocky", "inner", "outer", "gas", "ice", "dwarf", "explored")).setMaxTags(6).setTestId("tags");
        tags.addChangeListener(new Listener<ChangeEvent<List<String>>>() {
            @Override
            public void onEvent(ChangeEvent<List<String>> e) {
                note("tags", tags.getTags());
            }
        });
        form.addRow(Bundle.LBL_Tags(), tags);
        return form;
    }

    private Panel display() {
        Panel p = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 10));
        p.setPadding(8);
        Panel row = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8).wrap(true).align(Layout.Align.CENTER));
        row.add(new Badge("3", Badge.Kind.ACCENT).setTestId("badge"));
        row.add(new Badge("NEW", Badge.Kind.SUCCESS).setPill(false));
        row.add(new Badge("Failed", Badge.Kind.DANGER));
        row.add(new Badge("", Badge.Kind.WARNING).setDot(true));
        for (final Planet.Type t : Planet.Type.values()) {
            final Chip chip = new Chip(t.getDisplayName());
            chip.setIcon(t.getIcon()).setSelectable(true).setClosable(t == Planet.Type.DWARF).setTestId("chip-" + t.name().toLowerCase());
            chip.addSelectListener(new Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    note("chip", t + (chip.isSelected() ? " on" : " off"));
                }
            });
            chip.addCloseListener(new Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    chip.setVisible(false);
                    note("chip", "closed");
                }
            });
            row.add(chip);
        }
        row.add(new Avatar("Ada Lovelace").setStatus(Avatar.Status.ONLINE).setTestId("avatar"));
        row.add(new Avatar("Grace Hopper").setStatus(Avatar.Status.BUSY).setSize(40));
        row.add(new Avatar("Sun", Resource.of("org/webbeans/demo/icons/sun.svg")).setSquare(true));
        p.add(row);

        Panel cards = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 10).wrap(true));
        for (final Planet planet : SolarSystem.get().getPlanets().subList(0, 3)) {
            final Card card = new Card(planet.getName());
            card.setSubtitle(planet.getType().getDisplayName()).setIcon(planet.getType().getIcon()).setClickable(true).setTestId("card-" + planet.getName().toLowerCase());
            card.add(new Label(Bundle.COL_Moons() + ": " + planet.getMoons()));
            card.add(new Label(Bundle.COL_Gravity() + ": " + planet.getGravity()));
            card.setWidth(Size.px(180));
            card.onClick(new Listener<ClickEvent>() {
                @Override
                public void onEvent(ClickEvent e) {
                    card.setCardSelected(!Boolean.TRUE.equals(card.get(Card.CARD_SELECTED)));
                    note("card", planet.getName());
                }
            });
            cards.add(card);
        }
        p.add(cards);

        Accordion acc = new Accordion();
        acc.setTestId("accordion");
        acc.addSection(new Accordion.Section(Bundle.LBL_Details(), Resource.of("org/webbeans/demo/icons/info.svg"), "2"), new Label("Mercury is the smallest planet."));
        acc.addSection(Bundle.LBL_Nothing(), new EmptyState(Bundle.LBL_Nothing(), Bundle.LBL_NothingText()).setIcon(Resource.of("org/webbeans/demo/icons/planet.svg"))
                .setAction(Bundle.LBL_AddPlanet(), new Listener<ClickEvent>() {
                    @Override
                    public void onEvent(ClickEvent e) {
                        note("empty", "add");
                    }
                }).setTestId("emptyState"));
        acc.addToggleListener(new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                note("accordion", e.getData().get("expanded"));
            }
        });
        p.add(acc);
        return p;
    }

    private Panel navigation() {
        Panel p = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 10));
        p.setPadding(8);
        final Breadcrumb crumbs = new Breadcrumb();
        crumbs.add("home", "Solar system", Resource.of("org/webbeans/demo/icons/sun.svg")).add("inner", "Inner planets").add("earth", "Earth").add("moon", "Moon").setTestId("breadcrumb");
        crumbs.addSelectListener(new Listener<Breadcrumb.SelectEvent>() {
            @Override
            public void onEvent(Breadcrumb.SelectEvent e) {
                crumbs.truncateTo(e.getKey());
                note("breadcrumb", e.getKey());
            }
        });
        p.add(crumbs);
        stepper.clearSteps().addStep(Bundle.STEP_Choose(), Bundle.STEP_ChooseDesc()).addStep(Bundle.STEP_Configure(), Bundle.STEP_ConfigureDesc()).addStep(Bundle.STEP_Launch(), Bundle.STEP_LaunchDesc());
        stepper.setCurrent(1).setClickable(true).setTestId("stepper");
        stepper.addSelectListener(new Listener<Stepper.SelectEvent>() {
            @Override
            public void onEvent(Stepper.SelectEvent e) {
                stepper.setCurrent(e.getIndex());
                note("stepper", e.getIndex());
            }
        });
        Panel stepRow = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8).align(Layout.Align.CENTER));
        Button next = new Button("›");
        next.setTestId("stepNext");
        next.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                stepper.next();
                note("stepper", stepper.getCurrent());
            }
        });
        stepRow.add(stepper);
        stepRow.add(next);
        p.add(stepRow);

        final Button more = new Button(Bundle.LBL_Details());
        more.setTestId("popoverButton");
        Panel content = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 6));
        content.add(new Label("Earth: 1 moon, 9.81 m/s²"));
        Button ok = new Button("OK");
        ok.setTestId("popoverOk");
        content.add(ok);
        final Popover popover = new Popover(more, content);
        popover.setTestId("popover");
        ok.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                popover.close();
                note("popover", "ok");
            }
        });
        more.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                popover.toggle();
            }
        });
        p.add(more);
        p.add(popover);
        return p;
    }

    private Panel right() {
        Panel p = new Panel(new Layout.Border());
        // property grid over the first planet
        final PropertyGrid<Planet> grid = new PropertyGrid<Planet>();
        grid.setShowDescription(true).setTestId("propertyGrid");
        grid.addProperty("name", Bundle.PROP_Name(), String.class, new Function<Planet, String>() {
            @Override
            public String apply(Planet pl) {
                return pl.getName();
            }
        }, new BiConsumer<Planet, String>() {
            @Override
            public void accept(Planet pl, String v) {
                if (v == null || v.trim().isEmpty()) {
                    throw new IllegalArgumentException("Name required");
                }
                pl.setName(v.trim());
                SolarSystem.get().planetChanged(pl, "name");
            }
        }).category(Bundle.CAT_Identity()).description("The planet's name");
        grid.addProperty("type", Bundle.PROP_Type(), Planet.Type.class, new Function<Planet, Planet.Type>() {
            @Override
            public Planet.Type apply(Planet pl) {
                return pl.getType();
            }
        }, new BiConsumer<Planet, Planet.Type>() {
            @Override
            public void accept(Planet pl, Planet.Type v) {
                pl.setType(v);
                SolarSystem.get().planetChanged(pl, "type");
            }
        }).category(Bundle.CAT_Identity());
        grid.addProperty("gravity", Bundle.PROP_GravityUnit(), Double.class, new Function<Planet, Double>() {
            @Override
            public Double apply(Planet pl) {
                return pl.getGravity();
            }
        }, new BiConsumer<Planet, Double>() {
            @Override
            public void accept(Planet pl, Double v) {
                if (v == null || v <= 0) {
                    throw new IllegalArgumentException("Must be positive");
                }
                pl.setGravity(v);
                SolarSystem.get().planetChanged(pl, "gravity");
            }
        }).category(Bundle.CAT_Physics()).renderer(new Function<Double, Cell>() {
            @Override
            public Cell apply(Double g) {
                return g == null ? Cell.of("") : Cell.of(g + " m/s²").style(g > 15 ? "wb-strong" : null);
            }
        });
        grid.addProperty("moons", Bundle.PROP_Moons(), Integer.class, new Function<Planet, Integer>() {
            @Override
            public Integer apply(Planet pl) {
                return pl.getMoons();
            }
        }, new BiConsumer<Planet, Integer>() {
            @Override
            public void accept(Planet pl, Integer v) {
                pl.setMoons(v == null ? 0 : v);
                SolarSystem.get().planetChanged(pl, "moons");
            }
        }).category(Bundle.CAT_Physics());
        grid.addProperty("explored", Bundle.PROP_Explored(), Boolean.class, new Function<Planet, Boolean>() {
            @Override
            public Boolean apply(Planet pl) {
                return pl.isExplored();
            }
        }, new BiConsumer<Planet, Boolean>() {
            @Override
            public void accept(Planet pl, Boolean v) {
                pl.setExplored(Boolean.TRUE.equals(v));
                SolarSystem.get().planetChanged(pl, "explored");
            }
        }).category(Bundle.CAT_Physics());
        grid.setBean(SolarSystem.get().getPlanets().get(0));
        grid.addChangeListener(new Listener<PropertyGrid.ChangeEvent>() {
            @Override
            public void onEvent(PropertyGrid.ChangeEvent e) {
                note("property", e.getPropertyId() + "=" + e.getNewValue());
            }
        });

        // tree table: planets grouped by type
        final TreeTable<Object> tree = new TreeTable<Object>();
        tree.addColumn(Bundle.COL_Name(), new Function<Object, Object>() {
            @Override
            public Object apply(Object o) {
                return o instanceof Planet.Type ? Cell.of(((Planet.Type) o).getDisplayName(), ((Planet.Type) o).getIcon()) : ((Planet) o).getName();
            }
        }).width(200);
        tree.addColumn(Bundle.COL_Gravity(), new Function<Object, Object>() {
            @Override
            public Object apply(Object o) {
                return o instanceof Planet ? Double.valueOf(((Planet) o).getGravity()) : null;
            }
        }).align(org.webbeans.ui.table.Column.Align.RIGHT);
        tree.addColumn(Bundle.COL_Moons(), new Function<Object, Object>() {
            @Override
            public Object apply(Object o) {
                return o instanceof Planet ? Integer.valueOf(((Planet) o).getMoons()) : Integer.valueOf(moonsOf((Planet.Type) o));
            }
        }).align(org.webbeans.ui.table.Column.Align.RIGHT);
        tree.addColumn(Bundle.COL_Explored(), new Function<Object, Object>() {
            @Override
            public Object apply(Object o) {
                return o instanceof Planet ? (((Planet) o).isExplored() ? "✓" : "") : "";
            }
        });
        tree.setTree(new ArrayList<Object>(Arrays.asList(Planet.Type.values())), new Function<Object, List<Object>>() {
            @Override
            public List<Object> apply(Object o) {
                List<Object> out = new ArrayList<Object>();
                if (o instanceof Planet.Type) {
                    for (Planet pl : SolarSystem.get().getPlanets()) {
                        if (pl.getType() == o) {
                            out.add(pl);
                        }
                    }
                }
                return out;
            }
        });
        tree.setShowFilter(true).setTestId("treeTable");
        tree.expand(Planet.Type.ROCKY);
        tree.addSelectionListener(new Listener<org.webbeans.ui.table.Table.SelectionEvent>() {
            @Override
            public void onEvent(org.webbeans.ui.table.Table.SelectionEvent e) {
                Object o = tree.getSelectedRow();
                if (o instanceof Planet) {
                    grid.setBean((Planet) o);
                    StatusDisplayer.getDefault().setStatusText(((Planet) o).getName());
                }
            }
        });
        SplitPane split = new SplitPane(SplitPane.Orientation.VERTICAL, tree, grid);
        split.setPosition(300);
        p.add(split, Layout.Border.CENTER);
        return p;
    }

    private static int moonsOf(Planet.Type t) {
        int n = 0;
        for (Planet pl : SolarSystem.get().getPlanets()) {
            if (pl.getType() == t) {
                n += pl.getMoons();
            }
        }
        return n;
    }
}
