package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

/**
 * Enters a list of short strings as removable chips (keywords, recipients, labels): Enter or comma adds, Backspace
 * removes the last, suggestions complete. The value is the list, without duplicates, in entry order.
 */
@Renderer(id = "wb.TagInput", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class TagInput extends ValueComponent<List<String>> {

    @SuppressWarnings("unchecked")
    public static final Property<List<String>> VALUE = (Property<List<String>>) (Property<?>) Property.of("value", List.class, new ArrayList<String>()).clientOwned();
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> SUGGESTIONS = (Property<List<String>>) (Property<?>) Property.of("suggestions", List.class, new ArrayList<String>());
    /** Only suggestions can be entered. */
    public static final Property<Boolean> RESTRICT = Property.of("restrict", Boolean.class, Boolean.FALSE);
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    public static final Property<Integer> MAX_TAGS = Property.of("maxTags", Integer.class, null);
    public static final Property<Boolean> LOWER_CASE = Property.of("lowerCase", Boolean.class, Boolean.FALSE);

    public TagInput() {
        super("wb.TagInput", VALUE);
    }

    public TagInput(Collection<String> tags) {
        this();
        setValue(new ArrayList<String>(tags));
    }

    @Override
    protected List<String> fromClient(Object raw) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (raw instanceof Collection) {
            for (Object o : (Collection<?>) raw) {
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) {
                    out.add(Boolean.TRUE.equals(get(LOWER_CASE)) ? s.toLowerCase() : s);
                }
            }
        }
        if (Boolean.TRUE.equals(get(RESTRICT))) {
            out.retainAll(get(SUGGESTIONS));
        }
        Integer max = get(MAX_TAGS);
        if (max != null && out.size() > max) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_TooManyTags", max));
        }
        return new ArrayList<String>(out);
    }

    @Override
    protected boolean isEmpty(List<String> v) {
        return v == null || v.isEmpty();
    }

    public List<String> getTags() {
        List<String> v = getValue();
        return v == null ? Collections.<String>emptyList() : Collections.unmodifiableList(v);
    }

    public TagInput setTags(Collection<String> tags) {
        setValue(new ArrayList<String>(new LinkedHashSet<String>(tags)));
        return this;
    }

    public TagInput addTag(String tag) {
        List<String> l = new ArrayList<String>(getTags());
        if (!l.contains(tag)) {
            l.add(tag);
            setValue(l);
        }
        return this;
    }

    public TagInput removeTag(String tag) {
        List<String> l = new ArrayList<String>(getTags());
        if (l.remove(tag)) {
            setValue(l);
        }
        return this;
    }

    public TagInput setSuggestions(Collection<String> s) {
        set(SUGGESTIONS, new ArrayList<String>(s));
        return this;
    }

    public TagInput setRestrict(boolean b) {
        set(RESTRICT, b);
        return this;
    }

    public TagInput setPlaceholder(String p) {
        set(PLACEHOLDER, p);
        return this;
    }

    public TagInput setMaxTags(Integer n) {
        set(MAX_TAGS, n);
        return this;
    }

    public TagInput setLowerCase(boolean b) {
        set(LOWER_CASE, b);
        return this;
    }

    public void focus() {
        call("focus");
    }
}
