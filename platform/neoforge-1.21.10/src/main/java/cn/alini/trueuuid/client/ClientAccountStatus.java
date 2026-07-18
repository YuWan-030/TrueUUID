package cn.alini.trueuuid.client;

import cn.alini.trueuuid.config.TrueuuidConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 1.21.6+ GUI-matrix seam for the shared TrueUUID account badge. */
public final class ClientAccountStatus {
    private static volatile Status status = Status.NONE;

    private static final String[] LOCK_CLOSED = {
        "           ", "   11111   ", "  1233331  ", " 121111431 ", " 121   431 ",
        "55555555556", "57777777786", "57999999g56", "5799999gG56", "57g999gG956",
        "57Gg9gG9956", "579GgG99956", "5799G999956", "57999999956", "68555555556", "66666666666",
    };
    private static final String[] LOCK_OPEN = {
        "   11111   ", "  1233331  ", " 121111431 ", " 121   431 ", " 121       ",
        "55555555556", "57777777786", "579R999R956", "57RrR9RrR56", "579RrRrR956",
        "5799RrR9956", "579RrRrR956", "57RrR9RrR56", "579R999R956", "68555555556", "66666666666",
    };

    private static final int ICON_W = 11;
    private static final int ICON_H = 16;
    private static final int GAP = 3;
    private static final int MARGIN = 4;
    private static final int BODY_TOP = 5;
    private static final int BODY_BOTTOM = 16;
    private static final float TEXT_SCALE = 1.0F;

    public static void markPremium() { status = Status.PREMIUM; }
    public static void markOffline() { status = Status.OFFLINE; }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!TrueuuidConfig.showAccountOverlay() || minecraft.player == null || status == Status.NONE) return;

        Component text = Component.translatable(status.translationKey);
        float lockScale = TrueuuidConfig.overlayScale();
        float textScale = lockScale * TEXT_SCALE;
        float lockWidth = ICON_W * lockScale;
        float gapWidth = GAP * lockScale;
        float textWidth = minecraft.font.width(text) * textScale;
        float drawnWidth = lockWidth + gapWidth + textWidth;
        float drawnHeight = ICON_H * lockScale;
        boolean right = TrueuuidConfig.overlayCorner().endsWith("right");
        boolean bottom = TrueuuidConfig.overlayCorner().startsWith("bottom");
        float screenX = (right ? minecraft.getWindow().getGuiScaledWidth() - MARGIN - drawnWidth : MARGIN)
                + TrueuuidConfig.overlayOffsetX();
        float screenY = (bottom ? minecraft.getWindow().getGuiScaledHeight() - MARGIN - drawnHeight : MARGIN)
                + TrueuuidConfig.overlayOffsetY();

        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(lockScale, lockScale);
            drawSprite(graphics, Math.round(screenX / lockScale), Math.round(screenY / lockScale),
                    status == Status.PREMIUM ? LOCK_CLOSED : LOCK_OPEN);
        } finally {
            graphics.pose().popMatrix();
        }

        float bodyCentre = screenY + (BODY_TOP + BODY_BOTTOM) / 2.0F * lockScale;
        float textTop = bodyCentre - minecraft.font.lineHeight * textScale / 2.0F;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(textScale, textScale);
            graphics.drawString(minecraft.font, text,
                    Math.round((screenX + lockWidth + gapWidth) / textScale),
                    Math.round(textTop / textScale), 0xFF000000 | status.color, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawSprite(GuiGraphics graphics, int x, int y, String[] rows) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int column = 0;
            while (column < line.length()) {
                char pixel = line.charAt(column);
                if (pixel == ' ') {
                    column++;
                    continue;
                }
                int run = 1;
                while (column + run < line.length() && line.charAt(column + run) == pixel) run++;
                graphics.fill(x + column, y + row, x + column + run, y + row + 1, colorOf(pixel));
                column += run;
            }
        }
    }

    private static int colorOf(char pixel) {
        return switch (pixel) {
            case '1' -> 0xFF575C71;
            case '2' -> 0xFFC9CBD8;
            case '3' -> 0xFFACB0C1;
            case '4' -> 0xFF3F4656;
            case '5' -> 0xFFB26411;
            case '6' -> 0xFF752702;
            case '7' -> 0xFFFDF55F;
            case '8' -> 0xFFDC9613;
            case '9' -> 0xFFE9B114;
            case 'g' -> 0xFF4AB85C;
            case 'G' -> 0xFF259B4A;
            case 'r' -> 0xFFEE3934;
            case 'R' -> 0xFFBD2E2E;
            default -> 0;
        };
    }

    private enum Status {
        NONE("", 0),
        PREMIUM("trueuuid.overlay.premium", 0x259B4A),
        OFFLINE("trueuuid.overlay.offline", 0xBD2E2E);

        private final String translationKey;
        private final int color;

        Status(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }
    }

    private ClientAccountStatus() {}
}
