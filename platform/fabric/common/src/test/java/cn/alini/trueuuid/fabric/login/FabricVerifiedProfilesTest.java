package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.VerifiedProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricVerifiedProfilesTest {
    @Test
    void createsAProfileWithPropertiesAcrossMutableAndRecordAuthlibEras() {
        UUID uuid = UUID.randomUUID();
        VerifiedProfile verified = new VerifiedProfile(uuid, "Player", List.of(
                new VerifiedProfile.Property("textures", "value", "signature")));

        var profile = FabricVerifiedProfiles.create(verified);

        assertEquals(uuid, FabricGameProfiles.id(profile));
        assertEquals("Player", FabricGameProfiles.name(profile));
    }
}
