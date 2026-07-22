package cn.alini.trueuuid.fabric.login;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.util.UUID;

/** Authlib bean accessors used before GameProfile became a record. */
public final class FabricGameProfiles {
    public static String name(GameProfile profile) {
        return profile.getName();
    }

    public static UUID id(GameProfile profile) {
        return profile.getId();
    }

    public static GameProfile addProperty(GameProfile profile, String name, Property property) {
        profile.getProperties().put(name, property);
        return profile;
    }

    private FabricGameProfiles() {}
}
