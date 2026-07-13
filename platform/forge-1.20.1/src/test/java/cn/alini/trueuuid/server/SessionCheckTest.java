package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionCheckTest {
    @Test void privateAndMalformedRemoteAddressesAreOmittedFromHasJoined() {
        assertEquals("", SessionCheck.publicClientIpOrEmpty("127.0.0.1"));
        assertEquals("", SessionCheck.publicClientIpOrEmpty("::1"));
        assertEquals("", SessionCheck.publicClientIpOrEmpty("not-an-ip"));
        assertEquals("8.8.8.8", SessionCheck.publicClientIpOrEmpty("8.8.8.8"));
    }
}
