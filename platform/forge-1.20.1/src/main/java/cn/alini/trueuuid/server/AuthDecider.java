package cn.alini.trueuuid.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.protocol.AuthPolicy;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;

public final class AuthDecider {

    public static class Decision {
        public enum Kind { PREMIUM_GRACE, OFFLINE, DENY }
        public Kind kind;
        public UUID premiumUuid; // PREMIUM_GRACE 时填 (Fill when PREMIUM_GRACE)
        public AuthState.AuthSource graceSource;
        public String graceDisplayName;
        public String denyMessage;
        public Component denyComponent;
    }

    public static Decision onFailure(String name, String ip) {
        return onFailure(name, ip, false);
    }

    public static Decision onFailure(String name, String ip, boolean explicitOfflineClient) {
        Decision d = new Decision();
        boolean known = TrueuuidRuntime.NAME_REGISTRY.isKnownPremiumName(name);
        Optional<UUID> localProxyUuid = known && !explicitOfflineClient && isLocalProxyAddress(ip)
                ? TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name) : Optional.empty();
        Optional<RecentIpGraceCache.GraceResult> grace = TrueuuidConfig.recentIpGraceEnabled() && !explicitOfflineClient
                ? TrueuuidRuntime.IP_GRACE.tryGraceResult(name, ip, TrueuuidConfig.recentIpGraceTtlSeconds())
                : Optional.empty();
        AuthPolicy.Decision policy = AuthPolicy.decide(new AuthPolicy.Input(
                known, explicitOfflineClient, localProxyUuid.isPresent(), grace.isPresent(),
                TrueuuidConfig.knownPremiumDenyOffline(), TrueuuidConfig.allowOfflineOnFailure(),
                TrueuuidConfig.allowOfflineForUnknownOnly()));

        if (policy == AuthPolicy.Decision.PREMIUM_GRACE) {
            d.kind = Decision.Kind.PREMIUM_GRACE;
            if (grace.isPresent()) {
                d.premiumUuid = grace.get().premiumUuid();
                d.graceSource = grace.get().source();
                d.graceDisplayName = grace.get().displayName();
            } else {
                d.premiumUuid = localProxyUuid.orElse(null);
                d.graceSource = AuthState.AuthSource.MOJANG;
                d.graceDisplayName = "Local proxy grace";
            }
            return d;
        }
        if (policy == AuthPolicy.Decision.OFFLINE) {
            d.kind = Decision.Kind.OFFLINE;
            return d;
        }

        d.kind = Decision.Kind.DENY;
        if (known && TrueuuidConfig.knownPremiumDenyOffline()) {
            AuthState.AuthSource source = TrueuuidRuntime.NAME_REGISTRY.getAuthSource(name);
            String displayName = TrueuuidRuntime.NAME_REGISTRY.getAuthDisplayName(name);
            if (source == AuthState.AuthSource.YGGDRASIL) {
                d.denyComponent = Component.translatable("trueuuid.disconnect.bound_skin_site", displayName);
            } else {
                d.denyComponent = Component.translatable("trueuuid.disconnect.bound_premium");
            }
        } else {
            d.denyComponent = Component.translatable("trueuuid.disconnect.auth_denied");
        }
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
