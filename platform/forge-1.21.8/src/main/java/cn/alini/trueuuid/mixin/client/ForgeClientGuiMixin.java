package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 1.21 uses Mojang's layered HUD rather than Forge's former overlay event. */
@Mixin(Gui.class)
abstract class ForgeClientGuiMixin {
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void trueuuid$renderAccountStatus(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        ClientAccountStatus.render(graphics);
    }
}
