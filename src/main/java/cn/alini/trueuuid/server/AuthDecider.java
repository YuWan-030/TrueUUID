package cn.alini.trueuuid.server;

import cn.alini.trueuuid.config.TrueuuidConfig;

import java.util.Optional;
import java.util.UUID;

public final class AuthDecider {

    public static class Decision {
        public enum Kind { PREMIUM_GRACE, OFFLINE, DENY }
        public Kind kind;
        public UUID premiumUuid; // PREMIUM_GRACE 时填
        public AuthState.AuthSource graceSource;
        public String graceDisplayName;
        public String denyMessage;
    }

    public static Decision onFailure(String name, String ip) {
        return onFailure(name, ip, false);
    }

    public static Decision onFailure(String name, String ip, boolean explicitOfflineClient) {
        Decision d = new Decision();

        boolean known = TrueuuidRuntime.NAME_REGISTRY.isKnownPremiumName(name);

        // Velocity/VC proxy compatibility: if the modded client answered the
        // trueuuid:auth query but Mojang hasJoined still fails behind a local
        // proxy, preserve the already-bound premium UUID instead of denying.
        if (known && !explicitOfflineClient && isLocalProxyAddress(ip)) {
            Optional<UUID> premiumUuid = TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name);
            if (premiumUuid.isPresent()) {
                d.kind = Decision.Kind.PREMIUM_GRACE;
                d.premiumUuid = premiumUuid.get();
                d.graceSource = AuthState.AuthSource.MOJANG;
                d.graceDisplayName = "本地代理容错";
                return d;
            }
        }

        // 1) 近期同 IP 成功容错：临时按正版处理。
        // 必须优先于 knownPremiumDenyOffline，否则“刚刚成功验证过的正版名”在 Mojang 短暂失败时会被直接拒绝，
        // 配置里的 recentIpGrace.enabled/ttlSeconds 就失去意义。
        if (TrueuuidConfig.recentIpGraceEnabled() && !explicitOfflineClient) {
            Optional<RecentIpGraceCache.GraceResult> p = TrueuuidRuntime.IP_GRACE.tryGraceResult(name, ip, TrueuuidConfig.recentIpGraceTtlSeconds());
            if (p.isPresent()) {
                d.kind = Decision.Kind.PREMIUM_GRACE;
                d.premiumUuid = p.get().premiumUuid();
                d.graceSource = p.get().source();
                d.graceDisplayName = p.get().displayName();
                return d;
            }
        }

        // 2) 已验证过正版的名字：禁止离线回落
        if (known && TrueuuidConfig.knownPremiumDenyOffline()) {
            AuthState.AuthSource source = TrueuuidRuntime.NAME_REGISTRY.getAuthSource(name);
            String displayName = TrueuuidRuntime.NAME_REGISTRY.getAuthDisplayName(name);
            d.kind = Decision.Kind.DENY;
            if (source == AuthState.AuthSource.YGGDRASIL) {
                d.denyMessage = "该名称已绑定皮肤站 UUID（" + displayName + "），鉴权失败时不允许以离线模式进入。请使用对应皮肤站登录后重试。";
            } else {
                d.denyMessage = "该名称已绑定正版 UUID，鉴权失败时不允许以离线模式进入。请检查网络后重试。";
            }
            return d;
        }

        // 3) 未知名字：可允许离线兜底
        if (TrueuuidConfig.allowOfflineForUnknownOnly() && !known) {
            d.kind = Decision.Kind.OFFLINE;
            return d;
        }

        // 4) 否则拒绝
        d.kind = Decision.Kind.DENY;
        d.denyMessage = "鉴权失败，已禁止离线进入以保护你的正版存档。请稍后重试。";
        return d;
    }

    private static boolean isLocalProxyAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    private AuthDecider() {}

}
