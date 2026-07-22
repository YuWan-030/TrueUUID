package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

final class FabricVerifiedProfiles {
    static GameProfile create(VerifiedProfile verified) {
        GameProfile profile = new GameProfile(verified.uuid(), verified.name());
        for (VerifiedProfile.Property property : verified.properties()) {
            Property nativeProperty = property.signature() == null
                    ? new Property(property.name(), property.value())
                    : new Property(property.name(), property.value(), property.signature());
            profile = FabricGameProfiles.addProperty(profile, property.name(), nativeProperty);
        }
        return profile;
    }

    private FabricVerifiedProfiles() {}
}
