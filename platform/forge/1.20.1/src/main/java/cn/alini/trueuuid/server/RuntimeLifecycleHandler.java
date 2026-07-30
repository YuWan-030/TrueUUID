package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Trueuuid.MODID)
public final class RuntimeLifecycleHandler {
    @SubscribeEvent public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        TrueuuidRuntime.init();
    }

    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        TrueuuidRuntime.shutdown();
    }

    private RuntimeLifecycleHandler() {}
}
