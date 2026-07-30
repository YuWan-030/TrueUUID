package cn.alini.trueuuid;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.fml.common.Mod;
// EventBus 6 (Forge 48 / MC 1.20.2), same as Forge 52-55.
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Per-version client seam: registers the account badge as a Forge GUI overlay.
 * Forge 48-49 (Minecraft 1.20.2-1.20.4) carry the old overlay API
 * (RegisterGuiOverlaysEvent + IGuiOverlay), which Forge 51+ removed; the
 * 1.21.x modules use a Gui.render mixin (Forge 52/53) or ForgeLayeredDraw
 * (Forge 54+) instead. The drawing itself is shared in forge-common
 * (ClientAccountStatus), so this module's mixin config does not include
 * ForgeClientGuiMixin.
 */
@Mod.EventBusSubscriber(modid = Trueuuid.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrueuuidClientOverlay {
    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("account_status",
                (gui, graphics, partialTick, width, height) -> ClientAccountStatus.render(graphics));
    }

    private TrueuuidClientOverlay() {}
}
