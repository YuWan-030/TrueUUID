package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.OfflineAdmissionMode;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure policy shared by Bukkit, Fabric, Forge, and NeoForge adapters. */
public final class UnifiedAdmissionPolicy {
    public sealed interface Decision permits RequirePremiumProof, AllowOffline,
            RequireCollisionConsent, Deny {
    }

    public record RequirePremiumProof(UUID canonicalUuid, String canonicalName) implements Decision {
        public RequirePremiumProof {
            Objects.requireNonNull(canonicalUuid, "canonicalUuid");
            canonicalName = MinecraftNames.requireValid(canonicalName);
        }
    }

    public record AllowOffline(boolean aliasRequired) implements Decision {
    }

    /** A collision that must not mutate either stored identity automatically. */
    public record RequireCollisionConsent(CollisionResolution resolution) implements Decision {
        public RequireCollisionConsent { Objects.requireNonNull(resolution, "resolution"); }
    }

    public enum CollisionResolution {
        /** A new offline identity may join only under a server-owned alias. */
        ALIAS_INCOMING_OFFLINE,
        /** Move the existing offline binding only after premium proof succeeds. */
        MOVE_EXISTING_OFFLINE
    }

    public record Deny(Reason reason) implements Decision {
        public Deny { Objects.requireNonNull(reason, "reason"); }
    }

    public enum Reason {
        AUTHORITY_UNAVAILABLE,
        BLOCKED,
        OFFLINE_DISABLED,
        PREMIUM_RESERVED,
        FIRST_CLAIM_OWNED_BY_PREMIUM,
        FIRST_CLAIM_OWNED_BY_OFFLINE,
        AMBIGUOUS_STORED_STATE,
        AUTHORITY_NAME_MISMATCH
    }

    public record StoredBindings(
            boolean premiumBound,
            boolean offlineBound,
            boolean offlineAliased,
            boolean blocked
    ) {
        public StoredBindings {
            if (offlineAliased && !offlineBound) {
                throw new IllegalArgumentException("an alias requires an offline binding");
            }
        }
    }

    public Decision decide(
            ServerConfiguration configuration,
            String requestedName,
            Optional<UUID> loginStartUuidHint,
            AuthorityResult authority,
            StoredBindings stored
    ) {
        Objects.requireNonNull(configuration, "configuration");
        String requested = MinecraftNames.requireValid(requestedName);
        Objects.requireNonNull(loginStartUuidHint, "loginStartUuidHint");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(stored, "stored");

        if (stored.blocked()) return new Deny(Reason.BLOCKED);
        if (authority instanceof AuthorityResult.Unavailable) {
            return new Deny(Reason.AUTHORITY_UNAVAILABLE);
        }
        if (authority instanceof AuthorityResult.DefinitelyAbsent absent) {
            if (!absent.normalizedRequestedName().equals(MinecraftNames.normalize(requested))) {
                return new Deny(Reason.AUTHORITY_NAME_MISMATCH);
            }
            if (stored.premiumBound()) return new Deny(Reason.AMBIGUOUS_STORED_STATE);
            return offline(configuration, false);
        }

        AuthorityResult.PremiumProfile premium = (AuthorityResult.PremiumProfile) authority;
        if (!premium.matchesRequestedName(requested)) return new Deny(Reason.AUTHORITY_NAME_MISMATCH);
        boolean premiumIntent = loginStartUuidHint
                .map(premium.canonicalUuid()::equals)
                .orElse(false);

        return switch (configuration.admission().mode()) {
            case CONSENT_REQUIRED -> consentRequired(configuration, premium, premiumIntent, stored);
            case PREMIUM_RESERVED -> premiumIntent
                    ? new RequirePremiumProof(premium.canonicalUuid(), premium.canonicalName())
                    : new Deny(Reason.PREMIUM_RESERVED);
            case SAFE_PARALLEL -> premiumIntent
                    ? new RequirePremiumProof(premium.canonicalUuid(), premium.canonicalName())
                    : offline(configuration, true);
            case FIRST_CLAIM -> firstClaim(configuration, premium, premiumIntent, stored);
        };
    }

    private static Decision consentRequired(
            ServerConfiguration configuration,
            AuthorityResult.PremiumProfile premium,
            boolean premiumIntent,
            StoredBindings stored
    ) {
        if (premiumIntent) {
            if (stored.offlineBound() && !stored.offlineAliased()) {
                return new RequireCollisionConsent(CollisionResolution.MOVE_EXISTING_OFFLINE);
            }
            return new RequirePremiumProof(premium.canonicalUuid(), premium.canonicalName());
        }

        if (stored.premiumBound()) {
            if (stored.offlineBound()) return offline(configuration, stored.offlineAliased());
            return configuration.admission().offlineClient() == OfflineAdmissionMode.DENY
                    ? new Deny(Reason.OFFLINE_DISABLED)
                    : new RequireCollisionConsent(CollisionResolution.ALIAS_INCOMING_OFFLINE);
        }

        // A Mojang-existing name is still not proof that this connection is
        // premium. With no local claimant, the configured offline route may
        // take the base name. A later collision requires explicit consent.
        return offline(configuration, false);
    }

    private static Decision firstClaim(
            ServerConfiguration configuration,
            AuthorityResult.PremiumProfile premium,
            boolean premiumIntent,
            StoredBindings stored
    ) {
        if (stored.premiumBound() && stored.offlineBound()) return new Deny(Reason.AMBIGUOUS_STORED_STATE);
        if (stored.premiumBound()) {
            return premiumIntent
                    ? new RequirePremiumProof(premium.canonicalUuid(), premium.canonicalName())
                    : new Deny(Reason.FIRST_CLAIM_OWNED_BY_PREMIUM);
        }
        if (stored.offlineBound()) {
            return premiumIntent
                    ? new Deny(Reason.FIRST_CLAIM_OWNED_BY_OFFLINE)
                    : offline(configuration, false);
        }
        return premiumIntent
                ? new RequirePremiumProof(premium.canonicalUuid(), premium.canonicalName())
                : offline(configuration, false);
    }

    private static Decision offline(ServerConfiguration configuration, boolean aliasRequired) {
        return configuration.admission().offlineClient() == OfflineAdmissionMode.DENY
                ? new Deny(Reason.OFFLINE_DISABLED)
                : new AllowOffline(aliasRequired);
    }
}
