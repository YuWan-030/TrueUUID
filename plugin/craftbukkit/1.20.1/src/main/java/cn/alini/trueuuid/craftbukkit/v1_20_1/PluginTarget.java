package cn.alini.trueuuid.craftbukkit.v1_20_1;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

/**
 * Build metadata generated from {@code ci/server-plugin-targets.json} into every plugin JAR.
 *
 * <p>The running plugin checks it against its own platform adapter and the live server, so a JAR
 * can never report a target or support status other than the one it was built and approved as.
 */
record PluginTarget(String id, ServerPlatform platform, String minecraftVersion, String version, boolean supported) {
    static final String RESOURCE = "trueuuid-target.properties";

    PluginTarget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(version, "version");
        if (!id.equals(platform.id() + "-" + minecraftVersion)) {
            throw new IllegalArgumentException("target id " + id + " does not match "
                    + platform.id() + "-" + minecraftVersion);
        }
    }

    static PluginTarget load(ClassLoader loader) throws IOException {
        try (InputStream input = loader.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("plugin JAR is missing " + RESOURCE);
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return parse(properties);
        }
    }

    static PluginTarget parse(Properties properties) {
        String status = required(properties, "support_status");
        boolean supported = switch (status) {
            case "SUPPORTED" -> true;
            case "UNSUPPORTED-CANDIDATE" -> false;
            default -> throw new IllegalArgumentException("unknown support_status " + status);
        };
        return new PluginTarget(required(properties, "target"),
                ServerPlatform.fromId(required(properties, "platform")),
                required(properties, "minecraft"), required(properties, "version"), supported);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(RESOURCE + " lacks " + key);
        return value.trim();
    }
}
