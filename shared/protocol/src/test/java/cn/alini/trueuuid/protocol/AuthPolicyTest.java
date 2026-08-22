package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthPolicyTest {
    @Test void knownNameIsDeniedWithoutGrace() {
        assertEquals(AuthPolicy.Decision.DENY, AuthPolicy.decide(
                new AuthPolicy.Input(true, false, false, false, true, true, true)));
    }

    @Test void explicitOfflineClientCannotUseGrace() {
        assertEquals(AuthPolicy.Decision.DENY, AuthPolicy.decide(
                new AuthPolicy.Input(true, true, true, true, true, true, true)));
    }

    @Test void explicitOfflineUnknownNameMayUseConfiguredFallback() {
        assertEquals(AuthPolicy.Decision.OFFLINE, AuthPolicy.decide(
                new AuthPolicy.Input(false, true, false, false, true, true, true)));
    }

    @Test void proofFailureNeverDowngradesEvenWhenLegacyFallbackBooleanIsEnabled() {
        assertEquals(AuthPolicy.Decision.DENY, AuthPolicy.decide(
                new AuthPolicy.Input(false, false, true, true, false, true, false)));
    }

    @Test void failureFallbackCanBeDisabled() {
        assertEquals(AuthPolicy.Decision.DENY, AuthPolicy.decide(
                new AuthPolicy.Input(false, false, false, false, false, false, false)));
    }
}
