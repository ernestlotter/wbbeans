package org.webbeans.auth.spi;

import org.webbeans.auth.Principal;

/** Fine-grained permission checks beyond roles. All registered authorizers must agree. */
public interface Authorizer {

    /** @param target the object the permission applies to, or null */
    boolean isAllowed(Principal principal, String permission, Object target);
}
