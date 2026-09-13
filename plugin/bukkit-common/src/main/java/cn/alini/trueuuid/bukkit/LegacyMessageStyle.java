package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.presentation.BrandText;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Spigot rendering with colour codes. The wordmark uses the hexadecimal colour-code form that
 * Spigot 1.16 and later render exactly in chat, the action bar, and a colour console.
 */
public final class LegacyMessageStyle implements MessageStyle {
    static final String WORDMARK = legacyWordmark();
    static final String PREFIX = WORDMARK + ChatColor.DARK_GRAY + " » ";

    @Override public void send(CommandSender recipient, Tone tone, String text) {
        recipient.sendMessage(render(tone, text));
    }

    @Override public void actionBar(Player player, Tone tone, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(renderActionBar(tone, text)));
    }

    @Override public void banner(Plugin plugin, List<StartupBanner.Line> lines) {
        for (StartupBanner.Line line : lines) {
            Bukkit.getConsoleSender().sendMessage(renderBanner(line));
        }
    }

    static String render(Tone tone, String text) {
        String body = literal(text);
        return tone == Tone.DETAIL ? "  " + color(tone) + body : PREFIX + color(tone) + body;
    }

    static String renderActionBar(Tone tone, String text) {
        return WORDMARK + ChatColor.DARK_GRAY + " · " + color(tone) + literal(text);
    }

    static String renderBanner(StartupBanner.Line line) {
        String title = line.title().isEmpty() ? "  " : "  " + WORDMARK + ChatColor.RESET + " ";
        return title + color(line.tone()) + literal(line.detail());
    }

    /** Each glyph becomes the hex marker, its six digits, bold, and then the letter. */
    static String legacyWordmark() {
        StringBuilder text = new StringBuilder();
        for (BrandText.Glyph glyph : BrandText.glyphs()) {
            text.append(ChatColor.COLOR_CHAR).append('x');
            for (char digit : glyph.hex().toCharArray()) {
                text.append(ChatColor.COLOR_CHAR).append(digit);
            }
            text.append(ChatColor.COLOR_CHAR).append('l').append(glyph.text());
        }
        return text.toString();
    }

    static ChatColor color(Tone tone) {
        return switch (Objects.requireNonNull(tone, "tone")) {
            case SUCCESS -> ChatColor.GREEN;
            case INFO -> ChatColor.AQUA;
            case WARNING -> ChatColor.YELLOW;
            case ERROR -> ChatColor.RED;
            case DETAIL -> ChatColor.GRAY;
        };
    }

    /** Content is literal. A stray colour character can never restyle the rest of the line. */
    private static String literal(String text) {
        return Objects.requireNonNull(text, "text").replace(ChatColor.COLOR_CHAR, '?');
    }
}
