package cn.alini.trueuuid.fabric.client;

import net.minecraft.client.gui.DrawContext;

/** Four-dimensional draw matrix stack used by the 1.20 client API. */
final class FabricHudTransform {
    static void push(DrawContext context) {
        context.getMatrices().push();
    }

    static void scale(DrawContext context, float scale) {
        context.getMatrices().scale(scale, scale, 1.0F);
    }

    static void pop(DrawContext context) {
        context.getMatrices().pop();
    }

    private FabricHudTransform() {}
}
