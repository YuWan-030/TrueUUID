package cn.alini.trueuuid;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Registers the account badge as a NeoForge HUD layer. NeoForge composites
 * LayeredDraw layers itself, so the badge needs no Gui.render mixin here.
 */
@EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT)
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Trueuuid.MODID, "account_status"),
                (graphics, deltaTracker) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
