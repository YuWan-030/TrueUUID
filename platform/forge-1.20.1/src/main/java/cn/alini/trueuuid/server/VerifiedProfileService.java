package cn.alini.trueuuid.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.Connection;

final class VerifiedProfileService {
    static GameProfile create(SessionCheck.HasJoinedResult result) {
        GameProfile profile = new GameProfile(result.uuid(), result.name());
        for (SessionCheck.Property property : result.properties()) {
            Property value = property.signature() == null
                    ? new Property(property.name(), property.value())
                    : new Property(property.name(), property.value(), property.signature());
            profile.getProperties().put(property.name(), value);
        }
        return profile;
    }

    static void record(Connection connection, GameProfile profile, String ip,
                       AuthState.AuthSource source, String displayName) {
        TrueuuidRuntime.AUTH_STATE.markAuthSuccess(connection, profile.getId(), profile.getName(), source, displayName);
        TrueuuidRuntime.NAME_REGISTRY.recordSuccess(profile.getName(), profile.getId(), ip, source, displayName);
        TrueuuidRuntime.IP_GRACE.record(profile.getName(), ip, profile.getId(), source, displayName);
    }

    private VerifiedProfileService() {}
}
