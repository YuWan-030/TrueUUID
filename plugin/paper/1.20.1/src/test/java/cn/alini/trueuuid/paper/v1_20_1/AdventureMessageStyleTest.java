package cn.alini.trueuuid.paper.v1_20_1;

import cn.alini.trueuuid.bukkit.StartupBanner;
import cn.alini.trueuuid.bukkit.Tone;
import cn.alini.trueuuid.presentation.BrandText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdventureMessageStyleTest {
    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test void wordmarkUsesTheSharedGradientColoursInBold() {
        List<Component> letters = AdventureMessageStyle.BRAND.children();
        assertEquals(BrandText.glyphs().size(), letters.size());
        for (int index = 0; index < letters.size(); index++) {
            TextComponent letter = (TextComponent) letters.get(index);
            assertEquals(BrandText.glyphs().get(index).text(), letter.content());
            assertEquals(BrandText.glyphs().get(index).rgb(), letter.color().value());
            assertTrue(letter.hasDecoration(TextDecoration.BOLD));
        }
    }

    @Test void rendersTheSameWordsAsTheSpigotStyle() {
        assertEquals("TrueUUID » Premium account verified.",
                plain(AdventureMessageStyle.render(Tone.SUCCESS, "Premium account verified.")));
        assertEquals("  uuid=abc, kind=PREMIUM",
                plain(AdventureMessageStyle.render(Tone.DETAIL, "uuid=abc, kind=PREMIUM")));
        assertEquals("TrueUUID · Offline fallback",
                plain(AdventureMessageStyle.renderActionBar(Tone.ERROR, "Offline fallback")));
    }

    @Test void contentIsNeverParsedAsMarkup() {
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
