package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistentIdentityRepositoryTest {
    @TempDir Path directory;
    private static final UUID PREMIUM = UUID.fromString("d64da409-52f6-4ce8-a082-73b9a5d303bd");
    private static final UUID OFFLINE = UUID.fromString("5d2c7559-62bc-3e8b-bb90-9e27b7e42d64");

    @Test void premiumAndAliasedOfflineBindingsCoexistAndSurviveRestart() throws Exception {
        Path file = directory.resolve("identity-repository.json");
        PersistentIdentityRepository repository = new PersistentIdentityRepository(file);
        repository.recordPremium(PREMIUM, "FixGOD", "FixGOD", AuthenticatedIdentity.Authority.MOJANG);
        AuthenticatedIdentity offline = repository.recordOffline(OFFLINE, "FixGOD", "o_FixGODABCDE");
        assertTrue(offline.aliased());

        PersistentIdentityRepository restarted = new PersistentIdentityRepository(file);
        PersistentIdentityRepository.Record record = restarted.findByBaseName("fixgod").orElseThrow();
        assertEquals(PREMIUM, record.premium().uuid());
        assertEquals(OFFLINE, record.offline().uuid());
        assertEquals("o_FixGODABCDE", restarted.identityOf(OFFLINE).orElseThrow().effectiveName());
    }

    @Test void existingAliasDoesNotChangeWhenNewPrefixIsConfigured() throws Exception {
        Path file = directory.resolve("store.json");
        PersistentIdentityRepository repository = new PersistentIdentityRepository(file);
        repository.recordOffline(OFFLINE, "FixGOD", "o_FixGODABCDE");
        AuthenticatedIdentity repeated = repository.recordOffline(OFFLINE, "FixGOD", "x_FixGODABCDE");
        assertEquals("o_FixGODABCDE", repeated.effectiveName());
        assertEquals("o_FixGODABCDE", new PersistentIdentityRepository(file)
                .identityOf(OFFLINE).orElseThrow().effectiveName());
    }

    @Test void manualOfflineAliasCannotOccupyPremiumCanonicalName() throws Exception {
        PersistentIdentityRepository repository = new PersistentIdentityRepository(directory.resolve("store.json"));
        repository.recordPremium(PREMIUM, "FixGOD", "FixGOD", AuthenticatedIdentity.Authority.MOJANG);
        repository.recordOffline(OFFLINE, "FixGOD", "o_FixGODABCDE");
        assertThrows(IOException.class, () -> repository.setOfflineAlias(OFFLINE, "FixGOD"));
    }

    @Test void duplicateUuidAndEffectiveNameIndexesFailClosed() throws Exception {
        PersistentIdentityRepository repository = new PersistentIdentityRepository(directory.resolve("store.json"));
        repository.recordOffline(OFFLINE, "First", "First");
        assertThrows(IOException.class, () -> repository.recordOffline(OFFLINE, "Second", "Second"));
        assertThrows(IOException.class, () -> repository.recordOffline(UUID.randomUUID(), "Second", "First"));
    }

    @Test void corruptUnknownAndIncompleteStateRefusesStartup() throws Exception {
        Path file = directory.resolve("store.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"records\":[],\"unexpected\":true}");
        assertThrows(IOException.class, () -> new PersistentIdentityRepository(file));
        Files.writeString(file, "{\"schemaVersion\":2}");
        assertThrows(IOException.class, () -> new PersistentIdentityRepository(file));
        Files.writeString(file, "not-json");
        assertThrows(IOException.class, () -> new PersistentIdentityRepository(file));
        Files.writeString(file, "{\"schemaVersion\":2,\"schemaVersion\":2,\"records\":[]}");
        assertThrows(IOException.class, () -> new PersistentIdentityRepository(file));
    }

    @Test void releaseIsGenerationBoundAndNeverTouchesUnrelatedFiles() throws Exception {
        Path playerData = directory.resolve("playerdata.dat");
        Files.writeString(playerData, "preserve-me");
        PersistentIdentityRepository repository = new PersistentIdentityRepository(directory.resolve("store.json"));
        repository.recordOffline(OFFLINE, "FixGOD", "FixGOD");
        long generation = repository.generation();
        repository.block("OtherName", true);
        assertFalse(repository.release(OFFLINE, generation));
        assertTrue(repository.release(OFFLINE, repository.generation()));
        assertEquals("preserve-me", Files.readString(playerData));
    }

    @Test void legacyLoaderRegistryImportsOnceAndOriginalIsPreserved() throws Exception {
        Path registry = directory.resolve("trueuuid-registry.json");
        String original = """
                {"FixGOD":{"premiumUuid":"d64da409-52f6-4ce8-a082-73b9a5d303bd",
                "firstVerifiedAt":10,"lastVerifiedAt":20,"authSource":"MOJANG","authDisplayName":"Mojang"}}
                """;
        Files.writeString(registry, original);
        PersistentIdentityRepository repository = new PersistentIdentityRepository(directory.resolve("store.json"));
        assertEquals(1, repository.importVerifiedRegistryIfEmpty(registry));
        assertEquals(0, repository.importVerifiedRegistryIfEmpty(registry));
        assertEquals(original, Files.readString(registry));
        assertEquals(AuthenticatedIdentity.Authority.MOJANG,
                repository.identityOf(PREMIUM).orElseThrow().authority());
    }

    @Test void malformedLegacyLoaderRegistryRefusesImport() throws Exception {
        Path registry = directory.resolve("trueuuid-registry.json");
        Files.writeString(registry, "{\"FixGOD\":{\"premiumUuid\":\""
                + PREMIUM + "\",\"unexpected\":true}}");
        PersistentIdentityRepository repository = new PersistentIdentityRepository(directory.resolve("store.json"));
        assertThrows(IOException.class, () -> repository.importVerifiedRegistryIfEmpty(registry));
        assertTrue(repository.snapshot().isEmpty());
    }
}
