package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.Optional;

/**
 * Startup gates that come before the seam table: the Minecraft version, the CraftBukkit
 * package, the server platform, and direct (non-proxied) connections.
 *
 * <p>No build number is pinned. The seam table decides whether a particular 1.20.1 build is
 * usable, and this class only rejects servers that can never be.
 */
public final class ServerCompatibility {
    public static final String MINECRAFT_VERSION = "1.20.1";
    static final String BUKKIT_VERSION = "1.20.1-R0.1-SNAPSHOT";
    static final String CRAFTBUKKIT_PACKAGE = "org.bukkit.craftbukkit.v1_20_R1";
    static final String SPIGOT_MARKER = "org.spigotmc.SpigotConfig";
    static final String PAPER_MARKER = "io.papermc.paper.configuration.GlobalConfiguration";

    static void requireMinecraft(String bukkitVersion, String serverClassName) {
        if (!BUKKIT_VERSION.equals(bukkitVersion)) {
            throw new IllegalStateException("this TrueUUID build is for Minecraft " + MINECRAFT_VERSION
                    + " but the server reports " + bukkitVersion
                    + ". Install the TrueUUID build for this Minecraft version.");
        }
        if (serverClassName == null || !serverClassName.startsWith(CRAFTBUKKIT_PACKAGE + ".")) {
            throw new IllegalStateException("expected a CraftBukkit " + CRAFTBUKKIT_PACKAGE
                    + " server implementation, found " + serverClassName);
        }
    }

    static void requirePlatform(ServerPlatform expected, boolean spigotRuntime, boolean paperRuntime) {
        Objects.requireNonNull(expected, "expected");
        switch (expected) {
            case PAPER -> {
                if (!paperRuntime) {
                    throw new IllegalStateException("this is the Paper build of TrueUUID. "
                            + "Install the TrueUUID Spigot " + MINECRAFT_VERSION + " build on Spigot.");
                }
            }
            case SPIGOT -> {
                if (paperRuntime) {
                    throw new IllegalStateException("this is the Spigot build of TrueUUID. "
                            + "Install the TrueUUID Paper " + MINECRAFT_VERSION + " build on Paper.");
                }
                if (!spigotRuntime) {
                    throw new IllegalStateException("the Spigot build of TrueUUID requires Spigot, "
                            + "not plain CraftBukkit.");
                }
            }
        }
    }

    /**
     * @param bungeeCordForwarding spigot.yml {@code settings.bungeecord}, or empty when unreadable
     * @param velocityForwarding Paper {@code proxies.velocity.enabled}, or empty when unreadable
     * @param velocityApplies whether the platform has Velocity modern forwarding at all
     */
    public static void requireDirectConnections(
            Optional<Boolean> bungeeCordForwarding,
            Optional<Boolean> velocityForwarding,
            boolean velocityApplies
    ) {
        if (bungeeCordForwarding.isEmpty()) {
            throw new IllegalStateException("could not read settings.bungeecord from spigot.yml");
        }
        if (bungeeCordForwarding.orElseThrow()) {
            throw new IllegalStateException("settings.bungeecord is enabled in spigot.yml. "
                    + "TrueUUID authenticates direct connections itself and cannot run behind a proxy.");
        }
        if (!velocityApplies) return;
        if (velocityForwarding.isEmpty()) {
            throw new IllegalStateException("could not read proxies.velocity.enabled from the Paper configuration");
        }
        if (velocityForwarding.orElseThrow()) {
            throw new IllegalStateException("proxies.velocity.enabled is true in the Paper configuration. "
                    + "TrueUUID authenticates direct connections itself and cannot run behind a proxy.");
        }
    }

    /** Reads one boolean server setting. A missing or non-boolean value is reported as empty. */
    public static Optional<Boolean> booleanSetting(ConfigurationSection section, String path) {
        if (section == null || !section.isBoolean(path)) return Optional.empty();
        return Optional.of(section.getBoolean(path));
    }

    static boolean classPresent(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    private ServerCompatibility() {
    }
}
