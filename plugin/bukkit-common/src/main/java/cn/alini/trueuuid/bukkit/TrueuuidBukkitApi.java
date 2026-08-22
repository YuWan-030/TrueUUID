package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.server.AuthenticatedIdentity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Bukkit service exposed to server-side addons, including future OfflineAuth. */
public interface TrueuuidBukkitApi {
    AccountStatus statusOf(UUID playerId);

    Optional<AuthenticatedIdentity> identityOf(UUID playerId);

    void registerLoginCallback(BiConsumer<Player, AccountStatus> callback);
}
