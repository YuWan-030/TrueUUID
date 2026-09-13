package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTargetTest {
    private static Properties properties(String target, String platform, String status) {
        Properties properties = new Properties();
        properties.setProperty("target", target);
        properties.setProperty("platform", platform);
        properties.setProperty("minecraft", "1.20.1");
        properties.setProperty("version", "1.3.0");
        properties.setProperty("support_status", status);
        return properties;
    }

    @Test void parsesGeneratedMetadata() {
        PluginTarget paper = PluginTarget.parse(properties("paper-1.20.1", "paper", "SUPPORTED"));
        assertEquals(ServerPlatform.PAPER, paper.platform());
        assertTrue(paper.supported());
        assertFalse(PluginTarget.parse(properties("spigot-1.20.1", "spigot", "UNSUPPORTED-CANDIDATE")).supported());
    }

    @Test void refusesInconsistentOrIncompleteMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> PluginTarget.parse(properties("paper-1.20.1", "spigot", "SUPPORTED")));
        assertThrows(IllegalArgumentException.class,
                () -> PluginTarget.parse(properties("paper-1.20.1", "paper", "MAYBE")));
        assertThrows(IllegalArgumentException.class,
                () -> PluginTarget.parse(properties("folia-1.20.1", "folia", "SUPPORTED")));
        Properties incomplete = properties("paper-1.20.1", "paper", "SUPPORTED");
        incomplete.remove("version");
        assertThrows(IllegalArgumentException.class, () -> PluginTarget.parse(incomplete));
    }
}
