package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.protocol.VerifiedProfile;

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

    /** Records a TrueUUID session verification into the persistent name registry. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile) {
        if (profile != null) registry().record(profile.name(), profile.uuid());
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
    }

    private static FabricVerifiedNameRegistry registry() {
        if (verifiedNames == null) verifiedNames = new FabricVerifiedNameRegistry();
        return verifiedNames;
    }

    private FabricAdapterRuntime() {}
}
