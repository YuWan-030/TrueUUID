package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCompatibilityTest {
    @Test void acceptsAnyCraftBukkit1201BuildWithoutPinningABuildNumber() {
        assertDoesNotThrow(() -> ServerCompatibility.requireMinecraft(
                "1.20.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_20_R1.CraftServer"));
    }

    @Test void refusesOtherMinecraftVersionsAndImplementations() {
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireMinecraft(
                "1.20.2-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_20_R2.CraftServer"));
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireMinecraft(
                "1.20.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_20_R2.CraftServer"));
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireMinecraft(
                "1.20.1-R0.1-SNAPSHOT", null));
    }

    @Test void eachJarRunsOnlyOnItsOwnPlatformAndNamesTheOtherBuild() {
        assertDoesNotThrow(() -> ServerCompatibility.requirePlatform(ServerPlatform.SPIGOT, true, false));
        assertDoesNotThrow(() -> ServerCompatibility.requirePlatform(ServerPlatform.PAPER, true, true));
        IllegalStateException spigotOnPaper = assertThrows(IllegalStateException.class,
                () -> ServerCompatibility.requirePlatform(ServerPlatform.SPIGOT, true, true));
        assertTrue(spigotOnPaper.getMessage().contains("Paper 1.20.1 build"));
        IllegalStateException paperOnSpigot = assertThrows(IllegalStateException.class,
                () -> ServerCompatibility.requirePlatform(ServerPlatform.PAPER, true, false));
        assertTrue(paperOnSpigot.getMessage().contains("Spigot 1.20.1 build"));
        assertThrows(IllegalStateException.class,
                () -> ServerCompatibility.requirePlatform(ServerPlatform.SPIGOT, false, false));
    }

    @Test void proxyForwardingOrAnUnreadableSettingRefusesStartup() {
        assertDoesNotThrow(() -> ServerCompatibility.requireDirectConnections(
                Optional.of(false), Optional.empty(), false));
        assertDoesNotThrow(() -> ServerCompatibility.requireDirectConnections(
                Optional.of(false), Optional.of(false), true));
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireDirectConnections(
                Optional.of(true), Optional.of(false), true));
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireDirectConnections(
                Optional.empty(), Optional.of(false), true));
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireDirectConnections(
                Optional.of(false), Optional.of(true), true));
        assertThrows(IllegalStateException.class, () -> ServerCompatibility.requireDirectConnections(
                Optional.of(false), Optional.empty(), true));
    }

    @Test void readsOnlyRealBooleanSettings() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("settings.bungeecord", false);
        configuration.set("proxies.velocity.enabled", "false");
        assertEquals(Optional.of(false), ServerCompatibility.booleanSetting(configuration, "settings.bungeecord"));
        assertEquals(Optional.empty(), ServerCompatibility.booleanSetting(configuration, "proxies.velocity.enabled"));
        assertEquals(Optional.empty(), ServerCompatibility.booleanSetting(configuration, "missing.key"));
        assertEquals(Optional.empty(), ServerCompatibility.booleanSetting(null, "settings.bungeecord"));
    }
}
