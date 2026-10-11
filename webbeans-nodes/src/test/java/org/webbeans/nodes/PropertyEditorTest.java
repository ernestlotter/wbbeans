package org.webbeans.nodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.junit.Test;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.nodes.labels.Shade;
import org.webbeans.ui.EnumLabels;
import org.webbeans.ui.Labeled;
import org.webbeans.ui.Resource;
import org.webbeans.ui.table.Cell;

/** Enum property editors: Labeled, bundle keys, prettified names, registered label/icon functions, cells and parsing. */
public class PropertyEditorTest {

    enum Priority implements Labeled {
        HIGH("Urgent", "org/acme/high.svg"), LOW("Can wait", null);

        private final String label;
        private final String icon;

        Priority(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }

        @Override
        public String getDisplayName() {
            return label;
        }

        @Override
        public Resource getIcon() {
            return icon == null ? null : Resource.of(icon);
        }
    }

    enum Plain { GAS_GIANT, iceGiant, ROCKY }

    @Test
    public void labeledEnumsUseTheirDisplayNameAndIcon() {
        PropertyEditor<Priority> e = PropertyEditors.forType(Priority.class);
        assertEquals(PropertyEditor.Kind.CHOICE, e.getKind());
        assertEquals("Urgent", e.getAsText(Priority.HIGH));
        assertEquals("", e.getAsText(null));
        List<PropertyEditor.Choice<Priority>> choices = e.getChoices();
        assertEquals("Can wait", choices.get(1).getLabel());
        assertEquals(Resource.of("org/acme/high.svg"), choices.get(0).getIcon());
        assertNull(choices.get(1).getIcon());
        Cell c = e.getAsCell(Priority.HIGH);
        assertNotNull(c);
        assertEquals("Urgent", c.getText());
        assertNull("no icon → plain text", e.getAsCell(Priority.LOW));
        // labels and names both parse, case-insensitively
        assertEquals(Priority.HIGH, e.fromText("Urgent"));
        assertEquals(Priority.LOW, e.fromText("LOW"));
        assertEquals(Priority.LOW, e.fromText("can wait"));
        assertNull(e.fromText(""));
    }

    @Test
    public void bundleKeysAndPrettifiedNames() {
        PropertyEditor<Shade> e = PropertyEditors.forType(Shade.class);
        LocaleContext.set(Locale.ENGLISH);
        try {
            assertEquals("Dark mode", e.getAsText(Shade.DARK));
            assertEquals("no key for this one → prettified", "System default", e.getAsText(Shade.SYSTEM_DEFAULT));
            LocaleContext.set(Locale.GERMAN);
            assertEquals("Dunkler Modus", e.getAsText(Shade.DARK));
            assertEquals(Shade.DARK, e.fromText("Dunkler Modus"));
        } finally {
            LocaleContext.set(null);
        }
        PropertyEditor<Plain> p = PropertyEditors.forType(Plain.class);
        assertEquals("Gas giant", p.getAsText(Plain.GAS_GIANT));
        assertEquals("Ice giant", p.getAsText(Plain.iceGiant));
        assertEquals("Rocky", p.getAsText(Plain.ROCKY));
        assertEquals(Plain.GAS_GIANT, p.fromText("gas giant"));
        assertEquals("Please wait", EnumLabels.prettify("PLEASE_WAIT"));
    }

    @Test
    public void registeredLabelsAndPerPropertyEditorsWin() {
        PropertyEditors.registerEnum(Plain.class, new Function<Plain, String>() {
            @Override
            public String apply(Plain v) {
                return "Class " + v.ordinal();
            }
        }, new Function<Plain, Resource>() {
            @Override
            public Resource apply(Plain v) {
                return Resource.of("icons/" + v.name().toLowerCase() + ".svg");
            }
        });
        try {
            PropertyEditor<Plain> e = PropertyEditors.forType(Plain.class);
            assertEquals("Class 0", e.getAsText(Plain.GAS_GIANT));
            assertEquals(Resource.of("icons/rocky.svg"), e.getChoices().get(2).getIcon());
            assertEquals("Class 2", e.getAsCell(Plain.ROCKY).getText());

            // an explicit editor for one property overrides the registered labels, and a renderer overrides the editor
            final Plain[] value = {Plain.ROCKY};
            PropertySupport.ReadWrite<Plain> prop = new PropertySupport.ReadWrite<Plain>("kind", Plain.class, "Kind", null) {
                @Override
                public Plain getValue() {
                    return value[0];
                }

                @Override
                public void setValue(Plain v) {
                    value[0] = v;
                }
            };
            prop.setPropertyEditor(PropertyEditors.enumEditor(Plain.class, new Function<Plain, String>() {
                @Override
                public String apply(Plain v) {
                    return v.name().charAt(0) + "";
                }
            }, null));
            assertEquals("R", prop.getValueAsText());
            assertEquals("the registered icon still applies", "R", prop.getValueAsCell().getText());
            prop.setRenderer(new Function<Plain, Cell>() {
                @Override
                public Cell apply(Plain v) {
                    return Cell.of(v.name()).style("kind-" + v.name().toLowerCase());
                }
            });
            assertEquals("ROCKY", prop.getValueAsCell().getText());
            assertTrue(prop.getValueAsCell().toJson().toString().contains("kind-rocky"));
        } finally {
            EnumLabels.register(Plain.class, new Function<Plain, String>() {
                @Override
                public String apply(Plain v) {
                    return null;    // back to the defaults
                }
            }, null);
        }
    }
}
