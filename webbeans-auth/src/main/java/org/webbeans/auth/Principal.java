package org.webbeans.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** An authenticated identity. Immutable; build with {@link #builder(String)}. */
public final class Principal {

    private final String id;
    private final String name;
    private final String displayName;
    private final String email;
    private final Set<String> roles;
    private final Map<String, String> attributes;
    private final String providerId;

    private Principal(Builder b) {
        this.id = b.id;
        this.name = b.name != null ? b.name : b.id;
        this.displayName = b.displayName != null ? b.displayName : this.name;
        this.email = b.email;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<String>(b.roles));
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.attributes));
        this.providerId = b.providerId;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** A copy with different roles (used by the RoleMapper chain). */
    public Principal withRoles(Set<String> newRoles) {
        return new Builder(this).roles(newRoles).build();
    }

    public Principal withProvider(String provider) {
        return new Builder(this).provider(provider).build();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** May be null. */
    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    /** May be null for principals created programmatically. */
    public String getProviderId() {
        return providerId;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... any) {
        for (String r : any) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Principal[" + id + (providerId != null ? "@" + providerId : "") + " roles=" + roles + "]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Principal && ((Principal) o).id.equals(id)
                && (providerId == null ? ((Principal) o).providerId == null : providerId.equals(((Principal) o).providerId));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public static final class Builder {
        private final String id;
        private String name;
        private String displayName;
        private String email;
        private final Set<String> roles = new LinkedHashSet<String>();
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();
        private String providerId;

        private Builder(String id) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Principal id required");
            }
            this.id = id;
        }

        private Builder(Principal p) {
            this.id = p.id;
            this.name = p.name;
            this.displayName = p.displayName;
            this.email = p.email;
            this.roles.addAll(p.roles);
            this.attributes.putAll(p.attributes);
            this.providerId = p.providerId;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder role(String role) {
            if (role != null && !role.trim().isEmpty()) {
                roles.add(role.trim());
            }
            return this;
        }

        public Builder roles(String... roles) {
            return roles(Arrays.asList(roles));
        }

        public Builder roles(Iterable<String> roles) {
            this.roles.clear();
            for (String r : roles) {
                role(r);
            }
            return this;
        }

        public Builder attribute(String key, String value) {
            if (value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public Builder provider(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Principal build() {
            return new Principal(this);
        }
    }
}
