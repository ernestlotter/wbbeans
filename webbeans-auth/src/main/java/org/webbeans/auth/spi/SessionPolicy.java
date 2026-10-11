package org.webbeans.auth.spi;

import java.time.Duration;

import org.webbeans.auth.Principal;

/** Session lifetime rules. One implementation is used (highest position); defaults come from the launcher flags. */
public interface SessionPolicy {

    /** Idle time after which a session ends. */
    Duration idleTimeout(Principal principal);

    /** Absolute maximum lifetime; null = none. */
    Duration maxLifetime(Principal principal);

    /** How often {@code AuthenticationProvider.revalidate} is called; null = never. */
    Duration revalidateInterval(Principal principal);

    /** False: a new login elsewhere ends the older sessions of the same principal. */
    boolean allowConcurrentSessions(Principal principal);
}
