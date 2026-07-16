package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.api.AccountStatus;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Live per-session status for the public addon API, mirroring the 1.21 line's
 * {@code ForgeAdapterRuntime} surface. Concurrent because addons may query it
 * off the server thread; entries live from login to logout.
 */
public final class AccountStatusTracker {
    private static final Map<UUID, AccountStatus> liveStatus = new ConcurrentHashMap<>();
    private static final List<BiConsumer<ServerPlayer, AccountStatus>> loginCallbacks = new CopyOnWriteArrayList<>();

    /** Publishes status and notifies addon callbacks; runs on the server thread at join. */
    static void publish(ServerPlayer player, AccountStatus status) {
        liveStatus.put(player.getUUID(), status);
        for (BiConsumer<ServerPlayer, AccountStatus> callback : loginCallbacks) {
            try {
                callback.accept(player, status);
            } catch (RuntimeException failure) {
                Trueuuid.LOGGER.warn("TrueUUID login callback threw for player={}", player.getUUID(), failure);
            }
        }
    }

    /** Drops a player's live status when they leave. */
    static void clear(UUID playerId) {
        if (playerId != null) liveStatus.remove(playerId);
    }

    /** Live authentication status for an online player id. */
    public static AccountStatus statusOf(UUID playerId) {
        return playerId == null ? AccountStatus.UNKNOWN : liveStatus.getOrDefault(playerId, AccountStatus.UNKNOWN);
    }

    /** Registers an addon login callback (invoked on the server thread at join). */
    public static void registerLoginCallback(BiConsumer<ServerPlayer, AccountStatus> callback) {
        if (callback != null) loginCallbacks.add(callback);
    }

    private AccountStatusTracker() {}
}
