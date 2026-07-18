package cn.alini.trueuuid.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Per-version seam for scaling the badge. Through MC 1.21.5 GuiGraphics#pose()
 * returns a PoseStack; on 1.21.6+ it returns a JOML Matrix3x2fStack with
 * different method names, so this tiny class is the only divergence.
 */
final class TrueuuidHudScale {
    static void push(GuiGraphics graphics, float scale) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
    }

    static void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    private TrueuuidHudScale() {}
}
