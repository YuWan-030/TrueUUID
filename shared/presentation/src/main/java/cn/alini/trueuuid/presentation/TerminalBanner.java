package cn.alini.trueuuid.presentation;

import java.io.Console;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Decorative startup line with the gradient wordmark, written straight to an interactive terminal.
 *
 * <p>Loader log files record messages verbatim on some platforms, so colour never goes through the
 * logger. The line uses the richest colour format the terminal is known to render: 24-bit colour,
 * the 256-colour palette, or the basic 16 colours. It prints nothing without an interactive
 * terminal, on a plain Windows console that would show raw escape codes, or when
 * {@code NO_COLOR} is set. The plain log line that tests and operators read is unchanged.
 */
public final class TerminalBanner {
    private static final String ESCAPE = "\u001B[";
    private static final String RESET = ESCAPE + "0m";

    /** The richest colour format a terminal is known to render. */
    enum ColorDepth { NONE, BASIC, INDEXED, TRUE_COLOR }

    public static boolean print(String detail) {
        return print(detail, depth(System.getenv()), System.console());
    }

    static boolean print(String detail, ColorDepth depth, Console console) {
        Objects.requireNonNull(detail, "detail");
        if (depth == ColorDepth.NONE || console == null || !interactive(console)) return false;
        console.writer().println(line(detail, depth));
        console.writer().flush();
        return true;
    }

    static ColorDepth depth(Map<String, String> environment) {
        String noColor = environment.get("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) return ColorDepth.NONE;
        String term = environment.getOrDefault("TERM", "").toLowerCase(Locale.ROOT);
        if (term.equals("dumb")) return ColorDepth.NONE;
        String colorTerm = environment.getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);
        if (colorTerm.equals("truecolor") || colorTerm.equals("24bit") || environment.containsKey("WT_SESSION")) {
            return ColorDepth.TRUE_COLOR;
        }
        boolean ansiHost = "ON".equalsIgnoreCase(environment.get("ConEmuANSI")) || environment.containsKey("ANSICON");
        if (term.contains("256color")) return ColorDepth.INDEXED;
        if (!term.isEmpty() || ansiHost) return ColorDepth.BASIC;
        // A Windows console without a terminal type does not interpret escape codes.
        return ColorDepth.NONE;
    }

    static String line(String detail, ColorDepth depth) {
        Objects.requireNonNull(detail, "detail");
        if (depth == ColorDepth.NONE) return BrandText.WORDMARK + " " + detail;
        return wordmark(depth) + " " + ESCAPE + "37m" + detail + RESET;
    }

    static String wordmark(ColorDepth depth) {
        if (depth == ColorDepth.TRUE_COLOR) return BrandText.ansiWordmark();
        if (depth == ColorDepth.NONE) return BrandText.WORDMARK;
        List<BrandText.Glyph> glyphs = BrandText.glyphs();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < glyphs.size(); index++) {
            BrandText.Glyph glyph = glyphs.get(index);
            String color = depth == ColorDepth.INDEXED
                    ? "38;5;" + indexedColor(glyph.rgb())
                    : index < glyphs.size() / 2 ? "34" : "36";
            text.append(ESCAPE).append("1;").append(color).append('m').append(glyph.text());
        }
        return text.append(RESET).toString();
    }

    /** Nearest colour in the 6x6x6 cube of the xterm 256-colour palette. */
    static int indexedColor(int rgb) {
        return 16 + 36 * cubeLevel((rgb >> 16) & 0xFF) + 6 * cubeLevel((rgb >> 8) & 0xFF) + cubeLevel(rgb & 0xFF);
    }

    private static int cubeLevel(int channel) {
        if (channel < 48) return 0;
        if (channel < 115) return 1;
        return (channel - 35) / 40;
    }

    /** Java 22 and later return a console even when output is redirected, so ask it directly. */
    private static boolean interactive(Console console) {
        try {
            Method isTerminal = Console.class.getMethod("isTerminal");
            return (boolean) isTerminal.invoke(console);
        } catch (NoSuchMethodException olderJava) {
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private TerminalBanner() {
    }
}
