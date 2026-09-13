package cn.alini.trueuuid.craftbukkit.v1_20_1;

import cn.alini.trueuuid.protocol.AuthMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineTrueuuidClientGateTest {
    @Test void acceptsOnlyTheExistingExplicitMissingSessionShape() {
        assertTrue(TrueuuidPlugin.isExplicitOfflineTrueuuidAnswer(
                new AuthMessages.Answer(false, "", false, true)));
        assertFalse(TrueuuidPlugin.isExplicitOfflineTrueuuidAnswer(
                new AuthMessages.Answer(true, "", false, false)));
        assertFalse(TrueuuidPlugin.isExplicitOfflineTrueuuidAnswer(
                new AuthMessages.Answer(false, "https://attacker.example", false, true)));
        assertFalse(TrueuuidPlugin.isExplicitOfflineTrueuuidAnswer(
                new AuthMessages.Answer(false, "", true, true)));
    }
}
