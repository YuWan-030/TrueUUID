package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SafeSessionVerifierTest {
    @Test
    void omitsServerObservedClientIpFromHasJoinedRequest() throws Exception {
        var request = new SessionVerifier.Request(
                "PremiumUser", "0123456789abcdef", "203.0.113.42", "");
        URI target = SafeSessionVerifier.withQuery(
                SafeSessionVerifier.MOJANG_HAS_JOINED, request);

        assertEquals("username=PremiumUser&serverId=0123456789abcdef", target.getRawQuery());
        assertFalse(target.getRawQuery().contains("ip="));
    }

    @Test
    void encodesHasJoinedValuesExactlyOnce() throws Exception {
        var request = new SessionVerifier.Request(
                "Premium User", "nonce+value", "2001:db8::1", "https://auth.example.com/hasJoined");
        URI target = SafeSessionVerifier.withQuery(
                URI.create("https://auth.example.com/sessionserver/session/minecraft/hasJoined"),
                request);

        assertEquals("username=Premium+User&serverId=nonce%2Bvalue", target.getRawQuery());
    }
}
