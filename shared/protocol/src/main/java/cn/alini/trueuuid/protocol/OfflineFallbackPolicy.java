package cn.alini.trueuuid.protocol;

/** Loader-neutral policy for deciding whether an unverified name may retain its offline UUID. */
public final class OfflineFallbackPolicy {
    public static boolean permits(boolean knownVerifiedName, boolean allowOnFailure,
                                  boolean denyKnownPremium, boolean unknownNamesOnly) {
        if (!allowOnFailure) return false;
        if (!knownVerifiedName) return true;
        return !denyKnownPremium && !unknownNamesOnly;
    }

    private OfflineFallbackPolicy() {}
}
