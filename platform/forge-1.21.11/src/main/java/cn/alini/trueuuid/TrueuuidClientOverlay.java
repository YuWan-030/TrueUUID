package cn.alini.trueuuid;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.common.Mod;
// EventBus 7 (Forge 56+). Forge <=55 uses eventbus.api.SubscribeEvent.
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

/**
 * Per-version client seam: registers the account badge as a Forge HUD layer.
 * Forge 54+ composites the HUD through ForgeLayeredDraw, so a raw Gui.render
 * inject is dropped there; Forge 52/53 have no layer API and use the mixin
 * instead (see TrueuuidMixinPlugin). The drawing itself is shared in
 * forge-common (ClientAccountStatus).
 */
@Mod.EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void addLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                Identifier.fromNamespaceAndPath(Trueuuid.MODID, "account_status"),
                (graphics, deltaTracker) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
