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
@EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        // tryParse instead of fromNamespaceAndPath: RegisterGuiLayersEvent
        // and this annotation form already exist on NeoForge 20.6, but the
        // fromNamespaceAndPath factory only exists from 1.21 — tryParse is
        // present across the whole range this file compiles on (1.20.6+).
        // The 20.2/20.4 modules exclude this file entirely (pre-layers era).
        event.registerAboveAll(java.util.Objects.requireNonNull(
                        ResourceLocation.tryParse(Trueuuid.MODID + ":account_status")),
                (graphics, deltaTracker) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
