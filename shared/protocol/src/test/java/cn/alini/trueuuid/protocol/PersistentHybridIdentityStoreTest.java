package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentHybridIdentityStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @TempDir Path temporaryDirectory;

    @Test void missingFileStartsEmptyAndPremiumLockSurvivesRestart() throws Exception {
        Path file = temporaryDirectory.resolve("identities.json");
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PersistentHybridIdentityStore store = store(file);
        assertEquals(HybridIdentityPolicy.StoredIdentity.UNKNOWN, store.classification("Alice"));

        store.recordPremium(new VerifiedProfile(uuid, "Alice", List.of()),
                PersistentHybridIdentityStore.Authority.MOJANG);
        assertEquals(HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED, store.classification("ALICE"));

        PersistentHybridIdentityStore reopened = store(file);
        PersistentHybridIdentityStore.Entry loaded = reopened.find("alice").orElseThrow();
        assertEquals(uuid, loaded.uuid());
        assertEquals("Alice", loaded.canonicalName());
        assertEquals(PersistentHybridIdentityStore.Authority.MOJANG, loaded.authority());
        assertEquals(NOW.toEpochMilli(), loaded.firstVerifiedAt());
        assertEquals(NOW.toEpochMilli(), loaded.lastVerifiedAt());
    }

    @Test void premiumAndOfflineOwnershipCannotReplaceEachOther() throws Exception {
        Path file = temporaryDirectory.resolve("identities.json");
        PersistentHybridIdentityStore store = store(file);
        UUID premium = UUID.randomUUID();
        store.recordPremium(new VerifiedProfile(premium, "Alice", List.of()),
                PersistentHybridIdentityStore.Authority.MOJANG);

        assertThrows(IOException.class, () -> store.recordOffline(UUID.randomUUID(), "alice"));
        assertEquals(premium, store.find("Alice").orElseThrow().uuid());

        PersistentHybridIdentityStore offline = store(temporaryDirectory.resolve("offline.json"));
        UUID offlineUuid = UUID.randomUUID();
        offline.recordOffline(offlineUuid, "LocalUser");
        assertThrows(IOException.class, () -> offline.recordPremium(
                new VerifiedProfile(UUID.randomUUID(), "localuser", List.of()),
                PersistentHybridIdentityStore.Authority.MOJANG));
        assertEquals(offlineUuid, offline.find("LOCALUSER").orElseThrow().uuid());
    }

    @Test void recordsExplicitVanillaAndTrueuuidClientOfflineAuthorities() throws Exception {
        PersistentHybridIdentityStore vanilla = store(temporaryDirectory.resolve("vanilla.json"));
        UUID vanillaUuid = OfflineIdentity.profile("VanillaUser").uuid();
        vanilla.recordOffline(vanillaUuid, "VanillaUser",
                PersistentHybridIdentityStore.Authority.OFFLINE_NAME_ONLY);
        assertEquals(PersistentHybridIdentityStore.Authority.OFFLINE_NAME_ONLY,
                store(temporaryDirectory.resolve("vanilla.json")).find("vanillauser").orElseThrow().authority());

        PersistentHybridIdentityStore modded = store(temporaryDirectory.resolve("modded.json"));
        UUID moddedUuid = OfflineIdentity.profile("ModdedUser").uuid();
        modded.recordOffline(moddedUuid, "ModdedUser",
                PersistentHybridIdentityStore.Authority.TRUEUUID_CLIENT_GATE);
        assertEquals(PersistentHybridIdentityStore.Authority.TRUEUUID_CLIENT_GATE,
                store(temporaryDirectory.resolve("modded.json")).find("MODDEDUSER").orElseThrow().authority());
    }

    @Test void failedPersistenceNeverChangesInMemoryOwnership() throws Exception {
        Path invalidParent = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(invalidParent, "file", StandardCharsets.UTF_8);
        PersistentHybridIdentityStore store = store(invalidParent.resolve("identities.json"));

        assertThrows(IOException.class, () -> store.recordOffline(UUID.randomUUID(), "Alice"));
        assertTrue(store.snapshot().isEmpty());
    }

    @Test void corruptUnknownDuplicateAndTrailingStateFailClosed() throws Exception {
        assertCorrupt("{}");
        assertCorrupt("{\"schemaVersion\":1,\"entries\":[],\"unexpected\":true}");
        assertCorrupt("{\"schemaVersion\":1,\"schemaVersion\":1,\"entries\":[]}");
        assertCorrupt("{\"schemaVersion\":1,\"entries\":[]} trailing");
        assertCorrupt("{\"schemaVersion\":2,\"entries\":[]}");
        assertCorrupt("{\"schemaVersion\":1,\"entries\":[{\"key\":\"alice\",\"identity\":\"UNKNOWN\","
                + "\"uuid\":\"11111111-2222-3333-4444-555555555555\",\"canonicalName\":\"Alice\","
                + "\"authority\":\"MOJANG\",\"firstVerifiedAt\":1,\"lastVerifiedAt\":1}]}");
    }

    @Test void duplicateOrNonCanonicalNamesFailClosed() throws Exception {
        String first = entry("alice", "Alice", "11111111-2222-3333-4444-555555555555");
        String second = entry("alice", "ALICE", "22222222-3333-4444-5555-666666666666");
        assertCorrupt("{\"schemaVersion\":1,\"entries\":[" + first + "," + second + "]}");
        assertCorrupt("{\"schemaVersion\":1,\"entries\":[" + entry("bob", "Alice",
                "11111111-2222-3333-4444-555555555555") + "]}");
    }

    @Test void symlinkStoreIsRejected() throws Exception {
        Path target = temporaryDirectory.resolve("target.json");
        Files.writeString(target, "{\"schemaVersion\":1,\"entries\":[]}", StandardCharsets.UTF_8);
        Path link = temporaryDirectory.resolve("linked.json");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException unsupported) {
            return;
        }
        assertThrows(IOException.class, () -> store(link));
        assertFalse(Files.isSymbolicLink(target));
    }

    private PersistentHybridIdentityStore store(Path file) throws IOException {
        return new PersistentHybridIdentityStore(file, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void assertCorrupt(String json) throws Exception {
        Path file = temporaryDirectory.resolve("corrupt-" + UUID.randomUUID() + ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> store(file));
    }

    private static String entry(String key, String canonicalName, String uuid) {
        return "{\"key\":\"" + key + "\",\"identity\":\"PREMIUM_LOCKED\",\"uuid\":\"" + uuid
                + "\",\"canonicalName\":\"" + canonicalName + "\",\"authority\":\"MOJANG\","
                + "\"firstVerifiedAt\":1,\"lastVerifiedAt\":1}";
    }
}
