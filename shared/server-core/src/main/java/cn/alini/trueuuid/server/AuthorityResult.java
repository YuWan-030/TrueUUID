package cn.alini.trueuuid.server;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Detailed authoritative name lookup. This classifies a route; it never authenticates a client. */
public sealed interface AuthorityResult permits AuthorityResult.PremiumProfile,
        AuthorityResult.DefinitelyAbsent, AuthorityResult.Unavailable {

    record PremiumProfile(UUID canonicalUuid, String canonicalName) implements AuthorityResult {
        public PremiumProfile {
            Objects.requireNonNull(canonicalUuid, "canonicalUuid");
            canonicalName = MinecraftNames.requireValid(canonicalName);
        }

        public boolean matchesRequestedName(String requestedName) {
            return canonicalName.toLowerCase(Locale.ROOT)
                    .equals(MinecraftNames.normalize(requestedName));
        }
    }

    record DefinitelyAbsent(String normalizedRequestedName) implements AuthorityResult {
        public DefinitelyAbsent {
            normalizedRequestedName = MinecraftNames.normalize(normalizedRequestedName);
        }
    }

    record Unavailable(Failure failure) implements AuthorityResult {
        public Unavailable {
            Objects.requireNonNull(failure, "failure");
        }
    }

    enum Failure {
        TIMEOUT,
        RATE_LIMITED,
        SERVER_ERROR,
        MALFORMED_RESPONSE,
        REDIRECTED,
        TLS_FAILURE,
        DNS_FAILURE,
        CANCELLED,
        NAME_MISMATCH,
        INTERNAL_ERROR
    }
}
