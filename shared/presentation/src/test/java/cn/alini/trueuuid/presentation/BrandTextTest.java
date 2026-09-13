package cn.alini.trueuuid.presentation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.alini.trueuuid.presentation.TerminalBanner.ColorDepth.BASIC;
import static cn.alini.trueuuid.presentation.TerminalBanner.ColorDepth.INDEXED;
import static cn.alini.trueuuid.presentation.TerminalBanner.ColorDepth.NONE;
import static cn.alini.trueuuid.presentation.TerminalBanner.ColorDepth.TRUE_COLOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandTextTest {
    private static String plain(String text) {
        return text.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    @Test void reproducesTheApprovedWordmarkColoursExactly() {
        List<String> expected = List.of("0D61D0", "0E69D2", "0E72D4", "0F7AD6", "1082D7", "118AD9", "1193DB", "129BDD");
        assertEquals(expected, BrandText.glyphs().stream().map(BrandText.Glyph::hex).toList());
        assertEquals(BrandText.WORDMARK,
                BrandText.glyphs().stream().map(BrandText.Glyph::text).collect(Collectors.joining()));
    }

    @Test void terminalWordmarkIsBoldTrueColourAndResets() {
        String ansi = BrandText.ansiWordmark();
        assertTrue(ansi.startsWith("\u001B[1;38;2;13;97;208mT"));
        assertTrue(ansi.contains("\u001B[1;38;2;18;155;221mD"));
        assertTrue(ansi.endsWith("\u001B[0m"));
        assertEquals(BrandText.WORDMARK, plain(ansi));
    }

    @Test void singleGlyphGradientUsesTheStartColour() {
        assertEquals(0x0D61D0, BrandText.gradient("T", BrandText.START_RGB, BrandText.END_RGB).get(0).rgb());
    }

    @Test void terminalColourDepthFollowsWhatTheTerminalSupports() {
        assertEquals(TRUE_COLOR, TerminalBanner.depth(Map.of("TERM", "xterm-256color", "COLORTERM", "truecolor")));
        assertEquals(TRUE_COLOR, TerminalBanner.depth(Map.of("WT_SESSION", "1")));
        assertEquals(INDEXED, TerminalBanner.depth(Map.of("TERM", "screen-256color")));
        assertEquals(BASIC, TerminalBanner.depth(Map.of("TERM", "xterm")));
        assertEquals(BASIC, TerminalBanner.depth(Map.of("ConEmuANSI", "ON")));
        assertEquals(NONE, TerminalBanner.depth(Map.of()));
        assertEquals(NONE, TerminalBanner.depth(Map.of("TERM", "dumb")));
        assertEquals(NONE, TerminalBanner.depth(Map.of("TERM", "xterm", "COLORTERM", "truecolor", "NO_COLOR", "1")));
    }

    @Test void everyColourDepthRendersTheSameWords() {
        for (TerminalBanner.ColorDepth depth : TerminalBanner.ColorDepth.values()) {
            assertEquals("TrueUUID adapter loaded", plain(TerminalBanner.line("adapter loaded", depth)), depth.name());
        }
        assertTrue(TerminalBanner.wordmark(INDEXED).startsWith("\u001B[1;38;5;26mT"));
        assertTrue(TerminalBanner.wordmark(INDEXED).contains("\u001B[1;38;5;38mD"));
        assertTrue(TerminalBanner.wordmark(BASIC).startsWith("\u001B[1;34mT"));
        assertTrue(TerminalBanner.wordmark(BASIC).contains("\u001B[1;36mU"));
        assertFalse(TerminalBanner.line("adapter loaded", NONE).contains("\u001B"));
    }

    @Test void bannerIsSilentWithoutATerminal() {
        assertFalse(TerminalBanner.print("adapter loaded", TRUE_COLOR, null));
        assertFalse(TerminalBanner.print("adapter loaded", NONE, null));
    }
}
