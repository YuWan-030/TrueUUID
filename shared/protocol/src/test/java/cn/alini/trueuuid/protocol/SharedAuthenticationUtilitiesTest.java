package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedAuthenticationUtilitiesTest {
    @Test void fallbackPolicyIsConsistent() {
        assertFalse(OfflineFallbackPolicy.permits(false, false, true, true));
        assertTrue(OfflineFallbackPolicy.permits(false, true, true, true));
        assertFalse(OfflineFallbackPolicy.permits(true, true, true, true));
        assertTrue(OfflineFallbackPolicy.permits(true, true, false, false));
    }

    @Test void yggdrasilEndpointIsNormalizedAndMojangIsNotEchoed() {
        assertEquals("https://skin.example/api/yggdrasil/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot("https://skin.example/api/yggdrasil"));
        assertEquals("https://skin.example/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.sanitizeUrl(
                        "http://127.0.0.1:1234/https/skin.example/sessionserver/session/minecraft/hasJoined?x=1"));
        assertEquals("", ClientYggdrasilEndpoint.sanitizeUrl(
                "https://sessionserver.mojang.com/session/minecraft/hasJoined"));
    }

    @Test void diagnosticsNeverExposeRawFailureDetails() {
        assertEquals("account session rejected (refresh the launcher account)",
                ClientAuthDiagnostics.failureCategory(new IllegalStateException("invalid token abc")));
        assertEquals("authentication request rejected (unknown failure)",
                ClientAuthDiagnostics.failureCategory(null));
    }

    @Test void migrationLocksAreCaseInsensitiveAndClearable() {
        try (MigrationLockRegistry locks = new MigrationLockRegistry()) {
            locks.mark("Player");
            assertTrue(locks.contains("player"));
            locks.clear("PLAYER");
            assertFalse(locks.contains("Player"));
        }
    }
}
