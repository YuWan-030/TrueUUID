package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.protocol.HybridIdentityPolicy;
import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAuthPort;
import cn.alini.trueuuid.server.UnifiedAdmissionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginDenialMessagesTest {
    @Test void everyCoordinatorDenialHasABoundedPlayerMessage() {
        for (HybridLoginCoordinator.DenialReason reason : HybridLoginCoordinator.DenialReason.values()) {
            String message = LoginDenialMessages.forAttempt(reason,
                    HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE, null, null, null);
            assertFalse(message.isBlank());
            assertTrue(message.length() <= 240);
        }
    }

    @Test void everyPremiumFailureHasABoundedPlayerMessage() {
        for (HybridIdentityPolicy.PremiumProof failure : HybridIdentityPolicy.PremiumProof.values()) {
            if (failure == HybridIdentityPolicy.PremiumProof.VERIFIED) continue;
            String message = LoginDenialMessages.forAttempt(
                    HybridLoginCoordinator.DenialReason.PREMIUM_PROOF_FAILED,
                    HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS, failure, null, null);
            assertFalse(message.isBlank());
            assertTrue(message.length() <= 240);
        }
    }

    @Test void everyOfflineFailureHasABoundedPlayerMessage() {
        for (OfflineAuthPort.Failure failure : OfflineAuthPort.Failure.values()) {
            String message = LoginDenialMessages.forAttempt(
                    HybridLoginCoordinator.DenialReason.OFFLINE_AUTH_FAILED,
                    HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT, null, failure, null);
            assertFalse(message.isBlank());
            assertTrue(message.length() <= 240);
        }
    }

    @Test void premiumAndAuthorityFailuresExplainTheSafeRecovery() {
        String premiumName = LoginDenialMessages.forAttempt(
                HybridLoginCoordinator.DenialReason.IDENTITY_POLICY,
                HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS, null, null, null);
        assertTrue(premiumName.contains("premium Minecraft account"));
        assertTrue(premiumName.contains("offline impersonation is blocked"));

        String unavailable = LoginDenialMessages.forAttempt(
                HybridLoginCoordinator.DenialReason.IDENTITY_POLICY,
                HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE, null, null, null);
        assertTrue(unavailable.contains("lookup is unavailable"));
        assertTrue(unavailable.contains("Try again later"));
    }

    @Test void explicitOfflinePolicyMessagesTakePriority() {
        String explicit = "Offline accounts are disabled on this server. Use a premium account.";
        String actual = LoginDenialMessages.forAttempt(
                HybridLoginCoordinator.DenialReason.OFFLINE_AUTH_FAILED,
                HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT,
                null, OfflineAuthPort.Failure.UNAVAILABLE, explicit);
        assertTrue(actual.equals(explicit));
    }

    @Test void collisionMessagesNameTheChoiceAndRemainBoundedAtMaximumNameLength() {
        for (UnifiedAdmissionPolicy.CollisionResolution resolution
                : UnifiedAdmissionPolicy.CollisionResolution.values()) {
            String message = LoginDenialMessages.collision(
                    "ABCDEFGHIJKLMNOP", "-ABCDEFGHIJKLMNO", resolution);
            assertTrue(message.contains("no identity was changed"));
            assertTrue(message.contains("/trueuuid identity collision allow"));
            assertTrue(message.length() <= 240);
        }
    }
}
