package cn.alini.trueuuid.protocol;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Exact vanilla offline identity derivation, used only after policy permits it. */
public final class OfflineIdentity {
    public static VerifiedProfile profile(String requestedName) {
        String name = requireMinecraftName(requestedName);
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new VerifiedProfile(uuid, name, List.of());
    }

    private static String requireMinecraftName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || value.length() > 16 || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid Minecraft name");
        }
        return value;
    }

    private OfflineIdentity() {}
}
