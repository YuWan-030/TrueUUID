package cn.alini.trueuuid.server;

/** Server-owned collision policy for premium and offline identities. */
public enum AdmissionMode {
    /**
     * The first accepted identity keeps the base name. A later premium/offline
     * collision is denied until an administrator explicitly approves the
     * bounded alias transition; no profile is silently renamed.
     */
    CONSENT_REQUIRED,
    /** Premium keeps its Mojang name; colliding offline users receive a stable alias. */
    SAFE_PARALLEL,
    /** Any Mojang-existing name requires connection-bound premium proof. */
    PREMIUM_RESERVED,
    /** The first accepted identity kind owns the base name. Unsafe by design. */
    FIRST_CLAIM
}
