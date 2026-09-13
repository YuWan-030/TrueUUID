package cn.alini.trueuuid.craftbukkit.v1_20_1;

import cn.alini.trueuuid.bukkit.MessageStyle;
import org.bukkit.Server;

/** The small platform-specific surface a concrete Spigot or Paper plugin supplies. */
public interface PlatformAdapter {
    ServerPlatform platform();

    MessageStyle style();

    /**
     * Refuses a server that accepts identities forwarded by a proxy. TrueUUID is the
     * authentication boundary of a direct offline-mode server, and forwarding would let the proxy
     * handshake and this plugin both claim the same login.
     */
    void requireDirectConnections(Server server);
}
