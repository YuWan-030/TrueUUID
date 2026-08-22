package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineClientResponseTest {
    @Test void onlyExactNoSessionFixtureSelectsOffline() {
        assertTrue(OfflineClientResponse.isExplicit(
                new AuthMessages.Answer(false, "", false, true)));
        assertFalse(OfflineClientResponse.isExplicit(
                new AuthMessages.Answer(false, "", false, false)));
        assertFalse(OfflineClientResponse.isExplicit(
                new AuthMessages.Answer(false, "https://example.invalid", false, true)));
        assertFalse(OfflineClientResponse.isExplicit(
                new AuthMessages.Answer(false, "", true, true)));
        assertFalse(OfflineClientResponse.isExplicit(
                new AuthMessages.Answer(true, "", false, true)));
    }
}
