package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.api.AccountStatus;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BukkitLoginStatusServiceTest {
    @Test void publishesOnlyFinalHybridStatusesAndClearsOnLogout() {
        UUID uuid = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> method.getName().equals("getUniqueId") ? uuid : null);
        BukkitLoginStatusService service = new BukkitLoginStatusService(null);

        service.publish(player, AccountStatus.PREMIUM_VERIFIED);
        assertEquals(AccountStatus.PREMIUM_VERIFIED, service.statusOf(uuid));
        service.clear(uuid);
        assertEquals(AccountStatus.UNKNOWN, service.statusOf(uuid));
        assertThrows(IllegalArgumentException.class, () -> service.publish(player, AccountStatus.UNKNOWN));
    }
}
