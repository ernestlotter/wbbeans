package org.webbeans.ui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.files.UserFiles;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;
import org.webbeans.core.registry.UserOverlay;

/**
 * One logged-in user: principal, locale, lookup, the serial executor every component access runs on, and
 * the {@link UI}s (browser tabs). {@link #current()} is set while a task runs.
 */
public final class UISession implements Lookup.Provider {

    private static final Logger LOG = Logger.getLogger(UISession.class.getName());
    private static final ThreadLocal<UISession> CURRENT = new ThreadLocal<UISession>();
    private static volatile boolean strictThreading = true;
    private static volatile ScheduledExecutorService scheduler;

    public enum State { ACTIVE, CLOSED }

    private final String id;
    private volatile Authentication authentication;
    private volatile Locale locale;
    private final UserFiles userFiles;
    private final SerialExecutor executor;
    private final Map<String, UI> uis = Collections.synchronizedMap(new LinkedHashMap<String, UI>());
    private final Map<Object, Object> attributes = Collections.synchronizedMap(new LinkedHashMap<Object, Object>());
    private final Map<Class<?>, List<Consumer<Object>>> subscribers = new LinkedHashMap<Class<?>, List<Consumer<Object>>>();
    private final InstanceContent content = new InstanceContent();
    private final Lookup lookup;
    private final List<Runnable> closeListeners = new ArrayList<Runnable>();
    private volatile State state = State.ACTIVE;
    private volatile Instant lastActivity = Instant.now();
    private final Instant created = Instant.now();
    private UserOverlay overlay;
    private final List<Object> sessionServices = new ArrayList<Object>();

    /**
     * @param backend   the shared thread pool tasks run on; {@code Runnable::run} for synchronous tests
     * @param userFiles the user's files, or null (tests)
     */
    public UISession(Authentication authentication, Locale locale, UserFiles userFiles, Executor backend) {
        this.id = "s-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.authentication = authentication;
        this.locale = locale != null ? locale : Locale.getDefault();
        this.userFiles = userFiles;
        this.executor = new SerialExecutor(backend);
        content.add(this);
        content.add(authentication.getPrincipal());
        content.add(authentication);
        if (userFiles != null) {
            content.add(userFiles);
        }
        Lookup services = createSessionServices();
        this.lookup = new ProxyLookup(new AbstractLookup(content), services, Lookup.getDefault());
    }

