package cn.alini.trueuuid;

import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
// EventBus 7 (Forge 58 / MC 1.21.8). The EventBus 6 line (Forge 52 / MC 1.21.1)
// uses net.minecraftforge.eventbus.api.SubscribeEvent instead. This import is the
// ONLY per-version difference in the whole Forge adapter; all login/verification
// logic is shared from platform/forge-common.
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

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
    public static void onServerStopping(ServerStoppingEvent event) {
        ForgeAdapterRuntime.shutdown();
    }

    private TrueuuidForgeEvents() {}
}
