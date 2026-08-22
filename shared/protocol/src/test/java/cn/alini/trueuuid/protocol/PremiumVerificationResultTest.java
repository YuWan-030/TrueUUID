package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumVerificationResultTest {
    @Test void verifiedResultPreservesCanonicalProfile() {
        VerifiedProfile profile = new VerifiedProfile(UUID.randomUUID(), "Alice",
                List.of(new VerifiedProfile.Property("textures", "value", "signature")));
        PremiumVerificationResult result = PremiumVerificationResult.verified(profile);
        assertEquals(profile, result.verifiedProfile().orElseThrow());
    }

    @Test void failedResultCannotMasqueradeAsVerified() {
        assertThrows(IllegalArgumentException.class,
                () -> PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.VERIFIED));
        PremiumVerificationResult failure = PremiumVerificationResult.failed(
                HybridIdentityPolicy.PremiumProof.HTTP_204);
        assertTrue(failure.verifiedProfile().isEmpty());
    }
}
