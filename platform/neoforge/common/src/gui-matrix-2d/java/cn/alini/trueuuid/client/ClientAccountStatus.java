package cn.alini.trueuuid.client;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.presentation.BadgeArtwork;
import cn.alini.trueuuid.presentation.BadgeLayout;
import cn.alini.trueuuid.presentation.ClientStatusState;
import cn.alini.trueuuid.presentation.ConfirmedAccountStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Matrix3x2f-era NeoForge drawing seam; state and artwork remain shared. */
public final class ClientAccountStatus {
    private static final ClientStatusState STATE = new ClientStatusState();

    public static void markPremium() { STATE.receive(ConfirmedAccountStatus.PREMIUM); }
    public static void markOffline() { STATE.receive(ConfirmedAccountStatus.OFFLINE); }
    public static void setServerStatus(int wireId) {
        if (Minecraft.getInstance().getSingleplayerServer() != null) return;
        ConfirmedAccountStatus status = ConfirmedAccountStatus.fromWireId(wireId);
        if (status != null) STATE.receive(status);
    }
    public static void clear() { STATE.clear(); }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        reconcileIntegratedWorld(minecraft);
        var notice = STATE.transientNotice();
        if (!TrueuuidConfig.showAccountOverlay() || minecraft.options.hideGui || minecraft.screen != null
                || notice.isEmpty()) return;
        renderBadge(graphics, notice.get().status(), notice.get().alpha());
    }

    public static void renderPauseStatus(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !TrueuuidConfig.showAccountOverlay()) return;
        reconcileIntegratedWorld(minecraft);
        STATE.persistentStatus().ifPresent(status -> renderBadge(graphics, status, 1.0F));
    }

    private static void reconcileIntegratedWorld(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var transition = STATE.reconcileIntegratedWorld(server != null, server != null && server.isPublished());
        if (transition.chatTranslationKey() != null && TrueuuidConfig.showJoinFeedback()) {
            minecraft.player.displayClientMessage(Component.translatable(transition.chatTranslationKey()), false);
        }
    }

    private static void renderBadge(GuiGraphics graphics, ConfirmedAccountStatus status, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        Component text = Component.translatable(status.overlayTranslationKey());
        boolean right = TrueuuidConfig.overlayCorner().endsWith("right");
        boolean bottom = TrueuuidConfig.overlayCorner().startsWith("bottom");
        BadgeLayout layout = BadgeLayout.calculate(minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(), minecraft.font.width(text), minecraft.font.lineHeight,
                TrueuuidConfig.overlayScale(), right, bottom,
                TrueuuidConfig.overlayOffsetX(), TrueuuidConfig.overlayOffsetY());

        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(layout.scale(), layout.scale());
            drawSprite(graphics, layout, status, alpha);
            int color = (Math.round(alpha * 255.0F) << 24) | status.rgb();
            graphics.drawString(minecraft.font, text, layout.textX(), layout.textY(), color, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawSprite(GuiGraphics graphics, BadgeLayout layout,
                                   ConfirmedAccountStatus status, float alpha) {
        for (BadgeArtwork.PixelRun run : BadgeArtwork.runsFor(status)) {
            graphics.fill(layout.x() + run.x(), layout.y() + run.y(),
                    layout.x() + run.x() + run.length(), layout.y() + run.y() + 1,
                    BadgeArtwork.argb(run.paletteKey(), alpha));
        }
    }

    private ClientAccountStatus() {}
}
