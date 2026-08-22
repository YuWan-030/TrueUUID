package cn.alini.trueuuid.protocol;

import java.util.Objects;

/**
 * Fail-closed routing policy for a server that accepts both premium and
 * explicitly enrolled offline identities. Classification only selects the
 * proof flow; it never proves ownership of a premium account.
 */
public final class HybridIdentityPolicy {
    public enum StoredIdentity {
        PREMIUM_LOCKED,
        OFFLINE_ENROLLED,
        UNKNOWN
    }

    public enum AuthorityLookup {
        PREMIUM_EXISTS,
        DEFINITELY_ABSENT,
        UNAVAILABLE
    }

    public enum LoginRoute {
        REQUIRE_PREMIUM_PROOF,
        REQUIRE_OFFLINE_CREDENTIAL,
        ALLOW_OFFLINE_ENROLLMENT,
        DENY
    }

    public enum PremiumProof {
        VERIFIED,
        NOT_JOINED,
        HTTP_204,
        RATE_LIMITED,
        SERVER_ERROR,
        AUTHORITY_UNAVAILABLE,
        MALFORMED_RESPONSE,
        OVERSIZED_RESPONSE,
        REDIRECTED,
        TLS_FAILURE,
        DNS_FAILURE,
        NAME_MISMATCH,
        UUID_MISMATCH,
        WRONG_VERIFY_TOKEN,
        REPLAYED,
        CLIENT_ABORTED,
        CANCELLED,
        TIMEOUT,
        INTERNAL_ERROR
    }

    public enum PremiumDecision {
        ACCEPT_VERIFIED,
        DENY
    }

    /**
     * Selects the next authentication flow. An authority error is never
     * interpreted as proof that an unknown name is available for offline use.
     */
    public static LoginRoute route(StoredIdentity storedIdentity, AuthorityLookup authorityLookup) {
        Objects.requireNonNull(storedIdentity, "storedIdentity");
        Objects.requireNonNull(authorityLookup, "authorityLookup");

        return switch (storedIdentity) {
            case PREMIUM_LOCKED -> LoginRoute.REQUIRE_PREMIUM_PROOF;
            case OFFLINE_ENROLLED -> switch (authorityLookup) {
                case DEFINITELY_ABSENT -> LoginRoute.REQUIRE_OFFLINE_CREDENTIAL;
                // A name that became premium after local enrollment is an
                // ownership collision. Neither side takes it over implicitly.
                case PREMIUM_EXISTS, UNAVAILABLE -> LoginRoute.DENY;
            };
            case UNKNOWN -> switch (authorityLookup) {
                case PREMIUM_EXISTS -> LoginRoute.REQUIRE_PREMIUM_PROOF;
                case DEFINITELY_ABSENT -> LoginRoute.ALLOW_OFFLINE_ENROLLMENT;
                case UNAVAILABLE -> LoginRoute.DENY;
            };
        };
    }

    /**
     * Completes a premium route. Every failure remains a denial and must not
     * be converted to offline fallback by a platform adapter.
     */
    public static PremiumDecision finishPremiumProof(PremiumProof proof) {
        Objects.requireNonNull(proof, "proof");
        return proof == PremiumProof.VERIFIED
                ? PremiumDecision.ACCEPT_VERIFIED
                : PremiumDecision.DENY;
    }

    private HybridIdentityPolicy() {
    }
}
