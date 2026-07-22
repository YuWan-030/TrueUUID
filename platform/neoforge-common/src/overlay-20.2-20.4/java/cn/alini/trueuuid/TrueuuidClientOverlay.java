package cn.alini.trueuuid;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;

/**
 * Per-era client seam: registers the account badge as a NeoForge GUI overlay.
 * NeoForge 20.2 still carries the pre-1.21 overlay API
 * (RegisterGuiOverlaysEvent + IGuiOverlay) and the Mod.EventBusSubscriber
 * annotation form; the shared TrueuuidClientOverlay uses the 1.21-era
 * RegisterGuiLayersEvent and is excluded from this module's compile. The
 * drawing itself stays shared (ClientAccountStatus).
 */
@Mod.EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
/** Legacy HUD registration shared by NeoForge 20.2 and 20.4. */
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("account_status",
                (gui, graphics, partialTick, width, height) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
