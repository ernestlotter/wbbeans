package org.webbeans.demo;

import java.util.Arrays;
import java.util.function.Consumer;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.WizardDescriptor;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.components.ComboBox;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.TextField;

/** Planets ▸ New planet wizard: two steps (name and type, then physical data) with validation per step. */
@ActionID(category = "Planets", id = "org.webbeans.demo.PlanetWizardAction")
@ActionRegistration(displayName = "#CTL_PlanetWizard")
@ActionReference(path = "Menu/Planets", position = 400)
@Messages({"CTL_PlanetWizard=New planet wizard...", "STEP_Name=Name and type", "STEP_Physics=Physical data", "TITLE_Wizard=New planet",
    "LBL_Name=Name", "LBL_Type=Type", "LBL_WizGravity=Gravity (m/s²)", "LBL_Moons=Moons", "ERR_WizNameRequired=Enter a name",
    "ERR_NumberRequired={0} must be a number"})
public final class PlanetWizardAction implements ActionListener {

    /** The wizard's settings object. */
    static final class Data {
        String name = "";
        Planet.Type type = Planet.Type.ROCKY;
        double gravity = 9.8;
        int moons = 0;
    }

    static final class NamePanel extends WizardDescriptor.AbstractPanel<Data> {
        private final Panel panel = new Panel(new Layout.Form());
        private final TextField name = new TextField();
        private final ComboBox<Planet.Type> type = new ComboBox<Planet.Type>();
        private WizardDescriptor<Data> wizard;
        private String typed = "";

        NamePanel() {
            super(Bundle.STEP_Name());
            name.setTestId("wizard-name");
            name.addInputListener(new Listener<TextField.InputEvent>() {
                @Override
                public void onEvent(TextField.InputEvent e) {
                    validate(e.getValue());
                }
            });
            name.addChangeListener(new Listener<ChangeEvent<String>>() {
                @Override
                public void onEvent(ChangeEvent<String> e) {
                    validate(e.getValue());
                }
            });
            type.setItems(Arrays.asList(Planet.Type.values()));
            type.setAllowEmpty(false);
            panel.add(name, Layout.Form.row(Bundle.LBL_Name()));
            panel.add(type, Layout.Form.row(Bundle.LBL_Type()));
        }

        private void validate(String v) {
            typed = v == null ? "" : v;
            boolean ok = !typed.trim().isEmpty();
            if (wizard != null) {
                wizard.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, ok ? null : Bundle.ERR_WizNameRequired());
            }
            fireChange();
        }

        @Override
        public Component getComponent() {
            return panel;
        }

        @Override
        public boolean isValid() {
            return !typed.trim().isEmpty();
        }

        @Override
        public void readSettings(Data d) {
            typed = d.name;
            name.setValue(d.name);
            type.setValue(d.type);
        }

        @Override
        public void storeSettings(Data d) {
            d.name = typed.trim();
            d.type = type.getValue() == null ? Planet.Type.ROCKY : type.getValue();
        }

        void attach(WizardDescriptor<Data> w) {
            wizard = w;
        }
    }

    static final class PhysicsPanel extends WizardDescriptor.AbstractPanel<Data> {
        private final Panel panel = new Panel(new Layout.Form());
        private final TextField gravity = new TextField();
        private final TextField moons = new TextField();
        private String gravityText = "";
        private String moonsText = "";

        PhysicsPanel() {
            super(Bundle.STEP_Physics());
            gravity.setTestId("wizard-gravity");
            moons.setTestId("wizard-moons");
            gravity.addInputListener(new Listener<TextField.InputEvent>() {
                @Override
                public void onEvent(TextField.InputEvent e) {
                    gravityText = e.getValue();
                    fireChange();
                }
            });
            moons.addInputListener(new Listener<TextField.InputEvent>() {
                @Override
                public void onEvent(TextField.InputEvent e) {
                    moonsText = e.getValue();
                    fireChange();
                }
            });
            panel.add(gravity, Layout.Form.row(Bundle.LBL_WizGravity()));
            panel.add(moons, Layout.Form.row(Bundle.LBL_Moons()));
        }

        @Override
        public Component getComponent() {
            return panel;
        }

        @Override
        public boolean isValid() {
            return parse(gravityText) != null && parseInt(moonsText) != null;
        }

        private static Double parse(String s) {
            try {
                return s == null ? null : Double.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static Integer parseInt(String s) {
            try {
                return s == null ? null : Integer.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public void readSettings(Data d) {
            gravityText = String.valueOf(d.gravity);
            moonsText = String.valueOf(d.moons);
            gravity.setValue(gravityText);
            moons.setValue(moonsText);
        }

        @Override
        public void storeSettings(Data d) {
            Double g = parse(gravityText);
            Integer m = parseInt(moonsText);
            d.gravity = g == null ? d.gravity : g;
            d.moons = m == null ? d.moons : m;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Data data = new Data();
        data.name = "Planet " + (SolarSystem.get().getPlanets().size() + 1);
        NamePanel first = new NamePanel();
        final WizardDescriptor<Data> w = new WizardDescriptor<Data>(new WizardDescriptor.ArrayIterator<Data>(first, new PhysicsPanel()), data);
        first.attach(w);
        w.setTitle(Bundle.TITLE_Wizard());
        DialogDisplayer.getDefault().notify(w).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object option) {
                if (option == WizardDescriptor.FINISH_OPTION) {
                    Planet p = PlanetsTopComponent.addPlanet(data.name);
                    p.setType(data.type);
                    p.setGravity(data.gravity);
                    p.setMoons(data.moons);
                    SolarSystem.get().planetChanged(p, "type");
                }
            }
        });
    }
}
