package org.webbeans.options;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.openide.util.lookup.Lookups;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionListener;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.dialogs.DialogDescriptor;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;
import org.webbeans.ui.Size;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/** The options dialog: a category strip, a stack of controller components, OK / Apply / Cancel. */
final class DefaultOptionsDisplayer extends OptionsDisplayer {

    static final DefaultOptionsDisplayer INSTANCE = new DefaultOptionsDisplayer();

    /** One category: its entry, controller and (lazily created) component. */
    private static final class Category {
        final Entry entry;
        final OptionsPanelController controller;
        Component component;
        Button button;

        Category(Entry entry, OptionsPanelController controller) {
            this.entry = entry;
            this.controller = controller;
        }
    }

    @Override
    public CompletableFuture<Boolean> open(String categoryId) {
        final UI ui = currentUI();
        final List<Category> cats = new ArrayList<Category>();
        for (Entry e : Registry.getDefault().folder(FOLDER).getEntries()) {
            OptionsPanelController c = e.newInstance(OptionsPanelController.class);
            if (c != null) {
                cats.add(new Category(e, c));
            }
        }
        final CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
        if (cats.isEmpty()) {
            DialogDisplayer.getDefault(ui).notify(new NotifyDescriptor.Message(msg("MSG_NoOptions"), NotifyDescriptor.INFORMATION_MESSAGE));
            result.complete(false);
            return result;
        }
        final Panel content = new Panel(new Layout.Border());
        content.setTestId("options-dialog");
        final Panel strip = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 2));
        strip.setStyleName("wb-options-strip");
        strip.setWidth(Size.px(180));
        final Panel stack = new Panel(new Layout.Stack());
        stack.setStyleName("wb-options-stack");
        stack.setWidth(Size.px(520));
        stack.setHeight(Size.px(360));
        content.add(strip, Layout.Border.WEST);
        content.add(stack, Layout.Border.CENTER);
        final String ok = msg("CTL_OK"), apply = msg("CTL_Apply"), cancel = msg("CTL_Cancel");
        final DialogDescriptor dd = new DialogDescriptor(content, msg("CTL_Options"), true, new Object[] {ok, apply, cancel}, ok,
                DialogDescriptor.BOTTOM_ALIGN, null);
        dd.setClosingOptions(new Object[] {ok, cancel});
        final Category[] selected = new Category[1];
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                boolean valid = true;
                for (Category c : cats) {
                    if (c.component != null && !c.controller.isValid()) {
                        valid = false;
                    }
                }
                dd.setValid(valid);
            }
        };
        final Map<String, Category> byId = new LinkedHashMap<String, Category>();
        for (final Category c : cats) {
            byId.put(c.entry.getName(), c);
            String icon = c.entry.getString("iconBase");
            c.button = new Button(c.entry.getDisplayName());
            c.button.setKind(Button.Kind.LINK);
            c.button.setStyleName("wb-options-cat");
            c.button.setTestId("options-cat-" + c.entry.getName());
            if (icon != null && !icon.isEmpty()) {
                c.button.setIcon(Resource.of(icon));
            }
            c.button.addClickListener(new Listener<ClickEvent>() {
                @Override
                public void onEvent(ClickEvent e) {
                    select(c, cats, stack, selected, refresh, dd);
                }
            });
            strip.add(c.button);
        }
        Category first = categoryId != null && byId.containsKey(categoryId) ? byId.get(categoryId) : cats.get(0);
        select(first, cats, stack, selected, refresh, dd);
        dd.setButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (apply.equals(e.getActionCommand()) || ok.equals(e.getActionCommand())) {
                    for (Category c : cats) {
                        if (c.component != null && c.controller.isChanged()) {
                            c.controller.applyChanges();
                        }
                    }
                }
            }
        });
        DialogDisplayer.getDefault(ui).notify(dd).whenComplete(new BiConsumer<Object, Throwable>() {
            @Override
            public void accept(Object option, Throwable error) {
                boolean accepted = ok.equals(option);
                if (!accepted) {
                    for (Category c : cats) {
                        if (c.component != null) {
                            c.controller.cancel();
                        }
                    }
                }
                result.complete(accepted);
            }
        });
        return result;
    }

    private static void select(Category c, List<Category> cats, Panel stack, Category[] selected, final Runnable refresh, DialogDescriptor dd) {
        if (c.component == null) {
            c.component = c.controller.getComponent(Lookups.singleton(c.controller));
            c.controller.update();
            c.controller.addChangeListener(refresh);
            Panel page = new Panel(new Layout.Border());
            page.setPadding(8);
            Label title = new Label(c.entry.getDisplayName());
            title.setStyleName("wb-options-title");
            page.add(title, Layout.Border.NORTH);
            page.add(c.component, Layout.Border.CENTER);
            page.setTestId("options-page-" + c.entry.getName());
            c.component = page;
            stack.add(page);
        }
        for (Category o : cats) {
            o.button.setStyleName(o == c ? "wb-options-cat wb-options-cat-selected" : "wb-options-cat");
        }
        stack.setSelected(c.component);
        selected[0] = c;
        String helpId = c.controller.getHelpId();
        dd.setHelpCtx(helpId == null || helpId.isEmpty() ? org.webbeans.ui.HelpCtx.DEFAULT_HELP : new org.webbeans.ui.HelpCtx(helpId));
        refresh.run();
    }

    private static String msg(String key) {
        return Bundles.getMessage(DefaultOptionsDisplayer.class, key);
    }
}
