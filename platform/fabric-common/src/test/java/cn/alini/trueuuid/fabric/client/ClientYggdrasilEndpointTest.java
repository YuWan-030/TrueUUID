package cn.alini.trueuuid.fabric.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientYggdrasilEndpointTest {
    @Test
    void buildsHasJoinedUrlFromAuthlibInjectorApiRoot() {
        assertEquals("https://skin.example/api/yggdrasil/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot("https://skin.example/api/yggdrasil"));
        assertEquals("https://skin.example/api/yggdrasil/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot("https://skin.example/api/yggdrasil/"));
        assertEquals("", ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot(" "));
        assertEquals("", ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot(null));
    }

    @Test
    void stripsQueryAndMojangDefault() {
        assertEquals("https://skin.example/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.sanitizeUrl(
                        "https://skin.example/sessionserver/session/minecraft/hasJoined?username=A"));
        assertEquals("", ClientYggdrasilEndpoint.sanitizeUrl(
                "https://sessionserver.mojang.com/session/minecraft/hasJoined"));
    }

    @Test
    void unwrapsLocalAuthlibInjectorProxy() {
        assertEquals("https://skin.example/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.sanitizeUrl(
                        "http://127.0.0.1:12345/https/skin.example/sessionserver/session/minecraft/hasJoined"));
    }
}
