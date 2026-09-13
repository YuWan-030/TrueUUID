package cn.alini.trueuuid.paper.v1_20_1;

import cn.alini.trueuuid.bukkit.StartupBanner;
import cn.alini.trueuuid.bukkit.Tone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdventureMessageStyleTest {
    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test void rendersTheSameWordsAsTheSpigotStyle() {
        assertEquals("TrueUUID » Premium account verified.",
                plain(AdventureMessageStyle.render(Tone.SUCCESS, "Premium account verified.")));
        assertEquals("  uuid=abc, kind=PREMIUM",
                plain(AdventureMessageStyle.render(Tone.DETAIL, "uuid=abc, kind=PREMIUM")));
        assertEquals("TrueUUID · Offline fallback",
                plain(AdventureMessageStyle.renderActionBar(Tone.ERROR, "Offline fallback")));
    }

    @Test void contentIsNeverParsedAsMiniMessage() {
        assertEquals("TrueUUID » <red>name</red> <click:run_command:/op x>",
                plain(AdventureMessageStyle.render(Tone.INFO, "<red>name</red> <click:run_command:/op x>")));
    }

    @Test void everyToneHasADistinctColour() {
        assertEquals(Tone.values().length, Arrays.stream(Tone.values())
                .map(AdventureMessageStyle::color).distinct().count());
    }

    @Test void bannerKeepsPlainPolicyWords() {
        List<StartupBanner.Line> lines = StartupBanner.lines("1.3.0", "Paper", "1.20.1", true,
                "AUTO", "ALLOW_VANILLA", "CONSENT_REQUIRED");
        assertEquals("  TrueUUID 1.3.0 for Paper 1.20.1", plain(AdventureMessageStyle.renderBanner(lines.get(0))));
        assertEquals("  transport AUTO, offline clients ALLOW_VANILLA, admission CONSENT_REQUIRED",
                plain(AdventureMessageStyle.renderBanner(lines.get(1))));
    }
}
