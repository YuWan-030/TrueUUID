package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.api.AccountStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Fabric1201StatusPayloadTest {
    @Test void matchesTheFabric1201ServerOwnedStatusContract() {
        assertArrayEquals(new byte[]{1}, Fabric1201StatusPayload.encode(AccountStatus.PREMIUM_VERIFIED));
        assertArrayEquals(new byte[]{2}, Fabric1201StatusPayload.encode(AccountStatus.OFFLINE_FALLBACK));
    }

    @Test void unknownOrNativeStatusCannotBecomePremium() {
        assertThrows(IllegalArgumentException.class,
                () -> Fabric1201StatusPayload.encode(AccountStatus.UNKNOWN));
        assertThrows(IllegalArgumentException.class,
                () -> Fabric1201StatusPayload.encode(AccountStatus.ONLINE_MODE));
    }
}
