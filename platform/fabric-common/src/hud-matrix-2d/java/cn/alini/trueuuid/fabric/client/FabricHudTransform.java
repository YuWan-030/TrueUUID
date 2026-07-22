package cn.alini.trueuuid.fabric.client;

import net.minecraft.client.gui.DrawContext;

/** Two-dimensional JOML matrix stack used by the 1.21.11 draw context. */
final class FabricHudTransform {
    static void push(DrawContext context) {
        context.getMatrices().pushMatrix();
    }

    static void scale(DrawContext context, float scale) {
        context.getMatrices().scale(scale, scale);
    }

    static void pop(DrawContext context) {
        context.getMatrices().popMatrix();
    }

    private FabricHudTransform() {}
}
