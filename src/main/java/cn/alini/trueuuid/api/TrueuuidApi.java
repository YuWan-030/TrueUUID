package cn.alini.trueuuid.api;

import cn.alini.trueuuid.server.TrueuuidRuntime;
import java.util.UUID;

/**
 * TrueUUID API: 提供正版状态查询接口，供附属mod调用。
 */
public class TrueuuidApi {
    /**
     * 判断指定玩家名称是否已被TrueUUID判定为正版。
     * @param name 玩家名称（不区分大小写）
     * @return 如果已被判定为正版，返回true，否则返回false。
     */
    public static boolean isPremium(String name) {
        return TrueuuidRuntime.NAME_REGISTRY.isKnownPremiumName(name);
    }

    /**
     * 获取指定玩家名称对应的正版UUID（如有）。
     * @param name 玩家名称（不区分大小写）
     * @return 如果有正版UUID，返回UUID，否则返回null。
     */
    public static UUID getPremiumUuid(String name) {
        return TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
    }
}

