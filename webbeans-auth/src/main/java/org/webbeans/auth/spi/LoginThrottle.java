package org.webbeans.auth.spi;

/** Rate limiting of login attempts. One implementation is used; the default allows 10 failures per 5 minutes per key. */
public interface LoginThrottle {

    /** @param key the remote address or {@code user:<name>} */
    boolean isBlocked(String key);

    void recordFailure(String key);

    void recordSuccess(String key);
}
