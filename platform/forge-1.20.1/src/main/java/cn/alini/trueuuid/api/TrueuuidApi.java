package cn.alini.trueuuid.api;

import cn.alini.trueuuid.server.TrueuuidRuntime;
import java.util.UUID;

/**
 * TrueUUID API: 提供正版状态查询接口，供附属mod调用。
 * (TrueUUID API: Provides premium status query interface for addon mods to call.)
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
}

