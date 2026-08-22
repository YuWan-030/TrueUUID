package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAdmissionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigurationTest {
    @Test void freshDefaultsRequireExplicitCollisionConsent() {
        ServerConfiguration settings = ServerConfiguration.freshDefaults();
        assertEquals(HybridLoginCoordinator.Mode.AUTO, settings.authentication().transport());
        assertEquals(Duration.ofSeconds(30), settings.authentication().timeout());
        assertEquals(AdmissionMode.CONSENT_REQUIRED, settings.admission().mode());
        assertEquals(OfflineAdmissionMode.ALLOW_VANILLA, settings.admission().offlineClient());
        assertFalse(settings.admission().firstClaimRiskAccepted());
        assertEquals("-", settings.aliases().prefix());
        assertTrue(settings.feedback().privateChat());
        assertTrue(settings.feedback().vanillaActionBar());
        assertFalse(settings.feedback().title());
        assertTrue(settings.feedback().moddedOverlay());
    }

    @Test void firstClaimRequiresExplicitRiskAcknowledgement() {
        ServerConfiguration defaults = ServerConfiguration.freshDefaults();
        assertThrows(IllegalArgumentException.class, () -> new ServerConfiguration(
                defaults.authentication(),
                new ServerConfiguration.Admission(
                        AdmissionMode.FIRST_CLAIM, OfflineAdmissionMode.ALLOW_VANILLA, false),
                defaults.aliases(), defaults.feedback(), defaults.permissions()));
    }

    @Test void aliasPrefixAndBoundsAreStrict() {
        assertDoesNotThrow(() -> new ServerConfiguration.Aliases("o_"));
        assertDoesNotThrow(() -> new ServerConfiguration.Aliases("AB12"));
        assertDoesNotThrow(() -> new ServerConfiguration.Aliases("."));
        assertDoesNotThrow(() -> new ServerConfiguration.Aliases("+"));
        assertDoesNotThrow(() -> new ServerConfiguration.Aliases("-"));
        for (String invalid : List.of("", "five5", ",", "*", "a-", "é")) {
            assertThrows(IllegalArgumentException.class, () -> new ServerConfiguration.Aliases(invalid));
        }
    }

    @Test void legacySecureBooleanDerivesStrictMode() {
        assertEquals(AdmissionMode.PREMIUM_RESERVED,
                ServerConfiguration.deriveLegacyAdmissionMode(true));
        assertEquals(AdmissionMode.FIRST_CLAIM,
                ServerConfiguration.deriveLegacyAdmissionMode(false));
    }
}
