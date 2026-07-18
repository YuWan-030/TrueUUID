package cn.alini.trueuuid.fabric.login;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricAuthenticationSourceTest {
    @Test
    void mapsVerifiedGraceAndFallbackToTheSharedPublicVocabulary() {
        assertEquals("session-verified premium login", FabricAuthenticationSource.VERIFIED.auditLabel());
        assertEquals("recent same-IP grace login", FabricAuthenticationSource.GRACE.auditLabel());
        assertEquals("offline fallback login", FabricAuthenticationSource.OFFLINE_FALLBACK.auditLabel());
        assertEquals("trueuuid.chat.premium", FabricAuthenticationSource.VERIFIED.chatKey());
        assertEquals("trueuuid.chat.premium", FabricAuthenticationSource.GRACE.chatKey());
        assertEquals("trueuuid.chat.offline_fallback", FabricAuthenticationSource.OFFLINE_FALLBACK.chatKey());
        assertEquals(FabricAuthenticationSource.ClientStatus.PREMIUM, FabricAuthenticationSource.VERIFIED.clientStatus());
        assertEquals(FabricAuthenticationSource.ClientStatus.PREMIUM, FabricAuthenticationSource.GRACE.clientStatus());
        assertEquals(FabricAuthenticationSource.ClientStatus.OFFLINE, FabricAuthenticationSource.OFFLINE_FALLBACK.clientStatus());
    }

    @Test
    void unknownWireDataCannotManufacturePremium() {
        assertNull(FabricAuthenticationSource.ClientStatus.fromWireId(0));
        assertNull(FabricAuthenticationSource.ClientStatus.fromWireId(3));
        assertNull(FabricAuthenticationSource.ClientStatus.fromWireId(255));
    }
}
