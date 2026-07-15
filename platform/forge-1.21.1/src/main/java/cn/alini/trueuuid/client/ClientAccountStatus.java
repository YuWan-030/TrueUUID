package cn.alini.trueuuid.client;

import cn.alini.trueuuid.config.TrueuuidConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Client-local account state derived from the TrueUUID login handshake. */
public final class ClientAccountStatus {
    private static volatile Status status = Status.NONE;

    public static void markPremium() { status = Status.PREMIUM; }
    public static void markOffline() { status = Status.OFFLINE; }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!TrueuuidConfig.showAccountOverlay() || minecraft.player == null || status == Status.NONE) return;
        Component text = Component.translatable("trueuuid.overlay.label", Component.translatable(status.translationKey));
        int width = minecraft.font.width(text);
        int x = 6;
        int y = 6;
        graphics.fill(x - 3, y - 3, x + width + 3, y + 12, 0xA0000000);
        graphics.drawString(minecraft.font, text, x, y, status.color, true);
    }

    private enum Status {
        NONE("", 0),
        PREMIUM("trueuuid.overlay.premium", 0x55FF55),
        OFFLINE("trueuuid.overlay.offline", 0xFF5555);

        private final String translationKey;
        private final int color;

        Status(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }
    }

    private ClientAccountStatus() {}
}
