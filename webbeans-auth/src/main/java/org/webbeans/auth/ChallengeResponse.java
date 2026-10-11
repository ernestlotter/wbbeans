package org.webbeans.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The user's answer to a {@link Challenge} (a second factor, a consent, a password change). */
public final class ChallengeResponse implements Credentials {

    private final String challengeId;
    private final Map<String, String> values;

    public ChallengeResponse(String challengeId, Map<String, String> values) {
        this.challengeId = challengeId;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    public String getChallengeId() {
        return challengeId;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String get(String field) {
        return values.get(field);
    }
}
