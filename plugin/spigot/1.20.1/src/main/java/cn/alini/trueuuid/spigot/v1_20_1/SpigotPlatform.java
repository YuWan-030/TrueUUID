package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.bukkit.LegacyMessageStyle;
import cn.alini.trueuuid.bukkit.MessageStyle;
import cn.alini.trueuuid.craftbukkit.v1_20_1.PlatformAdapter;
import cn.alini.trueuuid.craftbukkit.v1_20_1.ServerCompatibility;
import cn.alini.trueuuid.craftbukkit.v1_20_1.ServerPlatform;
import org.bukkit.Server;

import java.util.Optional;

final class SpigotPlatform implements PlatformAdapter {
    private final MessageStyle style = new LegacyMessageStyle();

    @Override public ServerPlatform platform() {
        return ServerPlatform.SPIGOT;
    }

    @Override public MessageStyle style() {
        return style;
    }

    @Override public void requireDirectConnections(Server server) {
        ServerCompatibility.requireDirectConnections(
                ServerCompatibility.booleanSetting(server.spigot().getConfig(), "settings.bungeecord"),
                Optional.empty(),
                false);
    }
}
