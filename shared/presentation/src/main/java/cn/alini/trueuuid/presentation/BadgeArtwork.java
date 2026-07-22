package cn.alini.trueuuid.presentation;

import java.util.ArrayList;
import java.util.List;

/*
 * ================================================================
 * TrueUUID lock badge artwork
 * Copyright (C) 2026 F1xGOD. Original lock textures by F1xGOD.
 * Licensed with TrueUUID under the GNU LGPL 3.0; see LICENSE.
 * ================================================================
 */
/** One shared pixel-art source and palette for every loader drawing adapter. */
public final class BadgeArtwork {
    public static final int WIDTH = 11;
    public static final int HEIGHT = 16;
    public static final int GAP = 3;
    public static final int SAFE_MARGIN = 4;
    public static final int BODY_TOP = 5;
    public static final int BODY_BOTTOM = 16;

    private static final String[] PREMIUM = {
            "           ", "   11111   ", "  1233331  ", " 121111431 ", " 121   431 ",
            "55555555556", "57777777786", "57999999g56", "5799999gG56", "57g999gG956",
            "57Gg9gG9956", "579GgG99956", "5799G999956", "57999999956", "68555555556", "66666666666"
    };
    private static final String[] OFFLINE = {
            "   11111   ", "  1233331  ", " 121111431 ", " 121   431 ", " 121       ",
            "55555555556", "57777777786", "579R999R956", "57RrR9RrR56", "579RrRrR956",
            "5799RrR9956", "579RrRrR956", "57RrR9RrR56", "579R999R956", "68555555556", "66666666666"
    };
    /** User-supplied single-crop.png, preserved pixel-for-pixel on an 11x16 transparent canvas. */
    private static final String[] SINGLEPLAYER = {
            "           ", "   11 11   ", "  123 331  ", " 1211 1431 ", " 121   431 ",
            "55555555556", "57777777786", "57ss9999956", "57SSs99ss56", "5799S9sSS56",
            "5799s9S9956", "57ssS9s9956", "57SS99Sss56", "5799999SS56", "68555555556",
            "66666666666"
    };

    private static final List<PixelRun> PREMIUM_RUNS = runs(PREMIUM);
    private static final List<PixelRun> OFFLINE_RUNS = runs(OFFLINE);
    private static final List<PixelRun> SINGLEPLAYER_RUNS = runs(SINGLEPLAYER);

    /** Precomputed non-transparent horizontal runs; adapters only bind them to their fill API. */
    public static List<PixelRun> runsFor(ConfirmedAccountStatus status) {
        return switch (status) {
            case PREMIUM, LAN_PREMIUM -> PREMIUM_RUNS;
            case OFFLINE -> OFFLINE_RUNS;
            case SINGLEPLAYER -> SINGLEPLAYER_RUNS;
        };
    }

    private static List<PixelRun> runs(String[] rows) {
        if (rows.length != HEIGHT) throw new IllegalArgumentException("badge height mismatch");
        List<PixelRun> result = new ArrayList<>();
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            if (line.length() != WIDTH) throw new IllegalArgumentException("badge width mismatch");
            for (int column = 0; column < line.length();) {
                char pixel = line.charAt(column);
                if (pixel == ' ') { column++; continue; }
                int length = 1;
                while (column + length < line.length() && line.charAt(column + length) == pixel) length++;
                result.add(new PixelRun(column, row, length, pixel));
                column += length;
            }
        }
        return List.copyOf(result);
    }

    public record PixelRun(int x, int y, int length, char paletteKey) {}

    public static int argb(char pixel, float alpha) {
        int rgb = switch (pixel) {
            case '1' -> 0x575C71;
            case '2' -> 0xC9CBD8;
            case '3' -> 0xACB0C1;
            case '4' -> 0x3F4656;
            case '5' -> 0xB26411;
            case '6' -> 0x752702;
            case '7' -> 0xFDF55F;
            case '8' -> 0xDC9613;
            case '9' -> 0xE9B114;
            case 'g' -> 0x4AB85C;
            case 'G' -> 0x259B4A;
            case 'r' -> 0xEE3934;
            case 'R' -> 0xBD2E2E;
            case 's' -> 0x9CA3AF;
            case 'S' -> 0x4B5563;
            default -> 0;
        };
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | rgb;
    }

    private BadgeArtwork() {}
}
