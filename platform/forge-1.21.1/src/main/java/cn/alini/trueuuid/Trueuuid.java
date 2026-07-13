package cn.alini.trueuuid;

import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import cn.alini.trueuuid.config.TrueuuidConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Forge 1.21.1 entrypoint; this target does not depend on NeoForge APIs. */
@Mod(Trueuuid.MODID)
public final class Trueuuid {
    public static final String MODID = "trueuuid";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Trueuuid(ModContainer container) {
        TrueuuidConfig.register(container);
        ForgeAdapterRuntime.initialize();
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ForgeAdapterRuntime.shutdown());
        LOGGER.info("TrueUUID Forge 1.21.1 adapter loaded");
    }
}
