package cn.alini.trueuuid.fabric.client;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.fabric.login.FabricAuthenticationSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Server-confirmed account badge mirroring the Forge/NeoForge default: a
 * closed padlock plus green Premium, or an open padlock plus red Offline.
 */
/** HUD implementation shared by the Fabric 1.20 API era. */
public final class FabricClientStatus {
    private enum Status {
        NONE("", 0),
        PREMIUM("trueuuid.overlay.premium", 0xFF259B4A),
        OFFLINE("trueuuid.overlay.offline", 0xFFBD2E2E);

        final String translationKey;
        final int color;

        Status(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }
    }

    private static volatile Status status = Status.NONE;
    private static boolean registered;

    // Keep the pixel art and palette in lock-step with ClientAccountStatus on
    // Forge 1.20.1 and the shared modern Forge/NeoForge implementation.
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
            "66666666666"
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
            "66666666666"
    };
    private static final int ICON_WIDTH = 11;
    private static final int ICON_HEIGHT = 16;
    private static final int GAP = 3;
    private static final int MARGIN = 4;
    private static final int BODY_TOP = 5;
    private static final int BODY_BOTTOM = 16;

    /** The only writer for a non-empty badge is the authenticated server payload. */
    public static void setServerStatus(FabricAuthenticationSource.ClientStatus serverStatus) {
        status = serverStatus == FabricAuthenticationSource.ClientStatus.PREMIUM ? Status.PREMIUM : Status.OFFLINE;
    }

    public static void clear() {
        status = Status.NONE;
    }

    public static synchronized void registerHud() {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            Status current = status;
            MinecraftClient client = MinecraftClient.getInstance();
            if (!FabricConfig.showAccountOverlay() || current == Status.NONE || client.player == null) return;
            Text label = Text.translatable(current.translationKey);
            float scale = FabricConfig.overlayScale();
            int labelWidth = client.textRenderer.getWidth(label);
            float drawnWidth = (ICON_WIDTH + GAP + labelWidth) * scale;
            float drawnHeight = ICON_HEIGHT * scale;
            boolean right = FabricConfig.overlayCorner().endsWith("right");
            boolean bottom = FabricConfig.overlayCorner().startsWith("bottom");
            float x = (right ? drawContext.getScaledWindowWidth() - MARGIN - drawnWidth : MARGIN)
                    + FabricConfig.overlayOffsetX();
            float y = (bottom ? drawContext.getScaledWindowHeight() - MARGIN - drawnHeight : MARGIN)
                    + FabricConfig.overlayOffsetY();

            FabricHudTransform.push(drawContext);
            try {
                FabricHudTransform.scale(drawContext, scale);
                int scaledX = Math.round(x / scale);
                int scaledY = Math.round(y / scale);
                drawSprite(drawContext, scaledX, scaledY, current == Status.PREMIUM ? LOCK_CLOSED : LOCK_OPEN);
                int bodyCentre = scaledY + (BODY_TOP + BODY_BOTTOM) / 2;
                int textY = bodyCentre - client.textRenderer.fontHeight / 2;
                drawContext.drawTextWithShadow(client.textRenderer, label, scaledX + ICON_WIDTH + GAP, textY, current.color);
            } finally {
                FabricHudTransform.pop(drawContext);
            }
        });
    }

    private static void drawSprite(net.minecraft.client.gui.DrawContext drawContext, int x, int y, String[] rows) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int column = 0; column < line.length();) {
                char pixel = line.charAt(column);
                if (pixel == ' ') {
                    column++;
                    continue;
                }
                int run = 1;
                while (column + run < line.length() && line.charAt(column + run) == pixel) run++;
                drawContext.fill(x + column, y + row, x + column + run, y + row + 1, colorOf(pixel));
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

    private FabricClientStatus() {}
}
