package org.webbeans.demo;

import java.util.ArrayList;
import java.util.List;

import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.core.i18n.Messages;
import org.webbeans.graph.Anchor;
import org.webbeans.graph.AnchorShape;
import org.webbeans.graph.ConnectEvent;
import org.webbeans.graph.ConnectProvider;
import org.webbeans.graph.DeleteEvent;
import org.webbeans.graph.EdgeWidget;
import org.webbeans.graph.GraphScene;
import org.webbeans.graph.GraphTopComponent;
import org.webbeans.graph.LineStyle;
import org.webbeans.graph.NodeWidget;
import org.webbeans.graph.NoteWidget;
import org.webbeans.graph.PopupProvider;
import org.webbeans.graph.RenameEvent;
import org.webbeans.graph.Router;
import org.webbeans.graph.Shape;
import org.webbeans.graph.Side;
import org.webbeans.graph.TreeGraphLayout;
import org.webbeans.graph.Widget;
import org.webbeans.graph.WidgetEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UISession;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * A {@link GraphTopComponent} over a {@link GraphScene}: a deployment diagram whose nodes are services (shapes,
 * pins, accents and icons chosen per kind in {@code attachNodeWidget}), edges are calls (routers, anchors, end
 * shapes and labels per kind in {@code attachEdgeWidget}), with notes attached to widgets, a connect provider,
 * in-place renaming, a right-click menu and Delete.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 70)
@TopComponent.Description(preferredID = "DeploymentTC", iconBase = "org/webbeans/demo/icons/cube.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenDeployment", position = 330)
@Messages({"CTL_DeploymentTC=Deployment", "CTL_OpenDeployment=Deployment graph", "MSG_GraphActivated=Opened {0}", "MSG_GraphConnected=Connected {0} to {1}",
    "CTL_ToggleState=Toggle up/down", "CTL_AddNote=Add note", "NOTE_New=New note", "MSG_GraphRemoved=Removed {0}"})
public final class DeploymentTopComponent extends GraphTopComponent {

    /** A node object. */
    public static final class Service {
        final String name;
        final String kind;
        boolean up = true;

