package cn.alini.trueuuid.fabric;

import cn.alini.trueuuid.fabric.login.FabricLoginNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common Fabric entrypoint. Loader setup stays out of shared/protocol. */
public final class TrueuuidFabric implements ModInitializer {
    public static final String MOD_ID = "trueuuid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FabricLoginNetworking.registerServerHooks();
        LOGGER.info("TrueUUID Fabric 1.20.1 login transport registered");
    }
}
