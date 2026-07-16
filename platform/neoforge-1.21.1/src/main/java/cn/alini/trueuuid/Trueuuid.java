package cn.alini.trueuuid;

import cn.alini.trueuuid.server.AdapterRuntime;
import cn.alini.trueuuid.config.TrueuuidConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** NeoForge 1.21.1 entrypoint. Loader details stay in this module. */
@Mod(Trueuuid.MODID)
public final class Trueuuid {
    public static final String MODID = "trueuuid";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Logs at INFO only while the auth.debug config toggle is on, matching 1.20.1. */
    public static void debug(String message, Object... args) {
        if (TrueuuidConfig.debug()) LOGGER.info(message, args);
    }

    public Trueuuid(IEventBus modBus) {
        TrueuuidConfig.register();
        AdapterRuntime.initialize();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> AdapterRuntime.onPlayerLoggedIn(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> AdapterRuntime.onPlayerLoggedOut(event));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> AdapterRuntime.shutdown());
        LOGGER.info("TrueUUID NeoForge adapter loaded");
    }
}
