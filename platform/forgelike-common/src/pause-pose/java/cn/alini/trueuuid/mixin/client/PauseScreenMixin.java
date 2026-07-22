package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
/** PoseStack-era pause badge hook shared by Forge 1.20.1 and NeoForge. */
abstract class PauseScreenMixin {
    // Legacy Forge-family distributions use either SRG m_88315_ or the
    // official render name at runtime even though the source signature is the
    // same. Match both stable spellings without a version-specific copy.
    @Inject(method = {"render", "m_88315_"}, at = @At("TAIL"), remap = false)
    private void trueuuid$renderStatus(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo callback) {
        ClientAccountStatus.renderPauseStatus(graphics);
    }
}
