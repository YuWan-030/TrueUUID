package cn.alini.trueuuid.fabric.login;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side typed-payload play networking used by Minecraft 1.20.5+. */
final class FabricServerStatusNetworking {
    static void registerPayload() {
        PayloadTypeRegistry.playS2C().register(FabricStatusPayload.ID, FabricStatusPayload.CODEC);
    }

    static void send(ServerPlayerEntity player, FabricAuthenticationSource.ClientStatus status) {
        if (ServerPlayNetworking.canSend(player, FabricStatusPayload.ID)) {
            ServerPlayNetworking.send(player, new FabricStatusPayload(status));
        }
    }

    private FabricServerStatusNetworking() {}
}
