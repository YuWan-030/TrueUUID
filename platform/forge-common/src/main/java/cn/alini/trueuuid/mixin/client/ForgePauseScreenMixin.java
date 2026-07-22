package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Small screen seam: persistent state is drawn after the vanilla pause UI. */
@Mixin(PauseScreen.class)
abstract class ForgePauseScreenMixin {
    // Forge 48/49 production keeps SRG member names while Forge 50+ keeps
    // Mojang's official names. This tiny GUI seam is otherwise identical, so
    // select both runtime spellings directly instead of maintaining duplicate
    // mixins or emitting the wrong refmap entry for one side of the boundary.
    @Inject(method = {"render", "m_88315_"}, at = @At("TAIL"), remap = false)
    private void trueuuid$renderStatus(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo callback) {
        ClientAccountStatus.renderPauseStatus(graphics);
    }
}
