package org.webbeans.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A further step the user must complete before the login succeeds (second factor, consent, password change). */
public final class Challenge {

    private final String id;
    private final String type;
    private final String title;
    private final List<LoginField> fields;
    private final Map<String, Object> clientData;
    private final Instant expiresAt;

    private Challenge(String id, String type, String title, List<LoginField> fields, Map<String, Object> clientData, Instant expiresAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.fields = Collections.unmodifiableList(fields);
        this.clientData = Collections.unmodifiableMap(clientData);
        this.expiresAt = expiresAt;
    }

    public static Challenge of(String type, String title, LoginField... fields) {
        return new Challenge(UUID.randomUUID().toString(), type, title, Arrays.asList(fields),
                Collections.<String, Object>emptyMap(), Instant.now().plus(Duration.ofMinutes(5)));
    }

    public Challenge withClientData(Map<String, Object> data) {
        return new Challenge(id, type, title, fields, data, expiresAt);
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    /** May be a {@code #KEY}. */
    public String getTitle() {
        return title;
    }

    public List<LoginField> getFields() {
        return fields;
    }

    public Map<String, Object> getClientData() {
        return clientData;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
