package cn.alini.trueuuid.fabric.client;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.fabric.login.FabricAuthenticationSource;
import cn.alini.trueuuid.presentation.BadgeArtwork;
import cn.alini.trueuuid.presentation.BadgeLayout;
import cn.alini.trueuuid.presentation.ClientStatusState;
import cn.alini.trueuuid.presentation.ConfirmedAccountStatus;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Fabric drawing adapter over the shared server-confirmed client state. */
public final class FabricClientStatus {
    private static final ClientStatusState STATE = new ClientStatusState();
    private static boolean registered;

    public static void setServerStatus(FabricAuthenticationSource.ClientStatus serverStatus) {
        if (MinecraftClient.getInstance().isInSingleplayer()) return;
        STATE.receive(serverStatus == FabricAuthenticationSource.ClientStatus.PREMIUM
                ? ConfirmedAccountStatus.PREMIUM : ConfirmedAccountStatus.OFFLINE);
    }
    public static void clear() { STATE.clear(); }

    public static synchronized void registerHud() {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> renderHud(graphics));
    }

    public static void renderPauseStatus(DrawContext graphics) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !FabricConfig.showAccountOverlay()) return;
        reconcileIntegratedWorld(client);
        STATE.persistentStatus().ifPresent(status -> renderBadge(graphics, status, 1.0F));
    }

    private static void renderHud(DrawContext graphics) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        reconcileIntegratedWorld(client);
        var notice = STATE.transientNotice();
        if (!FabricConfig.showAccountOverlay() || client.options.hudHidden || client.currentScreen != null
                || notice.isEmpty()) return;
        renderBadge(graphics, notice.get().status(), notice.get().alpha());
    }

    private static void reconcileIntegratedWorld(MinecraftClient client) {
        var server = client.getServer();
        boolean integrated = client.isInSingleplayer() && server != null;
        var transition = STATE.reconcileIntegratedWorld(integrated, integrated && server.isRemote());
        if (transition.chatTranslationKey() != null && FabricConfig.showJoinFeedback()) {
            client.player.sendMessage(Text.translatable(transition.chatTranslationKey()), false);
        }
    }

    private static void renderBadge(DrawContext graphics, ConfirmedAccountStatus status, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        Text text = Text.translatable(status.overlayTranslationKey());
        boolean right = FabricConfig.overlayCorner().endsWith("right");
        boolean bottom = FabricConfig.overlayCorner().startsWith("bottom");
        BadgeLayout layout = BadgeLayout.calculate(graphics.getScaledWindowWidth(), graphics.getScaledWindowHeight(),
                client.textRenderer.getWidth(text), client.textRenderer.fontHeight, FabricConfig.overlayScale(),
                right, bottom, FabricConfig.overlayOffsetX(), FabricConfig.overlayOffsetY());

        FabricHudTransform.push(graphics);
        try {
            FabricHudTransform.scale(graphics, layout.scale());
            drawSprite(graphics, layout, status, alpha);
            int color = (Math.round(alpha * 255.0F) << 24) | status.rgb();
            graphics.drawTextWithShadow(client.textRenderer, text,
                    layout.textX(), layout.textY(), color);
        } finally {
            FabricHudTransform.pop(graphics);
        }
    }

    private static void drawSprite(DrawContext graphics, BadgeLayout layout,
                                   ConfirmedAccountStatus status, float alpha) {
        for (BadgeArtwork.PixelRun run : BadgeArtwork.runsFor(status)) {
            graphics.fill(layout.x() + run.x(), layout.y() + run.y(),
                    layout.x() + run.x() + run.length(), layout.y() + run.y() + 1,
                    BadgeArtwork.argb(run.paletteKey(), alpha));
        }
    }

    private FabricClientStatus() {}
}
