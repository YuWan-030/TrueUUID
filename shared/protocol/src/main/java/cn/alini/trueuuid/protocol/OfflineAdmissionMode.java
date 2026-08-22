package cn.alini.trueuuid.protocol;

/** Server policy after Mojang authoritatively reports that a name is absent. */
public enum OfflineAdmissionMode {
    /** Accept premium identities only. */
    DENY,
    /** Require a valid TrueUUID login response; this gates capability, not name ownership. */
    REQUIRE_TRUEUUID_CLIENT,
    /** Match vanilla offline-mode name-only admission. Offline names remain impersonable. */
    ALLOW_VANILLA
}
