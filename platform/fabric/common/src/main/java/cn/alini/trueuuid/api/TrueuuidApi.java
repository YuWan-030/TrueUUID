package cn.alini.trueuuid.api;

import cn.alini.trueuuid.fabric.login.FabricAccountStatusTracker;
import cn.alini.trueuuid.fabric.login.FabricAdapterRuntime;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Stable public API for addon mods on Fabric. Mirrors the Forge/NeoForge
 * {@code TrueuuidApi} so an addon can learn whether an online player
 * authenticated as premium or through offline fallback and branch behaviour
 * (separate spawn, restricted permissions, cosmetics, …).
 *
 * <p>Example — send offline players to a holding area on join:
 * <pre>{@code
 * TrueuuidApi.registerLoginCallback((player, status) -> {
 *     if (status.isOffline()) {
 *         // teleport to your offline world / coords
 *     }
 * });
 * }</pre>
 *
 * <p>All methods are server-side. Status is available from the moment a player
 * finishes login until they log out.
 */
public final class TrueuuidApi {

    /** Authentication status of an online player id, or {@link AccountStatus#UNKNOWN}. */
    public static AccountStatus getStatus(UUID playerId) {
        return FabricAccountStatusTracker.statusOf(playerId);
    }

    /** Authentication status of an online player, or {@link AccountStatus#UNKNOWN}. */
    public static AccountStatus getStatus(ServerPlayerEntity player) {
        return player == null ? AccountStatus.UNKNOWN : getStatus(player.getUuid());
    }

    public static boolean isPremium(UUID playerId) { return getStatus(playerId).isPremium(); }

    public static boolean isPremium(ServerPlayerEntity player) { return getStatus(player).isPremium(); }

    public static boolean isOffline(UUID playerId) { return getStatus(playerId).isOffline(); }

    public static boolean isOffline(ServerPlayerEntity player) { return getStatus(player).isOffline(); }

    /** True if this name has ever completed a verified premium login here. */
    public static boolean isKnownPremiumName(String name) {
        return FabricAdapterRuntime.isKnownPremiumName(name);
    }

    /** The premium UUID last bound to this name, if any. */
    public static Optional<UUID> getPremiumUuid(String name) {
        return FabricAdapterRuntime.premiumUuidOf(name);
    }

    /**
     * Registers a callback invoked on the server thread immediately after a
     * player's {@link AccountStatus} is known at login (before other join logic
     * that queries {@link #getStatus}). Register once during mod setup.
     */
    public static void registerLoginCallback(BiConsumer<ServerPlayerEntity, AccountStatus> callback) {
        FabricAccountStatusTracker.register(callback);
    }

    private TrueuuidApi() {}
}
