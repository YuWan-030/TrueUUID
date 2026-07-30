package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.client.FabricClientStatus;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side typed-payload play networking used by Minecraft 1.20.5+. */
final class FabricClientStatusNetworking {
    static void registerReceiver() {
        if (!ClientPlayNetworking.registerGlobalReceiver(FabricStatusPayload.ID, (payload, context) -> {
            if (payload.status() == null) {
                TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric account-status payload");
                return;
            }
            FabricClientStatus.setServerStatus(payload.status());
        })) {
            throw new IllegalStateException("TrueUUID Fabric status channel was already registered");
        }
    }

    private FabricClientStatusNetworking() {}
}
