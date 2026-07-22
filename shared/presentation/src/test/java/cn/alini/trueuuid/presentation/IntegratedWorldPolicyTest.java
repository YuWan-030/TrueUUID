package cn.alini.trueuuid.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedWorldPolicyTest {
    @Test void onlyAnUnpublishedIntegratedWorldIsPrivateSingleplayer() {
        assertTrue(IntegratedWorldPolicy.isPrivateSingleplayer(true, false));
        assertFalse(IntegratedWorldPolicy.isPrivateSingleplayer(true, true));
        assertFalse(IntegratedWorldPolicy.isPrivateSingleplayer(false, false));
        assertFalse(IntegratedWorldPolicy.isPrivateSingleplayer(false, true));
    }
}
