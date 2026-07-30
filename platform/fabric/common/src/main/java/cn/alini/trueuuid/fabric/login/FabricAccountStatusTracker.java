package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.api.AccountStatusStore;
import cn.alini.trueuuid.fabric.TrueuuidFabric;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Fabric-side live status for the public addon API, over the shared
 * {@link AccountStatusStore}. Mirrors the Forge/NeoForge adapters so an addon
 * sees the same {@link AccountStatus} on every loader. Status comes only from
 * the server-owned {@link FabricAuthenticationSource}; a client cannot publish.
 */
public final class FabricAccountStatusTracker {
    private static final AccountStatusStore<ServerPlayerEntity> STORE = new AccountStatusStore<>(
            (player, failure) -> TrueuuidFabric.LOGGER.warn("TrueUUID login callback threw for player={}",
                    player == null ? "<unknown>" : player.getUuid(), failure));

    /** Publishes status and notifies addon callbacks; runs on the server thread at join. */
    public static void publish(ServerPlayerEntity player, AccountStatus status) {
        if (player != null) STORE.publish(player, player.getUuid(), status);
    }

    /** Live authentication status for an online player id, or {@link AccountStatus#UNKNOWN}. */
    public static AccountStatus statusOf(UUID playerId) {
        return STORE.statusOf(playerId);
    }

    /** Drops a player's live status when they leave. */
    public static void clear(UUID playerId) {
        STORE.clear(playerId);
    }

    /** Drops all live statuses when the server stops; keeps registered callbacks. */
    public static void clearAll() {
        STORE.clearAll();
    }

    /** Registers an addon login callback (invoked on the server thread at join). */
    public static void register(BiConsumer<ServerPlayerEntity, AccountStatus> callback) {
        STORE.register(callback);
    }

    private FabricAccountStatusTracker() {}
}
