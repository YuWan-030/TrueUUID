package cn.alini.trueuuid.craftbukkit.v1_20_1;

import java.util.Locale;

/** The server platform a plugin JAR is built and tested for. */
public enum ServerPlatform {
    SPIGOT("Spigot"),
    PAPER("Paper");

    private final String displayName;

    ServerPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The manifest identifier, for example {@code paper}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static ServerPlatform fromId(String id) {
        for (ServerPlatform platform : values()) {
            if (platform.id().equals(id)) return platform;
        }
        throw new IllegalArgumentException("unknown server platform: " + id);
    }
}
