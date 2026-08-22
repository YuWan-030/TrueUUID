package cn.alini.trueuuid.protocol;

import java.util.Objects;

/** Strict classification of the existing TrueUUID client's explicit no-session answer. */
public final class OfflineClientResponse {
    /**
     * Returns true only for the unchanged bounded offline fixture. Malformed,
     * migration, endpoint-bearing, or deceptive responses are proof failures,
     * never offline selection.
     */
    public static boolean isExplicit(AuthMessages.Answer answer) {
        Objects.requireNonNull(answer, "answer");
        return !answer.joined()
                && answer.missingSessionToken()
                && !answer.migrationConfirmed()
                && answer.customEndpoint().isBlank();
    }

    private OfflineClientResponse() {
    }
}
