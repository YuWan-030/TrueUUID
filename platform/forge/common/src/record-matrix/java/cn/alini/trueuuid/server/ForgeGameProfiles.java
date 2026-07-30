package cn.alini.trueuuid.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Authlib record-era profile access shared by Forge 60 and 61. */
final class ForgeGameProfiles {
    static UUID id(GameProfile profile) { return profile == null ? null : profile.id(); }
    static String name(GameProfile profile) { return profile.name(); }
    static MinecraftServer server(ServerPlayer player) { return player.level().getServer(); }
    private ForgeGameProfiles() {}
}
