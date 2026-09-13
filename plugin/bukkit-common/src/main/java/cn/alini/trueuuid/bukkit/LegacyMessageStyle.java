package cn.alini.trueuuid.bukkit;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/** Spigot rendering with legacy colour codes. CraftBukkit maps them to ANSI on the console. */
public final class LegacyMessageStyle implements MessageStyle {
    static final String PREFIX = ChatColor.BLUE + "" + ChatColor.BOLD + "True"
            + ChatColor.AQUA + ChatColor.BOLD + "UUID" + ChatColor.DARK_GRAY + " » ";

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
        return ChatColor.BLUE + "True" + ChatColor.AQUA + "UUID" + ChatColor.DARK_GRAY + " · "
                + color(tone) + literal(text);
    }

    static String renderBanner(StartupBanner.Line line) {
        String title = line.title().isEmpty() ? "  " : "  " + ChatColor.BLUE + ChatColor.BOLD + "True"
                + ChatColor.AQUA + ChatColor.BOLD + "UUID" + ChatColor.RESET + " ";
        return title + color(line.tone()) + literal(line.detail());
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

    /** Content is literal. A stray section sign can never restyle the rest of the line. */
    private static String literal(String text) {
        return Objects.requireNonNull(text, "text").replace(ChatColor.COLOR_CHAR, '?');
    }
}
