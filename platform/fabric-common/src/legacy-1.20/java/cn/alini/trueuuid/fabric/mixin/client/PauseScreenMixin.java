package cn.alini.trueuuid.fabric.mixin.client;

import cn.alini.trueuuid.fabric.client.FabricClientStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
abstract class PauseScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void trueuuid$renderStatus(DrawContext graphics, int mouseX, int mouseY,
                                       float delta, CallbackInfo callback) {
        FabricClientStatus.renderPauseStatus(graphics);
    }
}
