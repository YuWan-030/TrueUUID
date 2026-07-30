package cn.alini.trueuuid.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared Forge 1.21.6+ seam for scaling the badge. GuiGraphics#pose()
 * returns a JOML Matrix3x2fStack (pushMatrix/popMatrix); through 1.21.5 it was a
 * PoseStack (pushPose/popPose), so this tiny class is the only divergence.
 */
final class TrueuuidHudScale {
    static void push(GuiGraphics graphics, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
    }

    static void pop(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }

    private TrueuuidHudScale() {}
}
