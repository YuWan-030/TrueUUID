package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HasJoinedProfileParserTest {
    @Test
    void parsesCompactUuidAndSignedProperties() {
        var response = new SafeSessionHttpClient.Response(200, """
                {"id":"0123456789abcdef0123456789abcdef","name":"PremiumUser","properties":[
                  {"name":"textures","value":"skin-payload","signature":"skin-signature"},
                  {"name":null,"value":"ignored"}
                ]}
                """);

        VerifiedProfile profile = HasJoinedProfileParser.parse(response).orElseThrow();
        assertEquals(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), profile.uuid());
        assertEquals("PremiumUser", profile.name());
        assertEquals(1, profile.properties().size());
        assertEquals("skin-signature", profile.properties().get(0).signature());
    }

    @Test
    void rejectsNonSuccessAndIncompleteProfiles() {
        assertTrue(HasJoinedProfileParser.parse(
                new SafeSessionHttpClient.Response(204, "")).isEmpty());
        assertTrue(HasJoinedProfileParser.parse(
                new SafeSessionHttpClient.Response(200, "{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"\"}"))
                .isEmpty());
    }

    @Test
    void rejectsMalformedProfileUuid() {
        var response = new SafeSessionHttpClient.Response(200,
                "{\"id\":\"not-a-uuid\",\"name\":\"PremiumUser\"}");
        assertThrows(IllegalArgumentException.class, () -> HasJoinedProfileParser.parse(response));
    }
}
