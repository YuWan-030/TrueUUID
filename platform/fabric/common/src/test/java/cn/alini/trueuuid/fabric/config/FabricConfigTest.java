package cn.alini.trueuuid.fabric.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricConfigTest {
    @TempDir Path temporaryDirectory;

    @Test
    void oldFilesKeepMissingFeedbackAndOverlayKeysAtCompatibleDefaults() throws Exception {
        Path file = temporaryDirectory.resolve("trueuuid.json");
        Files.writeString(file, "{\"auth\":{\"allowOfflineOnFailure\":false,\"showJoinFeedback\":false,"
                + "\"overlayCorner\":\"top_left\",\"overlayOffsetX\":17,\"overlayOffsetY\":-8,\"overlayScale\":2.0}}");

        FabricConfig.load(file);

        assertFalse(FabricConfig.allowOfflineOnFailure());
        assertFalse(FabricConfig.showJoinFeedback());
        assertFalse(FabricConfig.showJoinTitle());
        assertTrue(FabricConfig.showAccountOverlay());
        assertEquals("top_left", FabricConfig.overlayCorner());
        assertEquals(17, FabricConfig.overlayOffsetX());
        assertEquals(-8, FabricConfig.overlayOffsetY());
        assertEquals(2.0F, FabricConfig.overlayScale());
        assertTrue(FabricConfig.yggdrasilHosts().isEmpty());
    }

    @Test
    void readsBoundedYggdrasilHostAllowlist() throws Exception {
        Path file = temporaryDirectory.resolve("trueuuid.json");
        Files.writeString(file, "{\"auth\":{\"yggdrasilHosts\":[\"skin.example\",\"  auth.example  \",123,\"\"]}}");

        FabricConfig.load(file);

        assertEquals(java.util.List.of("skin.example", "auth.example"), FabricConfig.yggdrasilHosts());
    }

    @Test
    void malformedOrOversizedFilesRetainSecureDefaults() throws Exception {
        Path malformed = temporaryDirectory.resolve("malformed.json");
        Files.writeString(malformed, "not json");
        FabricConfig.load(malformed);
        assertTrue(FabricConfig.allowOfflineOnFailure());
        assertTrue(FabricConfig.knownPremiumDenyOffline());
        assertTrue(FabricConfig.showJoinFeedback());
        assertFalse(FabricConfig.showJoinTitle());
        assertTrue(FabricConfig.showAccountOverlay());
        assertEquals("bottom_right", FabricConfig.overlayCorner());
        assertEquals(1.0F, FabricConfig.overlayScale());

        Path oversized = temporaryDirectory.resolve("oversized.json");
        Files.write(oversized, new byte[1024 * 1024 + 1]);
        FabricConfig.load(oversized);
        assertTrue(FabricConfig.knownPremiumDenyOffline());
        assertTrue(FabricConfig.showJoinFeedback());
        assertEquals(0, FabricConfig.overlayOffsetX());
    }
}
