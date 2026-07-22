package cn.alini.trueuuid.fabric.login;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.util.UUID;

/** Authlib 7 record accessors and immutable properties used by Minecraft 1.21.10+. */
public final class FabricGameProfiles {
    public static String name(GameProfile profile) {
        return profile.name();
    }

    public static UUID id(GameProfile profile) {
        return profile.id();
    }

    public static GameProfile addProperty(GameProfile profile, String name, Property property) {
        ArrayListMultimap<String, Property> properties = ArrayListMultimap.create(profile.properties());
        properties.put(name, property);
        return new GameProfile(profile.id(), profile.name(), new PropertyMap(properties));
    }

    private FabricGameProfiles() {}
}
