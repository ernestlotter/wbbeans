package org.webbeans.auth.impl;

import java.time.Duration;

import org.webbeans.auth.Principal;
import org.webbeans.auth.spi.SessionPolicy;

/** Fixed durations from the launcher configuration; concurrent sessions allowed. */
public final class DefaultSessionPolicy implements SessionPolicy {

    private final Duration idle;
    private final Duration maxLifetime;
    private final Duration revalidate;

    public DefaultSessionPolicy(Duration idle, Duration maxLifetime, Duration revalidate) {
        this.idle = idle;
        this.maxLifetime = maxLifetime;
        this.revalidate = revalidate;
    }

    @Override
    public Duration idleTimeout(Principal principal) {
        return idle;
    }

    @Override
    public Duration maxLifetime(Principal principal) {
        return maxLifetime;
    }

    @Override
    public Duration revalidateInterval(Principal principal) {
        return revalidate;
    }

    @Override
    public boolean allowConcurrentSessions(Principal principal) {
        return true;
    }
}
