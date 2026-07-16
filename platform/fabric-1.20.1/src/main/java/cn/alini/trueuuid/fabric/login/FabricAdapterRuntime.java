package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.protocol.AuthSource;
import cn.alini.trueuuid.protocol.GraceCache;
import cn.alini.trueuuid.protocol.RecentIpGrace;
import cn.alini.trueuuid.protocol.VerifiedProfile;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Fabric-owned runtime state shared across login transactions: the persistent
 * verified-name registry and the offline-fallback decision, mirroring
 * {@code ForgeAdapterRuntime.canUseOfflineFallback} so the same name is
 * protected identically on every loader.
 */
public final class FabricAdapterRuntime {
    private static FabricVerifiedNameRegistry verifiedNames;
    private static RecentIpGrace ipGrace;

    /** Records a TrueUUID session verification plus the same-IP reconnect grace seed. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile, String clientIp) {
        if (profile == null) return;
        registry().record(profile.name(), profile.uuid());
        grace().record(profile.name(), clientIp, new GraceCache.Entry(profile.uuid(), AuthSource.MOJANG, "Mojang"));
    }

    /** Accepts a same-IP reconnect within the grace window, consuming the entry. */
    public static synchronized Optional<UUID> tryGraceLogin(String name, String clientIp) {
        if (!FabricConfig.recentIpGraceEnabled()) return Optional.empty();
        return grace().consume(name, clientIp, Duration.ofSeconds(FabricConfig.recentIpGraceTtlSeconds()))
                .map(GraceCache.Entry::uuid);
    }

    /** Starts the reconnect grace window when a player leaves. */
    public static synchronized void activateGraceAfterLogout(String name, String clientIp) {
        if (FabricConfig.recentIpGraceEnabled()) grace().activateAfterLogout(name, clientIp);
    }

    /** Applies the offline policy before vanilla keeps the unverified profile. */
    public static synchronized boolean canUseOfflineFallback(String name) {
        boolean knownVerifiedName = registry().contains(name);
        return OfflineFallbackPolicy.permits(knownVerifiedName, FabricConfig.allowOfflineOnFailure(),
                FabricConfig.knownPremiumDenyOffline(), FabricConfig.allowOfflineForUnknownOnly());
    }

    /** True if this name has ever completed a verified premium login here. */
    public static synchronized boolean isKnownPremiumName(String name) {
        return registry().contains(name);
    }

    /** The premium UUID last bound to this name, if any. */
    public static synchronized Optional<UUID> premiumUuidOf(String name) {
        return registry().premiumUuid(name);
    }

    public static synchronized void shutdown() {
        if (verifiedNames != null) verifiedNames.close();
        verifiedNames = null;
        if (ipGrace != null) ipGrace.close();
        ipGrace = null;
    }

    private static FabricVerifiedNameRegistry registry() {
        if (verifiedNames == null) verifiedNames = new FabricVerifiedNameRegistry();
        return verifiedNames;
    }

    private static RecentIpGrace grace() {
        if (ipGrace == null) ipGrace = new RecentIpGrace();
        return ipGrace;
    }

    private FabricAdapterRuntime() {}
}
