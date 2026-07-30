package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.protocol.AuthSource;
import cn.alini.trueuuid.protocol.ExpiringBoundedStore;
import cn.alini.trueuuid.protocol.GraceCache;
import cn.alini.trueuuid.protocol.MigrationLockRegistry;
import cn.alini.trueuuid.protocol.OfflineFallbackPolicy;
import cn.alini.trueuuid.protocol.PersistentVerifiedNameStore;
import cn.alini.trueuuid.protocol.RecentIpGrace;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import net.fabricmc.loader.api.FabricLoader;

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
    private static PersistentVerifiedNameStore verifiedNames;
    private static RecentIpGrace ipGrace;
    private static ExpiringBoundedStore<UUID, FabricAuthenticationSource> pendingLogins;
    private static MigrationCoordinator migrations;
    private static MigrationLockRegistry migrationLocks;

    /** Records a TrueUUID session verification plus the same-IP reconnect grace seed. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile, String clientIp, String endpoint) {
        if (profile == null) return;
        boolean yggdrasil = endpoint != null && !endpoint.isBlank();
        pendingLogins().put(profile.uuid(), yggdrasil
                ? FabricAuthenticationSource.YGGDRASIL : FabricAuthenticationSource.VERIFIED);
        registry().record(profile.name(), profile.uuid());
        grace().record(profile.name(), clientIp, new GraceCache.Entry(profile.uuid(),
                yggdrasil ? AuthSource.YGGDRASIL : AuthSource.MOJANG,
                yggdrasil ? "Yggdrasil" : "Mojang"));
    }

    /** Accepts a same-IP reconnect within the grace window, consuming the entry. */
    public static synchronized Optional<UUID> tryGraceLogin(String name, String clientIp) {
        if (!FabricConfig.recentIpGraceEnabled()) return Optional.empty();
        return grace().consume(name, clientIp, Duration.ofSeconds(FabricConfig.recentIpGraceTtlSeconds()))
                .map(GraceCache.Entry::uuid);
    }

    /** Records a grace acceptance until vanilla creates the player. */
    public static synchronized void recordGraceLogin(UUID playerId) {
        if (playerId != null) pendingLogins().put(playerId, FabricAuthenticationSource.GRACE);
    }

    /** Records an accepted offline profile until vanilla creates the player. */
    public static synchronized void recordOfflineFallback(UUID playerId) {
        if (playerId != null) pendingLogins().put(playerId, FabricAuthenticationSource.OFFLINE_FALLBACK);
    }

    /** Consumes a server-side login result exactly once after vanilla creates the player. */
    public static synchronized FabricAuthenticationSource consumePendingLogin(UUID playerId) {
        return pendingLogins == null ? null : pendingLogins.remove(playerId).orElse(null);
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

    private static PersistentVerifiedNameStore registry() {
        if (verifiedNames == null) {
            verifiedNames = new PersistentVerifiedNameStore(
                    FabricLoader.getInstance().getConfigDir().resolve("trueuuid-registry.json"));
        }
        return verifiedNames;
    }

    private static RecentIpGrace grace() {
        if (ipGrace == null) ipGrace = new RecentIpGrace();
        return ipGrace;
    }

    private static ExpiringBoundedStore<UUID, FabricAuthenticationSource> pendingLogins() {
        if (pendingLogins == null) pendingLogins =
                new ExpiringBoundedStore<>(4096, Duration.ofMinutes(5));
        return pendingLogins;
    }

    private static MigrationLockRegistry migrationLocks() {
        if (migrationLocks == null) migrationLocks = new MigrationLockRegistry();
        return migrationLocks;
    }

    private FabricAdapterRuntime() {}
}
