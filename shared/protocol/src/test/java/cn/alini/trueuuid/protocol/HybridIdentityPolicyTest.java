package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HybridIdentityPolicyTest {
    @Test void premiumLockedIdentityAlwaysRequiresPremiumProof() {
        for (HybridIdentityPolicy.AuthorityLookup lookup : HybridIdentityPolicy.AuthorityLookup.values()) {
            assertEquals(HybridIdentityPolicy.LoginRoute.REQUIRE_PREMIUM_PROOF,
                    HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED, lookup));
        }
    }

    @Test void offlineEnrollmentCannotBeSilentlyClaimedByANameLookup() {
        assertEquals(HybridIdentityPolicy.LoginRoute.REQUIRE_OFFLINE_CREDENTIAL,
                HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED,
                        HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT));
        assertEquals(HybridIdentityPolicy.LoginRoute.DENY,
                HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED,
                        HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS));
        assertEquals(HybridIdentityPolicy.LoginRoute.DENY,
                HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED,
                        HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE));
    }

    @Test void unknownIdentityRequiresAnAuthoritativeClassification() {
        assertEquals(HybridIdentityPolicy.LoginRoute.REQUIRE_PREMIUM_PROOF,
                HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.UNKNOWN,
                        HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS));
        assertEquals(HybridIdentityPolicy.LoginRoute.ALLOW_OFFLINE_ENROLLMENT,
                HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.UNKNOWN,
                        HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT));
        assertEquals(HybridIdentityPolicy.LoginRoute.DENY,
                HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.UNKNOWN,
                        HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE));
    }

    @Test void premiumProofFailureNeverDowngradesToOffline() {
        assertEquals(HybridIdentityPolicy.PremiumDecision.ACCEPT_VERIFIED,
                HybridIdentityPolicy.finishPremiumProof(HybridIdentityPolicy.PremiumProof.VERIFIED));

        EnumSet.complementOf(EnumSet.of(HybridIdentityPolicy.PremiumProof.VERIFIED))
                .forEach(proof -> assertEquals(HybridIdentityPolicy.PremiumDecision.DENY,
                        HybridIdentityPolicy.finishPremiumProof(proof), proof.name()));
    }

    @Test void missingSecurityInputsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> HybridIdentityPolicy.route(null, HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS));
        assertThrows(NullPointerException.class,
                () -> HybridIdentityPolicy.route(HybridIdentityPolicy.StoredIdentity.UNKNOWN, null));
        assertThrows(NullPointerException.class,
                () -> HybridIdentityPolicy.finishPremiumProof(null));
    }
}
