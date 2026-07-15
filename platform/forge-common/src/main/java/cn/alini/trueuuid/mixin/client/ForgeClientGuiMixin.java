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
 * <p>Note: no {@code remap = false} here. Gui.render is an ordinary vanilla
 * method whose runtime name is obfuscated, so it must be remapped through the
 * refmap. Only the Forge-preserved login methods may use remap = false.
 */
@Mixin(Gui.class)
abstract class ForgeClientGuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void trueuuid$renderAccountStatus(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        ClientAccountStatus.render(graphics);
    }
}
