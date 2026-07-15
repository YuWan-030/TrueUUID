package cn.alini.trueuuid.client;

import cn.alini.trueuuid.config.TrueuuidConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Client-local account badge derived from the TrueUUID login handshake: a closed
 * padlock bearing a green check next to green "Premium", or an open padlock
 * bearing a red X next to red "Offline". No backdrop.
 *
 * <p>Mirrors the Forge adapters' {@code platform/forge-common} badge exactly so
 * both loaders look identical; keep the two in step. MC 1.21.1 still exposes a
 * PoseStack from {@code GuiGraphics.pose()}, so the scale is inlined here rather
 * than going through a per-version seam.
 *
 * <p>The padlock is pixel art painted with {@code GuiGraphics.fill} rather than a
 * blit texture on purpose: {@code blit} takes a different argument list across the
 * supported Minecraft versions, so a texture would need a seam per version for no
 * visual gain. {@code fill} is identical everywhere.
 *
 * <p>Both frames are one common 11x16 grid rather than each cropped to its own
 * content. That is deliberate: the open shackle sits one pixel higher than the
 * closed one, and a shared grid keeps the lock bodies bottom-aligned so the badge
 * does not jump when the status flips.
 */
public final class ClientAccountStatus {
    private static volatile Status status = Status.NONE;

    // Sprites generated from the source PNGs. Palette: 1-4 shackle, 5/6/8/9 body,
    // 7 body highlight, g/G green check, r/R red cross. ' ' is transparent.
    private static final String[] LOCK_CLOSED = {
        "           ",
        "   11111   ",
        "  1233331  ",
        " 121111431 ",
        " 121   431 ",
        "55555555556",
        "57777777786",
        "57999999g56",
        "5799999gG56",
        "57g999gG956",
        "57Gg9gG9956",
        "579GgG99956",
        "5799G999956",
        "57999999956",
        "68555555556",
        "66666666666",
    };
    private static final String[] LOCK_OPEN = {
        "   11111   ",
        "  1233331  ",
        " 121111431 ",
        " 121   431 ",
        " 121       ",
        "55555555556",
        "57777777786",
        "579R999R956",
        "57RrR9RrR56",
        "579RrRrR956",
        "5799RrR9956",
        "579RrRrR956",
        "57RrR9RrR56",
        "579R999R956",
        "68555555556",
        "66666666666",
    };

    private static final int ICON_W = 11;
    private static final int ICON_H = 16;
    private static final int GAP = 3;
    private static final int MARGIN = 4;
    /**
     * Rows spanned by the padlock body, the shackle sitting above it. The label is
     * centred on the body rather than on the whole sprite: the sprite's midpoint
     * lands inside the shackle, which makes the text visibly ride high.
     */
    private static final int BODY_TOP = 5;
    private static final int BODY_BOTTOM = 16;
    /**
     * Label scale relative to the padlock. The art is {@link #ICON_H}px tall while
     * Minecraft's font is 8px, so the lock reads larger than its label. Fix that by
     * redrawing the source PNGs at roughly the font's height and regenerating the
     * sprite below -- do not resample here: the art has one-pixel shackle lines and
     * a one-pixel mark, so nearest-neighbour shreds them and a smooth filter blends
     * the green/red mark into the yellow body.
     */
    private static final float TEXT_SCALE = 1.0F;

    public static void markPremium() { status = Status.PREMIUM; }
    public static void markOffline() { status = Status.OFFLINE; }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!TrueuuidConfig.showAccountOverlay() || minecraft.player == null || status == Status.NONE) return;

        Component text = Component.translatable(status.translationKey);
        float lockScale = TrueuuidConfig.overlayScale();
        float textScale = lockScale * TEXT_SCALE;

        // Lay the badge out in screen pixels, since the padlock and the label are
        // drawn at different scales.
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

        // Each element is drawn inside its own scale and positioned by converting
        // the screen anchor back into that scale's units.
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(lockScale, lockScale, 1.0F);
            drawSprite(graphics, Math.round(screenX / lockScale), Math.round(screenY / lockScale),
                    status == Status.PREMIUM ? LOCK_CLOSED : LOCK_OPEN);
        } finally {
            graphics.pose().popPose();
        }

        float bodyCentre = screenY + (BODY_TOP + BODY_BOTTOM) / 2.0F * lockScale;
        float textTop = bodyCentre - minecraft.font.lineHeight * textScale / 2.0F;
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(textScale, textScale, 1.0F);
            graphics.drawString(minecraft.font, text,
                    Math.round((screenX + lockWidth + gapWidth) / textScale),
                    Math.round(textTop / textScale),
                    0xFF000000 | status.color, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Paints one pixel-art sprite, coalescing each row into horizontal runs. */
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
        // Label colours match the check/cross drawn inside each padlock sprite.
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
