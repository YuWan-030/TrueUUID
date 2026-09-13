package cn.alini.trueuuid.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The TrueUUID wordmark as bold per-letter colours, shared by every mod and plugin renderer.
 *
 * <p>The colours are a linear gradient from {@link #START_RGB} to {@link #END_RGB} with each channel
 * rounded to the nearest integer. Minecraft components, Bukkit colour codes, Adventure components,
 * and terminal escapes are all rendered from {@link #glyphs()}, so every surface shows identical colours.
 */
public final class BrandText {
    public static final String WORDMARK = "TrueUUID";
    public static final int START_RGB = 0x0D61D0;
    public static final int END_RGB = 0x129BDD;

    /** One visible character of the wordmark and its 24-bit RGB colour. */
    public record Glyph(String text, int rgb) {
        public Glyph {
            Objects.requireNonNull(text, "text");
            if (rgb < 0 || rgb > 0xFFFFFF) throw new IllegalArgumentException("rgb out of range");
        }

        public int red() { return (rgb >> 16) & 0xFF; }
        public int green() { return (rgb >> 8) & 0xFF; }
        public int blue() { return rgb & 0xFF; }

        /** Upper-case six-digit hexadecimal colour without a leading marker. */
        public String hex() { return String.format(Locale.ROOT, "%06X", rgb); }
    }

    private static final List<Glyph> GLYPHS = gradient(WORDMARK, START_RGB, END_RGB);

    public static List<Glyph> glyphs() {
        return GLYPHS;
    }

    static List<Glyph> gradient(String text, int startRgb, int endRgb) {
        int[] codePoints = Objects.requireNonNull(text, "text").codePoints().toArray();
        List<Glyph> glyphs = new ArrayList<>(codePoints.length);
        for (int index = 0; index < codePoints.length; index++) {
            double position = codePoints.length == 1 ? 0.0 : (double) index / (codePoints.length - 1);
            int rgb = (channel(startRgb >> 16, endRgb >> 16, position) << 16)
                    | (channel(startRgb >> 8, endRgb >> 8, position) << 8)
                    | channel(startRgb, endRgb, position);
            glyphs.add(new Glyph(new String(Character.toChars(codePoints[index])), rgb));
        }
        return List.copyOf(glyphs);
    }

    /** The bold wordmark as 24-bit terminal escapes, followed by a full reset. */
    public static String ansiWordmark() {
        StringBuilder text = new StringBuilder();
        for (Glyph glyph : GLYPHS) {
            text.append("\u001B[1;38;2;").append(glyph.red()).append(';').append(glyph.green()).append(';')
                    .append(glyph.blue()).append('m').append(glyph.text());
        }
        return text.append("\u001B[0m").toString();
    }

    private static int channel(int start, int end, double position) {
        int from = start & 0xFF;
        int to = end & 0xFF;
        return (int) Math.round(from + (to - from) * position);
    }

    private BrandText() {
    }
}
