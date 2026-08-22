package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAdmissionMode;
import cn.alini.trueuuid.server.AdmissionMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridPluginSettingsTest {
    @Test void loadsSecureFeedbackDefaults() {
        HybridPluginSettings settings = HybridPluginSettings.load(new YamlConfiguration());
        assertEquals(HybridLoginCoordinator.Mode.AUTO, settings.mode());
        assertEquals(Duration.ofSeconds(30), settings.loginTimeout());
        assertEquals(OfflineAdmissionMode.ALLOW_VANILLA, settings.offlineAdmissionMode());
        assertEquals(AdmissionMode.CONSENT_REQUIRED, settings.admissionMode());
        assertEquals("-", settings.aliasPrefix());
        assertTrue(settings.showPlayerChat());
        assertTrue(settings.showVanillaActionBar());
        assertEquals(20, settings.vanillaActionBarDelayTicks());
    }

    @Test void loadsExplicitFeedbackChoices() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("feedback.player-chat", false);
        configuration.set("feedback.vanilla-action-bar", false);
        configuration.set("feedback.vanilla-action-bar-delay-ticks", 40);
        configuration.set("offline.mode", "allow_vanilla");
        HybridPluginSettings settings = HybridPluginSettings.load(configuration);
        assertFalse(settings.showPlayerChat());
        assertFalse(settings.showVanillaActionBar());
        assertEquals(40, settings.vanillaActionBarDelayTicks());
        assertEquals(OfflineAdmissionMode.ALLOW_VANILLA, settings.offlineAdmissionMode());
    }

    @Test void rejectsAnUnboundedActionBarDelay() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("feedback.vanilla-action-bar-delay-ticks", 101);
        assertThrows(IllegalArgumentException.class,
                () -> HybridPluginSettings.load(configuration));
    }

    @Test void rejectsUnknownOfflineMode() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("offline.mode", "unsafe_magic");
        assertThrows(IllegalArgumentException.class, () -> HybridPluginSettings.load(configuration));
    }

    @Test void existingConfigurationWithoutAdmissionKeyKeepsStrictBehavior() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("authentication.mode", "AUTO");
        assertEquals(AdmissionMode.PREMIUM_RESERVED,
                HybridPluginSettings.load(configuration, false).admissionMode());
    }

    @Test void firstClaimRequiresExplicitRiskAcknowledgement() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("admission.mode", "FIRST_CLAIM");
        assertThrows(IllegalArgumentException.class, () -> HybridPluginSettings.load(configuration));
        configuration.set("admission.first-claim-risk-accepted", true);
        assertEquals(AdmissionMode.FIRST_CLAIM,
                HybridPluginSettings.load(configuration).admissionMode());
    }
}
