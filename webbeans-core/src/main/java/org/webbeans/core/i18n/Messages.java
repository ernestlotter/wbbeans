package org.webbeans.core.i18n;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares localisable messages next to the code that uses them, exactly like NetBeans' {@code @NbBundle.Messages}:
 *
 * <pre>
 * &#64;Messages({"CTL_Save=Save", "# a comment", "MSG_Deleted={0} items deleted"})
 * public class SaveAction { ... Bundle.CTL_Save() ... Bundle.MSG_Deleted(3) ... }
 * </pre>
 *
 * The annotation processor merges the keys into the package's {@code Bundle.properties} (in the class output)
 * and generates a package-private {@code Bundle} class with one static method per key, typed by the
 * {@code {n}} placeholders. The generated methods use the session locale ({@link Bundles}), which is why
 * WebBeans ships its own annotation rather than reusing NetBeans' (whose generated code is bound to
 * {@code Locale.getDefault()}).
 *
 * <p>Translations go into {@code Bundle_<locale>.properties} in the same package as usual.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Messages {
    /** {@code KEY=value} lines; lines starting with {@code #} are comments copied into the properties file. */
    String[] value();
}
