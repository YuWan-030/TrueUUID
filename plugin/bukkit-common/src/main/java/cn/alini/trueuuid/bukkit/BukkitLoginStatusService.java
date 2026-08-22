package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.api.AccountStatusStore;
import cn.alini.trueuuid.server.AuthenticatedIdentity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Lifecycle-safe Bukkit facade over the shared live status store. */
public final class BukkitLoginStatusService implements TrueuuidBukkitApi {
    private final AccountStatusStore<Player> statuses;
    private final Function<UUID, Optional<AuthenticatedIdentity>> identities;

    public BukkitLoginStatusService(BiConsumer<Player, RuntimeException> callbackFailureHandler) {
        this(callbackFailureHandler, ignored -> Optional.empty());
    }

    public BukkitLoginStatusService(
            BiConsumer<Player, RuntimeException> callbackFailureHandler,
            Function<UUID, Optional<AuthenticatedIdentity>> identities
    ) {
        statuses = new AccountStatusStore<>(callbackFailureHandler);
        this.identities = Objects.requireNonNull(identities, "identities");
    }

    public void publish(Player player, AccountStatus status) {
        Objects.requireNonNull(player, "player");
        if (status != AccountStatus.PREMIUM_VERIFIED && status != AccountStatus.OFFLINE_FALLBACK) {
            throw new IllegalArgumentException("unverified hybrid status cannot be published");
        }
        statuses.publish(player, player.getUniqueId(), status);
    }

    public void clear(UUID playerId) {
        statuses.clear(playerId);
    }

    public void clearAll() {
        statuses.clearAll();
    }

    @Override public AccountStatus statusOf(UUID playerId) {
        return statuses.statusOf(playerId);
    }

    @Override public Optional<AuthenticatedIdentity> identityOf(UUID playerId) {
        return identities.apply(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override public void registerLoginCallback(BiConsumer<Player, AccountStatus> callback) {
        statuses.register(Objects.requireNonNull(callback, "callback"));
    }
}
