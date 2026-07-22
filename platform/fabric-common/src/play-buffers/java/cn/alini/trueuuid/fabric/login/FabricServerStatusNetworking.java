package cn.alini.trueuuid.fabric.login;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side raw-buffer play networking used through Minecraft 1.20.4. */
final class FabricServerStatusNetworking {
    static void registerPayload() {
        // Raw channel identifiers need no codec registry in this API era.
    }

    static void send(ServerPlayerEntity player, FabricAuthenticationSource.ClientStatus status) {
        if (!ServerPlayNetworking.canSend(player, FabricLoginNetworking.STATUS_CHANNEL)) return;
        var payload = PacketByteBufs.create();
        FabricStatusPayloads.write(payload, status);
        ServerPlayNetworking.send(player, FabricLoginNetworking.STATUS_CHANNEL, payload);
    }

    private FabricServerStatusNetworking() {}
}
