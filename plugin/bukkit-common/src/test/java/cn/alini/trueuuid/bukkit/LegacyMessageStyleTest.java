package cn.alini.trueuuid.bukkit;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMessageStyleTest {
    @Test void prefixedLinesKeepTheWordsReadableAfterStrippingColour() {
        assertEquals("TrueUUID » Premium account verified.",
                ChatColor.stripColor(LegacyMessageStyle.render(Tone.SUCCESS, "Premium account verified.")));
        assertEquals("  uuid=abc, kind=PREMIUM",
                ChatColor.stripColor(LegacyMessageStyle.render(Tone.DETAIL, "uuid=abc, kind=PREMIUM")));
        assertEquals("TrueUUID · Offline fallback",
                ChatColor.stripColor(LegacyMessageStyle.renderActionBar(Tone.ERROR, "Offline fallback")));
    }

    @Test void contentCannotInjectColourCodes() {
        String rendered = LegacyMessageStyle.render(Tone.INFO, "name§4red");
        assertFalse(rendered.contains("§4"));
        assertTrue(rendered.endsWith("name?4red"));
    }

    @Test void everyToneHasADistinctColour() {
        assertEquals(Tone.values().length, java.util.Arrays.stream(Tone.values())
                .map(LegacyMessageStyle::color).distinct().count());
    }

    @Test void bannerHasTheBrandedHeadingAndPlainPolicyLine() {
        List<StartupBanner.Line> lines = StartupBanner.lines("1.3.0", "Spigot", "1.20.1", true,
                "AUTO", "ALLOW_VANILLA", "CONSENT_REQUIRED");
        assertEquals("  TrueUUID 1.3.0 for Spigot 1.20.1",
                ChatColor.stripColor(LegacyMessageStyle.renderBanner(lines.get(0))));
        assertEquals("  transport AUTO, offline clients ALLOW_VANILLA, admission CONSENT_REQUIRED",
                ChatColor.stripColor(LegacyMessageStyle.renderBanner(lines.get(1))));
        assertTrue(StartupBanner.lines("1.3.0", "Paper", "1.20.1", false, "AUTO", "DENY", "PREMIUM_RESERVED")
                .get(0).detail().endsWith("(unsupported candidate)"));
    }
}
