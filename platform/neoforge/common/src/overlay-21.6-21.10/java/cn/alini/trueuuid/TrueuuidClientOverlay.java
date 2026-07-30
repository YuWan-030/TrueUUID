package cn.alini.trueuuid;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** NeoForge's post-FML-7 subscriber API routes each event to its own bus. */
@EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT)
/** HUD registration shared by NeoForge 21.6 through 21.10. */
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(java.util.Objects.requireNonNull(
                        ResourceLocation.tryParse(Trueuuid.MODID + ":account_status")),
                (graphics, deltaTracker) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
