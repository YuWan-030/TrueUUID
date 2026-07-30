package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.api.AccountStatusStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Live per-session status for the public addon API, mirroring the 1.21 line's
 * {@code ForgeAdapterRuntime} surface over the shared {@link AccountStatusStore}.
 * Concurrent because addons may query it off the server thread; entries live
 * from login to logout.
 */
public final class AccountStatusTracker {
    private static final AccountStatusStore<ServerPlayer> STORE = new AccountStatusStore<>(
            (player, failure) -> Trueuuid.LOGGER.warn("TrueUUID login callback threw for player={}",
                    player.getUUID(), failure));

    /** Publishes status and notifies addon callbacks; runs on the server thread at join. */
    static void publish(ServerPlayer player, AccountStatus status) {
        STORE.publish(player, player.getUUID(), status);
    }

    /** Drops a player's live status when they leave. */
    static void clear(UUID playerId) {
        STORE.clear(playerId);
    }

    /** Live authentication status for an online player id. */
    public static AccountStatus statusOf(UUID playerId) {
        return STORE.statusOf(playerId);
    }

    /** Registers an addon login callback (invoked on the server thread at join). */
    public static void registerLoginCallback(BiConsumer<ServerPlayer, AccountStatus> callback) {
        STORE.register(callback);
    }

    private AccountStatusTracker() {}
}
