package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.client.FabricClientStatus;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side raw-buffer play networking used through Minecraft 1.20.4. */
final class FabricClientStatusNetworking {
    static void registerReceiver() {
        if (!ClientPlayNetworking.registerGlobalReceiver(FabricLoginNetworking.STATUS_CHANNEL,
                (client, handler, buffer, responseSender) -> {
                    FabricAuthenticationSource.ClientStatus status = FabricStatusPayloads.read(buffer);
                    if (status == null) {
                        TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric account-status payload");
                        return;
                    }
                    client.execute(() -> FabricClientStatus.setServerStatus(status));
                })) {
            throw new IllegalStateException("TrueUUID Fabric status channel was already registered");
        }
    }

    private FabricClientStatusNetworking() {}
}
