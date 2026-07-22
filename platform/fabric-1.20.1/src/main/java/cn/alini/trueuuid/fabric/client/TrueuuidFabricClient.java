package cn.alini.trueuuid.fabric.client;

import cn.alini.trueuuid.fabric.login.FabricClientLoginNetworking;
import net.fabricmc.api.ClientModInitializer;

/** Client-only entrypoint; this is the only side that will later use a token. */
public final class TrueuuidFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientLoginNetworking.registerClientHooks();
        FabricClientStatus.registerHud();
    }
}
