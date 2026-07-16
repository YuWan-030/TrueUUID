package cn.alini.trueuuid.fabric.login;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineFallbackPolicyTest {
    @Test
    void deniesEverythingWhenFallbackDisabled() {
        assertFalse(OfflineFallbackPolicy.permits(false, false, true, true));
        assertFalse(OfflineFallbackPolicy.permits(true, false, false, false));
    }

    @Test
    void permitsUnknownNames() {
        assertTrue(OfflineFallbackPolicy.permits(false, true, true, true));
        assertTrue(OfflineFallbackPolicy.permits(false, true, false, false));
    }

    @Test
    void deniesKnownPremiumNamesUnderDefaultPolicy() {
        assertFalse(OfflineFallbackPolicy.permits(true, true, true, true));
        assertFalse(OfflineFallbackPolicy.permits(true, true, true, false));
        assertFalse(OfflineFallbackPolicy.permits(true, true, false, true));
    }

    @Test
    void permitsKnownNamesOnlyWhenBothProtectionsAreOff() {
        assertTrue(OfflineFallbackPolicy.permits(true, true, false, false));
    }
}
