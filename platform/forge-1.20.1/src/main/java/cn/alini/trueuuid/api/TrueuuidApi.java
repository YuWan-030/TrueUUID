package cn.alini.trueuuid.api;

import cn.alini.trueuuid.server.AccountStatusTracker;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * TrueUUID API: 提供正版状态查询接口，供附属mod调用。
 * (TrueUUID API: Provides premium status query interface for addon mods to call.)
 *
 * <p>The {@link AccountStatus} surface (getStatus, isPremium/isOffline by
 * player, registerLoginCallback) matches the modern Forge/NeoForge adapters,
 * so an addon compiles against every target. The two original name-lookup
 * methods keep their pre-existing signatures for compatibility.</p>
 */
public class TrueuuidApi {
    /**
     * 判断指定玩家名称是否已被TrueUUID判定为正版。
     * (Determines whether the specified player name has been determined as premium by TrueUUID.)
     * @param name 玩家名称（不区分大小写） (Player name (case-insensitive))
     * @return 如果已被判定为正版，返回true，否则返回false。 (Returns true if determined as premium, otherwise false.)
     */
    public static boolean isPremium(String name) {
        return TrueuuidRuntime.NAME_REGISTRY.isKnownPremiumName(name);
    }

    /**
     * 获取指定玩家名称对应的正版UUID（如有）。
     * (Gets the premium UUID corresponding to the specified player name (if any).)
     * @param name 玩家名称（不区分大小写） (Player name (case-insensitive))
     * @return 如果有正版UUID，返回UUID，否则返回null。 (Returns UUID if there is a premium UUID, otherwise null.)
     */
    public static UUID getPremiumUuid(String name) {
        return TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
    }

    /** True if this name has ever completed a verified premium/Yggdrasil login. */
    public static boolean isKnownPremiumName(String name) {
        return TrueuuidRuntime.NAME_REGISTRY.isKnownPremiumName(name);
    }

    /** Authentication status of an online player id, or {@link AccountStatus#UNKNOWN}. */
    public static AccountStatus getStatus(UUID playerId) {
        return AccountStatusTracker.statusOf(playerId);
    }

    /** Authentication status of an online player, or {@link AccountStatus#UNKNOWN}. */
    public static AccountStatus getStatus(ServerPlayer player) {
        return player == null ? AccountStatus.UNKNOWN : getStatus(player.getUUID());
    }

    public static boolean isPremium(UUID playerId) { return getStatus(playerId).isPremium(); }

    public static boolean isPremium(ServerPlayer player) { return getStatus(player).isPremium(); }

    public static boolean isOffline(UUID playerId) { return getStatus(playerId).isOffline(); }

    public static boolean isOffline(ServerPlayer player) { return getStatus(player).isOffline(); }

    /**
     * Registers a callback invoked on the server thread immediately after a
     * player's {@link AccountStatus} is known at login (before other join logic
     * that queries {@link #getStatus}). Register once during mod setup.
     */
    public static void registerLoginCallback(BiConsumer<ServerPlayer, AccountStatus> callback) {
        AccountStatusTracker.registerLoginCallback(callback);
    }
}
