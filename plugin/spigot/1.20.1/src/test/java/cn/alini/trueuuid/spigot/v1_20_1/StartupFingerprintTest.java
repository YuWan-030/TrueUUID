package cn.alini.trueuuid.spigot.v1_20_1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartupFingerprintTest {
    @Test void exactRuntimeFingerprintIsAccepted() {
        assertDoesNotThrow(() -> StartupFingerprint.requireProtocolLib(
                StartupFingerprint.PROTOCOLLIB_VERSION, StartupFingerprint.PROTOCOLLIB_SHA256));
        assertDoesNotThrow(() -> StartupFingerprint.requireBukkitVersion(
                "This server is running CraftBukkit version " + StartupFingerprint.BUKKIT_VERSION));
        assertDoesNotThrow(() -> StartupFingerprint.requireImplementationVersion(
                ExactSpigot1201Bridge.EXPECTED_SPIGOT_IMPLEMENTATION));
    }

    @Test void protocolLibVersionOrChecksumMismatchIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> StartupFingerprint.requireProtocolLib("5.1.1", StartupFingerprint.PROTOCOLLIB_SHA256));
        assertThrows(IllegalStateException.class,
                () -> StartupFingerprint.requireProtocolLib(StartupFingerprint.PROTOCOLLIB_VERSION, "00"));
    }

    @Test void serverVersionOrImplementationMismatchIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> StartupFingerprint.requireBukkitVersion("Spigot 1.20.1 unknown"));
        assertThrows(IllegalStateException.class,
                () -> StartupFingerprint.requireImplementationVersion("3872-Spigot-other"));
    }
}
