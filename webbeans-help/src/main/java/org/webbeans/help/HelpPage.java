package org.webbeans.help;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A page of the help system, registered under {@code Help/Pages/<id>}: a Markdown ({@code .md}) or HTML
 * resource in the module jar, with a title, an optional parent (the table of contents is a tree) and search
 * keywords. Put it on any class or on {@code package-info.java}; several on one element are allowed.
 *
 * <pre>
 * &#64;HelpPage(id = "files", title = "#HLP_Files", resource = "org/acme/files/help/files.md", position = 200)
 * &#64;HelpPage(id = "files.editor", parent = "files", title = "#HLP_Editor", resource = "org/acme/files/help/editor.md")
 * </pre>
 *
 * Pages link to each other with relative links to the resource ({@code [editor](editor.md)}) or with
 * {@code help:<id>}; images next to the page are served from the module. A {@code <resource>_<lang>.md} next
 * to the page is used for that language. Windows name their page with {@code @TopComponent.Description(helpId)},
 * dialogs with {@code NotifyDescriptor.setHelpCtx}, options pages with {@code OptionsPanelController.getHelpId}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(HelpPages.class)
public @interface HelpPage {
    /** Topic id: letters, digits, '.', '_', '-' (e.g. {@code files.editor}). */
    String id();

    /** Title, or a {@code #key} of the package bundle. */
    String title();

    /** Class-path resource: {@code .md} (Markdown) or {@code .html} (a fragment; sanitised). */
    String resource();

    /** The id of the parent topic; empty for a top-level topic. */
    String parent() default "";

    int position() default Integer.MAX_VALUE;

    /** Extra search terms, comma separated (or a {@code #key}). */
    String keywords() default "";

    String[] roles() default {};
}
