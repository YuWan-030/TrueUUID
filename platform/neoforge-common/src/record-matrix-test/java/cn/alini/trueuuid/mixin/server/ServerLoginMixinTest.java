package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerLoginMixinTest {
    @Test
    void createsRecordEraProfileWithSignedTextureProperty() throws ReflectiveOperationException {
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        VerifiedProfile verified = new VerifiedProfile(uuid, "PremiumUser", List.of(
                new VerifiedProfile.Property("textures", "skin-payload", "skin-signature")
        ));

        Method factory = ServerLoginMixin.class.getDeclaredMethod("trueuuid$profile", VerifiedProfile.class);
        assertTrue(Modifier.isPrivate(factory.getModifiers()), "Mixin static helpers must remain private");
        factory.setAccessible(true);
        GameProfile profile = (GameProfile) factory.invoke(null, verified);
        Property texture = profile.properties().get("textures").iterator().next();

        assertEquals(uuid, profile.id());
        assertEquals("PremiumUser", profile.name());
        assertEquals("skin-payload", texture.value());
        assertEquals("skin-signature", texture.signature());
    }
}
