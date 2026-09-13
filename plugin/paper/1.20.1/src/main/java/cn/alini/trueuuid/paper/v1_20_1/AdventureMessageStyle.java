package cn.alini.trueuuid.paper.v1_20_1;

import cn.alini.trueuuid.bukkit.MessageStyle;
import cn.alini.trueuuid.bukkit.StartupBanner;
import cn.alini.trueuuid.bukkit.Tone;
import cn.alini.trueuuid.presentation.BrandText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Paper rendering with Adventure components and the shared bold gradient wordmark.
 *
 * <p>Every content argument becomes a literal text component, so a player name or configuration
 * value can never inject formatting or tags.
 */
final class AdventureMessageStyle implements MessageStyle {
    static final TextColor BRAND_LIGHT = TextColor.color(BrandText.END_RGB);
    static final Component BRAND = wordmark();

    @Override public void send(CommandSender recipient, Tone tone, String text) {
        recipient.sendMessage(render(tone, text));
    }

    @Override public void actionBar(Player player, Tone tone, String text) {
        player.sendActionBar(renderActionBar(tone, text));
    }

    @Override public void banner(Plugin plugin, List<StartupBanner.Line> lines) {
        for (StartupBanner.Line line : lines) {
            plugin.getComponentLogger().info(renderBanner(line));
        }
    }

    static Component render(Tone tone, String text) {
        Component body = Component.text(Objects.requireNonNull(text, "text"), color(tone));
        if (tone == Tone.DETAIL) return Component.text("  ").append(body);
        return Component.empty().append(BRAND)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(body);
    }

    static Component renderActionBar(Tone tone, String text) {
        return Component.empty().append(BRAND)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(Objects.requireNonNull(text, "text"), color(tone)));
    }

    static Component renderBanner(StartupBanner.Line line) {
        Component detail = Component.text(line.detail(), color(line.tone()));
        if (line.title().isEmpty()) return Component.text("  ").append(detail);
        return Component.text("  ").append(BRAND).append(Component.text(" ")).append(detail);
    }

    static Component wordmark() {
        TextComponent.Builder wordmark = Component.text();
        for (BrandText.Glyph glyph : BrandText.glyphs()) {
            wordmark.append(Component.text(glyph.text(), TextColor.color(glyph.rgb()), TextDecoration.BOLD));
        }
        return wordmark.build();
    }

    static TextColor color(Tone tone) {
        return switch (Objects.requireNonNull(tone, "tone")) {
            case SUCCESS -> NamedTextColor.GREEN;
            case INFO -> BRAND_LIGHT;
            case WARNING -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
            case DETAIL -> NamedTextColor.GRAY;
        };
    }
}
