package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the account badge on Forge 52/53, which predate Forge's HUD layer API.
 * TrueuuidMixinPlugin skips this mixin on Forge 54+, where ForgeLayeredDraw
 * exists and TrueuuidClientOverlay registers a real HUD layer instead; those
 * newer pipelines drop draws made from a Gui.render inject.
 *
 * <p>Forge 52+ production JARs retain Mojang's official member names. Remapping
 * this target through the userdev SRG refmap changes {@code render} to the
 * absent {@code m_280421_} and crashes before the title screen appears.
 */
@Mixin(value = Gui.class, remap = false)
abstract class ForgeClientGuiMixin {
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void trueuuid$renderAccountStatus(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        ClientAccountStatus.render(graphics);
    }
}
