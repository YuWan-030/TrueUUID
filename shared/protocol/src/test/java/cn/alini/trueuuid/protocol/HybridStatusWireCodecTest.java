package cn.alini.trueuuid.protocol;

import cn.alini.trueuuid.api.AccountStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridStatusWireCodecTest {
    @Test void premiumAndOfflineFixturesRemainStable() {
        assertEquals(1, Byte.toUnsignedInt(HybridStatusWireCodec.encode(AccountStatus.PREMIUM_VERIFIED)));
        assertEquals(2, Byte.toUnsignedInt(HybridStatusWireCodec.encode(AccountStatus.OFFLINE_FALLBACK)));
        assertEquals(AccountStatus.PREMIUM_VERIFIED, HybridStatusWireCodec.decode(1).orElseThrow());
        assertEquals(AccountStatus.OFFLINE_FALLBACK, HybridStatusWireCodec.decode(2).orElseThrow());
    }

    @Test void unknownValuesNeverBecomePremium() {
        assertTrue(HybridStatusWireCodec.decode(0).isEmpty());
        assertTrue(HybridStatusWireCodec.decode(255).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> HybridStatusWireCodec.encode(AccountStatus.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> HybridStatusWireCodec.encode(AccountStatus.ONLINE_MODE));
    }
}
