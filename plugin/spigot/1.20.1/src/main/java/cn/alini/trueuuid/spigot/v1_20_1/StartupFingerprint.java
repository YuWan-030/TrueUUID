package cn.alini.trueuuid.spigot.v1_20_1;

import java.util.Objects;

/** Exact startup allowlist for the unsupported Spigot 1.20.1 candidate. */
final class StartupFingerprint {
    static final String PROTOCOLLIB_VERSION = "5.1.0";
    static final String PROTOCOLLIB_SHA256 = "562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769";
    static final String BUKKIT_VERSION = "3871-Spigot-d2eba2c-3f9263b (MC: 1.20.1)";

    static void requireProtocolLib(String version, String sha256) {
        if (!PROTOCOLLIB_VERSION.equals(Objects.requireNonNullElse(version, ""))) {
            throw new IllegalStateException("ProtocolLib must be exactly " + PROTOCOLLIB_VERSION);
        }
        if (!PROTOCOLLIB_SHA256.equals(Objects.requireNonNullElse(sha256, ""))) {
            throw new IllegalStateException("ProtocolLib checksum mismatch: " + sha256);
        }
    }

    static void requireBukkitVersion(String version) {
        if (!Objects.requireNonNullElse(version, "").contains(BUKKIT_VERSION)) {
            throw new IllegalStateException("Spigot must be exact build 3871: " + version);
        }
    }

    static void requireImplementationVersion(String version) {
        if (!ExactSpigot1201Bridge.EXPECTED_SPIGOT_IMPLEMENTATION.equals(
                Objects.requireNonNullElse(version, ""))) {
            throw new IllegalStateException("unexpected Spigot implementation: " + version);
        }
    }

    private StartupFingerprint() {
    }
}
