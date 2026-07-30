package cn.alini.trueuuid.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Bean-profile/server access seam used through Minecraft 1.21.8. */
final class NeoGameProfiles {
    static UUID id(GameProfile profile) { return profile == null ? null : profile.getId(); }
    static String name(GameProfile profile) { return profile.getName(); }
    static MinecraftServer server(ServerPlayer player) { return player.getServer(); }
    private NeoGameProfiles() {}
}
