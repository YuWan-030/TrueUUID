package cn.alini.trueuuid;

import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
// EventBus 6 (Forge 52 / MC 1.21.1). The EventBus 7 line (Forge 58 / MC 1.21.8)
// moves this to net.minecraftforge.eventbus.api.listener.SubscribeEvent. This
// import is the ONLY per-version difference in the whole Forge adapter; all
// login/verification logic is shared from platform/forge-common.
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Per-version game-event seam: binds the shared runtime to this Forge's event bus. */
public final class TrueuuidForgeEvents {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(TrueuuidForgeEvents.class);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ForgeAdapterRuntime.onPlayerLoggedIn(event);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ForgeAdapterRuntime.onPlayerLoggedOut(event);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ForgeAdapterRuntime.shutdown();
    }

    private TrueuuidForgeEvents() {}
}
