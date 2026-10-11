package org.webbeans.core.registry;

/**
 * Contributes registrations to the {@link Registry}. The annotation processor generates one implementation per
 * module ({@code <module package>.generated.WbRegistrations}) and lists it in
 * {@code META-INF/services/org.webbeans.core.registry.RegistrationProvider}; hand-written providers may be
 * registered the same way.
 */
public interface RegistrationProvider {

    void register(RegistrationBuilder builder);
}
