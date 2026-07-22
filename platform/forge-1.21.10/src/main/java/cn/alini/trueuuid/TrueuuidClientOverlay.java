package cn.alini.trueuuid;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Forge 60 seam: the overlay event became a standalone record-event bus. */
@Mod.EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        AddGuiOverlayLayersEvent.BUS.addListener(TrueuuidClientOverlay::addLayers);
    }

    private static void addLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ResourceLocation.fromNamespaceAndPath(Trueuuid.MODID, "account_status"),
                (graphics, deltaTracker) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
