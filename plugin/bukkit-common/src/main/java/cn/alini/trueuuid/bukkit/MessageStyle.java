package cn.alini.trueuuid.bukkit;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Presentation-only rendering for chat, action bar, and the decorative startup banner.
 *
 * <p>Implementations must treat every text argument as literal content. Player names and
 * configuration values are never parsed as markup. Machine-read audit lines do not use this
 * interface and stay on the plain plugin logger.
 */
public interface MessageStyle {
    void send(CommandSender recipient, Tone tone, String text);

    void actionBar(Player player, Tone tone, String text);

    void banner(Plugin plugin, List<StartupBanner.Line> lines);
}
