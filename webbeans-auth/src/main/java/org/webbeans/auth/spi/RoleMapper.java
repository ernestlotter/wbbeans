package org.webbeans.auth.spi;

import java.util.Set;

import org.webbeans.auth.Principal;

/**
 * Turns provider claims/groups into application roles. Mappers chain in position order; each receives the
 * roles the previous one produced. Register with {@code @ServiceProvider(service = RoleMapper.class)}.
 */
public interface RoleMapper {

    /**
     * @param principal the principal as the provider built it (roles so far in {@code getRoles()})
     * @param provider  the provider that authenticated
     * @return the roles the principal should have
     */
    Set<String> mapRoles(Principal principal, AuthenticationProvider provider);
}
