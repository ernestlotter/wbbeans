package org.webbeans.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import org.webbeans.processor.handlers.FolderHandler;
import org.webbeans.processor.handlers.HideHandler;
import org.webbeans.processor.handlers.RegistrationHandler;
import org.webbeans.processor.handlers.RendererHandler;
import org.webbeans.processor.spi.AnnotationHandler;
import org.webbeans.processor.spi.RegistrationModel;

/**
 * The WebBeans annotation processor. Dispatches registration annotations to {@link AnnotationHandler}s
 * (built-in: {@code @Registration}, {@code @Hide}, {@code @Renderer}; platform APIs add theirs through
 * {@code META-INF/services}) and handles {@code @Messages} itself. At the end of the compilation it writes
 * {@code <module>.generated.WbRegistrations} plus its {@code META-INF/services} line, and the merged
 * {@code Bundle.properties} files.
 *
 * <p>Options: {@code -Awebbeans.module=<code name>} selects the package of the generated class
 * (default: the longest common package of the annotated elements).
 */
public final class WbProcessor extends AbstractProcessor {

    public static final String OPTION_MODULE = "webbeans.module";
    public static final String MESSAGES = "org.webbeans.core.i18n.Messages";

    private final Map<String, AnnotationHandler> handlers = new HashMap<String, AnnotationHandler>();
    private RegistrationModel model;
    private MessagesSupport messages;
    private boolean written;
    private RegistrationsWriter registrations;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        model = new RegistrationModel(env.getMessager(), env.getElementUtils());
        messages = new MessagesSupport(env);
        registrations = new RegistrationsWriter(env);
        register(new RegistrationHandler());
        register(new HideHandler());
        register(new FolderHandler());
        register(new RendererHandler());
        try {
            for (AnnotationHandler h : ServiceLoader.load(AnnotationHandler.class, WbProcessor.class.getClassLoader())) {
                register(h);
            }
        } catch (java.util.ServiceConfigurationError e) {
            env.getMessager().printMessage(Diagnostic.Kind.WARNING, "Cannot load WebBeans annotation handlers: " + e);
        }
    }

    private void register(AnnotationHandler h) {
        for (String type : h.getSupportedAnnotationTypes()) {
            handlers.put(type, h);
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new HashSet<String>(handlers.keySet());
        types.add(MESSAGES);
        return types;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton(OPTION_MODULE);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            String name = annotation.getQualifiedName().toString();
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                    if (!mirror.getAnnotationType().toString().equals(name)) {
                        continue;
                    }
                    if (name.equals(MESSAGES)) {
                        messages.collect(element, mirror, model);
                    } else {
                        AnnotationHandler h = handlers.get(name);
                        if (h != null) {
                            h.handle(element, mirror, model, processingEnv);
                        }
                    }
                }
            }
        }
        // Bundle classes must exist while user code is being attributed, so they are generated per round
        messages.writeBundleClasses(model);

        if (model.hasErrors()) {
            return true;
        }
        try {
            if (!roundEnv.processingOver()) {
                // one provider class per round that contributed something; never in the last round (javac warns)
                registrations.writeRound(model, moduleName());
            } else if (!written) {
                written = true;
                registrations.writeServices(model);
                messages.writeProperties(model);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot write WebBeans registrations: " + e);
        }
        return true;
    }

    private String moduleName() {
        String option = processingEnv.getOptions().get(OPTION_MODULE);
        if (option != null && !option.trim().isEmpty()) {
            return option.trim();
        }
        return commonPackage(model);
    }

    /** The longest common package prefix of the originating elements; {@code webbeans} if there is none. */
    private String commonPackage(RegistrationModel model) {
        List<String[]> packages = new ArrayList<String[]>();
        for (Element e : model.getOrigins()) {
            packages.add(model.packageOf(e).split("\\."));
        }
        if (packages.isEmpty()) {
            return "webbeans";
        }
        List<String> common = new ArrayList<String>();
        outer:
        for (int i = 0; ; i++) {
            String segment = null;
            for (String[] p : packages) {
                if (i >= p.length || p[i].isEmpty()) {
                    break outer;
                }
                if (segment == null) {
                    segment = p[i];
                } else if (!segment.equals(p[i])) {
                    break outer;
                }
            }
            common.add(segment);
        }
        if (common.isEmpty()) {
            return "webbeans";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : common) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** Exposed for tests. */
    Map<String, AnnotationHandler> getHandlers() {
        return new LinkedHashMap<String, AnnotationHandler>(handlers);
    }
}
