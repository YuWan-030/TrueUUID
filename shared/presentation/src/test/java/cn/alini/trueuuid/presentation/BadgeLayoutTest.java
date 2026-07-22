package cn.alini.trueuuid.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BadgeLayoutTest {
    @Test void allArtworkUsesBoundedImmutableRuns() {
        for (ConfirmedAccountStatus status : ConfirmedAccountStatus.values()) {
            var runs = BadgeArtwork.runsFor(status);
            assertFalse(runs.isEmpty());
            assertTrue(runs.stream().allMatch(run -> run.x() >= 0 && run.y() >= 0 && run.length() > 0
                    && run.x() + run.length() <= BadgeArtwork.WIDTH && run.y() < BadgeArtwork.HEIGHT));
            assertThrows(UnsupportedOperationException.class, () -> runs.add(runs.get(0)));
        }
    }

    @Test void cornerPlacementAndTextAlignmentAreCentralized() {
        BadgeLayout layout = BadgeLayout.calculate(320, 180, 50, 9, 1.0F,
                true, true, 0, 0);
        assertEquals(320 - BadgeArtwork.SAFE_MARGIN - BadgeArtwork.WIDTH - BadgeArtwork.GAP - 50,
                layout.x());
        assertEquals(180 - BadgeArtwork.SAFE_MARGIN - BadgeArtwork.HEIGHT, layout.y());
        assertEquals(layout.x() + BadgeArtwork.WIDTH + BadgeArtwork.GAP, layout.textX());
    }
}