        Service(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public String getKind() {
            return kind;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** An edge object. */
    public static final class Call {
        final Service from;
        final Service to;
        final String protocol;

        Call(Service from, Service to, String protocol) {
            this.from = from;
            this.to = to;
            this.protocol = protocol;
        }

        @Override
        public String toString() {
            return from + "→" + to;
        }
    }

    /** The scene: how services and calls look. */
    static final class DeploymentScene extends GraphScene<Service, Call> {
        @Override
        protected NodeWidget attachNodeWidget(Service s) {
            NodeWidget w = newNodeWidget().setLabel(s.name).setSecondaryLabel(s.kind).setLabelEditable(true).setTooltip(s.kind + " " + s.name);
            w.setAccent(s.up ? "#2f8a4a" : "#b43b3b").setAttribute("kind", s.kind);
            if ("database".equals(s.kind)) {
                w.setShape(Shape.CYLINDER).setPreferredSize(110, 70);
                w.addPin("in").setLabel("queries").setSide(Side.TOP);
            } else if ("queue".equals(s.kind)) {
                w.setShape(Shape.HEXAGON).setIcon(Resource.of("org/webbeans/demo/icons/orbit.svg"));
                w.addPin("pub").setLabel("publish");
                w.addPin("sub").setLabel("subscribe");
            } else if ("client".equals(s.kind)) {
                w.setShape(Shape.ICON).setIcon(Resource.of("org/webbeans/demo/icons/planet.svg")).setMovable(false);
            } else {
                w.setIcon(Resource.of("org/webbeans/demo/icons/cube.svg"));
                w.addPin("http").setLabel("HTTP :8080");
                w.addPin("metrics").setLabel("metrics").setSide(Side.BOTTOM);
            }
            return w;
        }

        @Override
        protected EdgeWidget attachEdgeWidget(Call c) {
            EdgeWidget w = newEdgeWidget().setLabel(c.protocol).setTooltip(c.toString());
            if ("async".equals(c.protocol)) {
                w.setRouter(Router.CURVED).setLineStyle(LineStyle.DASHED).setTargetShape(AnchorShape.CIRCLE_HOLLOW).setForeground("#5b6fd6");
            } else if ("replica".equals(c.protocol)) {
                w.setRouter(Router.DIRECT).setLineStyle(LineStyle.DOTTED).setSourceShape(AnchorShape.DIAMOND).setTargetShape(AnchorShape.DIAMOND).setAnchors(Anchor.CIRCULAR);
            } else {
                w.setRouter(Router.ORTHOGONAL).setTargetShape(AnchorShape.TRIANGLE);
            }
            return w;
        }
    }

    private final DeploymentScene scene;
    private int noteCounter;

    public DeploymentTopComponent() {
        this(new DeploymentScene());
    }

    private DeploymentTopComponent(final DeploymentScene scene) {
        super(scene);
        this.scene = scene;
        setDisplayName(Bundle.CTL_DeploymentTC());
        scene.setTestId("deployment").setMinimap(true).setRouter(Router.ORTHOGONAL);
        setGraphLayout(new TreeGraphLayout().setOrientation(TreeGraphLayout.Orientation.LEFT_RIGHT));

        Service browser = new Service("Browser", "client");
        Service web = new Service("web", "service");
        Service api = new Service("api", "service");
        Service queue = new Service("events", "queue");
        Service db = new Service("orders-db", "database");
        Service replica = new Service("orders-replica", "database");
        replica.up = false;
        scene.addNode(browser).setLocation(60, 150);
        scene.addNode(web).setLocation(240, 150);
        scene.addNode(api).setLocation(470, 150);
        scene.addNode(queue).setLocation(470, 320);
        scene.addNode(db).setLocation(720, 150);
        scene.addNode(replica).setLocation(720, 320);
        link(new Call(browser, web, "HTTPS"), null, "http");
        link(new Call(web, api, "REST"), null, "http");
        link(new Call(api, db, "SQL"), null, "in");
        link(new Call(api, queue, "async"), null, "pub");
        link(new Call(queue, web, "async"), "sub", null);
        link(new Call(db, replica, "replica"), null, null);
        scene.addNote("note:1", "Primary store. Replica fails over automatically when the health check fails twice.")
                .setLocation(640, 420).setWidth(190).attachTo(scene.findWidget(replica)).setTextEditable(true);
        scene.addNote("note:2", "Drag from a node's handle or a pin to add a call.\nDouble-click a name to rename it.")
                .setLocation(40, 20).setWidth(220).setBackground("#e3f1fb").setForeground("#2b5a7a");

        scene.setConnectProvider(new ConnectProvider() {
            @Override
            public boolean isSource(Widget source) {
                Object o = source.getObject();
                return o instanceof Service && !"client".equals(((Service) o).kind);
            }

            @Override
            public boolean canConnect(Widget source, Widget target) {
                return EdgeWidget.nodeOf(source) != EdgeWidget.nodeOf(target);
            }

            @Override
            public void connect(Widget source, Widget target) {
                Call c = new Call((Service) source.getObject(), (Service) target.getObject(), "REST");
                scene.addEdge(c).setSource(source).setTarget(target);
            }
        });
        scene.setPopupProvider(new PopupProvider() {
            @Override
            public List<Action> getPopupActions(GraphScene<?, ?> s, Widget widget) {
                List<Action> l = new ArrayList<Action>();
                if (widget != null && widget.getObject() instanceof Service) {
                    l.add(toggleState((Service) widget.getObject()));
                }
                l.add(addNote(widget));
                return l;
            }
        });
        scene.addConnectListener(new Listener<ConnectEvent>() {
            @Override
            public void onEvent(ConnectEvent e) {
                status(Bundle.MSG_GraphConnected(e.getSourceObject(), e.getTargetObject()));
            }
        });
        scene.addActivateListener(new Listener<WidgetEvent>() {
            @Override
            public void onEvent(WidgetEvent e) {
                status(Bundle.MSG_GraphActivated(e.getObject() != null ? e.getObject() : e.getWidget()));
            }
        });
        scene.addRenameListener(new Listener<RenameEvent>() {
            @Override
            public void onEvent(RenameEvent e) {
                e.accept();
            }
        });
        scene.addDeleteListener(new Listener<DeleteEvent>() {
            @Override
            public void onEvent(DeleteEvent e) {
                for (Widget w : e.getWidgets()) {
                    Object o = w.getObject();
                    if (o instanceof Service) {
                        scene.removeNode((Service) o);
                    } else if (o instanceof Call) {
                        scene.removeEdge((Call) o);
                    } else if (w instanceof NoteWidget) {
                        scene.removeNote(w.getId());
                    }
                    status(Bundle.MSG_GraphRemoved(o != null ? o : w.getId()));
                }
            }
        });
    }

    private void link(Call c, String sourcePin, String targetPin) {
        scene.addEdge(c);
        if (sourcePin != null) {
            scene.setEdgeSourcePin(c, c.from, sourcePin);
        } else {
            scene.setEdgeSource(c, c.from);
        }
        if (targetPin != null) {
            scene.setEdgeTargetPin(c, c.to, targetPin);
        } else {
            scene.setEdgeTarget(c, c.to);
        }
    }

    private Action toggleState(final Service s) {
        return new AbstractAction(Bundle.CTL_ToggleState()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                s.up = !s.up;
                scene.findNodeWidget(s).setAccent(s.up ? "#2f8a4a" : "#b43b3b");
            }
        };
    }

    private Action addNote(final Widget target) {
        return new AbstractAction(Bundle.CTL_AddNote()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                NoteWidget n = scene.addNote("note:" + (++noteCounter + 2), Bundle.NOTE_New()).setTextEditable(true);
                if (target instanceof NodeWidget) {
                    n.setLocation(((NodeWidget) target).getLocation().x + 80, ((NodeWidget) target).getLocation().y + 60).attachTo(target);
                } else {
                    n.setLocation(300, 420);
                }
            }
        };
    }

    private static void status(String text) {
        StatusDisplayer sd = StatusDisplayer.getDefault();
        if (sd != null) {
            sd.setStatusText(text);
        }
        UISession.current().publish(new LogLine(text));
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_DeploymentTC());
    }
}
