package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ServerLoginMixinTest {
    @Test
    void createsRecordEraProfileWithSignedTextureProperty() {
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        VerifiedProfile verified = new VerifiedProfile(uuid, "FixGOD", List.of(
                new VerifiedProfile.Property("textures", "skin-payload", "skin-signature")
        ));

        GameProfile profile = ServerLoginMixin.trueuuid$profile(verified);
        Property texture = profile.properties().get("textures").iterator().next();

        assertEquals(uuid, profile.id());
        assertEquals("FixGOD", profile.name());
        assertEquals("skin-payload", texture.value());
        assertEquals("skin-signature", texture.signature());
    }
}
