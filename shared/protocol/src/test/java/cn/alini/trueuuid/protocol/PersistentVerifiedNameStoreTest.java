package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentVerifiedNameStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsCurrentAndLegacyEntriesCaseInsensitively() throws Exception {
        UUID current = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        UUID legacy = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        Path file = temporaryDirectory.resolve("trueuuid-registry.json");
        Files.writeString(file, """
                {
                  "PremiumUser":{"premiumUuid":"01234567-89ab-cdef-0123-456789abcdef"},
                  "LegacyUser":"fedcba98-7654-3210-fedc-ba9876543210"
                }
                """);

        try (var store = new PersistentVerifiedNameStore(file)) {
            assertTrue(store.contains("premiumuser"));
            assertTrue(store.contains("LEGACYUSER"));
            assertEquals(current, store.premiumUuid("PREMIUMUSER").orElseThrow());
            assertEquals(legacy, store.premiumUuid("legacyuser").orElseThrow());
            assertFalse(store.contains("unknown"));
        }
    }

    @Test
    void malformedRegistryFailsClosedToAnEmptyStore() throws Exception {
        Path file = temporaryDirectory.resolve("trueuuid-registry.json");
        Files.writeString(file, "not-json");

        try (var store = new PersistentVerifiedNameStore(file)) {
            assertTrue(store.premiumUuid("PremiumUser").isEmpty());
        }
    }
}
