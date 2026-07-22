package cn.alini.trueuuid.fabric.client;

import cn.alini.trueuuid.fabric.login.FabricClientLoginNetworking;
import net.fabricmc.api.ClientModInitializer;

/** Client-only 1.20-era entrypoint; this is the only side that uses a token. */
public final class TrueuuidFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientLoginNetworking.registerClientHooks();
        FabricClientStatus.registerHud();
    }
}
