package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineIdentityTest {
    @Test void derivesTheExactVanillaOfflineUuidAndPreservesCase() {
        VerifiedProfile profile = OfflineIdentity.profile("1233");
        assertEquals(UUID.fromString("5b314e2f-f184-32cb-9495-d55385f89b89"), profile.uuid());
        assertEquals("1233", profile.name());
        assertEquals(0, profile.properties().size());
    }

    @Test void rejectsNamesMinecraftCannotAccept() {
        assertThrows(IllegalArgumentException.class, () -> OfflineIdentity.profile("bad name"));
        assertThrows(IllegalArgumentException.class, () -> OfflineIdentity.profile("x".repeat(17)));
    }
}
