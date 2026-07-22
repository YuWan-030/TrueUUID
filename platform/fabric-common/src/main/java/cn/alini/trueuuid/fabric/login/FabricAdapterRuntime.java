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
    private static FabricPendingLoginStore pendingLogins;
    private static MigrationCoordinator migrations;
    private static MigrationLockRegistry migrationLocks;

    /** Records a TrueUUID session verification plus the same-IP reconnect grace seed. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile, String clientIp) {
        if (profile == null) return;
        pendingLogins().record(profile.uuid(), FabricAuthenticationSource.VERIFIED);
        registry().record(profile.name(), profile.uuid());
        grace().record(profile.name(), clientIp, new GraceCache.Entry(profile.uuid(), AuthSource.MOJANG, "Mojang"));
    }

    /** Accepts a same-IP reconnect within the grace window, consuming the entry. */
    public static synchronized Optional<UUID> tryGraceLogin(String name, String clientIp) {
        if (!FabricConfig.recentIpGraceEnabled()) return Optional.empty();
        return grace().consume(name, clientIp, Duration.ofSeconds(FabricConfig.recentIpGraceTtlSeconds()))
                .map(GraceCache.Entry::uuid);
    }

    /** Records a grace acceptance until vanilla creates the player. */
    public static synchronized void recordGraceLogin(UUID playerId) {
        pendingLogins().record(playerId, FabricAuthenticationSource.GRACE);
    }

    /** Records an accepted offline profile until vanilla creates the player. */
    public static synchronized void recordOfflineFallback(UUID playerId) {
        pendingLogins().record(playerId, FabricAuthenticationSource.OFFLINE_FALLBACK);
    }

    /** Consumes a server-side login result exactly once after vanilla creates the player. */
    public static synchronized FabricAuthenticationSource consumePendingLogin(UUID playerId) {
        return pendingLogins == null ? null : pendingLogins.consume(playerId);
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

    public static synchronized boolean isMigrationPending(String name) {
        return migrationLocks().contains(name);
    }

    public static synchronized void markMigrationPending(String name) {
        migrationLocks().mark(name);
    }

    public static synchronized void clearMigrationPending(String name) {
        if (migrationLocks != null) migrationLocks.clear(name);
    }

    public static synchronized MigrationCoordinator migrations() {
        if (migrations == null) migrations = new MigrationCoordinator();
        return migrations;
    }

    public static java.util.concurrent.CompletableFuture<Integer> probeMojangAsync() {
        return FabricSessionCheck.probeMojangAsync();
    }

    public static synchronized void shutdown() {
        if (pendingLogins != null) pendingLogins.clear();
        pendingLogins = null;
        if (verifiedNames != null) verifiedNames.close();
        verifiedNames = null;
        if (ipGrace != null) ipGrace.close();
        ipGrace = null;
        if (migrations != null) migrations.close();
        migrations = null;
        if (migrationLocks != null) migrationLocks.close();
        migrationLocks = null;
    }

    private static FabricVerifiedNameRegistry registry() {
        if (verifiedNames == null) verifiedNames = new FabricVerifiedNameRegistry();
        return verifiedNames;
    }

    private static RecentIpGrace grace() {
        if (ipGrace == null) ipGrace = new RecentIpGrace();
        return ipGrace;
    }

    private static FabricPendingLoginStore pendingLogins() {
        if (pendingLogins == null) pendingLogins = new FabricPendingLoginStore();
        return pendingLogins;
    }

    private static MigrationLockRegistry migrationLocks() {
        if (migrationLocks == null) migrationLocks = new MigrationLockRegistry();
        return migrationLocks;
    }

    private FabricAdapterRuntime() {}
}
