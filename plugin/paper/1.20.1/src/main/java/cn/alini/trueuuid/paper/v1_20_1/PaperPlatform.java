package cn.alini.trueuuid.paper.v1_20_1;

import cn.alini.trueuuid.bukkit.MessageStyle;
import cn.alini.trueuuid.craftbukkit.v1_20_1.PlatformAdapter;
import cn.alini.trueuuid.craftbukkit.v1_20_1.ServerCompatibility;
import cn.alini.trueuuid.craftbukkit.v1_20_1.ServerPlatform;
import org.bukkit.Server;

final class PaperPlatform implements PlatformAdapter {
    private final MessageStyle style = new AdventureMessageStyle();

    @Override public ServerPlatform platform() {
        return ServerPlatform.PAPER;
    }

    @Override public MessageStyle style() {
        return style;
    }

    @Override public void requireDirectConnections(Server server) {
        ServerCompatibility.requireDirectConnections(
                ServerCompatibility.booleanSetting(server.spigot().getConfig(), "settings.bungeecord"),
                ServerCompatibility.booleanSetting(server.spigot().getPaperConfig(), "proxies.velocity.enabled"),
                true);
    }
}
