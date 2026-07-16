package cn.alini.trueuuid.fabric;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.fabric.login.FabricLoginNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common Fabric entrypoint, shared from {@code platform/fabric-common}.
 * {@code FabricLoginNetworking} is the per-version seam it wires up, like
 * forge-common's {@code TrueuuidForgeEvents}.
 */
public final class TrueuuidFabric implements ModInitializer {
    public static final String MOD_ID = "trueuuid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Logs at INFO only while the auth.debug config toggle is on, matching 1.20.1. */
    public static void debug(String message, Object... args) {
        if (cn.alini.trueuuid.fabric.config.FabricConfig.debug()) LOGGER.info(message, args);
    }

    @Override
    public void onInitialize() {
        FabricConfig.load();
        FabricLoginNetworking.registerServerHooks();
        LOGGER.info("TrueUUID Fabric adapter loaded");
    }
}
