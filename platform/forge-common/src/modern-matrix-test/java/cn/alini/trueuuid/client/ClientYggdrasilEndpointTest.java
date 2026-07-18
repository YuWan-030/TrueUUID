package cn.alini.trueuuid.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Contract test reused by each modern-matrix Forge target. */
class ClientYggdrasilEndpointTest {
    @Test
    void buildsHasJoinedUrlFromApiRoot() {
        assertEquals("https://skin.example/api/yggdrasil/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot("https://skin.example/api/yggdrasil"));
        assertEquals("https://skin.example/api/yggdrasil/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot("https://skin.example/api/yggdrasil/"));
        assertEquals("", ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot(" "));
        assertEquals("", ClientYggdrasilEndpoint.buildHasJoinedUrlFromApiRoot(null));
    }

    @Test
    void unwrapsAuthlibInjectorLocalProxy() {
        assertEquals("https://skin.example/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.sanitizeUrl(
                        "http://127.0.0.1:32749/https/skin.example/sessionserver/session/minecraft/hasJoined"));
        assertEquals("http://skin.example/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.sanitizeUrl(
                        "http://localhost:32749/http/skin.example/sessionserver/session/minecraft/hasJoined"));
    }

    @Test
    void mojangEndpointsStayEmpty() {
        assertEquals("", ClientYggdrasilEndpoint.sanitizeUrl(
                "https://sessionserver.mojang.com/session/minecraft/hasJoined"));
        assertEquals("", ClientYggdrasilEndpoint.sanitizeUrl(
                "http://127.0.0.1:32749/https/sessionserver.mojang.com/session/minecraft/hasJoined"));
    }

    @Test
    void stripsQueryParameters() {
        assertEquals("https://skin.example/sessionserver/session/minecraft/hasJoined",
                ClientYggdrasilEndpoint.sanitizeUrl(
                        "https://skin.example/sessionserver/session/minecraft/hasJoined?username=x&serverId=y"));
    }
}
