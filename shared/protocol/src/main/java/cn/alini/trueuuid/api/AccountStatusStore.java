package cn.alini.trueuuid.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Loader-neutral live account-status store behind every adapter's addon API.
 * Keyed by player id; an entry lives from login to logout. Concurrent because
 * addons may query it off the server thread.
 *
 * <p>{@code P} is the loader's server-player type. This class never references a
 * Minecraft type, so it is unit-testable once here and shared identically by the
 * Forge, NeoForge, and Fabric adapters instead of each re-implementing the same
 * map plus callback list.
 */
public final class AccountStatusStore<P> {
    private final Map<UUID, AccountStatus> liveStatus = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BiConsumer<P, AccountStatus>> loginCallbacks = new CopyOnWriteArrayList<>();
    private final BiConsumer<P, RuntimeException> callbackErrorHandler;

    public AccountStatusStore() {
        this(null);
    }

    /**
     * @param callbackErrorHandler invoked when a registered callback throws, so
     *                             each adapter keeps its own "callback threw"
     *                             log line; a throwing callback never breaks
     *                             publication or the other callbacks.
     */
    public AccountStatusStore(BiConsumer<P, RuntimeException> callbackErrorHandler) {
        this.callbackErrorHandler = callbackErrorHandler == null ? (player, failure) -> {} : callbackErrorHandler;
    }

    /**
     * Publishes a player's status and notifies callbacks on the calling thread
     * (the adapters call this on the server thread at join, before other join
     * logic queries {@link #statusOf}).
     */
    public void publish(P player, UUID playerId, AccountStatus status) {
        if (playerId == null || status == null) return;
        liveStatus.put(playerId, status);
        for (BiConsumer<P, AccountStatus> callback : loginCallbacks) {
            try {
                callback.accept(player, status);
            } catch (RuntimeException failure) {
                callbackErrorHandler.accept(player, failure);
            }
        }
    }

    /** Live status for a player id, or {@link AccountStatus#UNKNOWN}. */
    public AccountStatus statusOf(UUID playerId) {
        return playerId == null ? AccountStatus.UNKNOWN : liveStatus.getOrDefault(playerId, AccountStatus.UNKNOWN);
    }

    /** Drops a player's live status when they leave. */
    public void clear(UUID playerId) {
        if (playerId != null) liveStatus.remove(playerId);
    }

    /** Drops every live status (e.g. on server shutdown); registered callbacks are kept. */
    public void clearAll() {
        liveStatus.clear();
    }

    /** Registers an addon login callback (invoked where {@link #publish} runs). */
    public void register(BiConsumer<P, AccountStatus> callback) {
        if (callback != null) loginCallbacks.add(callback);
    }
}