    private Lookup createSessionServices() {
        InstanceContent ic = new InstanceContent();
        try {
            for (Entry e : Registry.getDefault().folder("Services/Session").getEntries()) {
                Entry t = e.getTarget();
                if (t == null) {
                    continue;
                }
                Object o = t.newInstance(Object.class);
                if (o != null) {
                    sessionServices.add(o);
                    ic.add(o);
                }
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Cannot create session services", e);
        }
        return new AbstractLookup(ic);
    }

    // ---- static context ---------------------------------------------------------------------------------------

    /** The session whose task the current thread is running, or null. */
    public static UISession current() {
        return CURRENT.get();
    }

    /** When true (default), component access off the session executor throws. */
    public static void setStrictThreading(boolean strict) {
        strictThreading = strict;
    }

    public static boolean isStrictThreading() {
        return strictThreading;
    }

    static synchronized ScheduledExecutorService scheduler() {
        if (scheduler == null) {
            ScheduledThreadPoolExecutor s = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "wb-timer-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });
            s.setRemoveOnCancelPolicy(true);
            scheduler = s;
        }
        return scheduler;
    }

    // ---- identity ----------------------------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public Principal getPrincipal() {
        return authentication.getPrincipal();
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    /** Replaces the authentication after a refresh / step-up (same user). */
    public void setAuthentication(Authentication a) {
        if (!a.getPrincipal().getId().equals(authentication.getPrincipal().getId())) {
            throw new IllegalArgumentException("Different principal");
        }
        content.remove(authentication);
        content.remove(authentication.getPrincipal());
        this.authentication = a;
        content.add(a);
        content.add(a.getPrincipal());
    }

    public Locale getLocale() {
        return locale;
    }

    /** Changes the session locale; registry views and bundles follow on the next task. Publishes a {@link LocaleChangeEvent}. */
    public void setLocale(Locale l) {
        if (l == null || l.equals(locale)) {
            return;
        }
        Locale old = locale;
        this.locale = l;
        LocaleContext.set(l);
        RegistryContext.set(registryContext());
        publish(new LocaleChangeEvent(old, l));
    }

    public UserFiles getUserFiles() {
        return userFiles;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    public State getState() {
        return state;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public Instant getCreated() {
        return created;
    }

    public void touch() {
        lastActivity = Instant.now();
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(Class<T> key) {
        return (T) attributes.get(key);
    }

    public <T> void setAttribute(Class<T> key, T value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    /** The user's registry customisations, loaded on first use. */
    public synchronized UserOverlay getOverlay() {
        if (overlay == null) {
            overlay = new UserOverlay();
            if (userFiles != null) {
                try {
                    overlay = UserOverlay.load(userFiles.registryOverlayFile());
                } catch (java.io.IOException e) {
                    LOG.log(Level.WARNING, "Cannot load registry overlay", e);
                }
            }
        }
        return overlay;
    }

    RegistryContext registryContext() {
        return RegistryContext.of(getPrincipal().getRoles(), locale, getOverlay());
    }

    // ---- executor ----------------------------------------------------------------------------------------------

    /** Runs the task on the session executor with the session context installed; flushes touched UIs afterwards. */
    public Future<?> access(final Runnable task) {
        return access(task, null);
    }

    Future<?> access(final Runnable task, final UI ui) {
        final CompletableFuture<Void> done = new CompletableFuture<Void>();
        if (state == State.CLOSED) {
            done.completeExceptionally(new IllegalStateException("Session closed"));
            return done;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                UISession prevSession = CURRENT.get();
                UI prevUI = UI.CURRENT.get();
                CURRENT.set(UISession.this);
                UI.CURRENT.set(ui);
                LocaleContext.set(locale);
                RegistryContext.set(registryContext());
                ClassLoader prevLoader = Thread.currentThread().getContextClassLoader();
                ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
                if (system != null) {
                    Thread.currentThread().setContextClassLoader(system);
                }
                Throwable failure = null;
                try {
                    task.run();
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "Task failed in session " + id, t);
                    failure = t;
                } finally {
                    try {
                        flushAll();
                    } finally {
                        Thread.currentThread().setContextClassLoader(prevLoader);
                        CURRENT.set(prevSession);
                        UI.CURRENT.set(prevUI);
                        LocaleContext.set(null);
                        RegistryContext.set(null);
                        // completed only after the flush: a waiter (the poll handler) may read the transport next
                        if (failure == null) {
                            done.complete(null);
                        } else {
                            done.completeExceptionally(failure);
                        }
                    }
                }
            }
        });
        return done;
    }

    private void flushAll() {
        for (UI ui : new ArrayList<UI>(uis.values())) {
            try {
                ui.flush();
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Flush failed for " + ui, e);
            }
        }
    }

    /** True while the calling thread runs this session's task. */
    public boolean isSessionThread() {
        return executor.isCurrentThread();
    }

    SerialExecutor executor() {
        return executor;
    }

    // ---- UIs ---------------------------------------------------------------------------------------------------

    /** Creates a UI for a new browser tab; its tree is built by the {@link UIProvider} on first connect. */
    public UI createUI(String uiId) {
        if (state == State.CLOSED) {
            throw new IllegalStateException("Session closed");
        }
        UI ui = new UI(this, uiId);
        uis.put(uiId, ui);
        return ui;
    }

    public UI getUI(String uiId) {
        return uis.get(uiId);
    }

    public Collection<UI> getUIs() {
        return new ArrayList<UI>(uis.values());
    }

    void removeUI(UI ui) {
        uis.remove(ui.getId());
    }

    // ---- event bus ---------------------------------------------------------------------------------------------

    /** Delivers the event to subscribers on the session executor. */
    public void publish(final Object event) {
        access(new Runnable() {
            @Override
            public void run() {
                List<Consumer<Object>> targets = new ArrayList<Consumer<Object>>();
                synchronized (subscribers) {
                    for (Map.Entry<Class<?>, List<Consumer<Object>>> e : subscribers.entrySet()) {
                        if (e.getKey().isInstance(event)) {
                            targets.addAll(e.getValue());
                        }
                    }
                }
                for (Consumer<Object> c : targets) {
                    try {
                        c.accept(event);
                    } catch (RuntimeException ex) {
                        LOG.log(Level.WARNING, "Subscriber failed", ex);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <E> Registration subscribe(Class<E> type, final Consumer<E> listener) {
        final Consumer<Object> c = (Consumer<Object>) listener;
        synchronized (subscribers) {
            List<Consumer<Object>> l = subscribers.get(type);
            if (l == null) {
                l = new ArrayList<Consumer<Object>>();
                subscribers.put(type, l);
            }
            l.add(c);
        }
        final Class<E> t = type;
        return new Registration() {
            @Override
            public void remove() {
                synchronized (subscribers) {
                    List<Consumer<Object>> l = subscribers.get(t);
                    if (l != null) {
                        l.remove(c);
                    }
                }
            }
        };
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    public void addCloseListener(Runnable r) {
        synchronized (closeListeners) {
            closeListeners.add(r);
        }
    }

    /** Closes every UI and the session. Safe from any thread. */
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        // close the UIs on the session executor so component detach runs on the right thread
        access(new Runnable() {
            @Override
            public void run() {
                for (UI ui : getUIs()) {
                    ui.closeInternal();
                }
            }
        });
        state = State.CLOSED;
        for (Object s : sessionServices) {
            if (s instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) s).close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Session service close failed", e);
                }
            }
        }
        List<Runnable> ls;
        synchronized (closeListeners) {
            ls = new ArrayList<Runnable>(closeListeners);
        }
        for (Runnable r : ls) {
            try {
                r.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Close listener failed", e);
            }
        }
    }

    @Override
    public String toString() {
        return "UISession[" + id + " " + getPrincipal().getId() + "]";
    }

    /** Published on the session bus when the locale changes. */
    public static final class LocaleChangeEvent {
        private final Locale oldLocale;
        private final Locale newLocale;

        LocaleChangeEvent(Locale oldLocale, Locale newLocale) {
            this.oldLocale = oldLocale;
            this.newLocale = newLocale;
        }

        public Locale getOldLocale() {
            return oldLocale;
        }

        public Locale getNewLocale() {
            return newLocale;
        }
    }
}
