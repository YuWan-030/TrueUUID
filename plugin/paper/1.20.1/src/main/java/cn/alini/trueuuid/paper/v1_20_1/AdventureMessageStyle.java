package cn.alini.trueuuid.paper.v1_20_1;

import cn.alini.trueuuid.bukkit.MessageStyle;
import cn.alini.trueuuid.bukkit.StartupBanner;
import cn.alini.trueuuid.bukkit.Tone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Paper rendering with Adventure components and the blue TrueUUID gradient.
 *
 * <p>MiniMessage parses only the constant brand markup. Every content argument becomes a literal
 * text component, so a player name or configuration value can never inject tags.
 */
final class AdventureMessageStyle implements MessageStyle {
    static final TextColor BRAND_LIGHT = TextColor.color(0x4FC3F7);
    static final Component BRAND = MiniMessage.miniMessage()
            .deserialize("<bold><gradient:#4FC3F7:#1565C0>TrueUUID</gradient></bold>");
    static final Component BRAND_PLAIN_WEIGHT = MiniMessage.miniMessage()
            .deserialize("<gradient:#4FC3F7:#1565C0>TrueUUID</gradient>");

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
        return Component.empty().append(BRAND_PLAIN_WEIGHT)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(Objects.requireNonNull(text, "text"), color(tone)));
    }

    static Component renderBanner(StartupBanner.Line line) {
        Component detail = Component.text(line.detail(), color(line.tone()));
        if (line.title().isEmpty()) return Component.text("  ").append(detail);
        return Component.text("  ").append(BRAND).append(Component.text(" ")).append(detail);
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
