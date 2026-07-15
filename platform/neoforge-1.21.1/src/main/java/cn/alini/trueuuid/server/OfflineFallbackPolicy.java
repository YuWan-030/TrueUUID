package cn.alini.trueuuid.server;

/** Pure policy for deciding whether an unverified name may retain its offline UUID. */
final class OfflineFallbackPolicy {
    static boolean permits(boolean knownVerifiedName, boolean allowOnFailure,
                           boolean denyKnownPremium, boolean unknownNamesOnly) {
        if (!allowOnFailure) return false;
        if (!knownVerifiedName) return true;
        return !denyKnownPremium && !unknownNamesOnly;
    }

    private OfflineFallbackPolicy() {}
}
