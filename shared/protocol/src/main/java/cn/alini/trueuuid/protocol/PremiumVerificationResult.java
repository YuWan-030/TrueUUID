package cn.alini.trueuuid.protocol;

import java.util.Objects;
import java.util.Optional;

/** A connection-bound premium proof result with an explicit failure reason. */
public sealed interface PremiumVerificationResult
        permits PremiumVerificationResult.Verified, PremiumVerificationResult.Failed {
    record Verified(VerifiedProfile profile) implements PremiumVerificationResult {
        public Verified {
            Objects.requireNonNull(profile, "profile");
        }
    }

    record Failed(HybridIdentityPolicy.PremiumProof reason) implements PremiumVerificationResult {
        public Failed {
            Objects.requireNonNull(reason, "reason");
            if (reason == HybridIdentityPolicy.PremiumProof.VERIFIED) {
                throw new IllegalArgumentException("VERIFIED is not a failure reason");
            }
        }
    }

    default Optional<VerifiedProfile> verifiedProfile() {
        return this instanceof Verified verified ? Optional.of(verified.profile()) : Optional.empty();
    }

    static Verified verified(VerifiedProfile profile) {
        return new Verified(profile);
    }

    static Failed failed(HybridIdentityPolicy.PremiumProof reason) {
        return new Failed(reason);
    }
}
