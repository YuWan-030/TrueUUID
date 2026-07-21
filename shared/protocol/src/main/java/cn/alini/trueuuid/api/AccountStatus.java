package cn.alini.trueuuid.api;

/**
 * How a currently-online player authenticated, as determined by TrueUUID during
 * login. Addons query this through {@link TrueuuidApi} to branch behaviour, e.g.
 * spawning offline players in a separate world.
 */
public enum AccountStatus {
    /** Verified through a TrueUUID premium/Yggdrasil session check. */
    PREMIUM_VERIFIED,
    /** The server itself runs in online-mode and verified the account natively. */
    ONLINE_MODE,
    /** Accepted through the server's configured offline fallback (not premium). */
    OFFLINE_FALLBACK,
    /** No TrueUUID login record for this player id (never joined, or logged out). */
    UNKNOWN;

    /** True for accounts proven to own the name (session-verified or online-mode). */
    public boolean isPremium() {
        return this == PREMIUM_VERIFIED || this == ONLINE_MODE;
    }

    /** True only for a configured offline-fallback login. */
    public boolean isOffline() {
        return this == OFFLINE_FALLBACK;
    }
}
